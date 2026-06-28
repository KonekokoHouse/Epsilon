package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.bus.listeners.ConsumerListener;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.player.FindItemResult;
import com.github.epsilon.utils.player.InteractionUtils;
import com.github.epsilon.utils.player.InvUtils;
import com.github.epsilon.utils.player.PlayerUtils;
import com.github.epsilon.utils.render.animation.Easing;
import com.github.epsilon.utils.rotation.Priority;
import com.github.epsilon.utils.rotation.RaytraceUtils;
import com.github.epsilon.utils.rotation.Rot2f;
import com.github.epsilon.utils.rotation.RotationUtils;
import com.github.epsilon.utils.world.BlockUtils;
import com.github.epsilon.utils.world.HoleUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class FeetTrap extends Module {

    public static final FeetTrap INSTANCE = new FeetTrap();

    private FeetTrap() {
        super("Feet Trap", Category.COMBAT);
        EventBus.INSTANCE.subscribe(new ConsumerListener<>(Render3DEvent.class,
                _ -> {
                    if (!render.getValue() || renderBoxes.isEmpty()) return;

                    long time = System.currentTimeMillis();
                    long fadeTime = this.fadeTime.getValue().longValue();

                    renderBoxes.removeIf(box -> time - box.startTime() > fadeTime);

                    for (RenderInfo box : renderBoxes) {
                        float progress = Mth.clamp((float) (time - box.startTime()) / fadeTime, 0.0f, 1.0f);

                        double scale = 1.0;
                        if (box.shrink()) {
                            scale = 1.0 - Easing.EASE_IN_OUT_EXPO.getFunction().apply(progress);
                            if (scale < 0) scale = 0;
                        }

                        float alphaFactor = box.fade() ? Mth.clamp(1.0f - progress, 0.0f, 1.0f) : 1.0f;

                        Color sideColor = box.sideColor();
                        Color lineColor = box.lineColor();

                        Color side = new Color(sideColor.getRed(), sideColor.getGreen(), sideColor.getBlue(), (int) (sideColor.getAlpha() * alphaFactor));
                        Color line = new Color(lineColor.getRed(), lineColor.getGreen(), lineColor.getBlue(), (int) (lineColor.getAlpha() * alphaFactor));

                        AABB renderBox = box.aabb;
                        if (box.shrink()) {
                            renderBox = AABB.ofSize(renderBox.getCenter(), renderBox.getXsize() * scale, renderBox.getYsize() * scale, renderBox.getZsize() * scale);
                        }

                        Managers.RENDER.addFilledBox(renderBox, side);
                        Managers.RENDER.addOutlineBox(renderBox, line);
                    }
                }
        ));
    }

    private enum SwitchMode {
        Visible,
        Silent
    }

    private enum RotateMode {
        None,
        Silent
    }

    private enum PlaceResult {
        Placed,
        Waiting,
        Skipped
    }

    private final EnumSetting<SwitchMode> switchMode = enumSetting("Switch", SwitchMode.Visible);
    private final EnumSetting<RotateMode> rotate = enumSetting("Rotate", RotateMode.Silent);
    private final EnumSetting<PlayerUtils.HelperMode> helperMode = enumSetting("Helper Mode", PlayerUtils.HelperMode.Grim);
    private final IntSetting rotationSpeed = intSetting("Rotation Speed", 180, 10, 180, 10, () -> rotate.is(RotateMode.Silent));
    private final BoolSetting sideCheck = boolSetting("Side Check", false, () -> rotate.is(RotateMode.Silent));
    private final IntSetting blocksPerTick = intSetting("Blocks Per Tick", 2, 1, 8, 1);
    private final IntSetting delay = intSetting("Delay", 0, 0, 20, 1);
    private final BoolSetting toggleOnYChange = boolSetting("Toggle On Y Change", true);
    private final BoolSetting toggleWhenDone = boolSetting("Toggle When Done", false);
    private final BoolSetting autoSneak = boolSetting("Auto Sneak", true);
    private final BoolSetting airPlace = boolSetting("Air Place", false);
    private final BoolSetting airPlacePacket = boolSetting("Air Place Packet", true, airPlace::getValue);
    private final BoolSetting airPlaceGrimBypass = boolSetting("Air Place Grim Bypass", false, airPlace::getValue);

    private final BoolSetting swingHand = boolSetting("Swing Hand", true);
    private final BoolSetting render = boolSetting("Render", true);
    private final BoolSetting fade = boolSetting("Fade", true, render::getValue);
    private final IntSetting fadeTime = intSetting("Fade Time", 500, 0, 3000, 50, () -> render.getValue() && fade.getValue());
    private final BoolSetting shrink = boolSetting("Shrink", false, render::getValue);
    private final ColorSetting sideColor = colorSetting("Side Color", new Color(255, 183, 197, 100), render::getValue);
    private final ColorSetting lineColor = colorSetting("Line Color", new Color(255, 105, 180), render::getValue);

    private int timer;
    private double prevY;
    private BlockPos pendingPos;

    private final List<RenderInfo> renderBoxes = new ArrayList<>();

    @Override
    protected void onEnable() {
        if (nullCheck()) {
            toggle();
            return;
        }
        timer = 0;
        prevY = mc.player.getY();
        pendingPos = null;
    }

    @Override
    protected void onDisable() {
        pendingPos = null;
        InvUtils.swapBack();
    }

    @EventHandler
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        if (toggleOnYChange.getValue() && prevY != mc.player.getY()) {
            toggle();
            return;
        }

        prevY = mc.player.getY();

        if (timer > 0) {
            timer--;
            pendingPos = null;
            return;
        }

        List<BlockPos> blocks = getBlocks();
        if (blocks.isEmpty()) {
            pendingPos = null;
            if (toggleWhenDone.getValue()) toggle();
            return;
        }

        FindItemResult obsidian = switchMode.is(SwitchMode.Silent) ? InvUtils.find(Items.OBSIDIAN) : InvUtils.findInHotbar(Items.OBSIDIAN);
        if (!obsidian.found()) return;

        if (timer > 0) return;

        int placed = 0;
        List<BlockPos> skipped = new ArrayList<>();
        while (placed < blocksPerTick.getValue()) {
            // 静默转头模式会优先继续尝试上一 tick 的目标，避免刚转到一半就切换位置。
            if (pendingPos != null && !BlockUtils.canPlaceAt(pendingPos)) {
                pendingPos = null;
            }

            BlockPos targetBlock = pendingPos != null ? pendingPos : getSequentialPos(skipped);
            if (targetBlock == null) break;

            PlaceResult result = placeBlock(targetBlock, obsidian);
            switch (result) {
                case Placed -> {
                    pendingPos = null;
                    placed++;
                    timer = delay.getValue();
                }
                case Waiting -> {
                    return;
                }
                case Skipped -> {
                    pendingPos = null;
                    skipped.add(targetBlock);
                }
            }
        }
    }

    private List<BlockPos> getBlocks() {
        BlockPos playerPos = getPlayerPos();
        List<BlockPos> offsets = new ArrayList<>();

        // 玩家靠近方块边缘时碰撞箱会跨格，需要向对应方向扩展围脚范围。
        int x;
        int z;
        double decimalX = Math.abs(mc.player.getX()) - Math.floor(Math.abs(mc.player.getX()));
        double decimalZ = Math.abs(mc.player.getZ()) - Math.floor(Math.abs(mc.player.getZ()));
        int lengthXPos = HoleUtils.calcLength(decimalX, false);
        int lengthXNeg = HoleUtils.calcLength(decimalX, true);
        int lengthZPos = HoleUtils.calcLength(decimalZ, false);
        int lengthZNeg = HoleUtils.calcLength(decimalZ, true);
        List<BlockPos> tempOffsets = new ArrayList<>();
        // 先补玩家脚下可能重叠的格子，防止贴边时漏掉底部方块。
        offsets.addAll(getOverlapPos());

        // 根据玩家跨格范围生成外侧一圈围脚方块。
        for (x = 1; x < lengthXPos + 1; ++x) {
            tempOffsets.add(HoleUtils.addToPlayer(playerPos, x, 0.0, 1 + lengthZPos));
            tempOffsets.add(HoleUtils.addToPlayer(playerPos, x, 0.0, -(1 + lengthZNeg)));
        }
        for (x = 0; x <= lengthXNeg; ++x) {
            tempOffsets.add(HoleUtils.addToPlayer(playerPos, -x, 0.0, 1 + lengthZPos));
            tempOffsets.add(HoleUtils.addToPlayer(playerPos, -x, 0.0, -(1 + lengthZNeg)));
        }
        for (z = 1; z < lengthZPos + 1; ++z) {
            tempOffsets.add(HoleUtils.addToPlayer(playerPos, 1 + lengthXPos, 0.0, z));
            tempOffsets.add(HoleUtils.addToPlayer(playerPos, -(1 + lengthXNeg), 0.0, z));
        }
        for (z = 0; z <= lengthZNeg; ++z) {
            tempOffsets.add(HoleUtils.addToPlayer(playerPos, 1 + lengthXPos, 0.0, -z));
            tempOffsets.add(HoleUtils.addToPlayer(playerPos, -(1 + lengthXNeg), 0.0, -z));
        }

        for (BlockPos pos : tempOffsets) {
            // 如果目标位置周围都是可替换方块，则额外尝试向下一格补底。
            if (getDown(pos)) {
                offsets.add(pos.offset(0, -1, 0));
            }
            offsets.add(pos);
        }

        return offsets;
    }

    private List<BlockPos> getOverlapPos() {
        List<BlockPos> positions = new ArrayList<>();

        // calcOffset 使用原始小数部分，负坐标会保留 floor 后的跨格方向。
        double decimalX = mc.player.getX() - Math.floor(mc.player.getX());
        double decimalZ = mc.player.getZ() - Math.floor(mc.player.getZ());
        int offX = HoleUtils.calcOffset(decimalX);
        int offZ = HoleUtils.calcOffset(decimalZ);
        positions.add(getPlayerPos());
        for (int x = 0; x <= Math.abs(offX); ++x) {
            for (int z = 0; z <= Math.abs(offZ); ++z) {
                int properX = x * offX;
                int properZ = z * offZ;
                positions.add(getPlayerPos().offset(properX, -1, properZ));
            }
        }

        return positions;
    }

    private boolean getDown(BlockPos pos) {
        // 只有目标及六个相邻方向都可替换时，才认为该位置下方也需要补方块。
        for (Direction dir : Direction.values()) {
            if (!mc.level.getBlockState(pos.relative(dir)).canBeReplaced()) {
                return false;
            }
        }

        return mc.level.getBlockState(pos).canBeReplaced();
    }

    private BlockPos getPlayerPos() {
        return BlockPos.containing(mc.player.getX(), mc.player.getY() - Math.floor(mc.player.getY()) > 0.8 ? Math.floor(mc.player.getY()) + 1.0 : Math.floor(mc.player.getY()), mc.player.getZ());
    }

    private BlockPos getSequentialPos(List<BlockPos> skipped) {
        for (BlockPos bp : getBlocks()) {
            if (skipped.contains(bp)) continue;
            if (new AABB(bp).intersects(mc.player.getBoundingBox())) continue;
            if (BlockUtils.canPlaceAt(bp)) {
                return bp;
            }
        }
        return null;
    }

    private PlaceResult placeBlock(BlockPos blockPos, FindItemResult item) {
        BlockHitResult hitResult = PlayerUtils.getPlaceResult(blockPos, helperMode.getValue(), true);
        if (hitResult == null) {
            return airPlace(blockPos, item);
        }
        if (mc.level.isEmptyBlock(hitResult.getBlockPos())) {
            pendingPos = null;
            return PlaceResult.Skipped;
        }
        if (!PlayerUtils.isValidBlock(hitResult.getBlockPos())) {
            pendingPos = null;
            return PlaceResult.Skipped;
        }

        if (rotate.is(RotateMode.Silent)) {
            // 第一次看到该目标时只提交旋转，下一 tick 对准后再实际放置。
            if (!blockPos.equals(pendingPos)) {
                pendingPos = blockPos;
                setRotation(hitResult);
                return PlaceResult.Waiting;
            }

            // 旋转尚未命中目标面时继续等待，避免未对准就发放置包。
            if (!isLookingAt(hitResult)) {
                setRotation(hitResult);
                return PlaceResult.Waiting;
            }

            // 等待转头期间世界状态可能变化，放置前再校验一次点击目标。
            if (mc.level.isEmptyBlock(hitResult.getBlockPos())) {
                pendingPos = null;
                return PlaceResult.Skipped;
            }

            if (!PlayerUtils.isValidBlock(hitResult.getBlockPos())) {
                pendingPos = null;
                return PlaceResult.Skipped;
            }
        }

        int oldSlot = mc.player.getInventory().getSelectedSlot();
        Input oldInput = mc.player.input.keyPresses;

        // 可见切换用于普通交互，静默切换用于尽量不改变玩家当前手持物显示。
        if (switchMode.is(SwitchMode.Visible)) {
            if (oldSlot != item.slot()) {
                InvUtils.swap(item.slot(), true);
            }
        } else {
            InvUtils.invSwap(item.slot());
        }

        if (autoSneak.getValue()) {
            // 放置时临时按下 Shift，防止右键打开容器或与方块交互。
            setShiftState(true);
        }

        InteractionResult result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);
        if (result.consumesAction()) {
            if (swingHand.getValue()) {
                mc.player.swing(InteractionHand.MAIN_HAND);
            } else {
                mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
            }

            if (render.getValue()) {
                renderBoxes.add(new RenderInfo(new AABB(blockPos), lineColor.getValue(), sideColor.getValue(), System.currentTimeMillis(), fade.getValue(), shrink.getValue()));
            }
        }

        if (autoSneak.getValue()) {
            setShiftState(oldInput);
        }

        if (switchMode.is(SwitchMode.Visible)) {
            if (oldSlot != item.slot()) {
                InvUtils.swapBack();
            }
        } else {
            InvUtils.invSwapBack();
        }

        return result.consumesAction() ? PlaceResult.Placed : PlaceResult.Skipped;
    }

    private PlaceResult airPlace(BlockPos blockPos, FindItemResult item) {
        if (!airPlace.getValue()) {
            pendingPos = null;
            return PlaceResult.Skipped;
        }

        if (rotate.is(RotateMode.Silent)) {
            if (!blockPos.equals(pendingPos)) {
                pendingPos = blockPos;
                setAirPlaceRotation(blockPos);
                return PlaceResult.Waiting;
            }
        }

        int oldSlot = mc.player.getInventory().getSelectedSlot();
        Input oldInput = mc.player.input.keyPresses;

        if (switchMode.is(SwitchMode.Visible)) {
            if (oldSlot != item.slot()) {
                InvUtils.swap(item.slot(), true);
            }
        } else {
            InvUtils.invSwap(item.slot());
        }

        if (autoSneak.getValue()) {
            setShiftState(true);
        }

        InteractionResult result = InteractionUtils.airPlace(
                blockPos,
                rotate.is(RotateMode.Silent),
                InteractionHand.MAIN_HAND,
                airPlacePacket.getValue(),
                airPlaceGrimBypass.getValue()
        );

        if (result.consumesAction()) {
            InteractionHand swing = airPlaceGrimBypass.getValue() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            if (swingHand.getValue()) {
                mc.player.swing(swing);
            } else {
                mc.getConnection().send(new ServerboundSwingPacket(swing));
            }

            if (render.getValue()) {
                renderBoxes.add(new RenderInfo(new AABB(blockPos), lineColor.getValue(), sideColor.getValue(), System.currentTimeMillis(), fade.getValue(), shrink.getValue()));
            }
        }

        if (autoSneak.getValue()) {
            setShiftState(oldInput);
        }

        if (switchMode.is(SwitchMode.Visible)) {
            if (oldSlot != item.slot()) {
                InvUtils.swapBack();
            }
        } else {
            InvUtils.invSwapBack();
        }

        return result.consumesAction() ? PlaceResult.Placed : PlaceResult.Skipped;
    }

    private void setRotation(BlockHitResult hitResult) {
        Rot2f rotation = RotationUtils.calculate(hitResult.getLocation());
        Managers.ROTATION.setRotations(rotation, rotationSpeed.getValue(), Priority.High);
    }

    private void setAirPlaceRotation(BlockPos pos) {
        Direction side = RotationUtils.getDirection(pos);
        Rot2f rotation = RotationUtils.calculate(Vec3.atCenterOf(pos).relative(side, 0.5));
        Managers.ROTATION.setRotations(rotation, rotationSpeed.getValue(), Priority.High);
    }

    private boolean isLookingAt(BlockHitResult hitResult) {
        return RaytraceUtils.overBlock(
                Managers.ROTATION.getRotation(),
                hitResult.getDirection(),
                hitResult.getBlockPos(),
                sideCheck.getValue()
        );
    }

    private void setShiftState(boolean state) {
        Input current = mc.player.input.keyPresses;
        setShiftState(new Input(current.forward(), current.backward(), current.left(), current.right(), current.jump(), state, current.sprint()));
    }

    private void setShiftState(Input input) {
        mc.player.input.keyPresses = input;
        mc.player.setShiftKeyDown(input.shift());
        mc.getConnection().send(new ServerboundPlayerInputPacket(input));
    }

    private record RenderInfo(
            AABB aabb,
            Color lineColor,
            Color sideColor,
            long startTime,
            boolean fade,
            boolean shrink
    ) {
    }

}
