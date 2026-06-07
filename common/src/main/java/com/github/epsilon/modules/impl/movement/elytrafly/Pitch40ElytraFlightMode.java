package com.github.epsilon.modules.impl.movement.elytrafly;

import com.github.epsilon.assets.i18n.EpsilonTranslateComponent;
import com.github.epsilon.assets.i18n.TranslateComponent;
import com.github.epsilon.events.impl.KeyboardInputEvent;
import com.github.epsilon.managers.RotationManager;
import com.github.epsilon.modules.impl.hud.notification.NotificationManager;
import com.github.epsilon.modules.impl.hud.notification.NotificationMode;
import com.github.epsilon.modules.impl.movement.ElytraFly;
import com.github.epsilon.utils.player.FindItemResult;
import com.github.epsilon.utils.player.InvUtils;
import com.github.epsilon.utils.rotation.Priority;
import com.github.epsilon.utils.rotation.Rot2f;
import com.github.epsilon.utils.timer.TimerUtils;
import net.minecraft.world.item.Items;

public class Pitch40ElytraFlightMode extends ElytraFlightMode {

    private static final float DESCEND_PITCH = 37.72F;
    private static final float ASCEND_PITCH = -54.77F;
    private static final float RESET_PITCH = -40.0F;
    private static final float TAKEOFF_PITCH = -40.0F;

    private static final TranslateComponent TAKEOFF_COMPLETE = EpsilonTranslateComponent.create("modules.elytra fly", "pitch40_takeoff_complete");
    private static final TranslateComponent BELOW_UPPER_BOUNDS = EpsilonTranslateComponent.create("modules.elytra fly", "pitch40_below_upper_bounds");
    private static final TranslateComponent TOO_CLOSE_TO_LOWER_BOUNDS = EpsilonTranslateComponent.create("modules.elytra fly", "pitch40_too_close_to_lower_bounds");
    private static final TranslateComponent NO_USABLE_ELYTRA = EpsilonTranslateComponent.create("modules.elytra fly", "pitch40_no_usable_elytra");

    private boolean pitchingDown = true;
    private boolean goingUp = true;
    private boolean takingOff;
    private boolean shouldJump;
    private int packetDelayTicks;
    private float pitch;
    private final TimerUtils fireworkTimer = new TimerUtils();

    public Pitch40ElytraFlightMode(ElytraFly elytraFly) {
        super(elytraFly);
    }

    @Override
    public void onEnable() {
        pitchingDown = true;
        goingUp = true;
        takingOff = false;
        shouldJump = false;
        packetDelayTicks = 0;
        pitch = DESCEND_PITCH;
        fireworkTimer.setMs(917813L);

        if (elytraFly.pitch40AutoTakeoff.getValue() && mc.player.getY() < getTakeoffTargetHeight()) {
            takingOff = true;
            pitch = TAKEOFF_PITCH;
            return;
        }

        if (mc.player.getY() < elytraFly.pitch40upperBounds.getValue()) {
            fail(BELOW_UPPER_BOUNDS);
        } else if (mc.player.getY() - 40.0 < elytraFly.pitch40lowerBounds.getValue()) {
            fail(TOO_CLOSE_TO_LOWER_BOUNDS);
        }
    }

    @Override
    public void onDisable() {
        takingOff = false;
        shouldJump = false;
        packetDelayTicks = 0;
    }

    @Override
    public void onPlayerTick() {
        if (takingOff) {
            updateTakeoff();
            redirectRotation();
            return;
        }

        maintainFallFlying();
        updateBounds();
        updatePitch();
        updateFirework();
        redirectRotation();
    }

    @Override
    public void onKeyboardInput(KeyboardInputEvent event) {
        if (shouldJump) {
            event.setJump(true);
            shouldJump = false;
        }
    }

    @Override
    public boolean shouldCancelRightClick() {
        return false;
    }

    private void updateTakeoff() {
        pitch = TAKEOFF_PITCH;

        if (mc.player.getY() >= getTakeoffTargetHeight()) {
            finishTakeoff();
            return;
        }

        FindItemResult elytra = InvUtils.find(Items.ELYTRA);
        if (!canGlide(elytra.found())) {
            fail(NO_USABLE_ELYTRA);
            return;
        }

        if (mc.player.onGround()) {
            shouldJump = true;
            return;
        }

        if (elytraFly.armored.getValue()) {
            refreshArmoredFallFlying(elytra.slot(), false);
        } else if (canStartFallFlying() && startFallFlying()) {
            shouldJump = true;
        }

        if (mc.player.isFallFlying() && fireworkTimer.hasDelayed(elytraFly.pitch40FireworkCooldown.getValue()) && useFirework()) {
            fireworkTimer.reset();
        }
    }

