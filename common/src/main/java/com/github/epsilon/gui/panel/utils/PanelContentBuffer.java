package com.github.epsilon.gui.panel.utils;

import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.renderers.*;
import com.github.epsilon.graphics.text.ttf.TtfFontLoader;
import com.github.epsilon.gui.panel.PanelLayout;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;


/**
 * 面板视口内容缓冲。
 * <p>
 * 该缓冲用于承接需要裁剪输出的子内容，例如滚动列表、弹窗下拉内容等。
 * 编译阶段先写入缓冲，flush 阶段再统一应用 scissor 并输出滚动条。
 */
public class PanelContentBuffer {

    private final RoundRectRenderer roundRectRenderer = RoundRectRenderer.create();
    private final RoundRectOutlineRenderer roundRectOutlineRenderer = RoundRectOutlineRenderer.create();
    private final RectRenderer rectRenderer = RectRenderer.create();
    private final ShadowRenderer shadowRenderer = ShadowRenderer.create();
    private final TriangleRenderer triangleRenderer = TriangleRenderer.create();
    private final TextRenderer textRenderer = TextRenderer.create();
    private final RoundRectRenderer scrollBarRenderer = RoundRectRenderer.create();
    private final TextRenderer marqueeRenderer = TextRenderer.create();
    private final List<MarqueeTextDraw> marqueeDraws = new ArrayList<>();

    private boolean pending;
    private PanelLayout.Rect pendingViewport;
    private int pendingGuiHeight;

    public RoundRectRenderer roundRectRenderer() {
        return roundRectRenderer;
    }

    public RectRenderer rectRenderer() {
        return rectRenderer;
    }

    public RoundRectOutlineRenderer roundRectOutlineRenderer() {
        return roundRectOutlineRenderer;
    }

    public ShadowRenderer shadowRenderer() {
        return shadowRenderer;
    }

    public TriangleRenderer triangleRenderer() {
        return triangleRenderer;
    }

    public TextRenderer textRenderer() {
        return textRenderer;
    }

    /**
     * 清空内容缓冲及滚动条缓冲，并重置待输出标记。
     */
    public void clear() {
        clearContent();
        scrollBarRenderer.clear();
        marqueeRenderer.clear();
        marqueeDraws.clear();
        pending = false;
    }

    /**
     * 注册一个带独立 scissor 的文本绘制（如跑马灯），将在视口内容输出之后执行。
     * <p>
     * 该绘制会与当前视口区域取交集后再应用 scissor，避免越出列表视口。
     */
    public void addMarqueeText(MarqueeTextDraw draw) {
        marqueeDraws.add(draw);
    }

    private void clearContent() {
        shadowRenderer.clear();
        roundRectRenderer.clear();
        roundRectOutlineRenderer.clear();
        rectRenderer.clear();
        triangleRenderer.clear();
        textRenderer.clear();
    }

    /**
     * 为当前内容缓冲登记一个视口输出阶段。
     * <p>
     * 该方法会应用裁剪区域并预先构建滚动条，但不会立即绘制。
     *
     * @param viewport      视口区域
     * @param guiHeight     当前 GUI 高度，用于换算 scissor 坐标
     * @param scroll        当前滚动偏移
     * @param maxScroll     最大滚动偏移
     * @param contentHeight 内容总高度
     */
    public void queueViewport(PanelLayout.Rect viewport, int guiHeight, float scroll, float maxScroll, float contentHeight) {
        PanelScissor.apply(viewport, rectRenderer, roundRectRenderer, roundRectOutlineRenderer, shadowRenderer, triangleRenderer, textRenderer, guiHeight);
        scrollBarRenderer.clear();
        ScrollBarUtils.draw(scrollBarRenderer, viewport, scroll, maxScroll, contentHeight);
        pendingViewport = viewport;
        pendingGuiHeight = guiHeight;
        pending = true;
    }

    /**
     * 输出当前缓冲中的内容，并在输出后恢复 scissor 状态。
     */
    public void flush() {
        if (!pending) {
            return;
        }
        shadowRenderer.draw();
        roundRectRenderer.draw();
        roundRectOutlineRenderer.draw();
        rectRenderer.draw();
        triangleRenderer.draw();
        textRenderer.draw();
        PanelScissor.clear(rectRenderer, roundRectRenderer, roundRectOutlineRenderer, shadowRenderer, triangleRenderer, textRenderer);
        flushMarqueeTexts();
        scrollBarRenderer.draw();
        scrollBarRenderer.clear();
        pending = false;
    }

    private void flushMarqueeTexts() {
        if (marqueeDraws.isEmpty() || pendingViewport == null) {
            marqueeDraws.clear();
            return;
        }
        for (MarqueeTextDraw draw : marqueeDraws) {
            PanelLayout.Rect clip = intersect(draw.clip(), pendingViewport);
            if (clip == null) {
                continue;
            }
            LuminRenderSystem.ScissorRect scissor = LuminRenderSystem.toFramebufferScissor(clip.x(), clip.y(), clip.width(), clip.height());
            int sx = scissor.x();
            int sy = scissor.y();
            int sw = scissor.width();
            int sh = scissor.height();
            if (sw <= 0 || sh <= 0) {
                continue;
            }
            marqueeRenderer.clear();
            if (draw.font() != null) {
                marqueeRenderer.addText(draw.text(), draw.x(), draw.y(), draw.scale(), draw.color(), draw.font());
            } else {
                marqueeRenderer.addText(draw.text(), draw.x(), draw.y(), draw.scale(), draw.color());
            }
            marqueeRenderer.setScissor(sx, sy, sw, sh);
            marqueeRenderer.draw();
            marqueeRenderer.clearScissor();
        }
        marqueeRenderer.clear();
        marqueeDraws.clear();
    }

    private static PanelLayout.Rect intersect(PanelLayout.Rect a, PanelLayout.Rect b) {
        float x = Math.max(a.x(), b.x());
        float y = Math.max(a.y(), b.y());
        float right = Math.min(a.right(), b.right());
        float bottom = Math.min(a.bottom(), b.bottom());
        if (right <= x || bottom <= y) {
            return null;
        }
        return new PanelLayout.Rect(x, y, right - x, bottom - y);
    }

    /**
     * 一个带独立裁剪框的文本绘制描述，用于实现行内跑马灯/水平裁剪文本。
     */
    public record MarqueeTextDraw(String text, float x, float y, float scale, Color color,
                                  TtfFontLoader font, PanelLayout.Rect clip) {
    }

    /**
     * 先输出视口内容，再清空内容缓冲。
     */
    public void flushAndClear() {
        flush();
        clearContent();
    }

}
