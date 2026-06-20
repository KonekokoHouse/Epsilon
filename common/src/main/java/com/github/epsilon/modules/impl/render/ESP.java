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
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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

    public static List<BlockVec> blocks = new ArrayList<>();

    @Override
    protected void onEnable() {
        blocks.clear();
        canContinue = true;
    }

    @EventHandler
    private void onPlayerTick(PlayerTickEvent event) {
        if (searchTimer.every(1000) && canContinue) {
            CompletableFuture.supplyAsync(this::scan, searchThread).thenAcceptAsync(this::sync, Util.backgroundExecutor());
            canContinue = false;
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (blocks.isEmpty()) return;

        for (BlockVec vec : Lists.newArrayList(blocks)) {
            if (vec.getDistance(mc.player.position()) > range.getValue()) {
                blocks.remove(vec);
                continue;
            }

            AABB b = new AABB(vec.x, vec.y, vec.z, vec.x + 1, vec.y + 1, vec.z + 1);

            if (blur.getValue()) Managers.RENDER.addBlurredBox(b, blurStrength.getValue());
            Managers.RENDER.addFilledBox(b, sideColor.getValue());
            Managers.RENDER.addOutlineBox(b, lineColor.getValue());
        }
    }

    private List<BlockVec> scan() {
        List<BlockVec> blocks = new ArrayList<>();
        int startX = Mth.floor(mc.player.getX() - range.getValue());
        int endX = Mth.ceil(mc.player.getX() + range.getValue());
        int startY = mc.level.getMinY() + 1;
        int endY = mc.level.getMaxY();
        int startZ = Mth.floor(mc.player.getZ() - range.getValue());
        int endZ = Mth.ceil(mc.player.getZ() + range.getValue());

        for (int x = startX; x <= endX; x++) {
            for (int y = startY; y <= endY; y++) {
                for (int z = startZ; z <= endZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState bs = mc.level.getBlockState(pos);
                    if (shouldAdd(bs.getBlock(), pos)) {
                        blocks.add(new BlockVec(pos.getX(), pos.getY(), pos.getZ()));
                    }
                }
            }
        }
        return blocks;
    }

    private void sync(List<BlockVec> b) {
        blocks = b;
        canContinue = true;
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
            if (false/*!mc.level.getEntityInAnyDimension()*/) {
                return pos.getY() > 4;
            } else {
                return pos.getY() > 127 || (pos.getY() < 123 && pos.getY() > 4);
            }
        }
        return false;
    }

    public record BlockVec(double x, double y, double z) {

        public double getDistance(Vec3 v) {
            double dx = x - v.x;
            double dy = y - v.y;
            double dz = z - v.z;
            return dx * dx + dy * dy + dz * dz;
        }

        public Vec3 getVector() {
            return new Vec3(x + 0.5f, y + 0.5f, z + 0.5f);
        }

    }

}