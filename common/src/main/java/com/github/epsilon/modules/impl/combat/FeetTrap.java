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

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class FeetTrap extends Module {

    public static final FeetTrap INSTANCE = new FeetTrap();

    private FeetTrap() {
        super("Feet Trap", Category.COMBAT);
        EventBus.INSTANCE.subscribe(new ConsumerListener<>(Render3DEvent.class,
                event -> {
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

    private final EnumSetting<SwitchMode> switchMode = enumSetting("Switch", SwitchMode.Visible);
    private final EnumSetting<RotateMode> rotate = enumSetting("Rotate", RotateMode.Silent);
    private final EnumSetting<PlayerUtils.HelperMode> helperMode = enumSetting("Helper Mode", PlayerUtils.HelperMode.Grim);
    private final IntSetting rotationSpeed = intSetting("Rotation Speed", 10, 1, 10, 1, () -> rotate.is(RotateMode.Silent));
    private final BoolSetting sideCheck = boolSetting("Side Check", false, () -> rotate.is(RotateMode.Silent));
    private final IntSetting blocksPerTick = intSetting("Blocks Per Tick", 2, 1, 8, 1);
    private final IntSetting delay = intSetting("Delay", 0, 0, 20, 1);
    private final BoolSetting toggleOnYChange = boolSetting("Toggle On Y Change", true);
    private final BoolSetting toggleWhenDone = boolSetting("Toggle When Done", false);

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

        FindItemResult obsidian = switchMode.is(SwitchMode.Silent) ? InvUtils.findInHotbar(Items.OBSIDIAN) : InvUtils.find(Items.OBSIDIAN);
        if (!obsidian.found()) return;

        if (timer > 0) return;

        int placed = 0;
        while (placed < blocksPerTick.getValue()) {
            BlockPos targetBlock = pendingPos != null && BlockUtils.canPlaceAt(pendingPos) ? pendingPos : getSequentialPos();
            if (targetBlock == null) break;
            if (placeBlock(targetBlock, obsidian)) {
                pendingPos = null;
                placed++;
                timer = delay.getValue();
            } else {
                break;
            }
        }
    }

    private List<BlockPos> getBlocks() {
        BlockPos playerPos = getPlayerPos();
        List<BlockPos> offsets = new ArrayList<>();

        int x;
        int z;
        double decimalX = Math.abs(mc.player.getX()) - Math.floor(Math.abs(mc.player.getX()));
        double decimalZ = Math.abs(mc.player.getZ()) - Math.floor(Math.abs(mc.player.getZ()));
        int lengthXPos = HoleUtils.calcLength(decimalX, false);
        int lengthXNeg = HoleUtils.calcLength(decimalX, true);
        int lengthZPos = HoleUtils.calcLength(decimalZ, false);
        int lengthZNeg = HoleUtils.calcLength(decimalZ, true);
        List<BlockPos> tempOffsets = new ArrayList<>();
        offsets.addAll(getOverlapPos());

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
            if (getDown(pos))
                offsets.add(pos.offset(0, -1, 0));
            offsets.add(pos);
        }

        return offsets;
    }

    private List<BlockPos> getOverlapPos() {
        List<BlockPos> positions = new ArrayList<>();

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

    private BlockPos getSequentialPos() {
        for (BlockPos bp : getBlocks()) {
            if (new AABB(bp).intersects(mc.player.getBoundingBox())) continue;
            if (BlockUtils.canPlaceAt(bp)) {
                return bp;
            }
        }
        return null;
    }

    private boolean placeBlock(BlockPos blockPos, FindItemResult item) {
        BlockHitResult hitResult = PlayerUtils.getPlaceResult(blockPos, helperMode.getValue(), true);
        if (hitResult == null) {
            pendingPos = null;
            return false;
        }
        if (mc.level.isEmptyBlock(hitResult.getBlockPos())) {
            pendingPos = null;
            return false;
        }
        if (!PlayerUtils.isValidBlock(hitResult.getBlockPos())) {
            pendingPos = null;
            return false;
        }

        if (rotate.is(RotateMode.Silent)) {
            if (!blockPos.equals(pendingPos)) {
                pendingPos = blockPos;
                setRotation(hitResult);
                return false;
            }

            if (!isLookingAt(hitResult)) {
                setRotation(hitResult);
                return false;
            }

            if (mc.level.isEmptyBlock(hitResult.getBlockPos())) {
                pendingPos = null;
                return false;
            }

            if (!PlayerUtils.isValidBlock(hitResult.getBlockPos())) {
                pendingPos = null;
                return false;
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

        setShiftState(true);

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

        setShiftState(oldInput);

        if (switchMode.is(SwitchMode.Visible)) {
            if (oldSlot != item.slot()) {
                InvUtils.swapBack();
            }
        } else {
            InvUtils.invSwapBack();
        }

        return result.consumesAction();
    }

    private void setRotation(BlockHitResult hitResult) {
        Rot2f rotation = RotationUtils.calculate(hitResult.getLocation());
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
