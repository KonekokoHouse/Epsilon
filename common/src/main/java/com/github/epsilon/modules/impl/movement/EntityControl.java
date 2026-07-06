package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.EntityMoveEvent;
import com.github.epsilon.events.impl.MoveEvent;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.utils.movement.AutoPilotUtil;
import com.github.epsilon.settings.SettingGroup;
import com.github.epsilon.settings.impl.*;
import com.github.epsilon.utils.player.ChatUtils;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
//import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
//import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.*;

//import static com.github.epsilon.Constants.mc;

public class EntityControl extends Module {

    public static final EntityControl INSTANCE = new EntityControl();

    private EntityControl() {
        super("Entity Control", Category.MOVEMENT);
    }

    // ==================== Enums ====================
    private enum ControlMode { Tradition, HappyGhast }
    private enum ActivationMode { Immediate, DoubleTapSpace }

    // ==================== Setting Groups ====================
    private final SettingGroup sgControl = settingGroup("Control");
    private final SettingGroup sgSpeed = settingGroup("Speed");
    private final SettingGroup sgFlight = settingGroup("Flight");
    private final SettingGroup sgMisc = settingGroup("Misc");

    // ==================== Control ====================
    private final RegistryListSetting<EntityType<?>> entities = entityTypeListSetting("entities",
            getAllRideableEntities()).group(sgControl);

    private final BoolSetting spoofSaddle = boolSetting("spoof-saddle", false).group(sgControl);
    private final BoolSetting maxJump = boolSetting("max-jump", true).group(sgControl);
    private final BoolSetting cancelServerPackets = boolSetting("cancel-server-packets", false).group(sgControl);
    private final EnumSetting<ControlMode> controlMode = enumSetting("control-mode", ControlMode.Tradition).group(sgControl);
    private final EnumSetting<ActivationMode> activationMode = enumSetting("activation-mode", ActivationMode.Immediate).group(sgControl);
    private final BoolSetting activationMessage = boolSetting("activation-message", true,
            () -> activationMode.getValue() == ActivationMode.DoubleTapSpace).group(sgControl);
    private final IntSetting dismountResetDelay = intSetting("dismount-reset-delay", 10, 1, 50, 1,
            () -> activationMode.getValue() == ActivationMode.DoubleTapSpace).group(sgControl);
    private final BoolSetting persistentUntilDismount = boolSetting("persistent-until-dismount", true,
            () -> activationMode.getValue() == ActivationMode.DoubleTapSpace).group(sgControl);
    private final KeybindSetting descendKey = keybindSetting("descend-key", GLFW.GLFW_KEY_LEFT_CONTROL,
            () -> controlMode.getValue() == ControlMode.Tradition).group(sgControl);

    // ==================== Speed ====================
    private final BoolSetting speed = boolSetting("speed", false).group(sgSpeed);
    private final DoubleSetting horizontalSpeed = doubleSetting("horizontal-speed", 100, 0, 1000, 1,
            () -> speed.getValue()).group(sgSpeed);
    private final BoolSetting onlyOnGround = boolSetting("only-on-ground", false,
            () -> speed.getValue()).group(sgSpeed);
    private final BoolSetting inWater = boolSetting("in-water", true,
            () -> speed.getValue()).group(sgSpeed);

    // ==================== Flight ====================
    private final BoolSetting flight = boolSetting("fly", false).group(sgFlight);
    private final DoubleSetting verticalSpeed = doubleSetting("vertical-speed", 20, 0, 50, 0.1,
            () -> flight.getValue()).group(sgFlight);
    private final DoubleSetting fallSpeed = doubleSetting("fall-speed", 0, 0, 50, 0.1,
            () -> flight.getValue()).group(sgFlight);
    private final BoolSetting antiKick = boolSetting("anti-fly-kick", true,
            () -> flight.getValue()).group(sgFlight);
    private final IntSetting delay = intSetting("delay", 40, 1, 80, 1,
            () -> flight.getValue() && antiKick.getValue()).group(sgFlight);

