package com.github.epsilon.modules.impl.hud;

import com.github.epsilon.graphics.renderers.RoundRectRenderer;
import com.github.epsilon.graphics.renderers.ShadowRenderer;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.graphics.shaders.BlurShader;
import com.github.epsilon.gui.hudeditor.HudEditorScreen;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.HudModule;
import com.github.epsilon.modules.impl.movement.Scaffold;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.render.animation.Easing;
import com.google.common.base.Suppliers;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

import java.awt.*;
import java.util.function.Supplier;

public class ScaffoldBlockHUD extends HudModule {

    public static final ScaffoldBlockHUD INSTANCE = new ScaffoldBlockHUD();

    private ScaffoldBlockHUD() {
        super("Scaffold Block HUD", Category.HUD, 0f, 0f, 84f, 28f);
    }

    private final DoubleSetting scale = doubleSetting("Scale", 1.0, 0.5, 2.0, 0.1);
    private final DoubleSetting cornerRadius = doubleSetting("Corner Radius", 14.0, 0.0, 20.0, 0.5);
    private final ColorSetting backgroundColor = colorSetting("Background Color", new Color(15, 15, 15, 210));
    private final ColorSetting accentColor = colorSetting("Accent Color", new Color(255, 183, 197, 255));
    private final ColorSetting textColor = colorSetting("Text Color", new Color(248, 249, 252, 245));
    private final ColorSetting textSecondary = colorSetting("Text Secondary", new Color(210, 214, 225, 170));

    private final BoolSetting drawShadow = boolSetting("Drop Shadow", true);
    private final DoubleSetting shadowBlur = doubleSetting("Shadow Blur", 4.5, 0.1, 32.0, 0.5, drawShadow::getValue);
    private final ColorSetting shadowColor = colorSetting("Shadow Color", new Color(0, 0, 0, 150), drawShadow::getValue);

    private final BoolSetting backgroundBlur = boolSetting("Background Blur", true);
    private final IntSetting blurStrength = intSetting("Blur Strength", 8, 1, 16, 1);

    private final BoolSetting smoothNumber = boolSetting("Smooth Number", true);
    private final DoubleSetting numberDelay = doubleSetting("Number Delay", 0.15, 0.0, 0.5, 0.01, smoothNumber::getValue);

    private final Supplier<RoundRectRenderer> roundRectRendererSupplier = Suppliers.memoize(RoundRectRenderer::create);
    private final Supplier<ShadowRenderer> shadowRendererSupplier = Suppliers.memoize(ShadowRenderer::create);
    private final Supplier<TextRenderer> textRendererSupplier = Suppliers.memoize(TextRenderer::create);

    private boolean initialized;
    private String previousCountText = "0";
    private String targetCountText = "0";
    private String pendingCountText = "0";
    private float numberAnimProgress = 1.0f;
    private float delayTimer;
    private float visibilityProgress;
    private long lastVisibilityUpdateMs;

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (nullCheck()) {
            resetAnimation();
            return;
        }

        boolean preview = mc.screen instanceof HudEditorScreen;
        int liveBlockCount = Math.max(0, Scaffold.INSTANCE.getBlockCount());
        boolean shouldShow = preview || (Scaffold.INSTANCE.isEnabled() && liveBlockCount > 0);
        int blockCount = preview ? Math.max(64, liveBlockCount) : liveBlockCount;

        updateVisibilityAnimation(shouldShow);
        float easedVisibility = (float) Math.pow(Mth.clamp(visibilityProgress, 0.0f, 1.0f), 3.0);
        float animationProgress = Easing.EASE_OUT_SINE.getFunction().apply(easedVisibility);
        if (!shouldShow && animationProgress <= 0.01f) {
            resetAnimation();
            return;
        }
        if (shouldShow) {
            syncNumberAnimation(blockCount, deltaTracker);
        } else if (!initialized) {
            return;
        }

        RoundRectRenderer roundRectRenderer = roundRectRendererSupplier.get();
        ShadowRenderer shadowRenderer = shadowRendererSupplier.get();
        TextRenderer textRenderer = textRendererSupplier.get();

