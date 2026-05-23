package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.KeyboardInputEvent;
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
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Input;
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
        //Boost
    }

    private enum SwapMode {
        Silent,
        InvSwitch
    }

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.Control);
    private final EnumSetting<SwapMode> swapMode = enumSetting("Swap Mode", SwapMode.InvSwitch);
    private final BoolSetting armored = boolSetting("Armored", false);
    private final BoolSetting highVersion = boolSetting("1206+", true);
    private final DoubleSetting horizontalSpeed = doubleSetting("Horizontal Speed", 1.35, 0.1, 5.0, 0.05, () -> mode.is(Mode.Control));
    private final DoubleSetting verticalSpeed = doubleSetting("Vertical Speed", 0.8, 0.1, 2.0, 0.05, () -> mode.is(Mode.Control));
    private final DoubleSetting accel = doubleSetting("Acceleration", 0.35, 0.05, 1.0, 0.05, () -> mode.is(Mode.Control));
    private final BoolSetting useFireworks = boolSetting("Use Fireworks", true, () -> mode.is(Mode.Control));
    private final IntSetting boostDelay = intSetting("Boost Delay", 20, 2, 50, 1, () -> mode.is(Mode.Control) && useFireworks.getValue());

    private boolean hasFirstFirework;
    private boolean shouldJump;
    private Input bypassedInput;
    private boolean shouldRestore;
    private final TimerUtils timer = new TimerUtils();

    @Override
    protected void onEnable() {
        hasFirstFirework = false;
        shouldJump = false;
        timer.setMs(917813L);
    }

    @Override
    protected void onDisable() {
        restoreInput();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (nullCheck()) return;

        redirectRotation(); // 让你转你就受着

        switch (mode.getValue()) {
            case Control -> updateControl();
            //case Boost -> updateBoost();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        restoreInput();
    }

    @EventHandler
    private void onKeyboardInput(KeyboardInputEvent event) {
        if (shouldJump) {
            event.setJump(true);
            shouldJump = false;
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
        return !mc.player.isFallFlying() && !mc.player.isInWater();
    }

    private boolean startFFlying() {
        if (mc.player.tryToStartFallFlying()) {
            mc.getConnection().send(new ServerboundPlayerInputPacket(new Input(false, false, false, false, true, false, false)));
            mc.getConnection().send(new ServerboundPlayerInputPacket(Input.EMPTY));
            mc.player.lastSentInput = Input.EMPTY;
            mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
            shouldJump = true;
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
    }

    private void redirectRotation() {
        RotationManager.INSTANCE.setRotations(new Rot2f(calcYaw(), calcPitch()), 10, Priority.Highest);
    }

    private float calcYaw() {
        float yaw = mc.player.getYRot();

        boolean forward = mc.options.keyUp.isDown();
        boolean back = mc.options.keyDown.isDown();
        boolean left = mc.options.keyLeft.isDown();
        boolean right = mc.options.keyRight.isDown();

        if (forward && !back) {
            if (left && !right) {
                yaw -= 45f;
            } else if (right && !left) {
                yaw += 45f;
            }
        } else if (back && !forward) {
            yaw += 180f;
            if (left && !right) {
                yaw += 45f;
            } else if (right && !left) {
                yaw -= 45f;
            }
        } else if (left && !right) {
            yaw -= 90f;
        } else if (right && !left) {
            yaw += 90f;
        }
        return Mth.wrapDegrees(yaw);
    }

    private float calcPitch() {
        float pitch = mc.player.getXRot();

        boolean jump = mc.options.keyJump.isDown();
        boolean sneak = mc.options.keyShift.isDown();
        boolean moving = MoveUtils.isMoving();

        if (sneak && jump) {
            pitch = -3f;
        } else if (jump) {
            pitch = moving ? -45f : -90f;
        } else if (sneak) {
            pitch = moving ? 45f : 90f;
        } else if (moving) {
            pitch = -1.9f;
        }
        return Mth.clamp(pitch, -90f, 90f);
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

        syncInput();

        swapArmor(elytra);

        startFFlying();

        useFirework();

        swapArmor(elytra);
    }

    private void syncInput() {
        if (!highVersion.getValue() || shouldRestore) return;
        bypassedInput = mc.player.input.keyPresses;
        mc.player.input.keyPresses = Input.EMPTY;
        mc.getConnection().send(new ServerboundPlayerInputPacket(Input.EMPTY));
        mc.player.lastSentInput = Input.EMPTY;
        shouldRestore = true;
    }

    private void restoreInput() {
        if (!shouldRestore) return;
        mc.player.input.keyPresses = bypassedInput;
        bypassedInput = null;
        shouldRestore = false;
    }

    private void swapArmor(int containerSlot) {
        int containerId = mc.player.containerMenu.containerId;
        mc.gameMode.handleContainerInput(containerId, containerSlot, 0, ContainerInput.PICKUP, mc.player);
        mc.gameMode.handleContainerInput(containerId, 6, 0, ContainerInput.PICKUP, mc.player);
        mc.gameMode.handleContainerInput(containerId, containerSlot, 0, ContainerInput.PICKUP, mc.player);
    }

    public boolean isMyFirework(FireworkRocketEntity firework) {
        return firework.getOwner() == mc.player;
    }

    public boolean isArmorMode() {
        return isEnabled() && mode.is(Mode.Control) && armored.getValue();
    }

}
