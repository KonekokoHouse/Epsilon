package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.MoveEvent;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class SafeWalk extends Module {

    public static final SafeWalk INSTANCE = new SafeWalk();

    private SafeWalk() {
        super("Safe Walk", Category.MOVEMENT);
    }

    private final DoubleSetting motion = doubleSetting("Motion", 1.0, 0.5, 1.0, 0.05);
    private final DoubleSetting speedMotion = doubleSetting("Speed Motion", 1.0, 0.5, 1.5, 0.05);
    private final BoolSetting air = boolSetting("Air", false);
    private final BoolSetting directionCheck = boolSetting("Direction Check", true);
    private final BoolSetting pitchCheck = boolSetting("Pitch Check", true);
    private final BoolSetting requirePress = boolSetting("Require Press", false);
    private final BoolSetting blocksOnly = boolSetting("Blocks Only", true);

    @EventHandler
    private void onMove(MoveEvent event) {
        if (canSafeWalk()) {
            Vec3 delta = backOffFromEdge(new Vec3(event.getX(), event.getY(), event.getZ()));
            event.setX(delta.x);
            event.setY(delta.y);
            event.setZ(delta.z);
            event.setCancelled(true);
        }
    }

    @EventHandler
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        if (!mc.player.onGround() || !isMoving() || !canSafeWalk()) return;

        double multiplier = getSpeedLevel() <= 0 ? motion.getValue() : speedMotion.getValue();
        if (multiplier != 1.0) {
            Vec3 velocity = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(velocity.x * multiplier, velocity.y, velocity.z * multiplier);
        }
    }

    private boolean canSafeWalk() {
        if (Scaffold.INSTANCE.isEnabled()) {
            return false;
        }

        if (directionCheck.getValue() && mc.options.keyUp.isDown()) {
            return false;
        }

        if (pitchCheck.getValue() && mc.player.getXRot() < 69.0F) {
            return false;
        }

        if (blocksOnly.getValue() && !(mc.player.getMainHandItem().getItem() instanceof BlockItem)) {
            return false;
        }

        if (requirePress.getValue() && !mc.options.keyUse.isDown()) {
            return false;
        }

        Vec3 velocity = mc.player.getDeltaMovement();
        return mc.player.onGround() && canMove(velocity.x, velocity.z, -1.0) || air.getValue() && canMove(velocity.x, velocity.z, -2.0);
    }

    // 复刻当前版本原版潜行防掉落的边缘回退逻辑
    private Vec3 backOffFromEdge(Vec3 delta) {
        if (mc.player.getAbilities().flying || delta.y > 0.0 || !isAboveGround(mc.player.maxUpStep())) {
            return delta;
        }

        double deltaX = delta.x;
        double deltaZ = delta.z;
        double stepX = Math.signum(deltaX) * 0.05;
        double stepZ = Math.signum(deltaZ) * 0.05;

        while (deltaX != 0.0 && canFallAtLeast(deltaX, 0.0, mc.player.maxUpStep())) {
            if (Math.abs(deltaX) <= 0.05) {
                deltaX = 0.0;
                break;
            }

            deltaX -= stepX;
        }

        while (deltaZ != 0.0 && canFallAtLeast(0.0, deltaZ, mc.player.maxUpStep())) {
            if (Math.abs(deltaZ) <= 0.05) {
                deltaZ = 0.0;
                break;
            }

            deltaZ -= stepZ;
        }

        while (deltaX != 0.0 && deltaZ != 0.0 && canFallAtLeast(deltaX, deltaZ, mc.player.maxUpStep())) {
            if (Math.abs(deltaX) <= 0.05) {
                deltaX = 0.0;
            } else {
                deltaX -= stepX;
            }

            if (Math.abs(deltaZ) <= 0.05) {
                deltaZ = 0.0;
            } else {
                deltaZ -= stepZ;
            }
        }

        return new Vec3(deltaX, delta.y, deltaZ);
    }

    private boolean isAboveGround(float maxDownStep) {
        return mc.player.onGround() || mc.player.fallDistance < maxDownStep && !canFallAtLeast(0.0, 0.0, maxDownStep - mc.player.fallDistance);
    }

    private boolean canFallAtLeast(double deltaX, double deltaZ, double minHeight) {
        AABB boundingBox = mc.player.getBoundingBox();
        return mc.level.noCollision(
                mc.player,
                new AABB(
                        boundingBox.minX + 1.0E-7 + deltaX,
                        boundingBox.minY - minHeight - 1.0E-7,
                        boundingBox.minZ + 1.0E-7 + deltaZ,
                        boundingBox.maxX - 1.0E-7 + deltaX,
                        boundingBox.minY,
                        boundingBox.maxZ - 1.0E-7 + deltaZ
                )
        );
    }

    private boolean canMove(double x, double z, double y) {
        AABB box = mc.player.getBoundingBox().move(x, y, z);
        return !mc.level.getCollisions(mc.player, box).iterator().hasNext();
    }

    private boolean isMoving() {
        return mc.options.keyUp.isDown() != mc.options.keyDown.isDown() || mc.options.keyLeft.isDown() != mc.options.keyRight.isDown();
    }

    private int getSpeedLevel() {
        if (!mc.player.hasEffect(MobEffects.SPEED) || mc.player.getEffect(MobEffects.SPEED) == null) {
            return 0;
        } else {
            return mc.player.getEffect(MobEffects.SPEED).getAmplifier() + 1;
        }
    }

}
