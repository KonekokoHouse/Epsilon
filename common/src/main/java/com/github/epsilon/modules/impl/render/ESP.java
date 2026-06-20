package com.github.epsilon.modules.impl.render;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.ClientTickEvent;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BlockListSetting;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;

import java.awt.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ESP extends Module {

    private static final double FILLED_BOX_DEFLATE = 0.01;

    public static final ESP INSTANCE = new ESP();

    private ESP() {
        super("ESP", Category.RENDER);
    }

    private final Supplier<List<Block>> defaultBlockList = Suppliers.memoize(() -> {
        final var list = new ArrayList<>(List.of(
                Blocks.CHEST,
                Blocks.TRAPPED_CHEST,
                Blocks.ENDER_CHEST,
                Blocks.BARREL,
                Blocks.SHULKER_BOX
        ));
        list.addAll(Blocks.DYED_SHULKER_BOX.asList());
        return list;
    });

    private final BoolSetting blocks = boolSetting("Blocks", true);
    private final BlockListSetting blockList = blockListSetting("Block List",
            defaultBlockList.get(), blocks::getValue);
    private final DoubleSetting range = doubleSetting("Range", 64.0, 1.0, 128.0, 1.0);
    private final IntSetting scanBudget = intSetting("Scan Budget", 4, 1, 16, 1, blocks::getValue);
    private final IntSetting eagerChunkRadius = intSetting("Eager Radius", 1, 0, 2, 1, blocks::getValue);
    private final ColorSetting color = colorSetting("Color", new Color(160, 210, 255, 30));
    private final BoolSetting blur = boolSetting("Blur", true);
    private final DoubleSetting blurStrength = doubleSetting("Blur Strength", 5.0, 0.0, 16.0, 0.5, blur::getValue);
    private List<Block> cachedBlockListValue = Collections.emptyList();
    private Set<Block> cachedSelectedBlocks = Collections.emptySet();
    private Set<Block> cachedBlockEntityBlocks = Collections.emptySet();
    private Set<Block> cachedNormalBlocks = Collections.emptySet();
    private final Map<Long, List<CachedBlockEntry>> cachedChunkEntries = new HashMap<>();
    private List<List<CachedBlockEntry>> renderChunkEntries = Collections.emptyList();
    private final Set<Long> queuedChunkKeys = new HashSet<>();
    private final Set<Long> dirtyChunkKeys = new HashSet<>();
    private final Queue<Long> pendingChunkKeys = new ArrayDeque<>();
    private final Queue<PendingChunkAction> pendingChunkActions = new ConcurrentLinkedQueue<>();
    private final Set<BlockPos> renderedBlocks = new HashSet<>();
    private Object cachedLevelRef;
    private long lastCenterChunkKey = Long.MIN_VALUE;
    private int lastChunkRadius = -1;
    private int lastRenderDistance = -1;
    private int lastPlayerSectionY = Integer.MIN_VALUE;
    private double lastScanRange = -1.0;

    @Override
    protected void onEnable() {
        clearChunkCache();
    }

    @Override
    protected void onDisable() {
        clearChunkCache();
    }

    @EventHandler
    private void onClientTick(ClientTickEvent.Post event) {
        if (!blocks.getValue() || nullCheck()) {
            clearChunkCache();
            return;
        }

        refreshBlockCaches();
        if (cachedSelectedBlocks.isEmpty()) {
            clearChunkCache();
            return;
        }

        applyPendingChunkActions();
        int renderDistance = mc.options.renderDistance().get();
        double scanRange = range.getValue();
        int chunkRadius = Math.max(1, Math.min(renderDistance, (int) Math.ceil(scanRange / 16.0) + 1));
        ChunkPos centerChunk = mc.player.chunkPosition();
        int playerSectionY = mc.player.blockPosition().getY() >> 4;
        long centerChunkKey = chunkKey(centerChunk.x(), centerChunk.z());
        boolean contextChanged = cachedLevelRef != mc.level
                || renderDistance != lastRenderDistance
                || chunkRadius != lastChunkRadius
                || playerSectionY != lastPlayerSectionY
                || Double.compare(scanRange, lastScanRange) != 0
                || centerChunkKey != lastCenterChunkKey;

        if (contextChanged) {
            cachedLevelRef = mc.level;
            lastRenderDistance = renderDistance;
            lastChunkRadius = chunkRadius;
            lastPlayerSectionY = playerSectionY;
            lastScanRange = scanRange;
            lastCenterChunkKey = centerChunkKey;
            pruneFarChunks(centerChunk, chunkRadius);
            rebuildScanQueue(centerChunk, chunkRadius);
            flushImmediateChunks(centerChunk, chunkRadius, scanRange);
        } else if (!dirtyChunkKeys.isEmpty()) {
            enqueueDirtyChunks(centerChunk, chunkRadius);
            flushImmediateChunks(centerChunk, chunkRadius, scanRange);
        }

        int tickBudget = scanBudget.getValue();
        for (int i = 0; i < tickBudget && !pendingChunkKeys.isEmpty(); i++) {
            Long chunkKey = pendingChunkKeys.poll();
            if (chunkKey == null) {
                break;
            }

            queuedChunkKeys.remove(chunkKey);
            dirtyChunkKeys.remove(chunkKey);
            scanChunk(chunkKey, scanRange);
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (nullCheck()) {
            return;
        }

        if (event.getPacket() instanceof ClientboundBlockUpdatePacket packet) {
            queueChunkDirty(chunkKey(packet.getPos().getX() >> 4, packet.getPos().getZ() >> 4));
            return;
        }

        if (event.getPacket() instanceof ClientboundSectionBlocksUpdatePacket packet) {
            packet.runUpdates((pos, state) -> queueChunkDirty(chunkKey(pos.getX() >> 4, pos.getZ() >> 4)));
            return;
        }

        if (event.getPacket() instanceof ClientboundLevelChunkWithLightPacket packet) {
            queueChunkDirty(chunkKey(packet.getX(), packet.getZ()));
            return;
        }

        if (event.getPacket() instanceof ClientboundForgetLevelChunkPacket packet) {
            queueChunkRemoval(packet.pos().x(), packet.pos().z());
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!blocks.getValue() || nullCheck()) {
            return;
        }

        refreshBlockCaches();
        if (cachedSelectedBlocks.isEmpty()) {
            return;
        }

        double maxRange = range.getValue();
        double maxRangeSqr = maxRange * maxRange;
        boolean drawBlur = blur.getValue();
        double blurAmount = blurStrength.getValue();
        BlockPos playerPos = mc.player.blockPosition();
        renderedBlocks.clear();

        for (List<CachedBlockEntry> entries : renderChunkEntries) {
            for (CachedBlockEntry entry : entries) {
                if (!mc.level.isLoaded(entry.blockPos())) {
                    continue;
                }
                renderBlock(entry, renderedBlocks, playerPos, maxRangeSqr, drawBlur, blurAmount);
            }
        }
    }

    private void refreshBlockCaches() {
        List<Block> selectedBlocks = blockList.getValue();
        if (selectedBlocks == null) {
            selectedBlocks = Collections.emptyList();
        }

        if (Objects.equals(selectedBlocks, cachedBlockListValue)) {
            return;
        }

        cachedBlockListValue = List.copyOf(selectedBlocks);
        clearChunkCache();
        if (selectedBlocks.isEmpty()) {
            cachedSelectedBlocks = Collections.emptySet();
            cachedBlockEntityBlocks = Collections.emptySet();
            cachedNormalBlocks = Collections.emptySet();
            return;
        }

        Set<Block> all = new LinkedHashSet<>(selectedBlocks);
        Set<Block> blockEntities = new LinkedHashSet<>();
        Set<Block> normalBlocks = new LinkedHashSet<>();
        for (Block block : all) {
            if (block.defaultBlockState().hasBlockEntity()) {
                blockEntities.add(block);
            } else {
                normalBlocks.add(block);
            }
        }

        cachedSelectedBlocks = Collections.unmodifiableSet(all);
        cachedBlockEntityBlocks = Collections.unmodifiableSet(blockEntities);
        cachedNormalBlocks = Collections.unmodifiableSet(normalBlocks);
    }

    private void rebuildScanQueue(ChunkPos centerChunk, int chunkRadius) {
        pendingChunkKeys.clear();
        queuedChunkKeys.clear();
        enqueueDirtyChunks(centerChunk, chunkRadius);
        for (long chunkKey : buildScanOrder(centerChunk, chunkRadius)) {
            if (cachedChunkEntries.containsKey(chunkKey) || !queuedChunkKeys.add(chunkKey)) {
                continue;
            }
            pendingChunkKeys.add(chunkKey);
        }
    }

    private void enqueueDirtyChunks(ChunkPos centerChunk, int chunkRadius) {
        if (dirtyChunkKeys.isEmpty()) {
            return;
        }

        List<Long> dirty = new ArrayList<>(dirtyChunkKeys);
        dirty.sort(Comparator.comparingLong(chunkKey -> chunkDistanceSqr(centerChunk, chunkKey)));
        for (long chunkKey : dirty) {
            if (!isChunkWithinRadius(centerChunk, chunkRadius, chunkKey) || !queuedChunkKeys.add(chunkKey)) {
                continue;
            }
            pendingChunkKeys.add(chunkKey);
        }
    }

    private void applyPendingChunkActions() {
        PendingChunkAction action;
        while ((action = pendingChunkActions.poll()) != null) {
            action.apply(this);
        }
    }

    private List<Long> buildScanOrder(ChunkPos centerChunk, int chunkRadius) {
        List<Long> chunkKeys = new ArrayList<>();
        for (int x = -chunkRadius; x <= chunkRadius; x++) {
            for (int z = -chunkRadius; z <= chunkRadius; z++) {
                int chunkX = centerChunk.x() + x;
                int chunkZ = centerChunk.z() + z;
                chunkKeys.add(chunkKey(chunkX, chunkZ));
            }
        }
        chunkKeys.sort(Comparator.comparingLong(chunkKey -> chunkDistanceSqr(centerChunk, chunkKey)));
        return chunkKeys;
    }

    private void scanChunk(long chunkKey, double scanRange) {
        int chunkX = chunkX(chunkKey);
        int chunkZ = chunkZ(chunkKey);
        if (!mc.level.hasChunk(chunkX, chunkZ)) {
            cachedChunkEntries.remove(chunkKey);
            return;
        }

        LevelChunk chunk = mc.level.getChunkSource().getChunkNow(chunkX, chunkZ);
        if (chunk == null) {
            cachedChunkEntries.remove(chunkKey);
            return;
        }

        List<CachedBlockEntry> entries = new ArrayList<>();
        if (!cachedBlockEntityBlocks.isEmpty()) {
            for (BlockEntity entity : chunk.getBlockEntities().values()) {
                BlockState state = entity.getBlockState();
                if (!cachedBlockEntityBlocks.contains(state.getBlock())) {
                    continue;
                }
                BlockPos pos = entity.getBlockPos().immutable();
                entries.add(new CachedBlockEntry(pos, state, getAABB(pos, state)));
            }
        }

        if (!cachedNormalBlocks.isEmpty()) {
            int minY = Math.max(mc.level.getMinY(), (int) Math.floor(mc.player.getY() - scanRange) - 16);
            int maxY = Math.min(mc.level.getMaxY() - 1, (int) Math.ceil(mc.player.getY() + scanRange) + 16);
            int minSectionIndex = Math.max(0, (minY - chunk.getMinY()) >> 4);
            int maxSectionIndex = Math.min(chunk.getSections().length - 1, (maxY - chunk.getMinY()) >> 4);
            int baseX = chunk.getPos().getMinBlockX();
            int baseZ = chunk.getPos().getMinBlockZ();

            for (int sectionIndex = minSectionIndex; sectionIndex <= maxSectionIndex; sectionIndex++) {
                LevelChunkSection section = chunk.getSections()[sectionIndex];
                if (section.hasOnlyAir() || !section.maybeHas(state -> cachedNormalBlocks.contains(state.getBlock()))) {
                    continue;
                }

                int sectionBaseY = chunk.getMinY() + (sectionIndex << 4);
                int localMinY = Math.max(0, minY - sectionBaseY);
                int localMaxY = Math.min(15, maxY - sectionBaseY);
                for (int localY = localMinY; localY <= localMaxY; localY++) {
                    for (int localX = 0; localX < 16; localX++) {
                        for (int localZ = 0; localZ < 16; localZ++) {
                            BlockState state = section.getBlockState(localX, localY, localZ);
                            if (!cachedNormalBlocks.contains(state.getBlock())) {
                                continue;
                            }

                            BlockPos pos = new BlockPos(baseX + localX, sectionBaseY + localY, baseZ + localZ);
                            entries.add(new CachedBlockEntry(pos, state, getAABB(pos, state)));
                        }
                    }
                }
            }
        }

        cachedChunkEntries.put(chunkKey, entries);
        refreshRenderSnapshot();
    }

    private void flushImmediateChunks(ChunkPos centerChunk, int chunkRadius, double scanRange) {
        int immediateRadius = Math.min(chunkRadius, eagerChunkRadius.getValue());
        if (immediateRadius <= 0) {
            return;
        }

        for (long chunkKey : buildScanOrder(centerChunk, immediateRadius)) {
            if (!dirtyChunkKeys.contains(chunkKey) && cachedChunkEntries.containsKey(chunkKey)) {
                continue;
            }

            pendingChunkKeys.remove(chunkKey);
            queuedChunkKeys.remove(chunkKey);
            dirtyChunkKeys.remove(chunkKey);
            scanChunk(chunkKey, scanRange);
        }
    }

    private void pruneFarChunks(ChunkPos centerChunk, int chunkRadius) {
        Set<Long> toRemove = new HashSet<>();
        for (long chunkKey : cachedChunkEntries.keySet()) {
            if (!isChunkWithinRadius(centerChunk, chunkRadius, chunkKey)) {
                toRemove.add(chunkKey);
            }
        }

        for (long chunkKey : toRemove) {
            cachedChunkEntries.remove(chunkKey);
            dirtyChunkKeys.remove(chunkKey);
            queuedChunkKeys.remove(chunkKey);
            pendingChunkKeys.remove(chunkKey);
        }
        if (!toRemove.isEmpty()) {
            refreshRenderSnapshot();
        }
    }

    private void markChunkDirty(long chunkKey) {
        dirtyChunkKeys.add(chunkKey);
        if (cachedLevelRef != mc.level) {
            return;
        }
        if (lastChunkRadius < 0 || lastCenterChunkKey == Long.MIN_VALUE) {
            return;
        }
        if (!isChunkWithinRadius(chunkX(lastCenterChunkKey), chunkZ(lastCenterChunkKey), lastChunkRadius, chunkKey)) {
            return;
        }
        if (queuedChunkKeys.add(chunkKey)) {
            pendingChunkKeys.add(chunkKey);
        }
    }

    private void removeChunk(int chunkX, int chunkZ) {
        long chunkKey = chunkKey(chunkX, chunkZ);
        cachedChunkEntries.remove(chunkKey);
        dirtyChunkKeys.remove(chunkKey);
        queuedChunkKeys.remove(chunkKey);
        pendingChunkKeys.remove(chunkKey);
        refreshRenderSnapshot();
    }

    private void clearChunkCache() {
        cachedChunkEntries.clear();
        renderChunkEntries = Collections.emptyList();
        dirtyChunkKeys.clear();
        queuedChunkKeys.clear();
        pendingChunkKeys.clear();
        pendingChunkActions.clear();
        renderedBlocks.clear();
        cachedLevelRef = null;
        lastCenterChunkKey = Long.MIN_VALUE;
        lastChunkRadius = -1;
        lastRenderDistance = -1;
        lastPlayerSectionY = Integer.MIN_VALUE;
        lastScanRange = -1.0;
    }

    private void renderBlock(CachedBlockEntry entry, Set<BlockPos> renderedBlocks, BlockPos playerPos, double maxRangeSqr, boolean drawBlur, double blurAmount) {
        BlockPos blockPos = entry.blockPos();
        if (blockPos.distSqr(playerPos) > maxRangeSqr || !renderedBlocks.add(blockPos)) {
            return;
        }

        BlockState state = entry.state();
        AABB box = entry.box();
        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos connectedPos = ChestBlock.getConnectedBlockPos(blockPos, state);
            if (mc.level.isLoaded(connectedPos)) {
                BlockState connectedState = mc.level.getBlockState(connectedPos);
                if (cachedSelectedBlocks.contains(connectedState.getBlock())
                        && connectedState.getBlock() == state.getBlock()
                        && connectedState.getValue(ChestBlock.TYPE) == state.getValue(ChestBlock.TYPE).getOpposite()
                        && connectedState.getValue(ChestBlock.FACING) == state.getValue(ChestBlock.FACING)) {
                    box = box.minmax(getAABB(connectedPos, connectedState));
                    renderedBlocks.add(connectedPos);
                }
            }
        }

        if (drawBlur) Managers.RENDER.addBlurredBox(box, blurAmount);
        Managers.RENDER.addFilledBox(getFilledRenderBox(box), color.getValue());
    }

    private AABB getAABB(BlockPos blockPos, BlockState state) {
        return state.getShape(mc.level, blockPos).bounds().move(blockPos);
    }

    private AABB getFilledRenderBox(AABB box) {
        double xDeflate = Math.min(FILLED_BOX_DEFLATE, box.getXsize() * 0.25);
        double yDeflate = Math.min(FILLED_BOX_DEFLATE, box.getYsize() * 0.25);
        double zDeflate = Math.min(FILLED_BOX_DEFLATE, box.getZsize() * 0.25);
        return box.deflate(xDeflate, yDeflate, zDeflate);
    }

    private void refreshRenderSnapshot() {
        renderChunkEntries = new ArrayList<>(cachedChunkEntries.values());
    }

    private boolean isChunkWithinRadius(ChunkPos centerChunk, int chunkRadius, long chunkKey) {
        return isChunkWithinRadius(centerChunk.x(), centerChunk.z(), chunkRadius, chunkKey);
    }

    private boolean isChunkWithinRadius(int centerChunkX, int centerChunkZ, int chunkRadius, long chunkKey) {
        int dx = Math.abs(chunkX(chunkKey) - centerChunkX);
        int dz = Math.abs(chunkZ(chunkKey) - centerChunkZ);
        return dx <= chunkRadius && dz <= chunkRadius;
    }

    private long chunkDistanceSqr(ChunkPos centerChunk, long chunkKey) {
        int dx = chunkX(chunkKey) - centerChunk.x();
        int dz = chunkZ(chunkKey) - centerChunk.z();
        return (long) dx * dx + (long) dz * dz;
    }

    private long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX & 0xFFFFFFFFL) | ((long) chunkZ & 0xFFFFFFFFL) << 32;
    }

    private int chunkX(long chunkKey) {
        return (int) chunkKey;
    }

    private int chunkZ(long chunkKey) {
        return (int) (chunkKey >> 32);
    }

    private void queueChunkDirty(long chunkKey) {
        pendingChunkActions.add(new MarkDirtyAction(chunkKey));
    }

    private void queueChunkRemoval(int chunkX, int chunkZ) {
        pendingChunkActions.add(new RemoveChunkAction(chunkX, chunkZ));
    }

    private record CachedBlockEntry(BlockPos blockPos, BlockState state, AABB box) {
    }

    private sealed interface PendingChunkAction permits MarkDirtyAction, RemoveChunkAction {

        void apply(ESP esp);

    }

    private record MarkDirtyAction(long chunkKey) implements PendingChunkAction {

        @Override
        public void apply(ESP esp) {
            esp.markChunkDirty(chunkKey);
        }

    }

    private record RemoveChunkAction(int chunkX, int chunkZ) implements PendingChunkAction {

        @Override
        public void apply(ESP esp) {
            esp.removeChunk(chunkX, chunkZ);
        }

    }

}
