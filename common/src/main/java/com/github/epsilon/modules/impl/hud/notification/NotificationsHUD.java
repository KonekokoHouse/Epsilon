package com.github.epsilon.modules.impl.hud.notification;

import com.github.epsilon.graphics.renderers.RectRenderer;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.HudModule;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.render.animation.Animation;
import com.google.common.base.Suppliers;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.awt.*;
import java.util.Iterator;
import java.util.function.Supplier;

public class NotificationsHUD extends HudModule {

    public static final NotificationsHUD INSTANCE = new NotificationsHUD();

    private NotificationsHUD() {
        super("Notifications Hud", Category.HUD, 4f, 4f, 150f, 35f);
    }

    private final DoubleSetting scale = doubleSetting("Scale", 1.0, 0.5, 2.0, 0.1);
    private final IntSetting backgroundAlpha = intSetting("BackgroundAlpha", 201, 0, 255, 1);
    private final IntSetting displayTime = intSetting("DisplayTime", 2000, 500, 5000, 100);

    private final Supplier<TextRenderer> textRendererSupplier = Suppliers.memoize(TextRenderer::create);
    private final Supplier<RectRenderer> rectRendererSupplier = Suppliers.memoize(RectRenderer::create);

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        NotificationManager.INSTANCE.update();
        if (NotificationManager.INSTANCE.isEmpty()) return;

        TextRenderer textRenderer = textRendererSupplier.get();
        RectRenderer rectRenderer = rectRendererSupplier.get();

        float s = scale.getValue().floatValue();
        float boxHeight = textRenderer.getLineHeight(s) * 4.0f - 20.0f;
        float spacing = boxHeight + 4.0f * s;
        int bgAlpha = backgroundAlpha.getValue();

        Iterator<Notification> iterator = NotificationManager.INSTANCE.getNotifications().iterator();
        int index = 0;
        int visibleCount = 0;
        float maxBoxWidth = 0f;

        while (iterator.hasNext()) {
            Notification n = iterator.next();
            n.update();
            if (n.isExpired()) continue;

            float boxWidth = Math.max(150.0f * s, 12.0f * s + textRenderer.getWidth(n.getTitle() + " " + n.getSubTitle(), s));
            if (boxWidth > maxBoxWidth) maxBoxWidth = boxWidth;

            float renderX = getHorizontalAnchor() == HorizontalAnchor.Right ? this.x + this.width - boxWidth
                    : getHorizontalAnchor() == HorizontalAnchor.Center ? this.x + (this.width - boxWidth) / 2.0f
                      : this.x;
            float targetY = getVerticalAnchor() == VerticalAnchor.Bottom ? this.y + this.height - (index + 1) * spacing : this.y + index * spacing;

            if (Math.abs(targetY - n.getCurrentY()) > 0.5f) {
                n.setTargetY(targetY);
                Animation anim = n.getYAnimation();
                if (anim != null) anim.setDuration(300L);
            }

            float y = n.getCurrentY();
            long time = System.currentTimeMillis() - n.getCreateTime();
            long exitTime = time - n.getDisplayDuration();
            boolean skipIntro = n.shouldSkipIntroAnimation();

            if (!skipIntro && time <= 300L) {
                float p = easeOutCubic(time / 300.0f);
                float w = boxWidth * p;
                rectRenderer.addRect(renderX + boxWidth - w, y, w, boxHeight, new Color(118, 185, 0, 255));
            } else if (!skipIntro && time <= 500L) {
                float p = easeOutCubic((time - 300L) / 200.0f);
                int a = (int) (bgAlpha * p);
                rectRenderer.addRect(renderX, y, boxWidth, boxHeight, new Color(0, 0, 0, a));
                float sliderWidth = 4.0f * s + (boxWidth - 4.0f * s) * (1.0f - p);
                rectRenderer.addRect(renderX, y, sliderWidth, boxHeight, new Color(118, 185, 0, 255));
                renderText(textRenderer, n, renderX, y, boxHeight, s, (int) (255 * p));
            } else if (exitTime < 0) {
                rectRenderer.addRect(renderX, y, boxWidth, boxHeight, new Color(0, 0, 0, bgAlpha));
                rectRenderer.addRect(renderX, y, 4.0f * s, boxHeight, new Color(118, 185, 0, 255));
                renderText(textRenderer, n, renderX, y, boxHeight, s, 255);
            } else if (exitTime <= 200L) {
                float p = easeOutCubicDec(exitTime / 200.0f);
                int a = (int) (bgAlpha * p);
                rectRenderer.addRect(renderX, y, boxWidth, boxHeight, new Color(0, 0, 0, a));
                float sliderWidth = 4.0f * s + (boxWidth - 4.0f * s) * (1.0f - p);
                rectRenderer.addRect(renderX, y, sliderWidth, boxHeight, new Color(118, 185, 0, 255));
                renderText(textRenderer, n, renderX, y, boxHeight, s, (int) (255 * p));
            } else if (exitTime <= 500L) {
                float p = easeOutCubicDec((exitTime - 200L) / 300.0f);
                float w = boxWidth * p;
                rectRenderer.addRect(renderX + boxWidth - w, y, w, boxHeight, new Color(118, 185, 0, 255));
            }

            index++;
            visibleCount++;
        }

        rectRenderer.drawAndClear();
        textRenderer.drawAndClear();

        float totalHeight = Math.max(boxHeight, visibleCount * (boxHeight + 4.0f * s));
        setBounds(maxBoxWidth > 0f ? maxBoxWidth : 150f * s, totalHeight);
    }

    private void renderText(TextRenderer textRenderer, Notification n, float x, float y, float boxHeight, float s, int alpha) {
        float textY = y + boxHeight * 0.5f - s - textRenderer.getLineHeight(s) * 0.5f;
        Color titleColor = n.isModule()
                ? new Color(n.getMode() == NotificationMode.Success ? 118 : 255, n.getMode() == NotificationMode.Success ? 185 : 75, n.getMode() == NotificationMode.Success ? 0 : 75, alpha)
                : getModeColor(n.getMode(), alpha);
        textRenderer.addText(n.getTitle(), x + 8.0f * s, textY, s, titleColor);
        textRenderer.addText(" " + n.getSubTitle(), x + 8.0f * s + textRenderer.getWidth(n.getTitle(), s), textY, s, new Color(255, 255, 255, alpha));
    }

    private Color getModeColor(NotificationMode mode, int alpha) {
        return switch (mode) {
            case Success -> new Color(118, 185, 0, alpha);
            case Error -> new Color(255, 75, 75, alpha);
            case Question -> new Color(255, 255, 85, alpha);
            case Info -> new Color(85, 170, 255, alpha);
        };
    }

    private static float easeOutCubic(float t) {
        return 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t);
    }

    private static float easeOutCubicDec(float t) {
        return 1.0f - easeOutCubic(t);
    }

    public static void addModuleNotification(String moduleName, boolean enabled) {
        NotificationManager.INSTANCE.postModuleNotification(moduleName, enabled, INSTANCE.displayTime.getValue());
    }

}
