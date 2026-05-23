package com.github.epsilon.managers;

import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.bus.EventPriority;
import com.github.epsilon.events.impl.*;
import com.github.epsilon.modules.impl.movement.MovementFix;
import com.github.epsilon.utils.rotation.Priority;
import com.github.epsilon.utils.rotation.RotationUtils;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.util.Mth;
import org.joml.Vector2f;

import java.util.function.Function;

import static com.github.epsilon.Constants.mc;

public class RotationManager {

    public static final RotationManager INSTANCE = new RotationManager();

    private final Vector2f offset = new Vector2f(0, 0);
    public Vector2f rotations;
    public Vector2f lastRotations = new Vector2f(0, 0);
    public Vector2f targetRotations;
    public Vector2f animationRotation = null;
    public Vector2f lastAnimationRotation = null;

    private boolean active;
    private boolean smoothed;
    private double rotationSpeed;
    private Function<Vector2f, Boolean> raytrace;
    private float randomAngle;
    private boolean s08;

    private int priority;
    private Runnable callback;

    private RotationManager() {
        EventBus.INSTANCE.subscribe(this);
    }

    public void setRotations(Vector2f rotations, double rotationSpeed) {
        setRotations(rotations, rotationSpeed, null, Priority.Medium, null);
    }

    public void setRotations(Vector2f rotations, double rotationSpeed, Priority priority) {
        setRotations(rotations, rotationSpeed, null, priority, null);
    }

    public void setRotations(Vector2f rotations, double rotationSpeed, Function<Vector2f, Boolean> raytrace) {
        setRotations(rotations, rotationSpeed, raytrace, Priority.Medium, null);
    }

    public void setRotations(Vector2f rotations, double rotationSpeed, Function<Vector2f, Boolean> raytrace, Priority priority) {
        setRotations(rotations, rotationSpeed, raytrace, priority, null);
    }

    public void setRotations(Vector2f rotations, double rotationSpeed, Function<Vector2f, Boolean> raytrace, Priority priority, Runnable callback) {
        if (rotations == null) return;

        if (this.active && priority.priority < this.priority) {
            return;
        }

        if (s08) {
            this.rotations = this.lastRotations = this.targetRotations = new Vector2f(mc.player.getYRot(), mc.player.getXRot());
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

        smooth();
    }

    private void smooth() {
        if (!smoothed) {
            float targetYaw = targetRotations.x;
            float targetPitch = targetRotations.y;

            if (raytrace != null && (Math.abs(targetYaw - rotations.x) > 5 || Math.abs(targetPitch - rotations.y) > 5)) {
                final Vector2f trueTargetRotations = new Vector2f(targetRotations.x, targetRotations.y);

                double speed = (Math.random() * Math.random() * Math.random()) * 20;
                randomAngle += (float) ((20 + (float) (Math.random() - 0.5) * (Math.random() * Math.random() * Math.random() * 360)) * (mc.player.tickCount / 10 % 2 == 0 ? -1 : 1));

                offset.x = (float) (offset.x + -Mth.sin((float) Math.toRadians(randomAngle)) * speed);
                offset.y = (float) (offset.y + Mth.cos((float) Math.toRadians(randomAngle)) * speed);

                targetYaw += offset.x;
                targetPitch += offset.y;

                if (!raytrace.apply(new Vector2f(targetYaw, targetPitch))) {
                    randomAngle = (float) Math.toDegrees(Math.atan2(trueTargetRotations.x - targetYaw, targetPitch - trueTargetRotations.y)) - 180;

                    targetYaw -= offset.x;
                    targetPitch -= offset.y;

                    offset.x = (float) (offset.x + -Mth.sin((float) Math.toRadians(randomAngle)) * speed);
                    offset.y = (float) (offset.y + Mth.cos((float) Math.toRadians(randomAngle)) * speed);

                    targetYaw = targetYaw + offset.x;
                    targetPitch = targetPitch + offset.y;
                }

                if (!raytrace.apply(new Vector2f(targetYaw, targetPitch))) {
                    offset.x = 0;
                    offset.y = 0;

                    targetYaw = (float) (targetRotations.x + Math.random() * 2);
                    targetPitch = (float) (targetRotations.y + Math.random() * 2);
                }
            }

            rotations = RotationUtils.smooth(new Vector2f(targetYaw, targetPitch), rotationSpeed + Math.random());
        }

        smoothed = true;
    }

    public float getXRot() {
        return getRotation().y();
    }
    public float getYRot() {
        return getRotation().x();
    }

    public Vector2f getRotation() {
        return active ? rotations : new Vector2f(mc.player.getYRot(), mc.player.getXRot());
    }

    public Vector2f getLastRotation() {
        return lastRotations != null ? lastRotations : new Vector2f(mc.player.yRotO, mc.player.xRotO);
    }

    public boolean isDone() {
        return Math.abs(Mth.wrapDegrees(rotations.x - targetRotations.x)) <= 1 && Math.abs(Mth.wrapDegrees(rotations.y - targetRotations.y)) <= 1;
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
            event.setPacket(new ServerboundUseItemPacket(packet.getHand(), packet.getSequence(), rotations.x, rotations.y));
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
            rotations = lastRotations = targetRotations = new Vector2f(mc.player.getYRot(), mc.player.getXRot());
        }

        if (active) {
            smooth();
            EventBus.INSTANCE.post(new AfterRotationEvent());

            if (callback != null) {
                callback.run();
            }
        }
    }

