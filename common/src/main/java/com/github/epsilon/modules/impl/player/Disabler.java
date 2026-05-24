package com.github.epsilon.modules.impl.player;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.events.impl.TickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.SettingGroup;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.utils.network.PacketUtils;
import com.github.epsilon.utils.player.ChatUtils;
import com.github.epsilon.utils.timer.TimerUtils;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.game.*;
import net.minecraft.util.Mth;

import java.util.Random;

public class Disabler extends Module {

    public static final Disabler INSTANCE = new Disabler();

    private Disabler() {
        super("Disabler", Category.PLAYER);
    }

    private final SettingGroup sgGrimAC = settingGroup("Grim AC");
    private final SettingGroup sgACA = settingGroup("Anti Cheat Addition");
    private final SettingGroup sgThemis = settingGroup("Themis");

    private final BoolSetting logging = boolSetting("Logging", false);
    private final BoolSetting onlyRemoteServer = boolSetting("Only Remote Server", false);

    // Grim Anti Cheat
    private final BoolSetting badPacketsA = boolSetting("Bad Packets A", true).group(sgGrimAC);
    private final BoolSetting aimModulo360 = boolSetting("Aim Modulo 360", true).group(sgGrimAC);
    private final BoolSetting duplicateRotPlace = boolSetting("Duplicate Rot Place", true).group(sgGrimAC);

    // Anti Cheat Addition
    private final BoolSetting acaFastSwitch = boolSetting("Fast Switch", true).group(sgACA);
    private final BoolSetting acaInventoryFrequency = boolSetting("Inventory Frequency", false).group(sgACA);
    private final BoolSetting acaAimStep = boolSetting("Aim Step", true).group(sgACA);
    private final BoolSetting acaPerfectRotation = boolSetting("Perfect Rotation", true).group(sgACA);

    // Themis
    private final BoolSetting themisBlink = boolSetting("Blink", true).group(sgThemis);

    private int lastSlot = -1;

    private long themisBlinkLastSend = System.currentTimeMillis();
    private int themisBlinkCount = 0;

    private float lastYaw = 0.0f;
    private float lastPitch = 0.0f;
    private float currentYaw = 0.0f;
    private float currentPitch = 0.0f;
    private float yawDiff = 0.0f;
    private float pitchDiff = 0.0f;
    private float lastPlacedYawDiff = 0.0f;
    private float lastPlacedPitchDiff = 0.0f;
    private boolean rotated = false;

    private boolean inventoryOpen = false;
    private long inventoryOpenTime = 0L;
    private ServerboundContainerClosePacket storedClosePacket = null;
    private long inventoryCloseDelay = 0L;
    private TimerUtils inventoryTimer = new TimerUtils();

    private final Random random = new Random();

    private float lastMovePacketYaw = 0.0f;
    private float lastMovePacketPitch = 0.0f;

    private static final double[] perfectRotSteps = new double[]{0.0, 5.625, 11.25, 16.875, 22.5, 28.125, 33.75, 39.375, 45.0, 50.625, 56.25, 61.875, 67.5, 73.125, 78.75, 84.375, 90.0};

    @Override
    protected void onEnable() {
        resetState();
    }

