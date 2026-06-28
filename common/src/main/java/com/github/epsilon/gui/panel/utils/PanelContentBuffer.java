package com.github.epsilon.gui.panel.utils;

import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.text.ttf.TtfFontLoader;
import com.github.epsilon.gui.dsl.PanelRenderBatch;
import com.github.epsilon.gui.panel.PanelLayout;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 面板视口内容缓冲。
 * <p>
 * 内容先写入一个 scheduler-backed 批次，flush 时再统一附加 scissor 和滚动条。
 * 这样列表、设置页、popup 视口不再各自维护一组 GPU renderer，能和主 GUI 一起进入 Render2DScheduler 合批。
 */
public class PanelContentBuffer {

    private final PanelRenderBatch batch = new PanelRenderBatch();
    private final PanelRenderBatch scrollBarBatch = batch.view(8);
    private final PanelRenderBatch marqueeBatch = batch.view(9);
    private final List<MarqueeTextDraw> marqueeDraws = new ArrayList<>();

    private boolean pending;
    private PanelLayout.Rect pendingViewport;
    private int pendingGuiHeight;

    public PanelRenderBatch.RoundRectFacade roundRectRenderer() {
        return batch.roundRectRenderer();
    }

    public PanelRenderBatch.RectFacade rectRenderer() {
        return batch.rectRenderer();
    }

    public PanelRenderBatch.RoundRectOutlineFacade roundRectOutlineRenderer() {
        return batch.roundRectOutlineRenderer();
    }

    public PanelRenderBatch.ShadowFacade shadowRenderer() {
        return batch.shadowRenderer();
    }

    public PanelRenderBatch.TriangleFacade triangleRenderer() {
        return batch.triangleRenderer();
    }

    public PanelRenderBatch.TextFacade textRenderer() {
        return batch.textRenderer();
    }

    public void clear() {
        clearContent();
        marqueeDraws.clear();
        pending = false;
        pendingViewport = null;
        pendingGuiHeight = 0;
    }

    public void addMarqueeText(MarqueeTextDraw draw) {
        marqueeDraws.add(draw);
    }

    private void clearContent() {
        batch.clear();
    }

    /**
     * 记录本帧视口信息，并把已有内容层绑定到对应 framebuffer scissor。
     */
    public void beginViewport(PanelLayout.Rect viewport) {
        LuminRenderSystem.ScissorRect scissor = LuminRenderSystem.toFramebufferScissor(viewport.x(), viewport.y(), viewport.width(), viewport.height());
        batch.setLayerScissor(0, scissor.x(), scissor.y(), scissor.width(), scissor.height());
    }

    /**
     * 记录本帧视口信息，并为滚动条与跑马灯文本准备本帧附加层。
     */
    public void queueViewport(PanelLayout.Rect viewport, int guiHeight, float scroll, float maxScroll, float contentHeight) {
        beginViewport(viewport);
        scrollBarBatch.clear();
        ScrollBarUtils.draw(scrollBarBatch.roundRectRenderer(), viewport, scroll, maxScroll, contentHeight);
        pendingViewport = viewport;
        pendingGuiHeight = guiHeight;
        pending = true;
    }

    public void flush() {
        if (!pending) {
            return;
        }
        flushMarqueeTexts();
        batch.flush();
        scrollBarBatch.clear();
        marqueeBatch.clear();
        pending = false;
    }

    private void flushMarqueeTexts() {
        marqueeBatch.clear();
        if (marqueeDraws.isEmpty() || pendingViewport == null) {
            marqueeDraws.clear();
            return;
        }
        for (MarqueeTextDraw draw : marqueeDraws) {
            // 跑马灯文本有自己的窄裁剪区，需要与 viewport 求交后单独写入文本层。
            PanelLayout.Rect clip = intersect(draw.clip(), pendingViewport);
            if (clip == null) {
                continue;
            }
            LuminRenderSystem.ScissorRect scissor = LuminRenderSystem.toFramebufferScissor(clip.x(), clip.y(), clip.width(), clip.height());
            if (scissor.width() <= 0 || scissor.height() <= 0) {
                continue;
            }
            marqueeBatch.setLayerScissor(0, scissor.x(), scissor.y(), scissor.width(), scissor.height());
            if (draw.font() != null) {
                marqueeBatch.textRenderer().addText(draw.text(), draw.x(), draw.y(), draw.scale(), draw.color(), draw.font());
            } else {
                marqueeBatch.textRenderer().addText(draw.text(), draw.x(), draw.y(), draw.scale(), draw.color());
            }
        }
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

    public record MarqueeTextDraw(String text, float x, float y, float scale, Color color,
                                  TtfFontLoader font, PanelLayout.Rect clip) {
    }

    public void flushAndClear() {
        flush();
        clearContent();
    }

}
