package com.github.epsilon.modules.impl.player;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.interfaces.LocalPlayerAccessor;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.utils.network.PacketUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;

public class Disabler extends Module {

    public static final Disabler INSTANCE = new Disabler();

    private Disabler() {
        super("Disabler", Category.PLAYER);
    }

    private final BoolSetting badPacketsA = boolSetting("BadPacketsA", true);

    private final BoolSetting sprinting = boolSetting("Sprinting", true);
    private final BoolSetting input = boolSetting("Input", true);

    private int lastSendSlot = -1;
    private boolean hasOldInput;
    private InputState oldInput;
    private boolean shouldRestore;

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        Packet<?> packet = event.getPacket();

        if (badPacketsA.getValue()) {
            if (packet instanceof ServerboundSetCarriedItemPacket setCarriedItemPacket) {
                int slot = setCarriedItemPacket.getSlot();
                if (slot == lastSendSlot && slot != -1) {
                    event.cancel();
                }
                lastSendSlot = setCarriedItemPacket.getSlot();
            }
        }

        if (packet instanceof ServerboundContainerClickPacket || packet instanceof ServerboundContainerClosePacket) {
            event.cancel();

            boolean sprinted = false;

            if (input.getValue()) {
                spoofInput();
                hasOldInput = true;
            }

            if (sprinting.getValue() && mc.player.isSprinting()) {
                raoGuoSprinting(false);
                sprinted = true;
            }

            PacketUtils.sendSilently(packet);

            if (sprinted) {
                raoGuoSprinting(true);
            }

            if (input.getValue() && hasOldInput) {
                restoreInput();
                hasOldInput = false;
            }
        }
    }

    private void raoGuoSprinting(boolean sprintState) {
        mc.player.setSprinting(sprintState);
        ((LocalPlayerAccessor) mc.player).epsilon$setWasSprinting(sprintState); // BadPacketsF
        mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, sprintState ? ServerboundPlayerCommandPacket.Action.START_SPRINTING : ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
    }

    private void spoofInput() {
        if (shouldRestore) return;
        oldInput = new InputState(
                mc.player.input.leftImpulse,
                mc.player.input.forwardImpulse,
                mc.player.input.jumping,
                mc.player.input.shiftKeyDown
        );
        mc.player.input.leftImpulse = 0.0f;
        mc.player.input.forwardImpulse = 0.0f;
        mc.player.input.jumping = false;
        mc.player.input.shiftKeyDown = false;
        mc.getConnection().send(new ServerboundPlayerInputPacket(0.0f, 0.0f, false, false));
        shouldRestore = true;
    }

    private void restoreInput() {
        if (!shouldRestore) return;
        mc.player.input.leftImpulse = oldInput.leftImpulse();
        mc.player.input.forwardImpulse = oldInput.forwardImpulse();
        mc.player.input.jumping = oldInput.jumping();
        mc.player.input.shiftKeyDown = oldInput.shiftKeyDown();
        oldInput = null;
        shouldRestore = false;
    }

    private record InputState(float leftImpulse, float forwardImpulse, boolean jumping, boolean shiftKeyDown) {
    }

}
