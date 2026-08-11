package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.KeyboardInputEvent;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.SettingGroup;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.utils.player.PlayerUtils;
import com.github.epsilon.utils.timer.TimerUtils;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.WindChargeItem;
import net.minecraft.world.phys.Vec3;

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

    private final SettingGroup sgExclusions = settingGroup("Exclusions");

    private final BoolSetting excludeSpearLunge = boolSetting("Exclude Spear Lunge", false, () -> mode.is(Mode.Cancel)).group(sgExclusions);
    private final BoolSetting excludeWindCharge = boolSetting("Exclude Wind Charge", false, () -> mode.is(Mode.Cancel)).group(sgExclusions);

    private final TimerUtils windChargeTimer = new TimerUtils();

    private boolean jump;

    @Override
    protected void onEnable() {
        jump = false;
        windChargeTimer.reset();
    }

    @Override
    protected void onDisable() {
        jump = false;
        windChargeTimer.reset();
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (excludeWindCharge.getValue() && event.getPacket() instanceof ServerboundUseItemPacket packet) {
            ItemStack stack = mc.player.getItemInHand(packet.getHand());
            if (stack.getItem() instanceof WindChargeItem) {
                windChargeTimer.reset();
            }
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (nullCheck()) return;

        switch (mode.getValue()) {
            case Cancel -> {
                if (nullCheck()) return;

                if (serverMotion.getValue() && event.getPacket() instanceof ClientboundSetEntityMotionPacket packet && packet.getId() == mc.player.getId()) {
                    if (!shouldExcludeMotion(packet)) {
                        event.cancel();
                    }
                    return;
                }

                if (
                        explosion.getValue() && event.getPacket() instanceof ClientboundExplodePacket packet
                                && (!explosionOnlyBlock.getValue() || PlayerUtils.isInBlock())
                ) {
                    if (shouldExcludeExplosion(packet)) {
                        return;
                    }
                    event.setPacket(new ClientboundExplodePacket(
                            packet.getX(),
                            packet.getY(),
                            packet.getZ(),
                            packet.getPower(),
                            packet.getToBlow(),
                            null,
                            packet.getBlockInteraction(),
                            packet.getSmallExplosionParticles(),
                            packet.getLargeExplosionParticles(),
                            packet.getExplosionSound()
                    ));
                }
            }
            case Legit -> {
                if (event.getPacket() instanceof ClientboundSetEntityMotionPacket packet && packet.getId() == mc.player.getId()) {
                    jump = true;
                }
            }
        }
    }

    @EventHandler
    private void onKeyboardInput(KeyboardInputEvent event) {
        if (jump) {
            if (mc.player.onGround() && com.github.epsilon.utils.player.MoveUtils.isMoving()) {
                event.setJump(true);
            }
            jump = false;
        }
    }

    private boolean shouldExcludeMotion(ClientboundSetEntityMotionPacket packet) {
        return excludeSpearLunge.getValue() && isSpearLungeMotion(packet);
    }

    private boolean shouldExcludeExplosion(ClientboundExplodePacket packet) {
        return excludeWindCharge.getValue() && isWindChargeExplosion(packet);
    }

    private boolean isSpearLungeMotion(ClientboundSetEntityMotionPacket packet) {
        return false;
    }

    private boolean isWindChargeExplosion(ClientboundExplodePacket packet) {
        if (windChargeTimer.passedMillise(3000)) return false;

        Vec3 center = new Vec3(packet.getX(), packet.getY(), packet.getZ());
        double dist = center.distanceTo(mc.player.position());
        if (dist > 12.0) return false;

        if (packet.getPower() > 3.0f) return false;

        return packet.getKnockbackX() != 0.0f
                || packet.getKnockbackY() != 0.0f
                || packet.getKnockbackZ() != 0.0f;
    }

}