    @Override
    protected void onDisable() {
        resetState();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (shouldSkip()) {
            resetState();
            return;
        }

        if (storedClosePacket != null && inventoryTimer.passedMillise(inventoryCloseDelay)) {
            PacketUtils.sendSilently(storedClosePacket);
            log("InventoryFrequency: Released stored close packet");
            storedClosePacket = null;
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (shouldSkip()) {
            resetState();
            return;
        }

        if (event.getPacket() instanceof ServerboundSetCarriedItemPacket packet) {
            int slot = packet.getSlot();
            if (badPacketsA.getValue() && slot == lastSlot && slot != -1) {
                event.setCancelled(true);
                log("BadPacketsA: Cancelled duplicate slot packet: " + slot);
                return;
            }
            if (acaFastSwitch.getValue() && lastSlot != -1 && slot != lastSlot) {
                sendIntermediateSlots(lastSlot, slot);
            }
            log("Processed slot switch: " + lastSlot + " -> " + slot);
            lastSlot = slot;
        }

        if (acaInventoryFrequency.getValue() && event.getPacket() instanceof ServerboundContainerClosePacket closePacket) {
            if (inventoryOpen) {
                long now = System.currentTimeMillis();
                long openDuration = now - inventoryOpenTime;
                if (openDuration <= 150L) {
                    event.setCancelled(true);
                    storedClosePacket = closePacket;
                    inventoryCloseDelay = 151L - openDuration;
                    inventoryTimer.reset();
                    log("InventoryFrequency: Storing close packet, will send after " + inventoryCloseDelay + "ms");
                    inventoryOpen = false;
                    return;
                }
                inventoryOpen = false;
                log("InventoryFrequency: Allowed close packet after " + openDuration + "ms");
            }
        }

        if (themisBlink.getValue()) {
            if (System.currentTimeMillis() - themisBlinkLastSend > 200L) {
                if (themisBlinkCount == 0) {
                    PacketUtils.sendSilently(new ServerboundPongPacket(0));
                }
                themisBlinkLastSend = System.currentTimeMillis();
                themisBlinkCount = 0;
            }
            if (event.getPacket() instanceof ServerboundMovePlayerPacket.StatusOnly || event.getPacket() instanceof ServerboundPongPacket) {
                ++themisBlinkCount;
            }
        }

        if (aimModulo360.getValue()) {
            if (event.getPacket() instanceof ServerboundMovePlayerPacket packet && packet.hasRotation()) {
                float yaw = packet.yRot;
                if (yaw < 360.0f && yaw > -360.0f) {
                    packet.yRot = yaw + 720.0f;
                    log("Disabled AimModulo360");
                }
            }
        }

        if (duplicateRotPlace.getValue()) {
            if (event.getPacket() instanceof ServerboundMovePlayerPacket movePacket) {
                if (movePacket.hasRotation()) {
                    float prevYaw = currentYaw;
                    float prevPitch = currentPitch;
                    currentYaw = movePacket.yRot;
                    currentPitch = movePacket.xRot;
                    yawDiff = Math.abs(currentYaw - prevYaw);
                    pitchDiff = Math.abs(currentPitch - prevPitch);
                    rotated = true;
                    float yawDelta;
                    if (yawDiff > 2.0f && (double) (yawDelta = Math.abs(yawDiff - lastPlacedYawDiff)) < 1.0E-4) {
                        float jitter = 0.001f + random.nextFloat() * 0.009f;
                        float newYaw = currentYaw - jitter;
                        movePacket.yRot = newYaw;
                        log("DuplicateRotPlace: Modified yaw from " + currentYaw + " to " + newYaw + " (yawDiff: " + yawDelta + ")");
                    }
                    float pitchDelta;
                    if (pitchDiff > 2.0f && (double) (pitchDelta = Math.abs(pitchDiff - lastPlacedPitchDiff)) < 1.0E-4) {
                        float jitter = 0.001f + random.nextFloat() * 0.009f;
                        float newPitch = Mth.clamp(currentPitch - jitter, -90.0f, 90.0f);
                        movePacket.xRot = newPitch;
                        log("DuplicateRotPlace: Modified pitch from " + currentPitch + " to " + newPitch + " (pitchDiff: " + pitchDelta + ")");
                    }
                }
            } else if (event.getPacket() instanceof ServerboundUseItemOnPacket && rotated) {
                lastPlacedYawDiff = yawDiff;
                lastPlacedPitchDiff = pitchDiff;
                rotated = false;
            }
        }

        if ((acaAimStep.getValue() || acaPerfectRotation.getValue()) && event.getPacket() instanceof ServerboundMovePlayerPacket movePacket2) {
            float yawAim = movePacket2.yRot;
            float pitchAim = movePacket2.xRot;
            boolean modified = false;
            if (acaAimStep.getValue() && isAimStepRotation(yawAim, pitchAim)) {
                float[] arr = applyAimStep(yawAim, pitchAim);
                yawAim = arr[0];
                pitchAim = arr[1];
                modified = true;
            }
            if (acaPerfectRotation.getValue()) {
                float[] arr = applyPerfectRotation(yawAim, pitchAim);
                if (arr[0] != yawAim || arr[1] != pitchAim) {
                    yawAim = arr[0];
                    pitchAim = arr[1];
                    modified = true;
                    log("PerfectRotation: Modified rotation");
                }
            }
            if (modified) {
                movePacket2.yRot = yawAim;
                movePacket2.xRot = Mth.clamp(pitchAim, -90.0f, 90.0f);
            }
            lastYaw = movePacket2.yRot;
            lastPitch = movePacket2.xRot;
        }

        if (event.getPacket() instanceof ServerboundMovePlayerPacket packet && packet.hasRotation()) {
            lastMovePacketYaw = packet.yRot;
            lastMovePacketPitch = packet.xRot;
        }

        if (event.getPacket() instanceof ServerboundUseItemPacket packet) {
            if (aimModulo360.getValue() || duplicateRotPlace.getValue() || acaAimStep.getValue() || acaPerfectRotation.getValue()) {
                packet.yRot = lastMovePacketYaw;
                packet.xRot = lastMovePacketPitch;
            }
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundLoginPacket) {
            resetState();
            return;
        }

        if (shouldSkip()) {
            resetState();
            return;
        }

        if (event.getPacket() instanceof ClientboundOpenScreenPacket) {
            inventoryOpenTime = System.currentTimeMillis();
            inventoryOpen = true;
            log("Inventory opened at: " + inventoryOpenTime);
        }
    }

    private void resetState() {
        lastSlot = -1;
        inventoryOpenTime = 0L;
        inventoryOpen = false;
        storedClosePacket = null;
        inventoryCloseDelay = 0L;
        themisBlinkLastSend = System.currentTimeMillis();
        themisBlinkCount = 0;
        lastYaw = 0.0f;
        lastPitch = 0.0f;
        currentYaw = 0.0f;
        currentPitch = 0.0f;
        yawDiff = 0.0f;
        pitchDiff = 0.0f;
        lastPlacedYawDiff = 0.0f;
        lastPlacedPitchDiff = 0.0f;
        rotated = false;
        lastMovePacketYaw = 0.0f;
        lastMovePacketPitch = 0.0f;
        inventoryTimer.reset();
    }

    private void sendIntermediateSlots(int fromSlot, int toSlot) {
        int distance = Math.abs(fromSlot - toSlot);
        if (distance > 1 && !isWrapAroundSlot(fromSlot, toSlot)) {
            int step = fromSlot > toSlot ? -1 : 1;
            for (int slot = fromSlot + step; slot != toSlot; slot += step) {
                if (slot < 0 || slot > 8) continue;
                PacketUtils.sendSilently(new ServerboundSetCarriedItemPacket(slot));
                log("Sent intermediate slot: " + slot);
            }
        }
    }

    private boolean isWrapAroundSlot(int fromSlot, int toSlot) {
        return fromSlot == 0 && toSlot == 8 || fromSlot == 8 && toSlot == 0;
    }

    private boolean shouldSkip() {
        return nullCheck()
                || onlyRemoteServer.getValue() && mc.isSingleplayer()
                || mc.player.isSpectator()
                || !mc.player.isAlive()
                || mc.player.isDeadOrDying()
                || mc.screen instanceof ProgressScreen;
    }

    private boolean isAimStepRotation(float yaw, float pitch) {
        if (lastYaw == 0.0f && lastPitch == 0.0f) {
            return false;
        }
        double yawDelta = Math.abs(Mth.wrapDegrees(yaw - lastYaw));
        double pitchDelta = Math.abs(pitch - lastPitch);
        boolean yawStuck = yawDelta < 1.0E-5 && pitchDelta > 1.0;
        boolean pitchStuck = pitchDelta < 1.0E-5 && yawDelta > 1.0;
        return yawStuck || pitchStuck;
    }

    private float[] applyAimStep(float yaw, float pitch) {
        double yawDelta = Math.abs(Mth.wrapDegrees(yaw - lastYaw));
        double pitchDelta = Math.abs(pitch - lastPitch);
        float newYaw = yaw;
        float newPitch = pitch;
        if (yawDelta < 1.0E-5 && pitchDelta > 1.0) {
            newYaw = lastYaw + (float) (random.nextGaussian() * 0.001);
        }
        if (pitchDelta < 1.0E-5 && yawDelta > 1.0) {
            newPitch = lastPitch + (float) (random.nextGaussian() * 0.001);
        }
        return new float[]{newYaw, newPitch};
    }

    private float[] applyPerfectRotation(float yaw, float pitch) {
        double jitter;
        if (lastYaw == 0.0f && lastPitch == 0.0f) {
            return new float[]{yaw, pitch};
        }
        double yawDelta = Math.abs(Mth.wrapDegrees(yaw - lastYaw));
        double pitchDelta = Math.abs(pitch - lastPitch);
        float newYaw = yaw;
        float newPitch = pitch;
        if (!isNearZeroOrMultiple(yawDelta) && isKnownRotationStep(yawDelta)) {
            jitter = random.nextGaussian() * 0.005;
            newYaw = yaw + (float) jitter;
        }
        if (!isNearZeroOrMultiple(pitchDelta) && isKnownRotationStep(pitchDelta)) {
            jitter = random.nextGaussian() * 0.005;
            newPitch = pitch + (float) jitter;
        }
        return new float[]{newYaw, newPitch};
    }

    private boolean isNearZeroOrMultiple(double value) {
        return Math.abs(value) <= 1.0E-10 || isMultipleOf(360.0, value);
    }

    private boolean isMultipleOf(double base, double value) {
        if (base == 0.0) {
            return Math.abs(value) <= 1.0E-10;
        }
        double ratio = value / base;
        return Math.abs(ratio - (double) Math.round(ratio)) <= 1.0E-10;
    }

    private boolean isKnownRotationStep(double value) {
        if (Double.isInfinite(value) || Double.isNaN(value)) return false;
        for (double step : perfectRotSteps) {
            if (isMultipleOf(step, value)) return true;
        }
        return false;
    }

    private void log(String message) {
        if (logging.getValue()) ChatUtils.addChatMessage("[Disabler] " + message);
    }

}