    @EventHandler
    private void onAnimation(RotationAnimationEvent event) {
        if (active && animationRotation != null && lastAnimationRotation != null) {
            event.setYaw(animationRotation.x);
            event.setLastYaw(lastAnimationRotation.x);
            event.setPitch(animationRotation.y);
            event.setLastPitch(lastAnimationRotation.y);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onSendPosition(SendPositionEvent event) {
        if (active && rotations != null) {
            float yaw = rotations.x;
            float pitch = rotations.y;
            if (!Float.isNaN(yaw) && !Float.isNaN(pitch) && active) {
                event.setYaw(yaw);
                event.setPitch(pitch);
            }

            if (Math.abs((rotations.x - mc.player.getYRot()) % 360) < 1 && Math.abs((rotations.y - mc.player.getXRot())) < 1) {
                active = false;
                priority = 0;
                callback = null;
                this.correctDisabledRotations();
            }

            lastRotations = rotations;
        } else {
            lastRotations = new Vector2f(mc.player.getYRot(), mc.player.getXRot());
        }

        lastAnimationRotation = animationRotation;
        animationRotation = new Vector2f(event.getYaw(), event.getPitch());
        targetRotations = new Vector2f(mc.player.getYRot(), mc.player.getXRot());
        raytrace = null;
        smoothed = false;
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onMoveInput(KeyboardInputEvent event) {
        MovementFix moveFix = MovementFix.INSTANCE;
        if (moveFix.isEnabled() && active && rotations != null) {
            moveFix.fixMovement(event, rotations.x);
        }
    }

    @EventHandler
    private void onRaytrace(RaytraceEvent event) {
        if (rotations != null && event.getEntity() == mc.player && active) {
            event.setYaw(rotations.x);
            event.setPitch(rotations.y);
        }
    }

    @EventHandler
    private void onItemRaytrace(UseItemRaytraceEvent event) {
        if (rotations != null && active) {
            event.setYaw(rotations.x);
            event.setPitch(rotations.y);
        }
    }

    @EventHandler
    private void onStrafe(StrafeEvent event) {
        if (MovementFix.INSTANCE.isEnabled() && active && rotations != null) {
            event.setYaw(rotations.x);
        }
    }

    @EventHandler
    private void onJump(JumpEvent event) {
        if (MovementFix.INSTANCE.isEnabled() && active && rotations != null) {
            event.setYaw(rotations.x);
        }
    }

    @EventHandler
    private void onFallFlying(FallFlyingEvent event) {
        if (rotations != null) {
            event.setPitch(rotations.y);
        }
    }

    @EventHandler
    private void onAttack(AttackYawEvent event) {
        if (rotations != null) {
            event.setYaw(rotations.x);
        }
    }

    private void correctDisabledRotations() {
        Vector2f rotations = new Vector2f(mc.player.getYRot(), mc.player.getXRot());
        Vector2f fixedRotations = RotationUtils.resetRotation(RotationUtils.applySensitivityPatch(rotations, lastRotations));
        mc.player.setYRot(fixedRotations.x);
        mc.player.setXRot(fixedRotations.y);
    }

}