    // ==================== Misc ====================
    private final BoolSetting scaleMount = boolSetting("scale-mount", false).group(sgMisc);
    private final DoubleSetting mountScale = doubleSetting("mount-scale", 0.5, 0.0, 1.0, 0.05,
            () -> scaleMount.getValue()).group(sgMisc);
    private final BoolSetting scaleMountWithoutActivation = boolSetting("always-scale-mount", false,
            () -> scaleMount.getValue() && activationMode.getValue() == ActivationMode.DoubleTapSpace).group(sgMisc);

    // ==================== AutoPilot ====================
    private final SettingGroup sgAutoPilot = settingGroup("AutoPilot");
    private final BoolSetting autoPlane = boolSetting("autoplane", false).group(sgAutoPilot);
    private final IntSetting autoPlaneY = intSetting("autoplane-y", 320, -1000, 4000, 1).group(sgAutoPilot);
    private final StringSetting destinationX = stringSetting("destination-x", "0").group(sgAutoPilot);
    private final StringSetting destinationZ = stringSetting("destination-z", "0").group(sgAutoPilot);
    private final BoolSetting toggleAutoPlane = boolSetting("auto-toggle-autoplane", true).group(sgAutoPilot);
    private final BoolSetting autoPauseAutoPlane = boolSetting("auto-pause-autoplane", false).group(sgAutoPilot);
    private final BoolSetting playerDodge = boolSetting("player-dodge", false).group(sgAutoPilot);

    // ==================== State ====================
    private int delayLeft;
    private double lastPacketY = Double.MAX_VALUE;
    private boolean sentPacket;
    private long lastSpacePressTime;
    private boolean doubleTapActive;
    private static final long DOUBLE_TAP_DELAY = 250;
    private boolean shouldControl;
    private boolean lastJumpPressed;
    private int vehicleNullTicks;
    private boolean persistentActive;
    private boolean wasRiding;
    private Entity lastVehicle;

    // External control API (for other modules to override movement)
    public boolean forcePause;
    private Vec3 customMotion;

    public Vec3 pendingTpTarget;
    public boolean isTeleporting;

    // ==================== Lifecycle ====================
    @Override
    protected void onEnable() {
        delayLeft = delay.getValue();
        sentPacket = false;
        lastPacketY = Double.MAX_VALUE;
        doubleTapActive = false;
        lastSpacePressTime = 0;
        shouldControl = false;
    }

    @Override
    protected void onDisable() {
        if (lastVehicle != null) lastVehicle.fallDistance = 0;
        lastVehicle = null;
        doubleTapActive = false;
        persistentActive = false;
        forcePause = false;
        customMotion = null;
    }

    // ==================== Public API (for mixins/other modules) ====================
    public boolean spoofSaddle() { return isEnabled() && spoofSaddle.getValue(); }
    public boolean maxJump() { return isEnabled() && maxJump.getValue(); }

    public boolean cancelJump() {
        Entity vehicle = mc.player.getVehicle();
        if (vehicle == null) return false;
        return isEnabled() && flight.getValue() && entities.getValue().contains(vehicle.getType()) && shouldControl;
    }

    public boolean shouldScaleMount() {
        if (!scaleMount.getValue() || !isEnabled()) return false;
        if (activationMode.getValue() == ActivationMode.DoubleTapSpace) {
            if (!doubleTapActive && !scaleMountWithoutActivation.getValue()) return false;
        }
        Entity vehicle = mc.player.getVehicle();
        return vehicle != null && entities.getValue().contains(vehicle.getType());
    }

    public float getMountScale() { return mountScale.getValue().floatValue(); }
    public Entity getMountedEntity() { return mc.player.getVehicle(); }

    public void setForcePause(boolean pause) {
        this.forcePause = pause;
        if (!pause) this.customMotion = null;
    }

    public void applyCustomMotion(Vec3 motion) {
        if (forcePause) this.customMotion = motion;
    }

    public boolean isControlActive() {
        if (!isEnabled()) return false;
        if (activationMode.getValue() == ActivationMode.Immediate) return true;
        return doubleTapActive;
    }

