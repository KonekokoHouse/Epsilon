package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.bus.listeners.ConsumerListener;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.managers.RotationManager;
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
import com.github.epsilon.utils.rotation.RaytraceUtils;
import com.github.epsilon.utils.rotation.Rot2f;
import com.github.epsilon.utils.rotation.RotationUtils;
import com.github.epsilon.utils.world.BlockUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

        Set<BlockPos> targets = getTargets();
        if (targets.isEmpty()) {
            if (toggleWhenDone.getValue()) setEnabled(false);
            return;
        }

        FindItemResult obsidian = switchMode.is(SwitchMode.Silent) ? InvUtils.find(Items.OBSIDIAN) : InvUtils.findInHotbar(Items.OBSIDIAN);
        if (!obsidian.found()) return;

        int placed = 0;
        for (BlockPos target : targets) {
            PlaceInfo placeInfo = getPlaceInfo(target);
            if (placeInfo == null) continue;

            if (rotate.is(RotateMode.Silent)) {
                Rot2f rotation = RotationUtils.calculate(placeInfo.neighbor(), placeInfo.side());
                RotationManager.INSTANCE.setRotations(rotation, rotationSpeed.getValue());
                if (!RaytraceUtils.overBlock(RotationManager.INSTANCE.getRotation(), placeInfo.side(), placeInfo.neighbor(), sideCheck.getValue())) {
                    break;
                }
            }

            if (placeBlock(placeInfo, obsidian)) {
                placed++;
                if (placed >= blocksPerTick.getValue()) break;
            }
        }

        if (placed > 0) {
            timer = delay.getValue();
        }
    }

    private Set<BlockPos> getTargets() {
        Set<BlockPos> feetPositions = new LinkedHashSet<>();
        AABB box = mc.player.getBoundingBox().deflate(0.001);
        int y = BlockPos.containing(mc.player.position()).getY();
        int minX = BlockPos.containing(box.minX, mc.player.getY(), box.minZ).getX();
        int maxX = BlockPos.containing(box.maxX, mc.player.getY(), box.maxZ).getX();
        int minZ = BlockPos.containing(box.minX, mc.player.getY(), box.minZ).getZ();
        int maxZ = BlockPos.containing(box.maxX, mc.player.getY(), box.maxZ).getZ();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                feetPositions.add(new BlockPos(x, y, z));
            }
        }

        Set<BlockPos> targets = new LinkedHashSet<>();
        for (BlockPos feetPos : feetPositions) {
            for (Direction dir : Direction.values()) {
                if (dir == Direction.UP || dir == Direction.DOWN) continue;
                BlockPos target = feetPos.relative(dir);
                if (!isInsideFeet(target, feetPositions) && BlockUtils.canPlaceAt(target)) {
                    targets.add(target);
                }
            }
        }
        return targets;
    }

    private boolean isInsideFeet(BlockPos pos, Set<BlockPos> feetPositions) {
        for (BlockPos feetPos : feetPositions) {
            if (pos.getX() == feetPos.getX() && pos.getZ() == feetPos.getZ()) return true;
        }
        return false;
    }

    private PlaceInfo getPlaceInfo(BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            if (!mc.level.getBlockState(neighbor).canBeReplaced()) {
                return new PlaceInfo(neighbor, direction.getOpposite());
            }
        }
        return null;
    }

    private boolean placeBlock(PlaceInfo placeInfo, FindItemResult item) {
        Vec3 hitVec = Vec3.atCenterOf(placeInfo.neighbor()).add(
                placeInfo.side().getStepX() * 0.5,
                placeInfo.side().getStepY() * 0.5,
                placeInfo.side().getStepZ() * 0.5
        );
        BlockHitResult hitResult = new BlockHitResult(hitVec, placeInfo.side(), placeInfo.neighbor(), false);
        int oldSlot = mc.player.getInventory().getSelectedSlot();

        if (switchMode.is(SwitchMode.Visible)) {
            if (oldSlot != item.slot()) {
                InvUtils.swap(item.slot(), true);
            }
        } else {
            InvUtils.invSwap(item.slot());
        }

        InteractionResult result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);
        if (result.consumesAction()) {
            if (swingHand.getValue()) {
                mc.player.swing(InteractionHand.MAIN_HAND);
            } else {
                mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
            }

            if (render.getValue()) {
                renderBoxes.add(new RenderInfo(new AABB(placeInfo.neighbor.relative(placeInfo.side)), lineColor.getValue(), sideColor.getValue(), System.currentTimeMillis(), fade.getValue(), shrink.getValue()));
            }
        }

        if (switchMode.is(SwitchMode.Visible)) {
            if (oldSlot != item.slot()) {
                InvUtils.swapBack();
            }
        } else {
            InvUtils.invSwapBack();
        }

        return result.consumesAction();
    }

    private record PlaceInfo(BlockPos neighbor, Direction side) {
    }

    private record RenderInfo(AABB aabb, Color lineColor, Color sideColor, long startTime, boolean fade,
                              boolean shrink) {
    }

}
