package com.github.epsilon.gui.panel.component;

import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.graphics.text.ttf.TtfFontLoader;
import com.github.epsilon.gui.dsl.PanelUiTree;
import com.github.epsilon.gui.panel.MD3Theme;
import com.github.epsilon.gui.panel.PanelLayout;
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

    private PanelElements() {
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

    public static void buildSwitch(PanelUiTree.Scope scope, PanelLayout.Rect rect, float toggleProgress, float hoverProgress) {
        scope.toggle(rect, toggleProgress, hoverProgress);
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

    /**
     * 在 DSL 中构建一个 assist chip 语义节点。
     */
    public static void buildAssistChip(PanelUiTree.Scope scope, TextRenderer textRenderer, PanelLayout.Rect bounds,
                                       String label, float textScale, Color background, Color foreground,
                                       @Nullable String trailingIcon, float trailingIconScale, @Nullable TtfFontLoader trailingIconFont) {
        scope.chip(bounds, label, textScale, background, foreground, trailingIcon, trailingIconScale, trailingIconFont);
    }

    /**
     * 在 DSL 中构建一个双段 segmented control 节点。
     */
    public static void buildSegmentedControl(PanelUiTree.Scope scope, TextRenderer textRenderer,
                                             PanelLayout.Rect bounds, String leadingLabel, String trailingLabel,
                                             float progress, float hoverProgress) {
        scope.segmented(bounds, leadingLabel, trailingLabel, progress, hoverProgress);
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

