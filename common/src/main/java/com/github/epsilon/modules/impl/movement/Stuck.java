package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.ClickEvent;
import com.github.epsilon.events.impl.KeyboardInputEvent;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.events.impl.TravelEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.utils.network.PacketUtils;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

public class Stuck extends Module {

    public static final Stuck INSTANCE = new Stuck();

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.NoPacket);

    private Stuck() {
        super("Stuck", Category.MOVEMENT);
    }

    private float lastYaw;
    private float lastPitch;

    private enum Mode {
        NoPacket,
        CancelMove
    }

    @Override
    protected void onDisable() {
        if (mode.is(Mode.NoPacket)) {
            if (mc.player != null && !mc.player.onGround()) {
                PacketUtils.sendSilently(new ServerboundMovePlayerPacket.PosRot(mc.player.getX() + 1337, mc.player.getY(), mc.player.getZ() + 1337, mc.player.getYRot() + 0.01f, mc.player.getXRot(), mc.player.onGround(), mc.player.horizontalCollision));
            }
        }
    }

    @EventHandler
    private void onKeyboardInput(KeyboardInputEvent event) {
        event.setForward(0);
        event.setStrafe(0);
    }

    @EventHandler
    private void onPacket(PacketEvent.Send e) {
        if (mode.is(Mode.NoPacket)) {
            if (e.getPacket() instanceof ServerboundMovePlayerPacket || (e.getPacket() instanceof ClientboundSetEntityMotionPacket setEntityMotionPacket && setEntityMotionPacket.id() == mc.player.getId())) {
                e.setCancelled(true);
            }
        }
        if (e.getPacket() instanceof ClientboundPlayerPositionPacket) {
            toggle();
        }
    }

    @EventHandler
    private void onTravel(TravelEvent event) {
        if (mode.is(Mode.CancelMove)) {
            if (mc.player.positionReminder < 19) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    private void onInteract(ClickEvent event) {
        if (mode.is(Mode.NoPacket)) {
            if (mc.player.getYRot() != lastYaw || mc.player.getXRot() != lastPitch) {
                PacketUtils.sendSilently(new ServerboundMovePlayerPacket.Rot(mc.player.getYRot(), mc.player.getXRot(), mc.player.onGround(), mc.player.horizontalCollision));
            }
            lastPitch = mc.player.getXRot();
            lastYaw = mc.player.getYRot();
        }
    }

}
