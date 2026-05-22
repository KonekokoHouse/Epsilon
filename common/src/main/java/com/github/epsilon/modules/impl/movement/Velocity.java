package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.KeyboardInputEvent;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.utils.player.MoveUtils;
import com.github.epsilon.utils.player.PlayerUtils;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;

import java.util.Optional;

public class Velocity extends Module {

    public static final Velocity INSTANCE = new Velocity();

    private Velocity() {
        super("Velocity", Category.MOVEMENT);
    }

    private enum Mode {
        Cancel,
        Legit,
    }

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.Cancel);
    private final BoolSetting serverMotion = boolSetting("Server Motion", true, () -> mode.is(Mode.Cancel));
    private final BoolSetting explosion = boolSetting("Explosion", true, () -> mode.is(Mode.Cancel));
    private final BoolSetting explosionOnlyBlock = boolSetting("Explosion Only Block", false, () -> mode.is(Mode.Cancel) && explosion.getValue());
    public final BoolSetting waterPush = boolSetting("No Water Push", true, () -> mode.is(Mode.Cancel));
    public final BoolSetting entityPush = boolSetting("No Entity Push", true, () -> mode.is(Mode.Cancel));
    public final BoolSetting blockPush = boolSetting("No Block Push", true, () -> mode.is(Mode.Cancel));

    private boolean jump;

    @Override
    protected void onEnable() {
        jump = false;
    }

    @Override
    protected void onDisable() {
        jump = false;
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (nullCheck()) return;

        switch (mode.getValue()) {
            case Cancel -> {
                if (nullCheck()) return;

                if (serverMotion.getValue() && event.getPacket() instanceof ClientboundSetEntityMotionPacket packet && packet.id() == mc.player.getId()) {
                    event.setCancelled(true);
                    return;
                }

                if (
                        explosion.getValue() && event.getPacket() instanceof ClientboundExplodePacket packet
                                && (!explosionOnlyBlock.getValue() || PlayerUtils.isInBlock())
                ) {
                    event.setPacket(new ClientboundExplodePacket(
                            packet.center(),
                            packet.radius(),
                            packet.blockCount(),
                            Optional.empty(),
                            packet.explosionParticle(),
                            packet.explosionSound(),
                            packet.blockParticles()
                    ));
                }
            }
            case Legit -> {
                if (event.getPacket() instanceof ClientboundSetEntityMotionPacket packet && packet.id() == mc.player.getId()) {
                    jump = true;
                }
            }
        }
    }

    @EventHandler
    private void onKeyboardInput(KeyboardInputEvent event) {
        if (jump) {
            if (mc.player.onGround() && MoveUtils.isMoving()) {
                mc.player.input.makeJump();
            }
            jump = false;
        }
    }

}
