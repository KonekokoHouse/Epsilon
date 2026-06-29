package com.github.epsilon.gui.dsl;

import com.github.epsilon.graphics.text.ttf.TtfFontLoader;
import com.github.epsilon.gui.panel.MD3Theme;
import com.github.epsilon.gui.panel.PanelLayout;
import com.github.epsilon.gui.panel.utils.PanelContentBuffer;

import java.awt.*;
import java.util.List;

/**
 * 将 {@link PanelUiTree} 编译为具体 renderer 调用的编译器。
 * <p>
 * 该类只负责把声明式节点翻译进对应批次，不直接负责真正的 draw/flush 时机。
 */
public class PanelUiCompiler {

    private PanelUiCompiler() {
    }

    /**
     * 将 UI 树写入支持 layer 的批次。
     * <p>
     * layer 在该入口统一生效，GUI 不再直接写入具体 renderer。
     */
    static void renderIntoLayeredBatch(PanelUiTree tree, PanelRenderBatch batch, int baseLayer) {
        renderNodesIntoLayeredBatch(tree.nodes(), batch, baseLayer);
    }

    private static void renderNodesIntoLayeredBatch(List<PanelUiTree.UiNode> nodes, PanelRenderBatch batch, int layer) {
        PanelRenderBatch.LayerRenderers renderers = batch.layer(layer);
        RenderTarget target = RenderTarget.forBatchLayer(renderers);
        for (PanelUiTree.UiNode node : nodes) {
            if (node instanceof PanelUiTree.LayerNode(int childLayer, List<PanelUiTree.UiNode> children)) {
                // 子树 layer 是相对偏移，允许弹窗、下拉层在调用点整体抬高。
                renderNodesIntoLayeredBatch(children, batch, layer + childLayer);
                continue;
            }
            if (node instanceof PanelUiTree.LayeredNode(int childLayer, PanelUiTree.UiNode child)) {
                // 单节点 layer 直接切换目标 renderer，避免为高频图元创建临时子树。
                PanelRenderBatch.LayerRenderers childRenderers = batch.layer(layer + childLayer);
                RenderTarget childTarget = RenderTarget.forBatchLayer(childRenderers);
                renderNode(child, childTarget);
                continue;
            }
            renderNode(node, target);
        }
    }