        float s = scale.getValue().floatValue();
        float pillHeight = 28.0f * s;
        float radius = Math.min(cornerRadius.getValue().floatValue() * s, pillHeight / 2.0f);
        float padX = 8.0f * s;
        float numberScale = 1.0f * s;
        float labelScale = 0.62f * s;
        float dotSize = 5.0f * s;
        float dotGap = 6.0f * s;
        float labelGap = 6.0f * s;
        float numberColumnWidth = getNumberColumnWidth(textRenderer, numberScale);
        float labelWidth = textRenderer.getWidth("Block", labelScale);
        float totalWidth = padX * 2.0f + dotSize + dotGap + numberColumnWidth + labelGap + labelWidth;
        float renderX = computeRenderX(totalWidth);
        float centerX = renderX + totalWidth / 2.0f;
        float centerY = this.y + pillHeight / 2.0f;
        float animatedWidth = totalWidth * animationProgress;
        float animatedX = Mth.lerp(animationProgress, centerX, renderX);
        float animatedRadius = Math.min(radius, animatedWidth / 2.0f);
        float contentAlpha = animationProgress;

        if (backgroundBlur.getValue()) {
            BlurShader.INSTANCE.render(animatedX, this.y, animatedWidth, pillHeight, animatedRadius, blurStrength.getValue());
        }

        if (drawShadow.getValue()) {
            shadowRenderer.addShadow(animatedX, this.y, animatedWidth, pillHeight, animatedRadius, shadowBlur.getValue().floatValue(), withAlpha(shadowColor.getValue(), contentAlpha));
            shadowRenderer.drawAndClear();
        }

        roundRectRenderer.addRoundRect(animatedX, this.y, animatedWidth, pillHeight, animatedRadius, withAlpha(backgroundColor.getValue(), contentAlpha));

        float dotRadius = dotSize / 2.0f;
        float dotX = renderX + padX;
        float animatedDotX = Mth.lerp(animationProgress, centerX - dotRadius, dotX);
        roundRectRenderer.addRoundRect(animatedDotX, centerY - dotRadius, dotSize, dotSize, dotRadius, withAlpha(accentColor.getValue(), contentAlpha));
        roundRectRenderer.drawAndClear();

        float numberColumnX = dotX + dotSize + dotGap;
        float numberTextHeight = textRenderer.getHeight(numberScale);
        float numberY = this.y + (pillHeight - numberTextHeight) / 2.0f - 1.0f * s;
        float animatedNumberColumnX = Mth.lerp(animationProgress, centerX - numberColumnWidth / 2.0f, numberColumnX);

        if (smoothNumber.getValue()) {
            drawRollingNumber(textRenderer, numberScale, animatedNumberColumnX, numberColumnWidth, numberY, contentAlpha);
        } else {
            textRenderer.addText(targetCountText, animatedNumberColumnX, numberY, numberScale, withAlpha(textColor.getValue(), contentAlpha));
        }

        float labelX = numberColumnX + numberColumnWidth + labelGap;
        float animatedLabelX = Mth.lerp(animationProgress, centerX - labelWidth / 2.0f, labelX);
        float labelY = this.y + (pillHeight - textRenderer.getHeight(labelScale)) / 2.0f - 0.5f * s;
        textRenderer.addText("Block", animatedLabelX, labelY, labelScale, withAlpha(textSecondary.getValue(), contentAlpha));
        textRenderer.drawAndClear();