    // ==================== Events ====================
    @EventHandler
    private void onMove(MoveEvent event) {
        if (nullCheck()) return;
        // TP override and forcePause handled via direct position modification
        if (isTeleporting && pendingTpTarget != null) {
            Entity vehicle = mc.player.getVehicle();
            if (vehicle != null) {
                vehicle.setPos(pendingTpTarget);
                isTeleporting = false;
                pendingTpTarget = null;
            }
        }
        if (forcePause && customMotion != null) {
            event.setX(customMotion.x);
            event.setY(customMotion.y);
            event.setZ(customMotion.z);
            event.setCancelled(true);
        }
    }

    @EventHandler
    private void onEntityMove(EntityMoveEvent event) {
        if (mc.player == null) return;
        Entity entity = event.entity;
        if (entity.getControllingPassenger() != mc.player || !entities.getValue().contains(entity.getType())) return;

        // TP override
        if (isTeleporting && pendingTpTarget != null && entity == mc.player.getVehicle()) {
            Vec3 currentPos = entity.position();
            Vec3 delta = new Vec3(
                    pendingTpTarget.x - currentPos.x,
                    pendingTpTarget.y - currentPos.y,
                    pendingTpTarget.z - currentPos.z);
            ((com.github.epsilon.interfaces.IVec3) (Object) event.movement).epsilon$set(delta.x, delta.y, delta.z);
            entity.hurtMarked = true;
            isTeleporting = false;
            pendingTpTarget = null;
            return;
        }

        // Force pause for external control
        if (forcePause) {
            if (customMotion != null) {
                ((com.github.epsilon.interfaces.IVec3) (Object) event.movement).epsilon$set(customMotion.x, customMotion.y, customMotion.z);
            }
            return;
        }

        // Activation check
        if (activationMode.getValue() == ActivationMode.Immediate) {
            shouldControl = true;
        } else if (activationMode.getValue() == ActivationMode.DoubleTapSpace) {
            shouldControl = doubleTapActive;
        }
        if (!shouldControl) return;

        double velX = entity.getDeltaMovement().x;
        double velY = entity.getDeltaMovement().y;
        double velZ = entity.getDeltaMovement().z;

        // AutoPilot
        float autoYaw = AutoPilotUtil.calcAutoMoveYaw(
                destinationX.getValue(), destinationZ.getValue(),
                autoPlaneY.getValue(), autoPlane.getValue(), playerDodge.getValue());
        if (autoYaw != -999.0F) {
            double speedVal = horizontalSpeed.getValue() / 20.0;
            double rad = Math.toRadians(autoYaw + 90.0);
            double motionX = Math.cos(rad) * speedVal;
            double motionZ = Math.sin(rad) * speedVal;
            if (autoPauseAutoPlane.getValue()) {
                int cx = (int) (mc.player.getX() / 16);
                int cz = (int) (mc.player.getZ() / 16);
                if (!mc.level.getChunkSource().hasChunk(cx, cz)) {
                    velX = 0;
                    velZ = 0;
                } else {
                    velX = motionX;
                    velZ = motionZ;
                }
            } else {
                velX = motionX;
                velZ = motionZ;
            }
            velY = 0;
            ((com.github.epsilon.interfaces.IVec3) (Object) event.movement).epsilon$set(velX, velY, velZ);
            // Auto-toggle when near destination (matching original calcAutoMoveYaw)
            if (toggleAutoPlane.getValue() && autoPlane.getValue()) {
                try {
                    double dx = Double.parseDouble(destinationX.getValue());
                    double dz = Double.parseDouble(destinationZ.getValue());
                    if (Math.sqrt((mc.player.getX() - dx) * (mc.player.getX() - dx)
                            + (mc.player.getZ() - dz) * (mc.player.getZ() - dz)) <= 40) {
                        autoPlane.setValue(false);
                    }
                } catch (NumberFormatException ignored) {}
            }
            return;
        }

        // Speed boost (horizontal only, before flight like original)
        if (speed.getValue()
                && (!onlyOnGround.getValue() || entity.onGround() || entity.isFlyingVehicle())
                && (inWater.getValue() || !entity.isInWater())) {
            Vec3 vel = getHorizontalVelocity(horizontalSpeed.getValue());
            velX = vel.x;
            velZ = vel.z;
        }

        // Flight (vertical control, after speed like original)
        if (flight.getValue()) {
            velY = 0;
            ControlMode mode = controlMode.getValue();
            if (mode == ControlMode.Tradition) {
                if (mc.options.keyJump.isDown()) {
                    velY += verticalSpeed.getValue() / 20;
                }
                if (InputConstants.isKeyDown(mc.getWindow(), descendKey.getValue())) {
                    velY -= verticalSpeed.getValue() / 20;
                } else {
                    velY -= fallSpeed.getValue() / 20;
                }
            } else if (mode == ControlMode.HappyGhast) {
                Vec3 lookVec = mc.player.getLookAngle();
                Vec3 horizontalLook = new Vec3(lookVec.x, 0, lookVec.z).normalize();
                Vec3 left = horizontalLook.cross(new Vec3(0, 1, 0)).normalize();
                Vec3 up = new Vec3(0, 1, 0);

                double moveForward = 0, moveRight = 0, moveUp = 0;
                if (mc.options.keyUp.isDown()) moveForward += 1;
                if (mc.options.keyDown.isDown()) moveForward -= 1;
                if (mc.options.keyRight.isDown()) moveRight += 1;
                if (mc.options.keyLeft.isDown()) moveRight -= 1;
                if (mc.options.keyJump.isDown()) moveUp += 1;

                if (moveForward != 0 || moveRight != 0 || moveUp != 0) {
                    Vec3 forwardVec = lookVec.scale(moveForward);
                    Vec3 rightVec = left.scale(moveRight);
                    Vec3 upVec = up.scale(moveUp);
                    Vec3 moveVec = forwardVec.add(rightVec).add(upVec).normalize();
                    velX = moveVec.x * horizontalSpeed.getValue() / 20;
                    velY = moveVec.y * verticalSpeed.getValue() / 20;
                    velZ = moveVec.z * horizontalSpeed.getValue() / 20;
                } else {
                    velX = 0;
                    velY = -fallSpeed.getValue() / 20;
                    velZ = 0;
                }
            }
        }

        ((com.github.epsilon.interfaces.IVec3) (Object) event.movement).epsilon$set(velX, velY, velZ);
    }

