package com.github.epsilon.managers;

import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.bus.EventPriority;
import com.github.epsilon.events.impl.*;
import com.github.epsilon.modules.impl.movement.MovementFix;
import com.github.epsilon.utils.rotation.Priority;
import com.github.epsilon.utils.rotation.Rot2f;
import com.github.epsilon.utils.rotation.RotationUtils;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.util.Mth;

import java.util.function.Function;

import static com.github.epsilon.Constants.mc;

public class RotationManager {

    public static final RotationManager INSTANCE = new RotationManager();

    private final Rot2f offset = new Rot2f(0, 0);
    public Rot2f rotations;
    public Rot2f lastRotations = new Rot2f(0, 0);
    public Rot2f targetRotations;
    public Rot2f animationRotation = null;
    public Rot2f lastAnimationRotation = null;

    private boolean active;
    private boolean smoothed;
    private double rotationSpeed;
    private Function<Rot2f, Boolean> raytrace;
    private float randomAngle;
    private boolean s08;

    private int priority;
    private Runnable callback;
    private boolean rotationRequested;

    private RotationManager() {
        EventBus.INSTANCE.subscribe(this);
    }

    public void setRotations(Rot2f rotations, double rotationSpeed) {
        setRotations(rotations, rotationSpeed, null, Priority.Medium, null);
    }

    public void setRotations(Rot2f rotations, double rotationSpeed, Priority priority) {
        setRotations(rotations, rotationSpeed, null, priority, null);
    }

    public void setRotations(Rot2f rotations, double rotationSpeed, Function<Rot2f, Boolean> raytrace) {
        setRotations(rotations, rotationSpeed, raytrace, Priority.Medium, null);
    }

    public void setRotations(Rot2f rotations, double rotationSpeed, Function<Rot2f, Boolean> raytrace, Priority priority) {
        setRotations(rotations, rotationSpeed, raytrace, priority, null);
    }

    public void setRotations(Rot2f rotations, double rotationSpeed, Function<Rot2f, Boolean> raytrace, Priority priority, Runnable callback) {
        if (rotations == null) return;

        if (this.active && priority.priority < this.priority) {
            return;
        }

        if (s08) {
            this.rotations = this.lastRotations = this.targetRotations = new Rot2f(mc.player.getYRot(), mc.player.getXRot());
            this.callback = null;
            s08 = false;
            return;
        }

        this.targetRotations = rotations;
        this.rotationSpeed = rotationSpeed * 18;
        this.raytrace = raytrace;
        this.priority = priority.priority;
        this.callback = callback;
        this.active = true;
        this.rotationRequested = true;

        smooth();
    }

    private void smooth() {
        if (!smoothed) {
            float targetYaw = targetRotations.getYaw();
            float targetPitch = targetRotations.getPitch();

            if (raytrace != null && (Math.abs(targetYaw - rotations.getYaw()) > 5 || Math.abs(targetPitch - rotations.getPitch()) > 5)) {
                final Rot2f trueTargetRotations = new Rot2f(targetRotations.getYaw(), targetRotations.getPitch());

                double speed = (Math.random() * Math.random() * Math.random()) * 20;
                randomAngle += (float) ((20 + (float) (Math.random() - 0.5) * (Math.random() * Math.random() * Math.random() * 360)) * (mc.player.tickCount / 10 % 2 == 0 ? -1 : 1));

                offset.setYaw((float) (offset.getYaw() + -Mth.sin((float) Math.toRadians(randomAngle)) * speed));
                offset.setPitch((float) (offset.getPitch() + Mth.cos((float) Math.toRadians(randomAngle)) * speed));

                targetYaw += offset.getYaw();
                targetPitch += offset.getPitch();

                if (!raytrace.apply(new Rot2f(targetYaw, targetPitch))) {
                    randomAngle = (float) Math.toDegrees(Math.atan2(trueTargetRotations.getYaw() - targetYaw, targetPitch - trueTargetRotations.getPitch())) - 180;

                    targetYaw -= offset.getYaw();
                    targetPitch -= offset.getPitch();

                    offset.setYaw((float) (offset.getYaw() + -Mth.sin((float) Math.toRadians(randomAngle)) * speed));
                    offset.setPitch((float) (offset.getPitch() + Mth.cos((float) Math.toRadians(randomAngle)) * speed));

                    targetYaw = targetYaw + offset.getYaw();
                    targetPitch = targetPitch + offset.getPitch();
                }

                if (!raytrace.apply(new Rot2f(targetYaw, targetPitch))) {
                    offset.setYaw(0);
                    offset.setPitch(0);

                    targetYaw = (float) (targetRotations.getYaw() + Math.random() * 2);
                    targetPitch = (float) (targetRotations.getPitch() + Math.random() * 2);
                }
            }

            rotations = RotationUtils.smooth(new Rot2f(targetYaw, targetPitch), rotationSpeed + Math.random());
        }

        smoothed = true;
    }

    public float getYaw() {
        return getRotation().getYaw();
    }

    public float getPitch() {
        return getRotation().getPitch();
    }

    public Rot2f getRotation() {
        return active ? rotations : new Rot2f(mc.player.getYRot(), mc.player.getXRot());
    }

    public Rot2f getLastRotation() {
        return lastRotations != null ? lastRotations : new Rot2f(mc.player.yRotO, mc.player.xRotO);
    }

