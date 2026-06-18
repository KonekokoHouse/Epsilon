package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.bus.listeners.ConsumerListener;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.player.FindItemResult;
import com.github.epsilon.utils.player.InvUtils;
import com.github.epsilon.utils.render.Render3DUtils;
import com.github.epsilon.utils.render.animation.Easing;
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
import java.util.Objects;

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

                        Render3DUtils.drawFilledBox(renderBox, side);
                        Render3DUtils.drawOutlineBox(event.getPoseStack(), renderBox, line);
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
    private final IntSetting rotationSpeed = intSetting("Rotation Speed", 10, 1, 10, 1, () -> rotate.is(RotateMode.Silent));
    private final BoolSetting sideCheck = boolSetting("Side Check", false, () -> rotate.is(RotateMode.Silent));
    private final IntSetting blocksPerTick = intSetting("Blocks Per Tick", 2, 1, 8, 1);
    private final IntSetting delay = intSetting("Delay", 0, 0, 20, 1);
    private final BoolSetting toggleWhenDone = boolSetting("Toggle When Done", false);

    private final BoolSetting swingHand = boolSetting("Swing Hand", true);
    private final BoolSetting render = boolSetting("Render", true);
    private final BoolSetting fade = boolSetting("Fade", true, render::getValue);
    private final IntSetting fadeTime = intSetting("Fade Time", 500, 0, 3000, 50, () -> render.getValue() && fade.getValue());
    private final BoolSetting shrink = boolSetting("Shrink", false, render::getValue);
    private final ColorSetting sideColor = colorSetting("Side Color", new Color(255, 183, 197, 100), render::getValue);
    private final ColorSetting lineColor = colorSetting("Line Color", new Color(255, 105, 180), render::getValue);

    private int timer;

    private final List<RenderInfo> renderBoxes = new ArrayList<>();

    @Override
    protected void onEnable() {
        timer = 0;
    }

    @Override
    protected void onDisable() {
        InvUtils.swapBack();
    }

    @EventHandler
    private void onTick(PlayerTickEvent.Pre event) {
        if (timer > 0) {
            timer--;
            return;
        }

        List<BlockPos> blocks = getBlocks();
        if (blocks.isEmpty()) {
            if (toggleWhenDone.getValue()) setEnabled(false);
            return;
        }

        FindItemResult obsidian = switchMode.is(SwitchMode.Silent) ? InvUtils.findInHotbar(Items.OBSIDIAN) : InvUtils.find(Items.OBSIDIAN);
        if (!obsidian.found()) return;

        if (timer > 0) return;

        int placed = 0;
        while (placed < blocksPerTick.getValue()) {
            BlockPos targetBlock = getSequentialPos();
            if (targetBlock == null) break;
            if (placeBlock(targetBlock, obsidian)) {
                placed++;
                timer = delay.getValue();
            } else {
                break;
            }
        }
    }

    private List<BlockPos> getBlocks() {
        final BlockPos playerPos = getPlayerPos();
        final List<BlockPos> offsets = new ArrayList<>();

        int z;
        int x;
        final double decimalX = Math.abs(mc.player.getX()) - Math.floor(Math.abs(mc.player.getX()));
        final double decimalZ = Math.abs(mc.player.getZ()) - Math.floor(Math.abs(mc.player.getZ()));
        final int lengthXPos = HoleUtils.calcLength(decimalX, false);
        final int lengthXNeg = HoleUtils.calcLength(decimalX, true);
        final int lengthZPos = HoleUtils.calcLength(decimalZ, false);
        final int lengthZNeg = HoleUtils.calcLength(decimalZ, true);
        final ArrayList<BlockPos> tempOffsets = new ArrayList<>();
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
                positions.add(Objects.requireNonNull(getPlayerPos()).offset(properX, -1, properZ));
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
        Vec3 hitVec = Vec3.atCenterOf(placeInfo.neighbor()).add(
                placeInfo.side().getStepX() * 0.5,
                placeInfo.side().getStepY() * 0.5,
                placeInfo.side().getStepZ() * 0.5
        );

        BlockHitResult hitResult = new BlockHitResult(hitVec, placeInfo.side(), placeInfo.neighbor(), false);

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
                renderBoxes.add(new RenderInfo(new AABB(placeInfo.placedPos()), lineColor.getValue(), sideColor.getValue(), System.currentTimeMillis(), fade.getValue(), shrink.getValue()));
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

    private void setShiftState(boolean state) {
        Input current = mc.player.input.keyPresses;
        setShiftState(new Input(current.forward(), current.backward(), current.left(), current.right(), current.jump(), state, current.sprint()));
    }

    private void setShiftState(Input input) {
        mc.player.input.keyPresses = input;
        mc.player.setShiftKeyDown(input.shift());
        mc.getConnection().send(new ServerboundPlayerInputPacket(input));
    }

    private record PlaceInfo(BlockPos placedPos, BlockPos neighbor, Direction side) {
    }

    private record RenderInfo(AABB aabb, Color lineColor, Color sideColor, long startTime, boolean fade,
                              boolean shrink) {
    }

}
