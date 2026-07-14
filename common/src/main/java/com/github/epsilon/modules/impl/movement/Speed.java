package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.bus.EventPriority;
import com.github.epsilon.events.impl.MoveEvent;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.player.MoveUtils;
import com.github.epsilon.utils.player.PlayerUtils;
import com.github.epsilon.utils.timer.TimerUtils;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.shapes.VoxelShape;

public class Speed extends Module {

    public static final Speed INSTANCE = new Speed();

    private Speed() {
        super("Speed", Category.MOVEMENT);
    }

    private enum Mode {
        Strafe,
        StrafeStrict,
        Grim,
    }

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.Strafe);
    public final DoubleSetting collideSpeed = doubleSetting("CollideSpeed", 0.08, 0, 0.08, 0.01, () -> mode.is(Mode.Grim));
    private final BoolSetting strict = boolSetting("Strict", true, () -> mode.is(Mode.Grim));
    private final BoolSetting boat = boolSetting("BoatLongJump", true, () -> mode.is(Mode.Grim));
    public final DoubleSetting boatExpand = doubleSetting("BoatExpand", 0.2, 0, 1, 0.01, () -> mode.is(Mode.Grim));
    public final DoubleSetting boatSpeed = doubleSetting("BoatSpeed", 0.2, -2, 2, 0.01, () -> mode.is(Mode.Grim));
    public final DoubleSetting boatJump = doubleSetting("BoatJump", 0.2, 0, 2, 0.01, () -> mode.is(Mode.Grim));

    private final BoolSetting inWater = boolSetting("InWater", false, () -> !mode.is(Mode.Grim));
    private final BoolSetting inBlock = boolSetting("InBlock", false, () -> !mode.is(Mode.Grim));
    private final BoolSetting airStop = boolSetting("AirStop", false, () -> !mode.is(Mode.Grim));
    private final DoubleSetting lagTime = doubleSetting("LagTime", 500, 0, 1000, 1, () -> !mode.is(Mode.Grim));

    private final BoolSetting jump = boolSetting("Jump", true, () -> mode.is(Mode.Strafe));
    private final DoubleSetting strafeSpeed = doubleSetting("Speed", 0.2873, 0, 1.0, 0.0001, () -> mode.is(Mode.Strafe));
    private final BoolSetting explosions = boolSetting("ExplosionsBoost", false, () -> mode.is(Mode.Strafe));
    private final BoolSetting velocity = boolSetting("VelocityBoost", true, () -> mode.is(Mode.Strafe));
    private final DoubleSetting multiplier = doubleSetting("H-Factor", 1.0, 0.0, 5.0, 0.01, () -> mode.is(Mode.Strafe));
    private final DoubleSetting vertical = doubleSetting("V-Factor", 1.0, 0.0, 5.0, 0.01, () -> mode.is(Mode.Strafe));
    private final IntSetting coolDown = intSetting("Cooldown", 1000, 0, 5000, 1, () -> mode.is(Mode.Strafe));
    private final BoolSetting slow = boolSetting("Slowness", false, () -> mode.is(Mode.Strafe));

    private boolean stop;
    private double speed;
    private double distance;

    private int strictTicks;
    private int strafe = 4;
    private int stage;
    private double lastExp;
    private boolean boost;

    private final TimerUtils expTimer = new TimerUtils();
    private final TimerUtils lagTimer = new TimerUtils();

    @Override
    protected void onEnable() {
        if (mc.player != null) {
            speed = getSpeed(false);
            distance = getDistance2D();
        }

        stage = 4;
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mode.is(Mode.Strafe)) {
            if (event.getPacket() instanceof EntityVelocityUpdateS2CPacket packet) {
                if (mc.player != null && packet.getEntityId() == mc.player.getId() && this.velocity.getValue()) {
                    double speed = Math.sqrt(packet.getVelocityX() * packet.getVelocityX() + packet.getVelocityZ() * packet.getVelocityZ());

                    this.lastExp = this.expTimer
                            .passedMillise(this.coolDown.getValue())
                            ? speed
                            : (speed - this.lastExp);

                    if (this.lastExp > 0) {
                        this.expTimer.reset();

                        this.speed += this.lastExp * this.multiplier.getValue();
                        this.distance += this.lastExp * this.multiplier.getValue();

                        if (mc.player.getDeltaMovement().y > 0 && this.vertical.getValue() != 0) {
                            mc.player.setDeltaMovement(0.0, mc.player.getDeltaMovement().y * this.vertical.getValue(), 0.0);
                        }
                    }
                }
            } else if (event.getPacket() instanceof ExplosionS2CPacket packet) {
                if (this.explosions.getValue()) {
                    if (mc.player.position().distanceTo(new Vec3d(packet.getX(), packet.getY(), packet.getZ())) < 15) {
                        double speed = Math.sqrt(packet.getPlayerVelocityX() * packet.getPlayerVelocityX() + packet.getPlayerVelocityZ() * packet.getPlayerVelocityZ());
                        this.lastExp = this.expTimer.passedMillise(this.coolDown.getValue()) ? speed : (speed - this.lastExp);

                        if (this.lastExp > 0) {
                            this.expTimer.reset();

                            this.speed += this.lastExp * this.multiplier.getValue();
                            this.distance += this.lastExp * this.multiplier.getValue();

                            if (mc.player.getDeltaMovement().y > 0) {
                                mc.player.setDeltaMovement(0.0, mc.player.getDeltaMovement().y * this.vertical.getValue(), 0.0);
                            }
                        }
                    }
                }
            }
        }
        if (event.getPacket() instanceof PlayerPositionLookS2CPacket) {
            lagTimer.reset();
            resetStrafe();
        }
    }

    @EventHandler
    private void onMove(MovedEvent event) {
        double dx = mc.player.getX() - mc.player.xo;
        double dz = mc.player.getZ() - mc.player.zo;
        distance = Math.sqrt(dx * dx + dz * dz);
    }

    @EventHandler
    private void onUpdate(PlayerTickEvent.Pre event) {
        if (mode.is(Mode.Grim)) {
            if (!MoveUtils.isMoving()) {
                return;
            }

            int collisions = 0;
            AABB box = strict.getValue() ? mc.player.getBoundingBox() : mc.player.getBoundingBox().expand(1.0);

            for (Entity entity : mc.level.entitiesForRendering()) {
                AABB entityBox = entity.getBoundingBox();
                if (boat.getValue() && mc.player.isOnGround() && entity instanceof BoatEntity && box.intersects(entityBox.expand(boatExpand.getValue()))) {
                    double yaw = Math.toRadians(getSprintYaw(mc.player.getYRot()));
                    double boost = boatSpeed.getValue();
                    mc.player.setDeltaMovement(-Math.sin(yaw) * boost, boatJump.getValue(), Math.cos(yaw) * boost);
                    return;
                } else if (box.intersects(entityBox) && canCauseSpeed(entity)) {
                    collisions++;
                }
            }

            double yaw = Math.toRadians(getSprintYaw(mc.player.getYRot()));
            double boost = this.collideSpeed.getValue() * collisions;
            mc.player.push(-Math.sin(yaw) * boost, 0.0, Math.cos(yaw) * boost);
        }
    }

    private boolean canCauseSpeed(Entity entity) {
        return entity != mc.player && entity instanceof LivingEntity && !(entity instanceof ArmorStandEntity);
    }

    @EventHandler
    private void onMove(MoveEvent event) {
        if (!MoveUtils.isMoving() && airStop.getValue() && !mode.is(Mode.Grim)) {
            mc.player.setDeltaMovement(0.0, mc.player.getDeltaMovement().y, 0.0);
        }
        if (!this.inWater.getValue() && (mc.player.isUnderWater() || mc.player.isInWater() || mc.player.isInLava())
                || mc.player.isRiding()
                || mc.player.isHoldingOntoLadder()
                || !inBlock.getValue() && PlayerUtils.isInBlock()
                || mc.player.getAbilities().flying
                || mc.player.isFallFlying()
                || !MoveUtils.isMoving()) {
            resetStrafe();
            this.stop = true;
            return;
        }
        if (mode.is(Mode.Strafe)) {

            if (this.stop) {
                this.stop = false;
                return;
            }

            if (!lagTimer.passedMillise(this.lagTime.getValue())) {
                return;
            }

            if (this.stage == 1) {
                this.speed = 1.35 * getSpeed(this.slow.getValue(), this.strafeSpeed.getValue()) - 0.01;
            } else if (this.stage == 2 && mc.player.onGround() && (mc.options.jumpKey.isPressed() || this.jump.getValue())) {
                double yMotion = 0.3999 + getJumpSpeed();
                mc.player.setDeltaMovement(0.0, yMotion, 0.0);
                event.setY(yMotion);
                this.speed = this.speed * (this.boost ? 1.6835 : 1.395);
            } else if (this.stage == 3) {
                this.speed = this.distance - 0.66
                        * (this.distance - (this.slow.getValue(), this.strafeSpeed.getValue()));

                this.boost = !this.boost;
            } else {
                if ((canCollide(null,
                        mc.player
                                .getBoundingBox()
                                .offset(0.0, mc.player.getDeltaMovement().y, 0.0))
                        || mc.player.collidedSoftly)
                        && this.stage > 0) {
                    this.stage = 1;
                }

                this.speed = this.distance - this.distance / 159.0;
            }

            this.speed = Math.min(this.speed, 10);
            this.speed = Math.max(this.speed, getSpeed(this.slow.getValue(), this.strafeSpeed.getValue()));
            double n = mc.player.input.movementForward;
            double n2 = mc.player.input.movementSideways;
            double n3 = mc.player.getYaw();
            if (n == 0.0 && n2 == 0.0) {
                event.setX(0.0);
                event.setZ(0.0);
            } else if (n != 0.0 && n2 != 0.0) {
                n *= Math.sin(0.7853981633974483);
                n2 *= Math.cos(0.7853981633974483);
            }
            event.setX((n * this.speed * -Math.sin(Math.toRadians(n3)) + n2 * this.speed * Math.cos(Math.toRadians(n3))) * 0.99);
            event.setZ((n * this.speed * Math.cos(Math.toRadians(n3)) - n2 * this.speed * -Math.sin(Math.toRadians(n3))) * 0.99);

            this.stage++;
            return;
        }
        double speedEffect = 1.0;
        double slowEffect = 1.0;
        if (mc.player.hasStatusEffect(StatusEffects.SPEED)) {
            double amplifier = mc.player.getStatusEffect(StatusEffects.SPEED).getAmplifier();
            speedEffect = 1 + (0.2 * (amplifier + 1));
        }
        if (mc.player.hasStatusEffect(StatusEffects.SLOWNESS)) {
            double amplifier = mc.player.getStatusEffect(StatusEffects.SLOWNESS).getAmplifier();
            slowEffect = 1 + (0.2 * (amplifier + 1));
        }
        final double base = 0.2873f * speedEffect / slowEffect;
        float jumpEffect = 0.0f;
        if (mc.player.hasStatusEffect(StatusEffects.JUMP_BOOST)) {
            jumpEffect += (mc.player.getStatusEffect(StatusEffects.JUMP_BOOST).getAmplifier() + 1) * 0.1f;
        }

        if (mode.getValue() == Mode.StrafeStrict) {
            if (!lagTimer.passedMillise(lagTime.getValue())) {
                return;
            }
            if (strafe == 1) {
                speed = 1.35f * base - 0.01f;
            } else if (strafe == 2) {
                if (mc.player.input.jumping || !mc.player.onGround()) {
                    return;
                }
                float jump = 0.3999999463558197f + jumpEffect;
                event.setY(jump);
                mc.player.setDeltaMovement(mc.player.getDeltaMovement().x, jump, mc.player.getDeltaMovement().z);
                speed *= 2.149;
            } else if (strafe == 3) {
                double moveSpeed = 0.66 * (distance - base);
                speed = distance - moveSpeed;
            } else {
                if ((!mc.level.isSpaceEmpty(mc.player, mc.player.getBoundingBox().offset(0,
                        mc.player.getDeltaMovement().y, 0)) || mc.player.verticalCollision) && strafe > 0) {
                    strafe = 1;
                }
                speed = distance - distance / 159.0;
            }
            strictTicks++;
            speed = Math.max(speed, base);
            double baseMax = 0.465 * speedEffect / slowEffect;
            double baseMin = 0.44 * speedEffect / slowEffect;
            speed = Math.min(speed, strictTicks > 25 ? baseMax : baseMin);
            if (strictTicks > 50) {
                strictTicks = 0;
            }
            final Vec2 motion = handleStrafeMotion((float) speed);
            event.setX(motion.x);
            event.setZ(motion.y);
            strafe++;
        }
    }

    public Vec2 handleStrafeMotion(final float speed) {
        float forward = mc.player.input.movementForward;
        float strafe = mc.player.input.movementSideways;
        float yaw = mc.player.prevYaw + (mc.player.getYaw() - mc.player.prevYaw) * mc.getRenderTickCounter().getTickDelta(true);
        if (forward == 0.0f && strafe == 0.0f) {
            return Vec2.ZERO;
        } else if (forward != 0.0f) {
            if (strafe >= 1.0f) {
                yaw += forward > 0.0f ? -45 : 45;
                strafe = 0.0f;
            } else if (strafe <= -1.0f) {
                yaw += forward > 0.0f ? 45 : -45;
                strafe = 0.0f;
            }
            if (forward > 0.0f) {
                forward = 1.0f;
            } else if (forward < 0.0f) {
                forward = -1.0f;
            }
        }
        float rx = (float) Math.cos(Math.toRadians(yaw));
        float rz = (float) -Math.sin(Math.toRadians(yaw));
        return new Vec2((forward * speed * rz) + (strafe * speed * rx), (forward * speed * rx) - (strafe * speed * rz));
    }

    private double getSpeed(boolean slowness) {
        double defaultSpeed = 0.2873;
        return getSpeed(slowness, defaultSpeed);
    }

    private double getSpeed(boolean slowness, double defaultSpeed) {
        if (mc.player.hasEffect(MobEffects.SPEED)) {
            int amplifier = mc.player.getActiveEffectsMap().get(MobEffects.SPEED).getAmplifier();
            defaultSpeed *= 1.0 + 0.2 * (amplifier + 1);
        }

        if (slowness && mc.player.hasEffect(MobEffects.SLOWNESS)) {
            int amplifier = mc.player.getActiveEffectsMap().get(MobEffects.SLOWNESS).getAmplifier();
            defaultSpeed /= 1.0 + 0.2 * (amplifier + 1);
        }

        if (mc.player.isCrouching()) {
            defaultSpeed /= 5;
        }
        return defaultSpeed;
    }

    private double getJumpSpeed() {
        double defaultSpeed = 0.0;

        if (mc.player.hasEffect(MobEffects.JUMP_BOOST)) {
            int amplifier = mc.player.getActiveEffectsMap().get(MobEffects.JUMP_BOOST).getAmplifier();
            defaultSpeed += (amplifier + 1) * 0.1;
        }

        return defaultSpeed;
    }

    private boolean canCollide(Entity entity, AABB box) {
        BlockCollisionSpliterator<VoxelShape> blockCollisionSpliterator = new BlockCollisionSpliterator<>(mc.level, entity, box, false, (pos, voxelShape) -> voxelShape);

        do {
            if (!blockCollisionSpliterator.hasNext()) {
                return false;
            }
        } while (blockCollisionSpliterator.next().isEmpty());

        return true;
    }

    private double getDistance2D() {
        double xDist = mc.player.getX() - mc.player.xo;
        double zDist = mc.player.getZ() - mc.player.zo;
        return Math.sqrt(xDist * xDist + zDist * zDist);
    }

    private float getSprintYaw(float yaw) {
        if (mc.options.forwardKey.isPressed() && !mc.options.backKey.isPressed()) {
            if (mc.options.leftKey.isPressed() && !mc.options.rightKey.isPressed()) {
                yaw -= 45f;
            } else if (mc.options.rightKey.isPressed() && !mc.options.leftKey.isPressed()) {
                yaw += 45f;
            }
        } else if (mc.options.backKey.isPressed() && !mc.options.forwardKey.isPressed()) {
            yaw += 180f;
            if (mc.options.leftKey.isPressed() && !mc.options.rightKey.isPressed()) {
                yaw += 45f;
            } else if (mc.options.rightKey.isPressed() && !mc.options.leftKey.isPressed()) {
                yaw -= 45f;
            }
        } else if (mc.options.leftKey.isPressed() && !mc.options.rightKey.isPressed()) {
            yaw -= 90f;
        } else if (mc.options.rightKey.isPressed() && !mc.options.leftKey.isPressed()) {
            yaw += 90f;
        }
        return Mth.wrapDegrees(yaw);
    }

    private void resetStrafe() {
        strafe = 4;
        strictTicks = 0;
        speed = 0.0f;
        distance = 0.0;
    }

}
