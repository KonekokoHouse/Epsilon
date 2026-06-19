package com.github.epsilon.modules.impl.render;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BlockListSetting;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

import java.awt.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ESP extends Module {

    public static final ESP INSTANCE = new ESP();

    private ESP() {
        super("ESP", Category.RENDER);
    }

    private final BoolSetting blocks = boolSetting("Blocks", true);
    private final BlockListSetting blockList = blockListSetting("Block List",
            List.of(
                    Blocks.CHEST,
                    Blocks.TRAPPED_CHEST,
                    Blocks.ENDER_CHEST,
                    Blocks.BARREL,
                    Blocks.SHULKER_BOX,
                    Blocks.WHITE_SHULKER_BOX,
                    Blocks.ORANGE_SHULKER_BOX,
                    Blocks.MAGENTA_SHULKER_BOX,
                    Blocks.LIGHT_BLUE_SHULKER_BOX,
                    Blocks.YELLOW_SHULKER_BOX,
                    Blocks.LIME_SHULKER_BOX,
                    Blocks.PINK_SHULKER_BOX,
                    Blocks.GRAY_SHULKER_BOX,
                    Blocks.LIGHT_GRAY_SHULKER_BOX,
                    Blocks.CYAN_SHULKER_BOX,
                    Blocks.PURPLE_SHULKER_BOX,
                    Blocks.BLUE_SHULKER_BOX,
                    Blocks.BROWN_SHULKER_BOX,
                    Blocks.GREEN_SHULKER_BOX,
                    Blocks.RED_SHULKER_BOX,
                    Blocks.BLACK_SHULKER_BOX
            ), blocks::getValue);
    private final DoubleSetting range = doubleSetting("Range", 64.0, 1.0, 128.0, 1.0);
    private final ColorSetting color = colorSetting("Color", new Color(160, 210, 255, 30));
    private final BoolSetting blur = boolSetting("Blur", true);
    private final DoubleSetting blurStrength = doubleSetting("Blur Strength", 5.0, 0.0, 16.0, 0.5, blur::getValue);

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (blocks.getValue()) {
            double maxRange = range.getValue();
            int renderDistance = mc.options.renderDistance().get();

            BlockPos playerPos = mc.player.blockPosition();
            ChunkPos playerChunk = mc.player.chunkPosition();
            Set<Block> selectedBlocks = blockList.asSet();
            Set<BlockPos> renderedBlocks = new HashSet<>();

            if (selectedBlocks.isEmpty()) {
                return;
            }

            for (int x = -renderDistance; x <= renderDistance; x++) {
                for (int z = -renderDistance; z <= renderDistance; z++) {
                    int chunkX = playerChunk.x() + x;
                    int chunkZ = playerChunk.z() + z;
                    if (!mc.level.hasChunk(chunkX, chunkZ)) {
                        continue;
                    }

                    LevelChunk chunk = mc.level.getChunkSource().getChunkNow(chunkX, chunkZ);
                    if (chunk == null) {
                        continue;
                    }

                    for (BlockEntity entity : chunk.getBlockEntities().values()) {
                        BlockPos blockPos = entity.getBlockPos();
                        BlockState state = mc.level.getBlockState(blockPos);
                        if (!selectedBlocks.contains(state.getBlock())) {
                            continue;
                        }
                        renderBlock(blockPos, state, selectedBlocks, renderedBlocks, playerPos, maxRange);
                    }
                }
            }

            int blockRange = (int) Math.ceil(maxRange);
            int minY = mc.level.getMinY();
            int maxY = mc.level.getMaxY();
            BlockPos min = new BlockPos(playerPos.getX() - blockRange, Math.max(minY, playerPos.getY() - blockRange), playerPos.getZ() - blockRange);
            BlockPos max = new BlockPos(playerPos.getX() + blockRange, Math.min(maxY, playerPos.getY() + blockRange), playerPos.getZ() + blockRange);
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                if (!mc.level.isLoaded(pos) || renderedBlocks.contains(pos)) {
                    continue;
                }
                BlockState state = mc.level.getBlockState(pos);
                if (selectedBlocks.contains(state.getBlock())) {
                    renderBlock(pos.immutable(), state, selectedBlocks, renderedBlocks, playerPos, maxRange);
                }
            }
        }
    }

    private void renderBlock(BlockPos blockPos, BlockState state, Set<Block> selectedBlocks, Set<BlockPos> renderedBlocks, BlockPos playerPos, double maxRange) {
        if (blockPos.distSqr(playerPos) > maxRange * maxRange || !renderedBlocks.add(blockPos)) {
            return;
        }

        AABB box = getAABB(blockPos);
        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos connectedPos = ChestBlock.getConnectedBlockPos(blockPos, state);
            if (mc.level.isLoaded(connectedPos)) {
                BlockState connectedState = mc.level.getBlockState(connectedPos);
                if (selectedBlocks.contains(connectedState.getBlock())
                        && connectedState.getBlock() == state.getBlock()
                        && connectedState.getValue(ChestBlock.TYPE) == state.getValue(ChestBlock.TYPE).getOpposite()
                        && connectedState.getValue(ChestBlock.FACING) == state.getValue(ChestBlock.FACING)) {
                    box = box.minmax(getAABB(connectedPos));
                    renderedBlocks.add(connectedPos);
                }
            }
        }

        if (blur.getValue()) Managers.RENDER.addBlurredBox(box, blurStrength.getValue());
        Managers.RENDER.addFilledBox(box, color.getValue());
    }

    private AABB getAABB(BlockPos blockPos) {
        return mc.level.getBlockState(blockPos).getShape(mc.level, blockPos).bounds().move(blockPos);
    }

}
