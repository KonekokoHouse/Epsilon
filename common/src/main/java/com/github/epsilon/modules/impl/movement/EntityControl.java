package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.EntityMoveEvent;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.SettingGroup;
import com.github.epsilon.settings.impl.*;
import com.github.epsilon.elements.impl.notification.NotificationMode;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.utils.player.ChatUtils;
import com.github.epsilon.utils.player.PlayerUtils;
import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class EntityControl extends Module {

    public static final EntityControl INSTANCE = new EntityControl();

    private EntityControl() {
        super("Entity Control", Category.MOVEMENT);
    }

    //Enums
    private enum ControlMode { Tradition, HappyGhast }
    private enum ActivationMode { Immediate, DoubleTapSpace }
    private enum AlertDisplayMode { Chat, Notification, Both }

    //Setting Groups
    private final SettingGroup sgControl = settingGroup("Control");
    private final SettingGroup sgSpeed = settingGroup("Speed");
    private final SettingGroup sgFlight = settingGroup("Flight");
    private final SettingGroup sgMisc = settingGroup("Misc");

    //Control
    private final RegistryListSetting<EntityType<?>> entities = entityTypeListSetting("Entities",
            getAllRideableEntities()).group(sgControl);

    private final BoolSetting spoofSaddle = boolSetting("Spoof Saddle", false).group(sgControl);
    private final BoolSetting maxJump = boolSetting("Max jump", true).group(sgControl);
    private final BoolSetting cancelServerPackets = boolSetting("Cancel Server Packets", false).group(sgControl);
    private final EnumSetting<ControlMode> controlMode = enumSetting("Control Mode", ControlMode.Tradition).group(sgControl);
    private final EnumSetting<ActivationMode> activationMode = enumSetting("Activation Mode", ActivationMode.Immediate).group(sgControl);
    private final BoolSetting activationMessage = boolSetting("Activation Message", true,
            () -> activationMode.getValue() == ActivationMode.DoubleTapSpace).group(sgControl);
    private final EnumSetting<AlertDisplayMode> alertDisplayMode = enumSetting("Alert Display Mode", AlertDisplayMode.Chat,
            () -> activationMode.getValue() == ActivationMode.DoubleTapSpace).group(sgControl);
    private final BoolSetting persistentUntilDismount = boolSetting("Persistent Until Dismount", true,
            () -> activationMode.getValue() == ActivationMode.DoubleTapSpace).group(sgControl);
    private final KeybindSetting descendKey = keybindSetting("Descend Key", GLFW.GLFW_KEY_LEFT_CONTROL,
            () -> controlMode.getValue() == ControlMode.Tradition).group(sgControl);

    //Speed
    private final BoolSetting speed = boolSetting("Speed", false).group(sgSpeed);
    private final DoubleSetting horizontalSpeed = doubleSetting("Horizontal Speed", 100, 0, 400, 0.1,
            () -> speed.getValue()).group(sgSpeed);
    private final BoolSetting onlyOnGround = boolSetting("Only On Ground", false,
            () -> speed.getValue()).group(sgSpeed);
    private final BoolSetting inWater = boolSetting("In Water", true,
            () -> speed.getValue()).group(sgSpeed);

    //Flight
    private final BoolSetting flight = boolSetting("Fly", false).group(sgFlight);
    private final DoubleSetting verticalSpeed = doubleSetting("Vertical Speed", 20, 0, 50, 0.1,
            () -> flight.getValue()).group(sgFlight);
    private final DoubleSetting fallSpeed = doubleSetting("Fall Speed", 0, 0, 50, 0.1,
            () -> flight.getValue()).group(sgFlight);
    private final BoolSetting antiKick = boolSetting("Anti Fly Kick", true,
            () -> flight.getValue()).group(sgFlight);
    private final IntSetting delay = intSetting("Delay", 40, 1, 80, 1,
            () -> flight.getValue() && antiKick.getValue()).group(sgFlight);

    //Misc
    private final BoolSetting scaleMount = boolSetting("Scale Mount", false).group(sgMisc);
    private final DoubleSetting mountScale = doubleSetting("Mount Scale", 0.5, 0.0, 1.0, 0.05,
            () -> scaleMount.getValue()).group(sgMisc);
    private final BoolSetting scaleMountWithoutActivation = boolSetting("Always Scale Mount", false,
            () -> scaleMount.getValue() && activationMode.getValue() == ActivationMode.DoubleTapSpace).group(sgMisc);

    //State
    private int delayLeft;
    private double lastPacketY = Double.MAX_VALUE;
    private boolean sentPacket;
    private long lastSpacePressTime;
    private boolean doubleTapActive;
    private static final long DOUBLE_TAP_DELAY = 250;
    private boolean lastJumpPressed;
    private boolean wasRiding;
    private Entity lastVehicle;

    //Lifecycle
    @Override
    protected void onEnable() {
        delayLeft = delay.getValue();
        sentPacket = false;
        lastPacketY = Double.MAX_VALUE;
        doubleTapActive = false;
        lastSpacePressTime = 0;
    }

    @Override
    protected void onDisable() {
        if (lastVehicle != null) lastVehicle.fallDistance = 0;
        lastVehicle = null;
        doubleTapActive = false;
    }

    //Public API (for mixins/other modules)
    public boolean spoofSaddle() { return isEnabled() && spoofSaddle.getValue(); }
    public boolean maxJump() { return isEnabled() && maxJump.getValue(); }

    public boolean cancelJump() {
        Entity vehicle = mc.player.getVehicle();
        if (vehicle == null) return false;
        return flight.getValue() && canControl(vehicle);
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

    public boolean handlesEntity(Entity entity) {
        return isEnabled()
                && mc.player != null
                && entity != null
                && entity.getControllingPassenger() == mc.player
                && entities.getValue().contains(entity.getType());
    }

    public boolean canControl(Entity entity) {
        return handlesEntity(entity) && isControlActive();
    }

    public double getHorizontalSpeed() {
        return horizontalSpeed.getValue();
    }

    public boolean isControlActive() {
        if (!isEnabled()) return false;
        if (activationMode.getValue() == ActivationMode.Immediate) return true;
        return doubleTapActive;
    }

    // Events
    @EventHandler
    private void onEntityMove(EntityMoveEvent event) {
        if (mc.player == null) return;
        Entity entity = event.entity;
        if (!handlesEntity(entity)) return;

        if (!isControlActive()) return;

        double velX = entity.getDeltaMovement().x;
        double velY = entity.getDeltaMovement().y;
        double velZ = entity.getDeltaMovement().z;

        // Speed boost (horizontal only, before flight like original)
        if (speed.getValue()
                && (!onlyOnGround.getValue() || entity.onGround() || entity.isFlyingVehicle())
                && (inWater.getValue() || !entity.isInWater())) {
            Vec3 vel = PlayerUtils.getHorizontalVelocity(horizontalSpeed.getValue());
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

        event.movement = new Vec3(velX, velY, velZ);
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
                    if (activationMessage.getValue()) {
                        String msg = doubleTapActive
                                ? EpsilonTranslations.EntityControl.ACTIVATED.getTranslatedName()
                                : EpsilonTranslations.EntityControl.DEACTIVATED.getTranslatedName();
                        sendActivationAlert(msg, doubleTapActive);
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
            if (persistentUntilDismount.getValue() && doubleTapActive) {
                doubleTapActive = false;
                if (activationMessage.getValue()) {
                    sendActivationAlert(
                            EpsilonTranslations.EntityControl.DEACTIVATED_DISMOUNT.getTranslatedName(),
                            false);
                }
            }
        }
        wasRiding = currentlyRiding;

        if (!currentlyRiding && !persistentUntilDismount.getValue()) {
            doubleTapActive = false;
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

    // Helpers
    private boolean shouldFlyDown(double currentY) {
        if (currentY >= lastPacketY) return true;
        return lastPacketY - currentY < 0.03130D;
    }

    private void sendActivationAlert(String msg, boolean isActivation) {
        AlertDisplayMode mode = alertDisplayMode.getValue();
        ChatFormatting color = isActivation ? ChatFormatting.GREEN : ChatFormatting.RED;
        if (mode == AlertDisplayMode.Chat || mode == AlertDisplayMode.Both) {
            ChatUtils.addChatMessage(Component.literal(msg).withStyle(color));
        }
        if (mode == AlertDisplayMode.Notification || mode == AlertDisplayMode.Both) {
            Managers.NOTIFICATION.notifyHud(msg, "",
                    isActivation ? NotificationMode.Success : NotificationMode.Error,
                    msg.hashCode());
        }
    }

    private static Set<EntityType<?>> getAllRideableEntities() {
        return Set.of(
            EntityType.HORSE, EntityType.DONKEY, EntityType.MULE,
            EntityType.SKELETON_HORSE, EntityType.ZOMBIE_HORSE,
            EntityType.PIG, EntityType.STRIDER,
            EntityType.CAMEL, EntityType.LLAMA, EntityType.TRADER_LLAMA,
            EntityType.HAPPY_GHAST, EntityType.NAUTILUS, EntityType.ZOMBIE_NAUTILUS,
            EntityType.OAK_BOAT, EntityType.SPRUCE_BOAT, EntityType.BIRCH_BOAT,
            EntityType.JUNGLE_BOAT, EntityType.ACACIA_BOAT, EntityType.DARK_OAK_BOAT,
            EntityType.CHERRY_BOAT, EntityType.MANGROVE_BOAT, EntityType.PALE_OAK_BOAT,
            EntityType.BAMBOO_RAFT,
            EntityType.OAK_CHEST_BOAT, EntityType.SPRUCE_CHEST_BOAT, EntityType.BIRCH_CHEST_BOAT,
            EntityType.JUNGLE_CHEST_BOAT, EntityType.ACACIA_CHEST_BOAT, EntityType.DARK_OAK_CHEST_BOAT,
            EntityType.CHERRY_CHEST_BOAT, EntityType.MANGROVE_CHEST_BOAT, EntityType.PALE_OAK_CHEST_BOAT,
            EntityType.BAMBOO_CHEST_RAFT
        );
    }
}