    private void maintainFallFlying() {
        if (mc.player.onGround() || mc.player.isInWater()) return;

        FindItemResult elytra = InvUtils.find(Items.ELYTRA);
        if (!canGlide(elytra.found())) return;

        if (elytraFly.armored.getValue()) {
            refreshArmoredFallFlying(elytra.slot(), true);
        } else if (startFallFlying()) {
            shouldJump = true;
        }
    }

    private void redirectRotation() {
        RotationManager.INSTANCE.setRotations(new Rot2f(mc.player.getYRot(), pitch), 10, Priority.Highest);
    }

    private void finishTakeoff() {
        takingOff = false;
        goingUp = false;
        pitchingDown = true;
        packetDelayTicks = 0;
        pitch = DESCEND_PITCH;
        resetBoundsFromTarget();
        maintainFallFlying();
        NotificationManager.INSTANCE.post(elytraFly.getTranslatedName(), TAKEOFF_COMPLETE.getTranslatedName(), NotificationMode.Success);
    }

    private void refreshArmoredFallFlying(int elytraSlot, boolean respectDelay) {
        if (respectDelay && ++packetDelayTicks <= elytraFly.pitch40PacketDelay.getValue()) {
            return;
        }
        packetDelayTicks = 0;

        int elytra = elytraSlot < 9 ? elytraSlot + 36 : elytraSlot;
        swapArmor(elytra);
        if (!mc.player.isFallFlying() && startFallFlying()) {
            shouldJump = true;
        } else if (mc.player.isFallFlying()) {
            restartFallFlying();
        }
        if (mc.player.isFallFlying() && fireworkTimer.hasDelayed(elytraFly.pitch40FireworkCooldown.getValue()) && useFirework()) {
            fireworkTimer.reset();
        }
        swapArmor(elytra);
    }

    private void updateBounds() {
        if (!elytraFly.pitch40AutoAdjustBounds.getValue()) return;

        if (mc.player.getY() <= elytraFly.pitch40lowerBounds.getValue() - 10.0) {
            resetBounds();
            return;
        }

        if (Math.round(pitch) == RESET_PITCH) {
            goingUp = true;
        } else if (goingUp && mc.player.getDeltaMovement().y <= 0.0) {
            goingUp = false;
            resetBounds();
        }
    }

    private void updatePitch() {
        /*
         * 下降时看向 37.72 度；上升时先快速抬头到 -54.77 度，再缓慢压回 37.72 度。
         */
        if (pitchingDown && mc.player.getY() <= elytraFly.pitch40lowerBounds.getValue()) {
            pitchingDown = false;
        } else if (!pitchingDown && mc.player.getY() >= elytraFly.pitch40upperBounds.getValue()) {
            pitchingDown = true;
        }

        if (!pitchingDown) {
            pitch -= randPitch(elytraFly.pitch40rotationSpeedUp.getValue().floatValue(), 1.0F);

            if (pitch < ASCEND_PITCH) {
                pitch = ASCEND_PITCH;
                pitchingDown = true;
            }
        } else if (pitch < DESCEND_PITCH) {
            pitch += randPitch(elytraFly.pitch40rotationSpeedDown.getValue().floatValue(), 0.50F);
        }
    }

    private void updateFirework() {
        if (!elytraFly.pitch40AutoFirework.getValue()) return;
        if (!fireworkTimer.hasDelayed(elytraFly.pitch40FireworkCooldown.getValue())) return;
        if (Math.round(pitch) != RESET_PITCH) return;
        if (mc.player.getDeltaMovement().y >= elytraFly.pitch40VelocityThreshold.getValue()) return;
        if (mc.player.getY() >= elytraFly.pitch40upperBounds.getValue()) return;

        if (useFirework()) {
            fireworkTimer.reset();
        }
    }

    private void resetBounds() {
        double upper = mc.player.getY() - 5.0;
        elytraFly.pitch40upperBounds.setValue(upper);
        elytraFly.pitch40lowerBounds.setValue(upper - elytraFly.pitch40BoundGap.getValue());
    }

    private void resetBoundsFromTarget() {
        double upper = Math.max(mc.player.getY() - 5.0, getTakeoffTargetHeight());
        elytraFly.pitch40upperBounds.setValue(upper);
        elytraFly.pitch40lowerBounds.setValue(upper - elytraFly.pitch40BoundGap.getValue());
    }

    private double getTakeoffTargetHeight() {
        return elytraFly.pitch40TakeoffTargetHeight.getValue();
    }

    private void fail(TranslateComponent message) {
        NotificationManager.INSTANCE.post(elytraFly.getTranslatedName(), message.getTranslatedName(), NotificationMode.Warning);
        elytraFly.toggle();
    }

    private float randPitch(float pitch, float bound) {
        return (float) (pitch + bound * (Math.random() - 0.5));
    }

}