    private static void renderNode(PanelUiTree.UiNode node, RenderTarget target) {
        if (node instanceof PanelUiTree.ShadowNode(
                float x2, float y2, float width1, float height1, float topLeft, float topRight, float bottomRight,
                float bottomLeft, float blurRadius, java.awt.Color color2
        )) {
            if (target.shadowRenderer() != null) {
                target.shadowRenderer().addShadow(
                        x2, y2, width1, height1,
                        topLeft, topRight, bottomRight, bottomLeft,
                        blurRadius, color2
                );
            }
            return;
        }
        if (node instanceof PanelUiTree.RoundRectNode(
                float x1, float y1, float width, float height, float radiusTopLeft, float radiusTopRight,
                float radiusBottomRight, float radiusBottomLeft, java.awt.Color color1
        )) {
            target.roundRectRenderer().addRoundRect(
                    x1, y1, width, height,
                    radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft,
                    color1
            );
            return;
        }
        if (node instanceof PanelUiTree.RoundRectGradientNode(
                float x1, float y1, float width, float height, float radiusTopLeft, float radiusTopRight,
                float radiusBottomRight, float radiusBottomLeft, Color topLeft, Color bottomLeft,
                Color bottomRight, Color topRight
        )) {
            target.roundRectRenderer().addRoundRectGradient(
                    x1, y1, width, height,
                    radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft,
                    topLeft, bottomLeft, bottomRight, topRight
            );
            return;
        }
        if (node instanceof PanelUiTree.RectNode(
                float x1, float y1, float width, float height, java.awt.Color color1
        )) {
            target.rectRenderer().addRect(x1, y1, width, height, color1);
            return;
        }
        if (node instanceof PanelUiTree.RectGradientNode(
                float x1, float y1, float width, float height, Color topLeft, Color bottomLeft,
                Color bottomRight, Color topRight
        )) {
            target.rectRenderer().addRectGradient(x1, y1, width, height, topLeft, bottomLeft, bottomRight, topRight);
            return;
        }
        if (node instanceof PanelUiTree.RectOutlineNode(
                float x1, float y1, float width, float height, float outlineWidth, Color color
        )) {
            target.rectRenderer().addOutline(x1, y1, width, height, outlineWidth, color);
            return;
        }
        if (node instanceof PanelUiTree.OutlineNode(
                float x1, float y1, float width, float height, float radiusTopLeft, float radiusTopRight,
                float radiusBottomRight, float radiusBottomLeft, float outlineWidth, Color color
        )) {
            if (target.roundRectOutlineRenderer() != null) {
                target.roundRectOutlineRenderer().addOutline(
                        x1, y1, width, height,
                        radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft,
                        outlineWidth, color
                );
            }
            return;
        }
        if (node instanceof PanelUiTree.TextNode(
                String text, float x, float y, float scale, java.awt.Color color,
                TtfFontLoader fontLoader
        )) {
            if (fontLoader != null) {
                target.textRenderer().addText(text, x, y, scale, color, fontLoader);
            } else {
                target.textRenderer().addText(text, x, y, scale, color);
            }
            return;
        }
        if (node instanceof PanelUiTree.MarqueeTextNode(
                String text, float x, float y, float scale, java.awt.Color color,
                TtfFontLoader fontLoader, PanelLayout.Rect clip
        )) {
            if (target.buffer() != null) {
                target.buffer().addMarqueeText(new PanelContentBuffer.MarqueeTextDraw(text, x, y, scale, color, fontLoader, clip));
            } else if (fontLoader != null) {
                target.textRenderer().addText(text, x, y, scale, color, fontLoader);
            } else {
                target.textRenderer().addText(text, x, y, scale, color);
            }
            return;
        }
        if (node instanceof PanelUiTree.TextureNode(
                var texture, float x, float y, float width, float height,
                float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft,
                float u0, float v0, float u1, float v1, Color color
        )) {
            if (target.textureRenderer() != null) {
                target.textureRenderer().addRoundedTexture(texture, x, y, width, height,
                        radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft,
                        u0, v0, u1, v1, color);
            }
            return;
        }
        if (node instanceof PanelUiTree.ButtonNode(
                float x, float y, float width, float height, float radius, Color background,
                String label, float labelScale, Color labelColor
        )) {
            renderButton(target, x, y, width, height, radius, background, label, labelScale, labelColor);
            return;
        }
        if (node instanceof PanelUiTree.SwitchNode(
                PanelLayout.Rect bounds, float toggleProgress, float hoverProgress
        )) {
            renderSwitch(target, bounds, toggleProgress, hoverProgress);
            return;
        }
        if (node instanceof PanelUiTree.FilledFieldNode(
                PanelLayout.Rect bounds, boolean focused, float hoverProgress
        )) {
            renderFilledField(target, bounds, focused, hoverProgress);
            return;
        }
        if (node instanceof PanelUiTree.InputNode(PanelUiTree.InputElement element)) {
            renderInput(target, element);
            return;
        }
        if (node instanceof PanelUiTree.AssistChipNode(
                PanelLayout.Rect bounds, String label, float textScale, Color background, Color foreground,
                String trailingIcon, float trailingIconScale, TtfFontLoader trailingIconFont
        )) {
            target.roundRectRenderer().addRoundRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), MD3Theme.CONTROL_RADIUS, background);
            float textY = bounds.y() + (bounds.height() - target.textRenderer().getHeight(textScale)) / 2.0f;
            target.textRenderer().addText(label, bounds.x() + 8.0f, textY, textScale, foreground);
            if (trailingIcon != null && !trailingIcon.isEmpty() && trailingIconFont != null) {
                float iconWidth = target.textRenderer().getWidth(trailingIcon, trailingIconScale, trailingIconFont);
                float iconY = bounds.y() + (bounds.height() - target.textRenderer().getHeight(trailingIconScale, trailingIconFont)) / 2.0f;
                target.textRenderer().addText(trailingIcon, bounds.right() - 8.0f - iconWidth, iconY, trailingIconScale, foreground, trailingIconFont);
            }
            return;
        }
        if (node instanceof PanelUiTree.SegmentedControlNode(
                PanelLayout.Rect bounds, String leadingLabel, String trailingLabel, float progress,
                float hoverProgress
        )) {
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
            float labelY = innerY + (innerHeight - target.textRenderer().getHeight(labelScale)) / 2.0f;
            Color inactiveLabel = MD3Theme.segmentedControlInactiveLabel();
            Color activeLabel = MD3Theme.segmentedControlActiveLabel();

            target.roundRectRenderer().addRoundRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), outerRadius, MD3Theme.OUTLINE_SOFT);
            target.roundRectRenderer().addRoundRect(innerX, innerY, innerWidth, innerHeight, Math.max(outerRadius - shellInset, 1.0f), MD3Theme.segmentedControlSurface());
            if (hoverProgress > 0.01f) {
                target.roundRectRenderer().addRoundRect(innerX, innerY, innerWidth, innerHeight, Math.max(outerRadius - shellInset, 1.0f),
                        MD3Theme.stateLayer(MD3Theme.TEXT_PRIMARY, hoverProgress, MD3Theme.isLightTheme() ? 10 : 14));
            }
            target.rectRenderer().addRect(innerX + segmentWidth - 0.5f, innerY + 3.0f, 1.0f, innerHeight - 6.0f, MD3Theme.OUTLINE_SOFT);
            target.roundRectRenderer().addRoundRect(indicatorX, indicatorY, indicatorWidth, indicatorHeight, indicatorRadius, MD3Theme.segmentedControlIndicator());
            float leadingWidth = target.textRenderer().getWidth(leadingLabel, labelScale);
            float trailingWidth = target.textRenderer().getWidth(trailingLabel, labelScale);
            target.textRenderer().addText(leadingLabel, innerX + (segmentWidth - leadingWidth) / 2.0f, labelY, labelScale, MD3Theme.lerp(activeLabel, inactiveLabel, progress));
            target.textRenderer().addText(trailingLabel, innerX + segmentWidth + (segmentWidth - trailingWidth) / 2.0f, labelY, labelScale, MD3Theme.lerp(inactiveLabel, activeLabel, progress));
            return;
        }
        if (node instanceof PanelUiTree.IconButtonNode(
                PanelLayout.Rect bounds, String label, float scale, Color tone, float hoverProgress
        )) {
            target.roundRectRenderer().addRoundRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), bounds.height() / 2.0f,
                    MD3Theme.stateLayer(tone, hoverProgress, 32));
            Color labelColor = MD3Theme.lerp(MD3Theme.TEXT_MUTED, tone, hoverProgress);
            float labelWidth = target.textRenderer().getWidth(label, scale);
            float labelHeight = target.textRenderer().getHeight(scale);
            target.textRenderer().addText(label,
                    bounds.x() + (bounds.width() - labelWidth) / 2.0f,
                    bounds.y() + (bounds.height() - labelHeight) / 2.0f,
                    scale,
                    labelColor);
            return;
        }
        if (node instanceof PanelUiTree.PopupCardNode(
                PanelLayout.Rect bounds, float radius, float blurRadius, Color shadowColor, Color surfaceColor
        )) {
            renderPopupCard(target, bounds, radius, blurRadius, shadowColor, surfaceColor);
            return;
        }
        if (node instanceof PanelUiTree.SliderNode(
                PanelLayout.Rect bounds, float progress, float trackRadius, Color trackColor, float activeEndInset,
                float activeMinWidth, Color activeColor, float handleWidth, float handleHeight, float handleRadius,
                Color handleColor
        )) {
            renderSlider(target, bounds, progress, trackRadius, trackColor, activeEndInset, activeMinWidth, activeColor, handleWidth, handleHeight, handleRadius, handleColor);
            return;
        }
        if (node instanceof PanelUiTree.TriangleNode(
                float centerX, float centerY, float size, float progress, java.awt.Color color
        )) {
            if (target.triangleRenderer() != null) {
                target.triangleRenderer().addChevronTriangle(centerX, centerY, size, progress, color);
            }
            return;
        }
        if (node instanceof PanelUiTree.ViewportNode(
                PanelContentBuffer buffer, PanelLayout.Rect viewport, int guiHeight, float scroll, float maxScroll,
                float contentHeight, List<PanelUiTree.UiNode> children
        )) {
            // viewport 子树先进入私有内容缓冲；真正绘制时由 buffer 按视口 scissor 输出。
            buffer.beginViewport(viewport);
            renderNodesIntoTarget(children, RenderTarget.forContentBuffer(buffer));
            buffer.queueViewport(viewport, guiHeight, scroll, maxScroll, contentHeight);
        }
    }

    private static void renderNodesIntoTarget(List<PanelUiTree.UiNode> nodes, RenderTarget target) {
        for (PanelUiTree.UiNode node : nodes) {
            if (node instanceof PanelUiTree.LayerNode(int ignoredLayer, List<PanelUiTree.UiNode> children)) {
                // viewport 缓冲拥有自己的本地层级；内容子树中的相对 layer 只表达结构，不逃逸到外层 scene。
                renderNodesIntoTarget(children, target);
                continue;
            }
            if (node instanceof PanelUiTree.LayeredNode(int ignoredLayer, PanelUiTree.UiNode child)) {
                renderNode(child, target);
                continue;
            }
            renderNode(node, target);
        }
    }

    private static void renderButton(RenderTarget target, float x, float y, float width, float height, float radius, Color background, String label, float labelScale, Color labelColor) {
        target.roundRectRenderer().addRoundRect(x, y, width, height, radius, background);
        float labelWidth = target.textRenderer().getWidth(label, labelScale);
        float labelHeight = target.textRenderer().getHeight(labelScale);
        target.textRenderer().addText(label,
                x + (width - labelWidth) / 2.0f,
                y + (height - labelHeight) / 2.0f,
                labelScale,
                labelColor);
    }

    private static void renderPopupCard(RenderTarget target, PanelLayout.Rect bounds, float radius, float blurRadius, Color shadowColor, Color surfaceColor) {
        if (target.shadowRenderer() != null) {
            target.shadowRenderer().addShadow(bounds.x(), bounds.y(), bounds.width(), bounds.height(), radius, blurRadius, shadowColor);
        }
        target.roundRectRenderer().addRoundRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), radius, surfaceColor);
    }

    private static void renderSlider(RenderTarget target, PanelLayout.Rect bounds, float progress, float trackRadius, Color trackColor, float activeEndInset, float activeMinWidth, Color activeColor, float handleWidth, float handleHeight, float handleRadius, Color handleColor) {
        float clampedProgress = Math.clamp(progress, 0.0f, 1.0f);
        float safeHandleWidth = Math.max(1.0f, handleWidth);
        float handleX = bounds.x() + bounds.width() * clampedProgress - safeHandleWidth / 2.0f;
        float handleY = bounds.centerY() - handleHeight / 2.0f;
        float activeWidth = Math.max(activeMinWidth, bounds.width() * clampedProgress - activeEndInset);

        target.roundRectRenderer().addRoundRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), trackRadius, trackColor);
        if (activeWidth > 0.0f) {
            float clampedActiveWidth = Math.min(bounds.width(), activeWidth);
            target.roundRectRenderer().addRoundRect(bounds.x(), bounds.y(), clampedActiveWidth, bounds.height(), trackRadius, 1.0f, 1.0f, trackRadius, activeColor);
        }
        target.roundRectRenderer().addRoundRect(handleX, handleY, safeHandleWidth, handleHeight, handleRadius, handleColor);
    }

    private static void renderSwitch(RenderTarget target, PanelLayout.Rect bounds, float toggleProgress, float hoverProgress) {
        Color track = MD3Theme.switchTrack(toggleProgress);
        Color knob = MD3Theme.switchKnob(toggleProgress);
        Color outline = MD3Theme.switchTrackOutline(toggleProgress, hoverProgress);
        float clampedToggle = Math.clamp(toggleProgress, 0.0f, 1.0f);
        float knobSize = MD3Theme.SWITCH_HANDLE_SIZE_OFF
                + (MD3Theme.SWITCH_HANDLE_SIZE_ON - MD3Theme.SWITCH_HANDLE_SIZE_OFF) * clampedToggle;
        float stretchFactor = 4.0f * clampedToggle * (1.0f - clampedToggle);
        float knobWidth = knobSize + 3.5f * stretchFactor;
        float inset = MD3Theme.SWITCH_HANDLE_INSET_OFF
                + (MD3Theme.SWITCH_HANDLE_INSET_ON - MD3Theme.SWITCH_HANDLE_INSET_OFF) * clampedToggle;
        float knobMinX = bounds.x() + inset + knobWidth / 2.0f;
        float knobMaxX = bounds.right() - inset - knobWidth / 2.0f;
        float knobCenterX = knobMinX + (knobMaxX - knobMinX) * toggleProgress;
        float knobCenterY = bounds.centerY();

        target.roundRectRenderer().addRoundRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), bounds.height() / 2.0f, track);
        if (outline.getAlpha() > 0 && target.roundRectOutlineRenderer() != null) {
            target.roundRectOutlineRenderer().addOutline(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                    bounds.height() / 2.0f, MD3Theme.switchTrackOutlineWidth(toggleProgress), outline);
        }
        if (hoverProgress > 0.02f) {
            float haloSize = MD3Theme.SWITCH_STATE_LAYER_SIZE;
            float haloX = knobCenterX - haloSize / 2.0f;
            float haloY = knobCenterY - haloSize / 2.0f;
            target.roundRectRenderer().addRoundRect(haloX, haloY, haloSize, haloSize, haloSize / 2.0f,
                    MD3Theme.stateLayer(MD3Theme.TEXT_PRIMARY, hoverProgress, 18));
        }
        target.roundRectRenderer().addRoundRect(knobCenterX - knobWidth / 2.0f, knobCenterY - knobSize / 2.0f,
                knobWidth, knobSize, knobSize / 2.0f, knob);
    }

    private static void renderFilledField(RenderTarget target, PanelLayout.Rect bounds, boolean focused, float hoverProgress) {
        target.roundRectRenderer().addRoundRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), MD3Theme.CONTROL_RADIUS, MD3Theme.filledFieldSurface(focused, hoverProgress));
    }

    private static void renderInput(RenderTarget target, PanelUiTree.InputElement element) {
        PanelLayout.Rect bounds = element.bounds();
        renderFilledField(target, bounds, element.focused(), element.hoverProgress());

        if (element.focusRingProgress() > 0.01f && element.focusRingInset() > 0.0f) {
            float inset = element.focusRingInset() * element.focusRingProgress();
            target.roundRectRenderer().addRoundRect(
                    bounds.x() - inset,
                    bounds.y() - inset,
                    bounds.width() + inset * 2.0f,
                    bounds.height() + inset * 2.0f,
                    MD3Theme.CONTROL_RADIUS + inset,
                    MD3Theme.withAlpha(element.focusRingColor(), (int) (48 * element.focusRingProgress()))
            );
        }

        String text = element.text();
        if (text != null && !text.isEmpty()) {
            float textX = bounds.x() + element.textInset();
            float textY = bounds.y() + (bounds.height() - target.textRenderer().getHeight(element.textScale())) / 2.0f;

            PanelUiTree.SelectionRange selection = element.selection();
            if (selection != null && element.selectionColor() != null) {
                int start = Math.clamp(selection.start(), 0, text.length());
                int end = Math.clamp(selection.end(), start, text.length());
                if (end > start) {
                    float selectionX = textX + target.textRenderer().getWidth(text.substring(0, start), element.textScale());
                    float selectionWidth = target.textRenderer().getWidth(text.substring(start, end), element.textScale());
                    target.rectRenderer().addRect(selectionX, bounds.y() + 3.0f, selectionWidth, bounds.height() - 6.0f, element.selectionColor());
                }
            }

            target.textRenderer().addText(text, textX, textY, element.textScale(), element.textColor());

            if (element.caretIndex() != null && element.caretColor() != null) {
                int caretIndex = Math.clamp(element.caretIndex(), 0, text.length());
                float caretX = textX + target.textRenderer().getWidth(text.substring(0, caretIndex), element.textScale());
                target.rectRenderer().addRect(caretX, bounds.y() + 4.0f, 1.0f, bounds.height() - 8.0f, element.caretColor());
            }
        }

        if (element.trailingHint() != null && !element.trailingHint().isBlank() && element.trailingHintColor() != null) {
            float hintWidth = target.textRenderer().getWidth(element.trailingHint(), element.trailingHintScale());
            float hintY = bounds.y() + (bounds.height() - target.textRenderer().getHeight(element.trailingHintScale())) / 2.0f;
            float hintX = bounds.right() - element.textInset() - hintWidth;
            target.textRenderer().addText(element.trailingHint(), hintX, hintY, element.trailingHintScale(), element.trailingHintColor());
        }
    }

    private record RenderTarget(ShadowSink shadowRenderer, RoundRectSink roundRectRenderer,
                                RoundRectOutlineSink roundRectOutlineRenderer, RectSink rectRenderer,
                                TriangleSink triangleRenderer, TextureSink textureRenderer, TextSink textRenderer,
                                PanelContentBuffer buffer) {
        private static RenderTarget forBatchLayer(PanelRenderBatch.LayerRenderers renderers) {
            return new RenderTarget(
                    new BatchShadowSink(renderers.shadowRenderer()),
                    new BatchRoundRectSink(renderers.roundRectRenderer()),
                    new BatchRoundRectOutlineSink(renderers.roundRectOutlineRenderer()),
                    new BatchRectSink(renderers.rectRenderer()),
                    new BatchTriangleSink(renderers.triangleRenderer()),
                    new BatchTextureSink(renderers.textureRenderer()),
                    new BatchTextSink(renderers.textRenderer()),
                    null
            );
        }

        private static RenderTarget forContentBuffer(PanelContentBuffer buffer) {
            return new RenderTarget(
                    new BatchShadowSink(buffer.shadowRenderer()),
                    new BatchRoundRectSink(buffer.roundRectRenderer()),
                    new BatchRoundRectOutlineSink(buffer.roundRectOutlineRenderer()),
                    new BatchRectSink(buffer.rectRenderer()),
                    new BatchTriangleSink(buffer.triangleRenderer()),
                    null,
                    new BatchTextSink(buffer.textRenderer()),
                    buffer
            );
        }
    }

    private interface ShadowSink {
        void addShadow(float x, float y, float width, float height,
                       float topLeft, float topRight, float bottomRight, float bottomLeft,
                       float blurRadius, Color color);

        default void addShadow(float x, float y, float width, float height, float radius, float blurRadius, Color color) {
            addShadow(x, y, width, height, radius, radius, radius, radius, blurRadius, color);
        }
    }

    private interface RoundRectSink {
        void addRoundRect(float x, float y, float width, float height,
                          float topLeft, float topRight, float bottomRight, float bottomLeft,
                          Color color);

        void addRoundRectGradient(float x, float y, float width, float height,
                                  float topLeftRadius, float topRightRadius, float bottomRightRadius, float bottomLeftRadius,
                                  Color topLeft, Color bottomLeft, Color bottomRight, Color topRight);

        default void addRoundRect(float x, float y, float width, float height, float radius, Color color) {
            addRoundRect(x, y, width, height, radius, radius, radius, radius, color);
        }
    }

    private interface RoundRectOutlineSink {
        void addOutline(float x, float y, float width, float height,
                        float topLeft, float topRight, float bottomRight, float bottomLeft,
                        float outlineWidth, Color color);

        default void addOutline(float x, float y, float width, float height, float radius, float outlineWidth, Color color) {
            addOutline(x, y, width, height, radius, radius, radius, radius, outlineWidth, color);
        }
    }

    private interface RectSink {
        void addRect(float x, float y, float width, float height, Color color);

        void addRectGradient(float x, float y, float width, float height,
                             Color topLeft, Color bottomLeft, Color bottomRight, Color topRight);

        void addOutline(float x, float y, float width, float height, float outlineWidth, Color color);
    }

    private interface TriangleSink {
        void addChevronTriangle(float centerX, float centerY, float size, float progress, Color color);
    }

    private interface TextureSink {
        void addRoundedTexture(com.github.epsilon.graphics.schedulers.Render2DTexture texture,
                               float x, float y, float width, float height,
                               float topLeft, float topRight, float bottomRight, float bottomLeft,
                               float u0, float v0, float u1, float v1, Color color);
    }

    private interface TextSink {
        void addText(String text, float x, float y, float scale, Color color);

        void addText(String text, float x, float y, float scale, Color color, TtfFontLoader fontLoader);

        float getHeight(float scale);

        float getHeight(float scale, TtfFontLoader fontLoader);

        float getWidth(String text, float scale);

        float getWidth(String text, float scale, TtfFontLoader fontLoader);
    }

    private record BatchShadowSink(PanelRenderBatch.ShadowFacade renderer) implements ShadowSink {
        @Override
        public void addShadow(float x, float y, float width, float height, float topLeft, float topRight,
                              float bottomRight, float bottomLeft, float blurRadius, Color color) {
            renderer.addShadow(x, y, width, height, topLeft, topRight, bottomRight, bottomLeft, blurRadius, color);
        }
    }

    private record BatchRoundRectSink(PanelRenderBatch.RoundRectFacade renderer) implements RoundRectSink {
        @Override
        public void addRoundRect(float x, float y, float width, float height, float topLeft, float topRight,
                                 float bottomRight, float bottomLeft, Color color) {
            renderer.addRoundRect(x, y, width, height, topLeft, topRight, bottomRight, bottomLeft, color);
        }

        @Override
        public void addRoundRectGradient(float x, float y, float width, float height,
                                         float topLeftRadius, float topRightRadius, float bottomRightRadius, float bottomLeftRadius,
                                         Color topLeft, Color bottomLeft, Color bottomRight, Color topRight) {
            renderer.addRoundRectGradient(x, y, width, height, topLeftRadius, topRightRadius,
                    bottomRightRadius, bottomLeftRadius, topLeft, bottomLeft, bottomRight, topRight);
        }
    }

    private record BatchRoundRectOutlineSink(PanelRenderBatch.RoundRectOutlineFacade renderer) implements RoundRectOutlineSink {
        @Override
        public void addOutline(float x, float y, float width, float height, float topLeft, float topRight,
                               float bottomRight, float bottomLeft, float outlineWidth, Color color) {
            renderer.addOutline(x, y, width, height, topLeft, topRight, bottomRight, bottomLeft, outlineWidth, color);
        }
    }

    private record BatchRectSink(PanelRenderBatch.RectFacade renderer) implements RectSink {
        @Override
        public void addRect(float x, float y, float width, float height, Color color) {
            renderer.addRect(x, y, width, height, color);
        }

        @Override
        public void addRectGradient(float x, float y, float width, float height,
                                    Color topLeft, Color bottomLeft, Color bottomRight, Color topRight) {
            renderer.addRectGradient(x, y, width, height, topLeft, bottomLeft, bottomRight, topRight);
        }

        @Override
        public void addOutline(float x, float y, float width, float height, float outlineWidth, Color color) {
            renderer.addOutline(x, y, width, height, outlineWidth, color);
        }
    }

    private record BatchTriangleSink(PanelRenderBatch.TriangleFacade renderer) implements TriangleSink {
        @Override
        public void addChevronTriangle(float centerX, float centerY, float size, float progress, Color color) {
            renderer.addChevronTriangle(centerX, centerY, size, progress, color);
        }
    }

    private record BatchTextureSink(PanelRenderBatch.TextureFacade renderer) implements TextureSink {
        @Override
        public void addRoundedTexture(com.github.epsilon.graphics.schedulers.Render2DTexture texture,
                                      float x, float y, float width, float height,
                                      float topLeft, float topRight, float bottomRight, float bottomLeft,
                                      float u0, float v0, float u1, float v1, Color color) {
            renderer.addRoundedTexture(texture, x, y, width, height, topLeft, topRight, bottomRight, bottomLeft, u0, v0, u1, v1, color);
        }
    }

    private record BatchTextSink(PanelRenderBatch.TextFacade renderer) implements TextSink {
        @Override
        public void addText(String text, float x, float y, float scale, Color color) {
            renderer.addText(text, x, y, scale, color);
        }

        @Override
        public void addText(String text, float x, float y, float scale, Color color, TtfFontLoader fontLoader) {
            renderer.addText(text, x, y, scale, color, fontLoader);
        }

        @Override
        public float getHeight(float scale) {
            return renderer.getHeight(scale);
        }

        @Override
        public float getHeight(float scale, TtfFontLoader fontLoader) {
            return renderer.getHeight(scale, fontLoader);
        }

        @Override
        public float getWidth(String text, float scale) {
            return renderer.getWidth(text, scale);
        }

        @Override
        public float getWidth(String text, float scale, TtfFontLoader fontLoader) {
            return renderer.getWidth(text, scale, fontLoader);
        }
    }

}
