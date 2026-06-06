package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.KeyboardInputEvent;
import com.github.epsilon.events.impl.MousePressEvent;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.events.impl.TravelEvent;
import com.github.epsilon.managers.RotationManager;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
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
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

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
    private final BoolSetting noSprint = boolSetting("No Sprint", true, () -> mode.is(Mode.Control) && armored.getValue());
    private final BoolSetting useFireworks = boolSetting("Use Fireworks", true, () -> mode.is(Mode.Control));
    private final IntSetting boostDelay = intSetting("Boost Delay", 20, 2, 50, 1, () -> mode.is(Mode.Control) && useFireworks.getValue());

    private boolean hasFirstFirework;
    private boolean shouldJump;
    private final TimerUtils timer = new TimerUtils();

    @Override
    protected void onEnable() {
        hasFirstFirework = false;
        shouldJump = false;
        timer.setMs(917813L);
    }

    @EventHandler
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        redirectRotation(); // 让你转你就受着

        switch (mode.getValue()) {
            case Control -> updateControl();
            //case Boost -> updateBoost();
        }
    }

    @EventHandler
    private void onTravel(TravelEvent event) {
        if (mode.is(Mode.Control)) {
            if (!hasMoveInput() && (!useFireworks.getValue() || hasFirstFirework)) {
                mc.player.setDeltaMovement(0, 0.02, 0);
            }
        }
    }

    @EventHandler
    private void onKeyboardInput(KeyboardInputEvent event) {
        if (noSprint.getValue()) event.setSprint(false);
        if (shouldJump) {
            event.setJump(true);
            shouldJump = false;
        }
    }

    @EventHandler
    private void onMousePress(MousePressEvent event) {
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && event.getAction() == GLFW.GLFW_PRESS) { // 你吃你冯了个福呢
            event.setCancelled(true);
        }
    }

    private void updateControl() {
        if (noSprint.getValue() && mc.player.isSprinting()) return;

        FindItemResult elytra = InvUtils.find(Items.ELYTRA);

        if (!canGlide(elytra.found()) || mc.player.onGround()) {
            shouldJump = true;
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
    }

    private void updateBoost() {
        // Todo: 完善
    }

    private boolean canFFlying() {
        return !mc.player.isFallFlying() && !mc.player.isInWater();
    }

    private boolean startFFlying() {
        if (mc.player.tryToStartFallFlying()) {
            shouldJump = true;
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

    private boolean hasMoveInput() {
        return mc.options.keyUp.isDown()
                || mc.options.keyDown.isDown()
                || mc.options.keyLeft.isDown()
                || mc.options.keyRight.isDown()
                || mc.options.keyJump.isDown()
                || mc.options.keyShift.isDown();
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

    public boolean isArmorMode() {
        return isEnabled() && mode.is(Mode.Control) && armored.getValue();
    }

}