    @EventHandler
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        if (nullCheck()) return;

        // Anti-kick resend (matching original: next tick after sent)
        if (sentPacket && mc.player.getVehicle() != null) {
            Entity vehicle = mc.player.getVehicle();
            mc.player.connection.send(new ServerboundMoveVehiclePacket(
                    new Vec3(vehicle.getX(), lastPacketY, vehicle.getZ()),
                    vehicle.getYRot(), vehicle.getXRot(), vehicle.onGround()));
            sentPacket = false;
        }
        delayLeft -= 1;

        // Double-tap space detection
        if (activationMode.getValue() == ActivationMode.DoubleTapSpace
                && mc.player.getVehicle() != null
                && entities.getValue().contains(mc.player.getVehicle().getType())) {
            boolean jumpPressed = mc.options.keyJump.isDown();
            if (jumpPressed && !lastJumpPressed) {
                long now = System.currentTimeMillis();
                if (now - lastSpacePressTime <= DOUBLE_TAP_DELAY) {
                    doubleTapActive = !doubleTapActive;
                    if (persistentUntilDismount.getValue()) persistentActive = doubleTapActive;
                    if (activationMessage.getValue()) {
                        String msg = doubleTapActive
                                ? com.github.epsilon.assets.i18n.EpsilonTranslations.EntityControl.ACTIVATED.getTranslatedName()
                                : com.github.epsilon.assets.i18n.EpsilonTranslations.EntityControl.DEACTIVATED.getTranslatedName();
                        ChatUtils.addChatMessage(Component.literal(msg)
                                .withStyle(doubleTapActive ? ChatFormatting.GREEN : ChatFormatting.RED));
                    }
                }
                lastSpacePressTime = now;
            }
            lastJumpPressed = jumpPressed;
        }

