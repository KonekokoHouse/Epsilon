package com.github.epsilon.modules.impl.render;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

import java.awt.*;
import java.util.HashSet;
import java.util.Set;

public class ESP extends Module {

    public static final ESP INSTANCE = new ESP();

    private ESP() {
        super("ESP", Category.RENDER);
    }

    private final BoolSetting chests = boolSetting("Chests", true);
    private final DoubleSetting range = doubleSetting("Range", 64.0, 1.0, 128.0, 1.0);
    private final ColorSetting color = colorSetting("Color", new Color(160, 210, 255, 30));
    private final BoolSetting blur = boolSetting("Blur", true);
    private final DoubleSetting blurStrength = doubleSetting("Blur Strength", 5.0, 0.0, 16.0, 0.5, blur::getValue);

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (chests.getValue()) {
            double maxRange = range.getValue();
            int renderDistance = mc.options.renderDistance().get();

            BlockPos playerPos = mc.player.blockPosition();
            ChunkPos playerChunk = mc.player.chunkPosition();
            Set<BlockPos> renderedChests = new HashSet<>();

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
                        if (!(entity instanceof RandomizableContainerBlockEntity)) {
                            continue;
                        }

                        BlockPos blockPos = entity.getBlockPos();
                        if (blockPos.distSqr(playerPos) > maxRange * maxRange || !renderedChests.add(blockPos)) {
                            continue;
                        }

                        AABB box = getAABB(blockPos);
                        BlockState state = mc.level.getBlockState(blockPos);
                        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                            BlockPos connectedPos = ChestBlock.getConnectedBlockPos(blockPos, state);
                            if (mc.level.isLoaded(connectedPos)) {
                                BlockState connectedState = mc.level.getBlockState(connectedPos);
                                if (connectedState.getBlock() == state.getBlock()
                                        && connectedState.getValue(ChestBlock.TYPE) == state.getValue(ChestBlock.TYPE).getOpposite()
                                        && connectedState.getValue(ChestBlock.FACING) == state.getValue(ChestBlock.FACING)) {
                                    box = box.minmax(getAABB(connectedPos));
                                    renderedChests.add(connectedPos);
                                }
                            }
                        }

                        if (blur.getValue()) Managers.RENDER.addBlurredBox(box, blurStrength.getValue());
                        Managers.RENDER.addFilledBox(box, color.getValue());
                    }
                }
            }
        }
    }

    private AABB getAABB(BlockPos blockPos) {
        return mc.level.getBlockState(blockPos).getShape(mc.level, blockPos).bounds().move(blockPos);
    }

}
