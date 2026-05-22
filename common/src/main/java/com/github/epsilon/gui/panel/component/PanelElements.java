package com.github.epsilon.gui.panel.component;

import com.github.epsilon.graphics.renderers.RectRenderer;
import com.github.epsilon.graphics.renderers.RoundRectOutlineRenderer;
import com.github.epsilon.graphics.renderers.RoundRectRenderer;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.graphics.text.ttf.TtfFontLoader;
import com.github.epsilon.gui.panel.MD3Theme;
import com.github.epsilon.gui.panel.PanelLayout;
import com.github.epsilon.gui.panel.dsl.PanelUiTree;
import org.jspecify.annotations.Nullable;

import java.awt.*;

/**
 * 面板通用组件辅助方法集合。
 * <p>
 * 该类统一提供行内控件的对齐规则，以及 switch、filled field、chip、segmented、icon button
 * 等语义元素的绘制/建树辅助，便于不同设置行保持一致的视觉和布局语义。
 */
public class PanelElements {

    public static final float ROW_LABEL_INSET = MD3Theme.ROW_CONTENT_INSET + 4.0f;
    public static final float ICON_BUTTON_SIZE = 20.0f;
    private static final float SWITCH_KNOB_STRETCH = 3.5f;

    private PanelElements() {
    }