        setBounds(totalWidth, pillHeight);
    }

    private float computeRenderX(float totalWidth) {
        return switch (getHorizontalAnchor()) {
            case Right -> this.x + this.width - totalWidth;
            case Center -> this.x + (this.width - totalWidth) / 2.0f;
            default -> this.x;
        };
    }

    private void syncNumberAnimation(int blockCount, DeltaTracker deltaTracker) {
        String nextText = Integer.toString(blockCount);
        if (!initialized) {
            previousCountText = nextText;
            targetCountText = nextText;
            pendingCountText = nextText;
            numberAnimProgress = 1.0f;
            delayTimer = 0.0f;
            initialized = true;
            return;
        }

        if (!smoothNumber.getValue()) {
            previousCountText = nextText;
            targetCountText = nextText;
            pendingCountText = nextText;
            numberAnimProgress = 1.0f;
            delayTimer = 0.0f;
            return;
        }

        float frameTime = deltaTracker == null ? 0.05f : deltaTracker.getGameTimeDeltaTicks() / 20.0f;
        if (!nextText.equals(targetCountText)) {
            pendingCountText = nextText;
            delayTimer += frameTime;
            if (delayTimer >= numberDelay.getValue().floatValue()) {
                previousCountText = targetCountText;
                targetCountText = pendingCountText;
                numberAnimProgress = 0.0f;
                delayTimer = 0.0f;
            }
        } else {
            delayTimer = 0.0f;
        }

        if (numberAnimProgress < 1.0f) {
            numberAnimProgress = Math.min(1.0f, numberAnimProgress + frameTime * 3.0f);
            if (numberAnimProgress >= 1.0f) {
                previousCountText = targetCountText;
            }
        }
    }

    private void updateVisibilityAnimation(boolean shouldShow) {
        long now = System.currentTimeMillis();
        if (lastVisibilityUpdateMs == 0L) {
            lastVisibilityUpdateMs = now;
        }

        float delta = Mth.clamp((now - lastVisibilityUpdateMs) / (float) 220L, 0.0f, 1.0f);
        lastVisibilityUpdateMs = now;

        if (shouldShow) {
            visibilityProgress = Math.min(1.0f, visibilityProgress + delta);
        } else {
            visibilityProgress = Math.max(0.0f, visibilityProgress - delta);
        }
    }

    private float getNumberColumnWidth(TextRenderer textRenderer, float numberScale) {
        float width = Math.max(
                textRenderer.getWidth(previousCountText, numberScale),
                textRenderer.getWidth(targetCountText, numberScale)
        );
        return width + 4.0f * scale.getValue().floatValue();
    }

    private void drawRollingNumber(TextRenderer textRenderer, float numberScale, float columnX, float columnWidth, float textY, float alphaMul) {
        String previous = previousCountText;
        String target = targetCountText;
        int maxLen = Math.max(previous.length(), target.length());
        float contentWidth = Math.max(
                textRenderer.getWidth(previous, numberScale),
                textRenderer.getWidth(target, numberScale)
        );
        float charX = columnX + Math.max(0.0f, (columnWidth - contentWidth) / 2.0f);
        float slideOffset = 4.0f * scale.getValue().floatValue();

        for (int i = 0; i < maxLen; i++) {
            char previousChar = i < previous.length() ? previous.charAt(i) : '\0';
            char targetChar = i < target.length() ? target.charAt(i) : '\0';

            float slotWidth = 0.0f;
            if (previousChar != '\0') {
                slotWidth = Math.max(slotWidth, textRenderer.getWidth(String.valueOf(previousChar), numberScale));
            }
            if (targetChar != '\0') {
                slotWidth = Math.max(slotWidth, textRenderer.getWidth(String.valueOf(targetChar), numberScale));
            }

            if (previousChar == targetChar) {
                if (targetChar != '\0') {
                    textRenderer.addText(String.valueOf(targetChar), charX, textY, numberScale, withAlpha(textColor.getValue(), alphaMul));
                }
            } else {
                float oldAlpha = 1.0f - numberAnimProgress;
                float newAlpha = numberAnimProgress;

                if (previousChar != '\0' && oldAlpha > 0.01f) {
                    textRenderer.addText(String.valueOf(previousChar), charX, textY - numberAnimProgress * slideOffset, numberScale, withAlpha(textColor.getValue(), oldAlpha * alphaMul));
                }
                if (targetChar != '\0' && newAlpha > 0.01f) {
                    textRenderer.addText(String.valueOf(targetChar), charX, textY + (1.0f - numberAnimProgress) * slideOffset, numberScale, withAlpha(textColor.getValue(), newAlpha * alphaMul));
                }
            }

            charX += slotWidth;
        }
    }

    private void resetAnimation() {
        initialized = false;
        previousCountText = "0";
        targetCountText = "0";
        pendingCountText = "0";
        numberAnimProgress = 1.0f;
        delayTimer = 0.0f;
        visibilityProgress = 0.0f;
        lastVisibilityUpdateMs = 0L;
    }

    private static Color withAlpha(Color color, float alphaMul) {
        int alpha = Mth.clamp((int) (color.getAlpha() * alphaMul), 0, 255);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

}
