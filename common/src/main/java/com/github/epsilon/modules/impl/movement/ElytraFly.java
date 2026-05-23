package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.TickEvent;
import com.github.epsilon.managers.RotationManager;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.player.FindItemResult;
import com.github.epsilon.utils.player.InvUtils;
import com.github.epsilon.utils.player.MoveUtils;
import com.github.epsilon.utils.rotation.Priority;
import com.github.epsilon.utils.rotation.Rot2f;
import com.github.epsilon.utils.timer.TimerUtils;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class ElytraFly extends Module {

    public static final ElytraFly INSTANCE = new ElytraFly();

    private ElytraFly() {
        super("Elytra Fly", Category.MOVEMENT);
    }

    private enum Mode {
        Control,
        Boost
    }

    private enum SwapMode {
        Silent,
        InvSwitch
    }

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.Control);
    private final EnumSetting<SwapMode> swapMode = enumSetting("SwapMode", SwapMode.InvSwitch);
    private final BoolSetting armored = boolSetting("Armored", false);
    private final DoubleSetting horizontalSpeed = doubleSetting("HorizontalSpeed", 1.35, 0.1, 5.0, 0.05, () -> mode.is(Mode.Control));
    private final DoubleSetting verticalSpeed = doubleSetting("VerticalSpeed", 0.8, 0.1, 2.0, 0.05, () -> mode.is(Mode.Control));
    private final DoubleSetting accel = doubleSetting("Acceleration", 0.35, 0.05, 1.0, 0.05, () -> mode.is(Mode.Control));
    private final BoolSetting useFireworks = boolSetting("UseFireworks", true, () -> mode.is(Mode.Control));
    private final IntSetting boostDelay = intSetting("BoostDelay", 20, 2, 50, 1, () -> mode.is(Mode.Control) && useFireworks.getValue());

    private boolean hasFirstFirework;
    private final TimerUtils timer = new TimerUtils();

    @Override
    protected void onEnable() {
        hasFirstFirework = false;
        timer.setMs(917813L);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (nullCheck()) return;

        switch (mode.getValue()) {
            case Control -> updateControl();
            case Boost -> updateBoost();
        }
    }

    private void updateControl() {
        FindItemResult elytra = InvUtils.find(Items.ELYTRA);

        if (!canGlide(elytra.found()) || mc.player.onGround()) {
            hasFirstFirework = false;
            return;
        }

        if (armored.getValue()) {
            if (canFFlying()) {
                jiaFei(elytra.slot());
            }
        } else {
            if (canFFlying()) {
                startFFlying();
            }
            useFirework();
        }

        if (!useFireworks.getValue() || hasFirstFirework) {
            applyMotion();
        }
    }

    private void updateBoost() {
        // Todo: 完善
    }

    private boolean canFFlying() {
        return !mc.player.isFallFlying() && !mc.player.isInWater() && hasInput();
    }

    private boolean startFFlying() {
        if (mc.player.tryToStartFallFlying()) {
            mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
            return true;
        }
        return false;
    }

    private boolean canGlide(boolean hasElytra) {
        if (armored.getValue() && hasElytra) {
            return true;
        }
        for (EquipmentSlot slot : EquipmentSlot.VALUES) {
            if (LivingEntity.canGlideUsing(mc.player.getItemBySlot(slot), slot)) {
                return true;
            }
        }
        return false;
    }

    private void useFirework() {
        if (!useFireworks.getValue() || !timer.hasDelayed(boostDelay.getValue())) return;

        FindItemResult rocket = swapMode.is(SwapMode.Silent) ? InvUtils.findInHotbar(Items.FIREWORK_ROCKET) : InvUtils.find(Items.FIREWORK_ROCKET);
        if (!rocket.found()) return;

        InteractionHand hand = rocket.getHand();

        if (swapMode.is(SwapMode.Silent)) {
            InvUtils.swap(rocket.slot(), true);
        } else {
            InvUtils.invSwap(rocket.slot());
        }

        InteractionResult result = mc.gameMode.useItem(mc.player, hand);

        if (result.consumesAction()) {
            hasFirstFirework = true;
            timer.reset();
            mc.player.swing(hand);
        }

        if (swapMode.is(SwapMode.Silent)) {
            InvUtils.swapBack();
        } else {
            InvUtils.invSwapBack();
        }
    }

    private void applyMotion() {
        if (!hasInput()) {
            mc.player.setDeltaMovement(Vec3.ZERO);
            mc.player.resetFallDistance();
            return;
        }

        Vec3 moveDir = getMoveDir();
        Vec3 thisMotion = mc.player.getDeltaMovement();

        boolean up = mc.options.keyJump.isDown();
        boolean down = mc.options.keyShift.isDown();

        double newX = moveDir.x * horizontalSpeed.getValue();
        double newY = up == down ? 0.0 : (up ? verticalSpeed.getValue() : -verticalSpeed.getValue());
        double newZ = moveDir.z * horizontalSpeed.getValue();

        double factor = Math.max(accel.getValue(), 0.85);
        newX = Mth.lerp(factor, thisMotion.x, newX);
        newY = Mth.lerp(factor, thisMotion.y, newY);
        newZ = Mth.lerp(factor, thisMotion.z, newZ);

        mc.player.setDeltaMovement(newX, newY, newZ);
        mc.player.resetFallDistance();

        rotateToMovement(newX, newY, newZ);
    }

    private void rotateToMovement(double x, double y, double z) {
        double horizontal = Math.sqrt(x * x + z * z);
        if (horizontal < 1.0E-5 && Math.abs(y) < 1.0E-5) return;
        float yaw = (float) Math.toDegrees(Math.atan2(z, x)) - 90.0f;
        float pitch = (float) (-Math.toDegrees(Math.atan2(y, Math.max(horizontal, 1.0E-5))));
        RotationManager.INSTANCE.setRotations(new Rot2f(Mth.wrapDegrees(yaw), Mth.clamp(pitch, -90.0f, 90.0f)), 10, Priority.Highest);
    }

    private boolean hasInput() {
        return MoveUtils.isMoving() || mc.options.keyJump.isDown() || mc.options.keyShift.isDown();
    }

    private Vec3 getMoveDir() {
        float forward = mc.player.input.getMoveVector().y;
        float left = mc.player.input.getMoveVector().x;

        if (forward == 0 && left == 0) {
            return Vec3.ZERO;
        }

        double yawRad = Math.toRadians(mc.player.getYRot());
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);

        double x = forward * -sin + left * cos;
        double z = forward * cos + left * sin;

        double length = Math.sqrt(x * x + z * z);
        if (length <= 0) return Vec3.ZERO;
        return new Vec3(x / length, 0, z / length);
    }

    private void jiaFei(int elytraSlot) {
        int elytra = elytraSlot < 9 ? elytraSlot + 36 : elytraSlot;

        swapArmor(elytra);

        startFFlying();

        useFirework();

        swapArmor(elytra);
    }

    private void swapArmor(int containerSlot) {
        int containerId = mc.player.containerMenu.containerId;
        mc.gameMode.handleContainerInput(containerId, containerSlot, 0, ContainerInput.PICKUP, mc.player);
        mc.gameMode.handleContainerInput(containerId, 6, 0, ContainerInput.PICKUP, mc.player);
        mc.gameMode.handleContainerInput(containerId, containerSlot, 0, ContainerInput.PICKUP, mc.player);
    }

    public boolean isFirework(FireworkRocketEntity firework) {
        return firework.getOwner() == mc.player;
    }

    public boolean isArmorMode() {
        return isEnabled() && mode.is(Mode.Control) && armored.getValue();
    }

}
