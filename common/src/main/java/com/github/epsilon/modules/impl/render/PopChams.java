package com.github.epsilon.modules.impl.render;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.utils.render.WireframeEntityRenderer;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class PopChams extends Module {

    public static final PopChams INSTANCE = new PopChams();

    private final BoolSetting onlyOne = boolSetting("Only One", false);
    private final DoubleSetting renderTime = doubleSetting("Render Time", 1.0, 0.1, 6.0, 0.1);
    private final DoubleSetting yModifier = doubleSetting("Y Modifier", 0.75, -4.0, 4.0, 0.05);
    private final DoubleSetting scaleModifier = doubleSetting("Scale Modifier", -0.25, -4.0, 4.0, 0.05);
    private final BoolSetting fadeOut = boolSetting("Fade Out", true);
    private final ColorSetting sideColor = colorSetting("Side Color", new Color(255, 255, 255, 25), true);
    private final ColorSetting lineColor = colorSetting("Line Color", new Color(255, 255, 255, 127), true);

    private final List<GhostPlayer> ghosts = new ArrayList<>();

    private PopChams() {
        super("Pop Chams", Category.RENDER);
    }

    @Override
    protected void onDisable() {
        synchronized (ghosts) {
            ghosts.clear();
        }
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (!(event.getPacket() instanceof ClientboundEntityEventPacket packet) || packet.getEventId() != EntityEvent.PROTECTED_FROM_DEATH)
            return;

        Entity entity = packet.getEntity(mc.level);
        if (!(entity instanceof Player player)/* || entity == mc.player*/) return;

        synchronized (ghosts) {
            if (onlyOne.getValue()) {
                ghosts.removeIf(ghost -> ghost.uuid.equals(entity.getUUID()));
            }

            ghosts.add(new GhostPlayer(player));
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        synchronized (ghosts) {
            Iterator<GhostPlayer> iterator = ghosts.iterator();
            while (iterator.hasNext()) {
                if (iterator.next().render(event)) {
                    iterator.remove();
                }
            }
        }
    }

    private final class GhostPlayer extends net.minecraft.client.player.RemotePlayer {
        private final UUID uuid;
        private double timer;
        private double scale = 1.0;

        private GhostPlayer(Player player) {
            super(mc.level, new GameProfile(UUID.randomUUID(), "ghost"));
            uuid = player.getUUID();

            copyPosition(player);
            yRotO = getYRot();
            xRotO = getXRot();
            yHeadRot = player.yHeadRot;
            yHeadRotO = yHeadRot;
            yBodyRot = player.yBodyRot;
            yBodyRotO = yBodyRot;
            getAttributes().assignAllValues(player.getAttributes());
            setPose(player.getPose());
            setHealth(player.getHealth());
            setAbsorptionAmount(player.getAbsorptionAmount());
            setOldPosAndRot();
        }

        private boolean render(Render3DEvent event) {
            float frameTime = mc.getDeltaTracker().getGameTimeDeltaTicks() / 20.0f;
            timer += frameTime;
            if (timer > renderTime.getValue()) return true;

            yOld = getY();
            double targetY = getY() + yModifier.getValue() * frameTime;
            setPos(getX(), targetY, getZ());

            scale += scaleModifier.getValue() * frameTime;
            if (scale <= 0.0) return true;

            int alphaSide = sideColor.getValue().getAlpha();
            int alphaLine = lineColor.getValue().getAlpha();
            float fadeFactor = fadeOut.getValue() ? (float) Math.max(0.0, 1.0 - timer / renderTime.getValue()) : 1.0f;

            Color side = withAlpha(sideColor.getValue(), Math.round(alphaSide * fadeFactor));
            Color line = withAlpha(lineColor.getValue(), Math.round(alphaLine * fadeFactor));

            WireframeEntityRenderer.render(event.getPoseStack(), this, scale, side, line, 2.0f);
            return false;
        }

        private Color withAlpha(Color color, int alpha) {
            return new Color(color.getRed(), color.getGreen(), color.getBlue(), Mth.clamp(alpha, 0, 255));
        }

        @Override
        public boolean shouldShowName() {
            return false;
        }

        @Override
        public @Nullable Component belowNameDisplay() {
            return null;
        }
    }
}
