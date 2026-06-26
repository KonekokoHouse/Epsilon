package com.github.epsilon.elements.impl.notification;

import com.github.epsilon.elements.HudModule;
import com.github.epsilon.graphics.renderers.RectRenderer;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.gui.hudeditor.HudEditorScreen;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.render.animation.Easing;
import com.google.common.base.Suppliers;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class Notifications extends HudModule {

    public static final Notifications INSTANCE = new Notifications();

    private Notifications() {
        super("Notifications", 3.2f, 3.2f, 120f, 28f);
    }

    private final DoubleSetting scale = doubleSetting("Scale", 1.0, 0.5, 2.0, 0.1);
    private final IntSetting backgroundAlpha = intSetting("Background Alpha", 145, 0, 255, 1);
    public final IntSetting displayTime = intSetting("Display Time", 2000, 500, 5000, 100);

    private static final float MIN_BOX_WIDTH = 138.0f;
    private static final float ACCENT_BAR_WIDTH = 2.4f;
    private static final float TEXT_PADDING = 3.0f;

    private final Supplier<TextRenderer> textRendererSupplier = Suppliers.memoize(TextRenderer::create);
    private final Supplier<RectRenderer> rectRendererSupplier = Suppliers.memoize(RectRenderer::create);

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Managers.NOTIFICATION.update();
        Notification previewNotification = createPreviewNotification();
        if (Managers.NOTIFICATION.isEmpty() && previewNotification == null) return;

        TextRenderer textRenderer = textRendererSupplier.get();
        RectRenderer rectRenderer = rectRendererSupplier.get();

        float s = scale.getValue().floatValue();
        float anchorWidth = MIN_BOX_WIDTH * s;
        float boxHeight = textRenderer.getHeight(s) * 2.5f;
        float spacing = boxHeight + TEXT_PADDING * s;
        int bgAlpha = backgroundAlpha.getValue();

        List<RenderEntry> entries = new ArrayList<>();
        float totalHeight = 0f;

        for (Notification notification : Managers.NOTIFICATION.getNotifications()) {
            RenderFrame frame = getRenderFrame(notification, spacing);
            if (frame.stage == RenderStage.HIDDEN) continue;

            float boxWidth = getBoxWidth(textRenderer, notification, s);
            totalHeight += frame.occupiedHeight;
            entries.add(new RenderEntry(notification, boxWidth, frame));
        }

        if (entries.isEmpty() && previewNotification != null) {
            float boxWidth = getBoxWidth(textRenderer, previewNotification, s);
            totalHeight = spacing;
            entries.add(new RenderEntry(previewNotification, boxWidth, new RenderFrame(RenderStage.SHOW, 1.0f, spacing)));
        }

        if (entries.isEmpty()) return;

        float resolvedHeight = Math.max(boxHeight, totalHeight);
        float currentY = getBaseY(resolvedHeight);

        for (RenderEntry entry : entries) {
            float renderX = getRenderX(anchorWidth, entry.boxWidth);
            renderNotification(rectRenderer, textRenderer, entry.notification, entry.frame, renderX, currentY, anchorWidth, entry.boxWidth, boxHeight, s, bgAlpha);
            currentY += entry.frame.occupiedHeight;
        }

        rectRenderer.drawAndClear();
        textRenderer.drawAndClear();

        setBounds(anchorWidth, boxHeight);
    }

    private float getBoxWidth(TextRenderer textRenderer, Notification notification, float scale) {
        return Math.max(MIN_BOX_WIDTH * scale, TEXT_PADDING * 3.0f * scale + textRenderer.getWidth(notification.getTitle() + " " + notification.getSubTitle(), scale));
    }

    private float getRenderX(float anchorWidth, float boxWidth) {
        return getHorizontalAnchor() == HorizontalAnchor.Right ? this.x + anchorWidth - boxWidth
                : getHorizontalAnchor() == HorizontalAnchor.Center ? this.x + (anchorWidth - boxWidth) / 2.0f
                  : this.x;
    }

    private float getBaseY(float totalHeight) {
        return getVerticalAnchor() == VerticalAnchor.Bottom ? this.y + this.height - totalHeight : this.y;
    }

    private RenderFrame getRenderFrame(Notification notification, float occupiedHeight) {
        long elapsedTime = notification.getElapsedTime();
        if (!notification.shouldSkipIntroAnim()) {
            if (elapsedTime <= 300L) {
                float progress = Easing.EASE_OUT_CUBIC.getFunction().apply(elapsedTime / 300.0f);
                return new RenderFrame(RenderStage.ENTER_BAR, progress, occupiedHeight * progress);
            }

            if (elapsedTime <= 500L) {
                float progress = Easing.EASE_OUT_CUBIC.getFunction().apply((elapsedTime - 300L) / 200.0f);
                return new RenderFrame(RenderStage.ENTER_CONTENT, progress, occupiedHeight);
            }
        }

        long exitTime = notification.getExitTime();
        if (exitTime < 0L) {
            return new RenderFrame(RenderStage.SHOW, 1.0f, occupiedHeight);
        }

        if (exitTime <= 200L) {
            float progress = 1.0f - Easing.EASE_OUT_CUBIC.getFunction().apply(exitTime / 200.0f);
            return new RenderFrame(RenderStage.EXIT_CONTENT, progress, occupiedHeight);
        }

        if (exitTime <= 500L) {
            float progress = 1.0f - Easing.EASE_OUT_CUBIC.getFunction().apply((exitTime - 200L) / 300.0f);
            return new RenderFrame(RenderStage.EXIT_BAR, progress, occupiedHeight * progress);
        }

        return new RenderFrame(RenderStage.HIDDEN, 0.0f, 0.0f);
    }

    private void renderNotification(RectRenderer rectRenderer, TextRenderer textRenderer, Notification notification, RenderFrame frame, float x, float y, float anchorWidth, float boxWidth, float boxHeight, float scale, int bgAlpha) {
        switch (frame.stage) {
            case ENTER_BAR, EXIT_BAR -> {
                renderStage1(rectRenderer, notification, x, y, anchorWidth, boxWidth, boxHeight, frame.progress);
            }
            case ENTER_CONTENT, EXIT_CONTENT, SHOW -> {
                renderStage2(rectRenderer, textRenderer, notification, x, y, boxWidth, boxHeight, scale, bgAlpha, frame.progress);
            }
            case HIDDEN -> {
            }
        }
    }

    private void renderStage1(RectRenderer rectRenderer, Notification notification, float x, float y, float anchorWidth, float boxWidth, float boxHeight, float progress) {
        float width = isLeftDocked() ? boxWidth * progress : boxWidth - anchorWidth * (1.0f - progress);
        float renderX = isLeftDocked() ? x : x + boxWidth - width;
        rectRenderer.addRect(renderX, y, width, boxHeight, notification.getMode().getColor());
    }

    private void renderStage2(RectRenderer rectRenderer, TextRenderer textRenderer, Notification notification, float x, float y, float boxWidth, float boxHeight, float scale, int bgAlpha, float progress) {
        rectRenderer.addRect(x, y, boxWidth, boxHeight, new Color(0, 0, 0, bgAlpha));
        renderText(textRenderer, notification, x, y, boxHeight, scale, Math.round(255.0f * progress));
        float accentWidth = ACCENT_BAR_WIDTH * scale + (boxWidth - ACCENT_BAR_WIDTH * scale) * (1.0f - progress);
        float accentX = isLeftDocked() ? x + boxWidth - accentWidth : x;
        rectRenderer.addRect(accentX, y, accentWidth, boxHeight, notification.getMode().getColor());
    }

    private void renderText(TextRenderer textRenderer, Notification n, float x, float y, float boxHeight, float s, int alpha) {
        float textY = y + boxHeight / 2.0f - s - textRenderer.getHeight(s) / 2.0f;
        float textX = x + (isLeftDocked() ? TEXT_PADDING * s : TEXT_PADDING * 2.0f * s);
        textRenderer.addText(n.getTitle(), textX, textY, s, new Color(255, 255, 255, alpha));
        textRenderer.addText(" " + n.getSubTitle(), textX + textRenderer.getWidth(n.getTitle(), s), textY, s, n.getMode().getColor(alpha));
    }

    private boolean isLeftDocked() {
        return getHorizontalAnchor() == HorizontalAnchor.Left;
    }

    private Notification createPreviewNotification() {
        if (mc.screen instanceof HudEditorScreen) {
            return new Notification("Preview", "Notification", NotificationMode.Success, false);
        }
        return null;
    }

    private enum RenderStage {
        ENTER_BAR,
        ENTER_CONTENT,
        SHOW,
        EXIT_CONTENT,
        EXIT_BAR,
        HIDDEN
    }

    private record RenderFrame(RenderStage stage, float progress, float occupiedHeight) {
    }

    private record RenderEntry(Notification notification, float boxWidth, RenderFrame frame) {
    }

}
