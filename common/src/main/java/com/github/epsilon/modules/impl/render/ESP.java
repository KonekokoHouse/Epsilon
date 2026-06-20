package com.github.epsilon.modules.impl.render;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BlockListSetting;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.utils.timer.TimerUtils;
import com.google.common.collect.Lists;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ESP extends Module {

    public static final ESP INSTANCE = new ESP();

    private ESP() {
        super("ESP", Category.RENDER);
    }

    private final BoolSetting blocksValue = boolSetting("Blocks", true);
    private final BlockListSetting blockListValue = blockListSetting("Block List",
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
            ), blocksValue::getValue);
    private final BoolSetting illegals = boolSetting("Illegals", true);
    private final DoubleSetting range = doubleSetting("Range", 64.0, 1.0, 128.0, 1.0);
    private final ColorSetting sideColor = colorSetting("Side Color", new Color(160, 210, 255, 30));
    private final ColorSetting lineColor = colorSetting("Line Color", new Color(160, 210, 255, 180));
    private final BoolSetting blur = boolSetting("Blur", true);
    private final DoubleSetting blurStrength = doubleSetting("Blur Strength", 5.0, 0.0, 16.0, 0.5, blur::getValue);

    private final ExecutorService searchThread = Executors.newSingleThreadExecutor();
    private final TimerUtils searchTimer = new TimerUtils();
    private boolean canContinue;

    public static List<AABB> boxes = new ArrayList<>();

    @Override
    protected void onEnable() {
        boxes.clear();
        canContinue = true;
    }

    @EventHandler
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        if (searchTimer.every(1000) && canContinue) {
            CompletableFuture.supplyAsync(this::scan, searchThread).thenAcceptAsync(newAABBList -> {
                boxes = newAABBList;
                canContinue = true;
            }, Util.backgroundExecutor());
            canContinue = false;
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!boxes.isEmpty()) {
            for (AABB aabb : Lists.newArrayList(boxes)) {
                if (blur.getValue()) Managers.RENDER.addBlurredBox(aabb, blurStrength.getValue());
                Managers.RENDER.addFilledBox(aabb, sideColor.getValue());
                Managers.RENDER.addOutlineBox(aabb, lineColor.getValue());
            }
        }
    }

    private List<AABB> scan() {
        List<AABB> boxes = new ArrayList<>();
        int startX = Mth.floor(mc.player.getX() - range.getValue());
        int endX = Mth.ceil(mc.player.getX() + range.getValue());
        int startY = mc.level.getMinY() + 1;
        int endY = mc.level.getMaxY();
        int startZ = Mth.floor(mc.player.getZ() - range.getValue());
        int endZ = Mth.ceil(mc.player.getZ() + range.getValue());

        for (int x = startX; x <= endX; x++) {
            for (int y = startY; y <= endY; y++) {
                for (int z = startZ; z <= endZ; z++) {
                    BlockPos blockPos = new BlockPos(x, y, z);
                    BlockState bs = mc.level.getBlockState(blockPos);
                    if (shouldAdd(bs.getBlock(), blockPos)) {
                        boxes.add(getShapeAABB(blockPos));
                    }
                }
            }
        }
        return boxes;
    }

    private boolean shouldAdd(Block block, BlockPos pos) {
        if (block instanceof AirBlock) return false;
        if (blockListValue.getValue().contains(block)) return true;
        if (illegals.getValue()) return isIllegal(block, pos);
        return false;
    }

    private boolean isIllegal(Block block, BlockPos pos) {
        if (block instanceof CommandBlock || block instanceof BarrierBlock) return true;

        if (block == Blocks.BEDROCK) {
            if (!Level.NETHER.equals(mc.level.dimension())) {
                return pos.getY() > 4;
            } else {
                return pos.getY() > 127 || (pos.getY() < 123 && pos.getY() > 4);
            }
        }
        return false;
    }

    private AABB getShapeAABB(BlockPos blockPos) {
        VoxelShape shape = mc.level.getBlockState(blockPos).getShape(mc.level, blockPos);
        return shape.isEmpty() ? new AABB(blockPos) : shape.bounds().move(blockPos);
    }

}