    public static void drawRowSurface(RoundRectRenderer roundRectRenderer, PanelLayout.Rect bounds, float hoverProgress) {
        roundRectRenderer.addRoundRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), MD3Theme.CARD_RADIUS, MD3Theme.rowSurface(hoverProgress));
    }

    public static void buildRowSurface(PanelUiTree.Scope scope, PanelLayout.Rect bounds, float hoverProgress) {
        scope.roundRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), MD3Theme.CARD_RADIUS, MD3Theme.rowSurface(hoverProgress));
    }

    /**
     * 计算一行内容中主标签的起始 X 坐标。
     */
    public static float rowLabelX(PanelLayout.Rect bounds) {
        return bounds.x() + ROW_LABEL_INSET;
    }

    /**
     * 将一个控件对齐到行尾区域，并在垂直方向居中。
     */
    public static PanelLayout.Rect alignTrailing(PanelLayout.Rect bounds, float width, float height) {
        return new PanelLayout.Rect(bounds.right() - MD3Theme.ROW_TRAILING_INSET - width, bounds.y() + (bounds.height() - height) / 2.0f, width, height);
    }

    /**
     * 计算行内开关控件的标准区域。
     */
    public static PanelLayout.Rect switchBounds(PanelLayout.Rect bounds) {
        return alignTrailing(bounds, MD3Theme.SWITCH_WIDTH, MD3Theme.SWITCH_HEIGHT);
    }

    public static PanelLayout.Rect compactFieldBounds(PanelLayout.Rect bounds, float width) {
        return alignTrailing(bounds, width, MD3Theme.CONTROL_HEIGHT);
    }

    public static void drawSwitch(RoundRectRenderer roundRectRenderer, PanelLayout.Rect rect, float toggleProgress, float hoverProgress) {
        drawSwitch(roundRectRenderer, null, rect, toggleProgress, hoverProgress);
    }

    public static void drawSwitch(RoundRectRenderer roundRectRenderer, RoundRectOutlineRenderer roundRectOutlineRenderer, PanelLayout.Rect rect, float toggleProgress, float hoverProgress) {
        Color track = MD3Theme.switchTrack(toggleProgress);
        Color knob = MD3Theme.switchKnob(toggleProgress);
        Color outline = MD3Theme.switchTrackOutline(toggleProgress, hoverProgress);
        float clampedToggle = Math.clamp(toggleProgress, 0.0f, 1.0f);
        float knobSize = MD3Theme.SWITCH_HANDLE_SIZE_OFF
                + (MD3Theme.SWITCH_HANDLE_SIZE_ON - MD3Theme.SWITCH_HANDLE_SIZE_OFF) * clampedToggle;
        float stretchFactor = 4.0f * clampedToggle * (1.0f - clampedToggle);
        float knobWidth = knobSize + SWITCH_KNOB_STRETCH * stretchFactor;
        float inset = MD3Theme.SWITCH_HANDLE_INSET_OFF
                + (MD3Theme.SWITCH_HANDLE_INSET_ON - MD3Theme.SWITCH_HANDLE_INSET_OFF) * clampedToggle;
        float knobMinX = rect.x() + inset + knobWidth / 2.0f;
        float knobMaxX = rect.right() - inset - knobWidth / 2.0f;
        float knobCenterX = knobMinX + (knobMaxX - knobMinX) * toggleProgress;
        float knobCenterY = rect.centerY();

        roundRectRenderer.addRoundRect(rect.x(), rect.y(), rect.width(), rect.height(), rect.height() / 2.0f, track);
        if (outline.getAlpha() > 0 && roundRectOutlineRenderer != null) {
            roundRectOutlineRenderer.addOutline(
                    rect.x(), rect.y(), rect.width(), rect.height(),
                    rect.height() / 2.0f,
                    MD3Theme.switchTrackOutlineWidth(toggleProgress),
                    outline
            );
        }
        if (hoverProgress > 0.02f) {
            float haloSize = MD3Theme.SWITCH_STATE_LAYER_SIZE;
            float haloX = knobCenterX - haloSize / 2.0f;
            float haloY = knobCenterY - haloSize / 2.0f;
            roundRectRenderer.addRoundRect(haloX, haloY, haloSize, haloSize, haloSize / 2.0f,
                    MD3Theme.stateLayer(MD3Theme.TEXT_PRIMARY, hoverProgress, 18));
        }
        roundRectRenderer.addRoundRect(knobCenterX - knobWidth / 2.0f, knobCenterY - knobSize / 2.0f, knobWidth, knobSize, knobSize / 2.0f, knob);
    }

    public static void buildSwitch(PanelUiTree.Scope scope, PanelLayout.Rect rect, float toggleProgress, float hoverProgress) {
        scope.toggle(rect, toggleProgress, hoverProgress);
    }

    public static FilledFieldColors drawFilledField(RoundRectRenderer roundRectRenderer, RectRenderer rectRenderer, PanelLayout.Rect bounds,
                                                    boolean focused, float hoverProgress) {
        Color container = MD3Theme.filledFieldSurface(focused, hoverProgress);
        Color text = MD3Theme.filledFieldContent(focused);
        Color caret = MD3Theme.filledFieldCaret(focused);
        Color indicator = MD3Theme.filledFieldIndicator(focused, hoverProgress);

        roundRectRenderer.addRoundRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), MD3Theme.CONTROL_RADIUS, container);
        float indicatorHeight = focused ? 1.5f : 1.0f;
        float indicatorInset = 4.0f;
        rectRenderer.addRect(bounds.x() + indicatorInset, bounds.bottom() - indicatorHeight,
                Math.max(0.0f, bounds.width() - indicatorInset * 2.0f), indicatorHeight, indicator);
        return new FilledFieldColors(text, caret, indicator);
    }

    /**
     * 在 DSL 中构建一个标准 filled field，并返回与其配套的语义色值。
     */
    public static FilledFieldColors buildFilledField(PanelUiTree.Scope scope, PanelLayout.Rect bounds, boolean focused, float hoverProgress) {
        Color text = MD3Theme.filledFieldContent(focused);
        Color caret = MD3Theme.filledFieldCaret(focused);
        Color indicator = MD3Theme.filledFieldIndicator(focused, hoverProgress);

        scope.input(bounds, focused, hoverProgress,
                6.0f, null, 0.0f, new Color(0, 0, 0, 0),
                null, null,
                null, 0.0f, null);
        return new FilledFieldColors(text, caret, indicator);
    }

    public static PanelLayout.Rect measureAssistChipBounds(TextRenderer textRenderer, PanelLayout.Rect rowBounds, String label,
                                                           float textScale, float horizontalPadding, float trailingSlotWidth, float maxWidth) {
        float desiredWidth = textRenderer.getWidth(label, textScale) + horizontalPadding * 2.0f + trailingSlotWidth;
        return alignTrailing(rowBounds, Math.min(maxWidth, desiredWidth), MD3Theme.COMPACT_CHIP_HEIGHT);
    }

    public static void drawAssistChip(RoundRectRenderer roundRectRenderer, TextRenderer textRenderer, PanelLayout.Rect bounds,
                                      String label, float textScale, Color background, Color foreground,
                                      @Nullable String trailingIcon, float trailingIconScale, @Nullable TtfFontLoader trailingIconFont) {
        roundRectRenderer.addRoundRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), MD3Theme.CONTROL_RADIUS, background);
        float textY = bounds.y() + (bounds.height() - textRenderer.getHeight(textScale)) / 2.0f - 1.0f;
        textRenderer.addText(label, bounds.x() + 8.0f, textY, textScale, foreground);
        if (trailingIcon == null || trailingIcon.isEmpty() || trailingIconFont == null) {
            return;
        }
        float iconWidth = textRenderer.getWidth(trailingIcon, trailingIconScale, trailingIconFont);
        float iconY = bounds.y() + (bounds.height() - textRenderer.getHeight(trailingIconScale, trailingIconFont)) / 2.0f - 1.0f;
        textRenderer.addText(trailingIcon, bounds.right() - 8.0f - iconWidth, iconY, trailingIconScale, foreground, trailingIconFont);
    }

    /**
     * 在 DSL 中构建一个 assist chip 语义节点。
     */
    public static void buildAssistChip(PanelUiTree.Scope scope, TextRenderer textRenderer, PanelLayout.Rect bounds,
                                       String label, float textScale, Color background, Color foreground,
                                       @Nullable String trailingIcon, float trailingIconScale, @Nullable TtfFontLoader trailingIconFont) {
        scope.chip(bounds, label, textScale, background, foreground, trailingIcon, trailingIconScale, trailingIconFont);
    }

    public static void drawSegmentedControl(RoundRectRenderer roundRectRenderer, RectRenderer rectRenderer, TextRenderer textRenderer,
                                            PanelLayout.Rect bounds, String leadingLabel, String trailingLabel,
                                            float progress, float hoverProgress) {
        float outerRadius = MD3Theme.CONTROL_RADIUS;
        float shellInset = 1.0f;
        float innerX = bounds.x() + shellInset;
        float innerY = bounds.y() + shellInset;
        float innerWidth = bounds.width() - shellInset * 2.0f;
        float innerHeight = bounds.height() - shellInset * 2.0f;
        float segmentWidth = innerWidth / 2.0f;
        float indicatorInset = 1.5f;
        float indicatorWidth = segmentWidth - indicatorInset * 2.0f;
        float indicatorX = innerX + indicatorInset + segmentWidth * progress;
        float indicatorY = innerY + indicatorInset;
        float indicatorHeight = innerHeight - indicatorInset * 2.0f;
        float indicatorRadius = Math.max(4.0f, outerRadius - 2.0f);
        float labelScale = 0.52f;
        float labelY = innerY + (innerHeight - textRenderer.getHeight(labelScale)) / 2.0f - 1.0f;
        Color inactiveLabel = MD3Theme.segmentedControlInactiveLabel();
        Color activeLabel = MD3Theme.segmentedControlActiveLabel();

        roundRectRenderer.addRoundRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), outerRadius, MD3Theme.OUTLINE_SOFT);
        roundRectRenderer.addRoundRect(innerX, innerY, innerWidth, innerHeight, Math.max(outerRadius - shellInset, 1.0f), MD3Theme.segmentedControlSurface());
        if (hoverProgress > 0.01f) {
            roundRectRenderer.addRoundRect(innerX, innerY, innerWidth, innerHeight, Math.max(outerRadius - shellInset, 1.0f),
                    MD3Theme.stateLayer(MD3Theme.TEXT_PRIMARY, hoverProgress, MD3Theme.isLightTheme() ? 10 : 14));
        }
        rectRenderer.addRect(innerX + segmentWidth - 0.5f, innerY + 3.0f, 1.0f, innerHeight - 6.0f, MD3Theme.OUTLINE_SOFT);
        roundRectRenderer.addRoundRect(indicatorX, indicatorY, indicatorWidth, indicatorHeight, indicatorRadius, MD3Theme.segmentedControlIndicator());

        float leadingWidth = textRenderer.getWidth(leadingLabel, labelScale);
        float trailingWidth = textRenderer.getWidth(trailingLabel, labelScale);
        textRenderer.addText(leadingLabel, innerX + (segmentWidth - leadingWidth) / 2.0f, labelY, labelScale, MD3Theme.lerp(activeLabel, inactiveLabel, progress));
        textRenderer.addText(trailingLabel, innerX + segmentWidth + (segmentWidth - trailingWidth) / 2.0f, labelY, labelScale, MD3Theme.lerp(inactiveLabel, activeLabel, progress));
    }

    /**
     * 在 DSL 中构建一个双段 segmented control 节点。
     */
    public static void buildSegmentedControl(PanelUiTree.Scope scope, TextRenderer textRenderer,
                                             PanelLayout.Rect bounds, String leadingLabel, String trailingLabel,
                                             float progress, float hoverProgress) {
        scope.segmented(bounds, leadingLabel, trailingLabel, progress, hoverProgress);
    }

    public static void drawIconButton(RoundRectRenderer roundRectRenderer, TextRenderer textRenderer, PanelLayout.Rect bounds,
                                      String label, float scale, Color tone, float hoverProgress) {
        roundRectRenderer.addRoundRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), bounds.height() / 2.0f,
                MD3Theme.stateLayer(tone, hoverProgress, 32));
        Color labelColor = MD3Theme.lerp(MD3Theme.TEXT_MUTED, tone, hoverProgress);
        float textWidth = textRenderer.getWidth(label, scale);
        float textHeight = textRenderer.getHeight(scale);
        textRenderer.addText(label,
                bounds.x() + (bounds.width() - textWidth) / 2.0f,
                bounds.y() + (bounds.height() - textHeight) / 2.0f - 1.0f,
                scale,
                labelColor);
    }

    /**
     * 在 DSL 中构建一个圆角图标按钮节点。
     */
    public static void buildIconButton(PanelUiTree.Scope scope, TextRenderer textRenderer, PanelLayout.Rect bounds,
                                       String label, float scale, Color tone, float hoverProgress) {
        scope.iconButton(bounds, label, scale, tone, hoverProgress);
    }

    /**
     * 标准 filled field 的语义色值集合。
     *
     * @param text      文本颜色
     * @param caret     光标颜色
     * @param indicator 底部指示线颜色
     */
    public record FilledFieldColors(Color text, Color caret, Color indicator) {
    }

}

