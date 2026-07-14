package com.github.epsilon.elements.impl;

import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.github.epsilon.elements.HudModule;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.AttackEntityEvent;
import com.github.epsilon.gui.hudeditor.HudEditorScreen;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.render.animation.Easing;
import net.minecraft.client.DeltaTracker;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.awt.*;

public class NiuRen extends HudModule {

    public static final NiuRen INSTANCE = new NiuRen();

    private NiuRen() {
        super("NiuRen", 0.0f, 0.0f, 92.0f, 92.0f * TEXTURE_ASPECT_RATIO);
    }

    private final DoubleSetting size = doubleSetting("Size", 92.0, 32.0, 180.0, 1.0);
    private final IntSetting duration = intSetting("Duration", 1600, 500, 4000, 100);
    private final DoubleSetting spins = doubleSetting("Spins", 3.0, 0.5, 8.0, 0.25);
    private final DoubleSetting wobble = doubleSetting("Wobble", 1.0, 0.0, 2.0, 0.1);

    private long animStartMs = -1L;

    private static final float TEXTURE_ASPECT_RATIO = 751.0f / 376.0f;
    private static final Identifier TEXTURE = ResourceLocationUtils.getIdentifier("textures/hud/deobf.png");

    @Override
    protected void onEnable() {
        animStartMs = -1L;
    }

    @EventHandler
    private void onAttackEntity(AttackEntityEvent event) {
        animStartMs = System.currentTimeMillis();
    }

    @Override
    public void render(DeltaTracker deltaTracker) {
        float baseWidth = size.getValue().floatValue();
        float baseHeight = baseWidth * TEXTURE_ASPECT_RATIO;
        setBounds(baseWidth, baseHeight);

        long now = System.currentTimeMillis();
        long durationMs = duration.getValue().longValue();
        boolean preview = mc.screen instanceof HudEditorScreen;
        float progress;

        if (preview) {
            progress = (now % durationMs) / (float) durationMs;
        } else {
            if (animStartMs < 0L) return;

            long elapsed = now - animStartMs;
            if (elapsed >= durationMs) {
                animStartMs = -1L;
                return;
            }
            progress = Mth.clamp(elapsed / (float) durationMs, 0.0f, 1.0f);
        }

        float chaos = wobble.getValue().floatValue();
        float easedRotation = Easing.EASE_OUT_EXPO.getFunction().apply(progress);
        float popProgress = Mth.clamp(progress / 0.28f, 0.0f, 1.0f);
        float pop = Easing.EASE_OUT_ELASTIC.getFunction().apply(popProgress);
        float fadeOut = 1.0f - Easing.EASE_IN_CUBIC.getFunction().apply(
                Mth.clamp((progress - 0.82f) / 0.18f, 0.0f, 1.0f)
        );

        float redProgress = Mth.sin(Mth.clamp(progress / 0.72f, 0.0f, 1.0f) * Mth.PI);
        int greenBlue = Mth.clamp(Math.round(Mth.lerp(redProgress, 255.0f, 42.0f)), 0, 255);
        int alpha = Mth.clamp(Math.round(255.0f * fadeOut), 0, 255);
        Color tint = new Color(255, greenBlue, greenBlue, alpha);

        float damping = 1.0f - progress;
        float squash = Mth.sin(progress * Mth.PI * 10.0f) * damping * 0.14f * chaos;
        float shakeX = Mth.sin(progress * Mth.PI * 24.0f) * damping * 8.0f * chaos;
        float bobY = -Math.abs(Mth.sin(progress * Mth.PI * 8.0f)) * damping * 7.0f * chaos;
        float rotation = spins.getValue().floatValue() * 360.0f * easedRotation + Mth.sin(progress * Mth.PI * 12.0f) * damping * 28.0f * chaos;

        float drawWidth = baseWidth * pop * (1.0f + squash);
        float drawHeight = baseHeight * pop * (1.0f - squash * 0.55f);
        float centerX = this.x + baseWidth / 2.0f + shakeX;
        float centerY = this.y + baseHeight / 2.0f + bobY;
        float drawX = centerX - drawWidth / 2.0f;
        float drawY = centerY - drawHeight / 2.0f;

        renderScope().rotatedTexture(TEXTURE, drawX, drawY, drawWidth, drawHeight, 0.0f, 0.0f, 1.0f, 1.0f, tint, centerX, centerY, rotation, true);
    }

}
