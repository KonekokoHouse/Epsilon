package com.github.epsilon.managers;

import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.bus.EventPriority;
import com.github.epsilon.events.impl.*;
import com.github.epsilon.modules.impl.ClientSetting;
import com.github.epsilon.modules.impl.movement.MovementFix;
import com.github.epsilon.utils.rotation.Priority;
import com.github.epsilon.utils.rotation.Rot2f;
import com.github.epsilon.utils.rotation.RotationUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
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
    private HitResult hitResult;
    private Entity crosshairPickEntity;

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
        this.rotationSpeed = rotationSpeed * 18.0;
        this.raytrace = raytrace;
        this.priority = priority.priority;
        this.callback = callback;
        this.active = true;

        smooth();
        updatePick();
    }

    private void smooth() {
        if (!smoothed) {
            float targetYaw = targetRotations.getYaw();
            float targetPitch = targetRotations.getPitch();

            if (raytrace != null && (Math.abs(targetYaw - rotations.getYaw()) > 5 || Math.abs(targetPitch - rotations.getPitch()) > 5)) {
                final Rot2f trueTargetRotations = new Rot2f(targetRotations.getYaw(), targetRotations.getPitch());

                double speed = (Math.random() * Math.random() * Math.random()) * 20;
                randomAngle += (float) ((20 + (float) (Math.random() - 0.5) * (Math.random() * Math.random() * Math.random() * 360)) * (mc.player.tickCount / 10 % 2 == 0 ? -1 : 1));

                offset.set(
                        (float) (offset.getYaw() + -Mth.sin((float) Math.toRadians(randomAngle)) * speed),
                        (float) (offset.getPitch() + Mth.cos((float) Math.toRadians(randomAngle)) * speed)
                );

                targetYaw += offset.getYaw();
                targetPitch += offset.getPitch();

                if (!raytrace.apply(new Rot2f(targetYaw, targetPitch))) {
                    randomAngle = (float) Math.toDegrees(Math.atan2(trueTargetRotations.getYaw() - targetYaw, targetPitch - trueTargetRotations.getPitch())) - 180;

                    targetYaw -= offset.getYaw();
                    targetPitch -= offset.getPitch();

                    offset.set(
                            (float) (offset.getYaw() + -Mth.sin((float) Math.toRadians(randomAngle)) * speed),
                            (float) (offset.getPitch() + Mth.cos((float) Math.toRadians(randomAngle)) * speed)
                    );

                    targetYaw = targetYaw + offset.getYaw();
                    targetPitch = targetPitch + offset.getPitch();
                }

                if (!raytrace.apply(new Rot2f(targetYaw, targetPitch))) {
                    offset.set(0, 0);

                    targetYaw = (float) (targetRotations.getYaw() + Math.random() * 2);
                    targetPitch = (float) (targetRotations.getPitch() + Math.random() * 2);
                }
            }

            rotations = RotationUtils.smooth(new Rot2f(targetYaw, targetPitch), rotationSpeed + Math.random());
        }

        smoothed = true;
    }

    private void correctDisabledRotations() {
        Rot2f rotations = new Rot2f(mc.player.getYRot(), mc.player.getXRot());
        Rot2f fixedRotations = RotationUtils.resetRotation(RotationUtils.applySensitivityPatch(rotations, lastRotations));
        mc.player.setYRot(fixedRotations.getYaw());
        mc.player.setXRot(fixedRotations.getPitch());
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

    public HitResult getHitResult() {
        updatePick();
        return hitResult;
    }

    public Entity getCrosshairPickEntity() {
        updatePick();
        return crosshairPickEntity;
    }

    public Rot2f getLastRotation() {
        return lastRotations != null ? lastRotations : new Rot2f(mc.player.yRotO, mc.player.xRotO);
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
        targetRotations = null;
        animationRotation = null;
        lastAnimationRotation = null;
        active = false;
        priority = 0;
        callback = null;
        smoothed = false;
        raytrace = null;
        randomAngle = 0;
        s08 = false;
        offset.set(0, 0);
        hitResult = null;
        crosshairPickEntity = null;
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundPlayerPositionPacket || event.getPacket() instanceof ClientboundPlayerRotationPacket) {
            s08 = true;
        }
    }

    @EventHandler(priority = -1000)
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        if (!active || rotations == null || lastRotations == null || targetRotations == null) {
            rotations = lastRotations = targetRotations = new Rot2f(mc.player.getYRot(), mc.player.getXRot());
        }

        if (active) {
            smooth();
            updatePick();
            EventBus.INSTANCE.post(new AfterRotationEvent());

            if (callback != null) {
                callback.run();
                callback = null;
            }
        }
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

            if (!Float.isNaN(yaw) && !Float.isNaN(pitch)) {
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
        targetRotations = new Rot2f(mc.player.getYRot(), mc.player.getXRot());
        raytrace = null;
        smoothed = false;
        updatePick();
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
        if (ClientSetting.INSTANCE.modifyCrosshair.getValue() && active && rotations != null) {
            event.setYaw(rotations.getYaw());
            event.setPitch(rotations.getPitch());
        }
    }

    @EventHandler
    private void onItemRaytrace(UseItemRaytraceEvent event) {
        if (active && rotations != null) {
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
    private void onUseItem(UseItemEvent event) {
        if (active && rotations != null) {
            event.setYaw(rotations.getYaw());
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

    private void updatePick() {
        if (mc.player == null || mc.level == null) {
            hitResult = null;
            crosshairPickEntity = null;
            return;
        }

        if (!active || rotations == null) {
            hitResult = mc.hitResult;
            crosshairPickEntity = mc.crosshairPickEntity;
            return;
        }

        Entity cameraEntity = mc.getCameraEntity();
        if (cameraEntity == null) {
            return;
        }

        float partialTicks = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        hitResult = raycastHitResult(cameraEntity, partialTicks);
        crosshairPickEntity = hitResult instanceof EntityHitResult entityHitResult ? entityHitResult.getEntity() : null;
    }

    private HitResult raycastHitResult(Entity cameraEntity, float partialTicks) {
        ItemStack itemStack = mc.player.getActiveItem();
        AttackRange itemAttackRange = itemStack.get(DataComponents.ATTACK_RANGE);
        double blockInteractionRange = mc.player.blockInteractionRange();
        HitResult result = null;

        if (itemAttackRange != null) {
            result = getClosestAttackRangeHit(cameraEntity, itemAttackRange, partialTicks);
            if (result instanceof BlockHitResult) {
                result = filterHitResult(result, cameraEntity.getEyePosition(partialTicks), blockInteractionRange);
            }
        }

        if (result == null || result.getType() == HitResult.Type.MISS) {
            result = pick(cameraEntity, blockInteractionRange, mc.player.entityInteractionRange(), partialTicks);
        }

        return result;
    }

    private HitResult getClosestAttackRangeHit(Entity cameraEntity, AttackRange attackRange, float partialTicks) {
        Vec3 look = getViewVector().normalize();
        Vec3 eyePosition = cameraEntity.getEyePosition(partialTicks);
        Vec3 from = eyePosition.add(look.scale(attackRange.effectiveMinRange(cameraEntity)));
        double movementComponent = cameraEntity.getKnownMovement().dot(look);
        Vec3 to = eyePosition.add(look.scale(attackRange.effectiveMaxRange(cameraEntity) + Math.max(0.0, movementComponent)));

        BlockHitResult blockHit = mc.level.clipIncludingBorder(new ClipContext(eyePosition, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, cameraEntity));
        if (blockHit.getType() != HitResult.Type.MISS) {
            to = blockHit.getLocation();
            if (eyePosition.distanceToSqr(to) < eyePosition.distanceToSqr(from)) {
                return blockHit;
            }
        }

        AABB searchArea = AABB.ofSize(from, attackRange.hitboxMargin(), attackRange.hitboxMargin(), attackRange.hitboxMargin())
                .expandTowards(to.subtract(from))
                .inflate(1.0);
        Collection<EntityHitResult> entityHits = ProjectileUtil.getManyEntityHitResult(
                mc.level,
                cameraEntity,
                from,
                to,
                searchArea,
                EntitySelector.CAN_BE_PICKED,
                attackRange.hitboxMargin(),
                ClipContext.Block.OUTLINE,
                true
        );

        EntityHitResult entityHit = null;
        double closestDistance = Double.MAX_VALUE;
        for (EntityHitResult target : entityHits) {
            double distance = eyePosition.distanceToSqr(target.getLocation());
            if (distance < closestDistance) {
                closestDistance = distance;
                entityHit = target;
            }
        }

        if (entityHit != null) {
            return entityHit;
        }

        Vec3 missPosition = eyePosition.add(look);
        return BlockHitResult.miss(missPosition, Direction.getApproximateNearest(look), BlockPos.containing(missPosition));
    }

    private HitResult pick(Entity cameraEntity, double blockInteractionRange, double entityInteractionRange, float partialTicks) {
        double maxDistance = Math.max(blockInteractionRange, entityInteractionRange);
        double maxDistanceSq = Mth.square(maxDistance);
        Vec3 from = cameraEntity.getEyePosition(partialTicks);
        HitResult blockHitResult = pickBlock(cameraEntity, maxDistance, partialTicks);
        double blockDistanceSq = blockHitResult.getLocation().distanceToSqr(from);
        if (blockHitResult.getType() != HitResult.Type.MISS) {
            maxDistanceSq = blockDistanceSq;
            maxDistance = Math.sqrt(blockDistanceSq);
        }

        Vec3 direction = getViewVector();
        Vec3 to = from.add(direction.x * maxDistance, direction.y * maxDistance, direction.z * maxDistance);
        AABB box = cameraEntity.getBoundingBox().expandTowards(direction.scale(maxDistance)).inflate(1.0, 1.0, 1.0);
        EntityHitResult entityHitResult = ProjectileUtil.getEntityHitResult(cameraEntity, from, to, box, EntitySelector.CAN_BE_PICKED, maxDistanceSq);
        return entityHitResult != null && entityHitResult.getLocation().distanceToSqr(from) < blockDistanceSq
                ? filterHitResult(entityHitResult, from, entityInteractionRange)
                : filterHitResult(blockHitResult, from, blockInteractionRange);
    }

    private HitResult pickBlock(Entity cameraEntity, double range, float partialTicks) {
        Vec3 from = cameraEntity.getEyePosition(partialTicks);
        Vec3 viewVector = getViewVector();
        Vec3 to = from.add(viewVector.x * range, viewVector.y * range, viewVector.z * range);
        return mc.level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, cameraEntity));
    }

    private HitResult filterHitResult(HitResult result, Vec3 from, double maxRange) {
        Vec3 hitLocation = result.getLocation();
        if (!hitLocation.closerThan(from, maxRange)) {
            Direction direction = Direction.getApproximateNearest(hitLocation.x - from.x, hitLocation.y - from.y, hitLocation.z - from.z);
            return BlockHitResult.miss(hitLocation, direction, BlockPos.containing(hitLocation));
        }

        return result;
    }

    private Vec3 getViewVector() {
        Rot2f rotation = active && rotations != null ? rotations : new Rot2f(mc.player.getYRot(), mc.player.getXRot());
        return mc.player.calculateViewVector(rotation.getPitch(), rotation.getYaw());
    }

}