    public boolean isDone() {
        return Math.abs(Mth.wrapDegrees(rotations.getYaw() - targetRotations.getYaw())) <= 1 && Math.abs(Mth.wrapDegrees(rotations.getPitch() - targetRotations.getPitch())) <= 1;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isSmoothed() {
        return smoothed;
    }

    public void setSmoothed(boolean smoothed) {
        this.smoothed = smoothed;
    }

    @EventHandler
    private void onRespawn(RespawnEvent event) {
        lastRotations = null;
        rotations = null;
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (active && event.getPacket() instanceof ServerboundUseItemPacket packet) {
            event.setPacket(new ServerboundUseItemPacket(packet.getHand(), packet.getSequence(), rotations.getYaw(), rotations.getPitch()));
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundPlayerPositionPacket || event.getPacket() instanceof ClientboundPlayerRotationPacket) {
            s08 = true;
        }
    }

    @EventHandler(priority = -1000)
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;

        if (!active || rotations == null || lastRotations == null || targetRotations == null) {
            rotations = lastRotations = targetRotations = new Rot2f(mc.player.getYRot(), mc.player.getXRot());
        }

        if (active) {
            smooth();
            EventBus.INSTANCE.post(new AfterRotationEvent());

            if (callback != null) {
                callback.run();
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onTickRotateBack(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;
        if (active && !rotationRequested && callback == null) {
            targetRotations = new Rot2f(mc.player.getYRot(), mc.player.getXRot());
            rotationSpeed = 10 * 18;
            raytrace = null;
            smoothed = false;
        }
        rotationRequested = false;
    }

    @EventHandler
    private void onAnimation(RotationAnimationEvent event) {
        if (active && animationRotation != null && lastAnimationRotation != null) {
            event.setYaw(animationRotation.getYaw());
            event.setLastYaw(lastAnimationRotation.getYaw());
            event.setPitch(animationRotation.getPitch());
            event.setLastPitch(lastAnimationRotation.getPitch());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onSendPosition(SendPositionEvent event) {
        if (active && rotations != null) {
            float yaw = rotations.getYaw();
            float pitch = rotations.getPitch();
            if (!Float.isNaN(yaw) && !Float.isNaN(pitch) && active) {
                event.setYaw(yaw);
                event.setPitch(pitch);
            }

            if (Math.abs((rotations.getYaw() - mc.player.getYRot()) % 360) < 1 && Math.abs((rotations.getPitch() - mc.player.getXRot())) < 1) {
                active = false;
                priority = 0;
                callback = null;
                this.correctDisabledRotations();
            }

            lastRotations = rotations;
        } else {
            lastRotations = new Rot2f(mc.player.getYRot(), mc.player.getXRot());
        }

        lastAnimationRotation = animationRotation;
        animationRotation = new Rot2f(event.getYaw(), event.getPitch());
        if (!active) {
            targetRotations = new Rot2f(mc.player.getYRot(), mc.player.getXRot());
            raytrace = null;
        }
        smoothed = false;
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onMoveInput(KeyboardInputEvent event) {
        MovementFix moveFix = MovementFix.INSTANCE;
        if (moveFix.isEnabled() && active && rotations != null && !mc.player.isFallFlying()) {
            moveFix.fixMovement(event, rotations.getYaw());
        }
    }

    @EventHandler
    private void onRaytrace(RaytraceEvent event) {
        if (rotations != null && event.getEntity() == mc.player && active) {
            event.setYaw(rotations.getYaw());
            event.setPitch(rotations.getPitch());
        }
    }

    @EventHandler
    private void onItemRaytrace(UseItemRaytraceEvent event) {
        if (rotations != null && active) {
            event.setYaw(rotations.getYaw());
            event.setPitch(rotations.getPitch());
        }
    }

    @EventHandler
    private void onStrafe(StrafeEvent event) {
        if (MovementFix.INSTANCE.isEnabled() && active && rotations != null && !mc.player.isFallFlying()) {
            event.setYaw(rotations.getYaw());
        }
    }

    @EventHandler
    private void onJump(JumpEvent event) {
        if (MovementFix.INSTANCE.isEnabled() && active && rotations != null && !mc.player.isFallFlying()) {
            event.setYaw(rotations.getYaw());
        }
    }

    @EventHandler
    private void onFallFlying(FallFlyingEvent event) {
        if (MovementFix.INSTANCE.isEnabled() && active && rotations != null) {
            event.setPitch(rotations.getPitch());
        }
    }

    @EventHandler
    private void onFireworkUpdate(FireworkUpdateEvent event) {
        if (active && rotations != null) {
            event.setYaw(rotations.getYaw());
            event.setPitch(rotations.getPitch());
        }
    }

    @EventHandler
    private void onAttack(AttackYawEvent event) {
        if (rotations != null) {
            event.setYaw(rotations.getYaw());
        }
    }

    private void correctDisabledRotations() {
        Rot2f rotations = new Rot2f(mc.player.getYRot(), mc.player.getXRot());
        Rot2f fixedRotations = RotationUtils.resetRotation(RotationUtils.applySensitivityPatch(rotations, lastRotations));
        mc.player.setYRot(fixedRotations.getYaw());
        mc.player.setXRot(fixedRotations.getPitch());
    }

}