        // Dismount detection
        boolean currentlyRiding = mc.player.getVehicle() != null;
        boolean shiftPressed = mc.options.keyShift.isDown();
        if (wasRiding && !currentlyRiding && shiftPressed) {
            if (persistentUntilDismount.getValue() && persistentActive) {
                doubleTapActive = false;
                persistentActive = false;
                if (activationMessage.getValue()) {
                    ChatUtils.addChatMessage(Component.literal(
                            com.github.epsilon.assets.i18n.EpsilonTranslations.EntityControl.DEACTIVATED_DISMOUNT.getTranslatedName())
                            .withStyle(ChatFormatting.RED));
                }
            }
        }
        wasRiding = currentlyRiding;

        // Vehicle null timeout
        if (mc.player.getVehicle() == null) {
            vehicleNullTicks++;
            if (vehicleNullTicks >= dismountResetDelay.getValue()) {
                if (!(persistentUntilDismount.getValue() && persistentActive)) {
                    doubleTapActive = false;
                    persistentActive = false;
                }
                vehicleNullTicks = 0;
            }
        } else {
            vehicleNullTicks = 0;
        }
        Entity cv = mc.player.getVehicle();
        if (cv != null) lastVehicle = cv;
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (nullCheck()) return;
        if (!(event.getPacket() instanceof ServerboundMoveVehiclePacket packet) || !antiKick.getValue()) return;

        double currentY = packet.position().y;
        Entity vehicle = mc.player.getVehicle();
        if (delayLeft <= 0 && !sentPacket && shouldFlyDown(currentY)
                && vehicle != null && !vehicle.onGround() && !vehicle.isFlyingVehicle()) {
            Vec3 newPos = new Vec3(packet.position().x, lastPacketY - 0.03130D, packet.position().z);
            ServerboundMoveVehiclePacket newPacket = new ServerboundMoveVehiclePacket(
                    newPos, packet.yRot(), packet.xRot(), packet.onGround());
            event.setCancelled(true);
            sentPacket = true;
            mc.player.connection.send(newPacket);
            delayLeft = delay.getValue();
            return;
        }
        lastPacketY = currentY;
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundMoveVehiclePacket && cancelServerPackets.getValue()) {
            event.setCancelled(true);
        }
    }

    // ==================== Helpers ====================
    private boolean shouldFlyDown(double currentY) {
        if (currentY >= lastPacketY) return true;
        return lastPacketY - currentY < 0.03130D;
    }

    private Vec3 getHorizontalVelocity(double hSpeed) {
        float yaw = mc.player.getYHeadRot();
        double rad = Math.toRadians(yaw + 90);
        // Calculate movement direction from WASD input (like Meteor's PlayerUtils.getHorizontalVelocity)
        float forward = 0, sideways = 0;
        if (mc.options.keyUp.isDown()) forward += 1;
        if (mc.options.keyDown.isDown()) forward -= 1;
        if (mc.options.keyLeft.isDown()) sideways += 1;
        if (mc.options.keyRight.isDown()) sideways -= 1;
        if (forward == 0 && sideways == 0) return Vec3.ZERO;
        double h = hSpeed / 20.0;
        double f = forward, s = sideways;
        double len = Math.sqrt(f * f + s * s);
        f /= len; s /= len;
        double sin = Math.sin(rad);
        double cos = Math.cos(rad);
        return new Vec3((f * cos + s * sin) * h, 0, (f * sin - s * cos) * h);
    }

    private static Set<EntityType<?>> getAllRideableEntities() {
        Set<EntityType<?>> set = new HashSet<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (type != EntityType.MINECART && type != EntityType.LLAMA && type != EntityType.TRADER_LLAMA) {
                set.add(type);
            }
        }
        return set;
    }
}
