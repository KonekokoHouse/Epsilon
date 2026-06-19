package com.github.epsilon.gui.dropdown;

import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.graphics.text.ttf.TtfFontLoader;
import com.github.epsilon.gui.dsl.PanelRenderBatch;
import com.github.epsilon.gui.dsl.PanelUiTree;

import java.awt.*;

/**
 * Dropdown GUI 的 DSL 渲染门面。
 * <p>
 * 旧组件仍按 renderer.rect()/text() 的形式提交绘制，但实际只写入 UI 节点树。
 * 每个 pass 会映射为独立 layer，帧尾统一合批输出。
 */
public class DropdownRenderer {

    private static final int MAX_PASSES = 96;

    private final PanelRenderBatch batch = new PanelRenderBatch();
    // 整帧复用一个 Scope 收集 DSL 节点，endFrame 时再一次性编译进 batch。
    private final PanelUiTree.Scope scope = new PanelUiTree.Scope();
    // facade 不再直接持有文本绘制数据，但旧组件仍依赖 renderer 的测量 API。
    private final TextRenderer measureTextRenderer = TextRenderer.create();
    private final ShadowFacade shadowFacade = new ShadowFacade();
    private final RoundRectFacade roundRectFacade = new RoundRectFacade();
    private final OutlineFacade outlineFacade = new OutlineFacade();
    private final RectFacade rectFacade = new RectFacade();
    private final TriangleFacade triangleFacade = new TriangleFacade();
    private final TextFacade textFacade = new TextFacade();

    private int passIndex = -1;
    private int passCount;
    private boolean frameOpen;

    public ShadowFacade shadow() {
        ensurePass();
        return shadowFacade;
    }

    public RoundRectFacade roundRect() {
        ensurePass();
        return roundRectFacade;
    }

    public OutlineFacade outline() {
        ensurePass();
        return outlineFacade;
    }

    public RectFacade rect() {
        ensurePass();
        return rectFacade;
    }

    public TriangleFacade triangle() {
        ensurePass();
        return triangleFacade;
    }

    public TextFacade text() {
        ensurePass();
        return textFacade;
    }

    public void setScissor(float guiX, float guiY, float guiW, float guiH, int guiHeight) {
        ensurePass();
        LuminRenderSystem.ScissorRect scissor = LuminRenderSystem.toFramebufferScissor(guiX, guiY, guiW, guiH);
        Pass pass = currentPass();
        // 裁剪状态记录在当前 pass，帧尾再绑定到对应 layer 的 renderer 组。
        pass.scissorEnabled = true;
        pass.scissorX = scissor.x();
        pass.scissorY = scissor.y();
        pass.scissorW = scissor.width();
        pass.scissorH = scissor.height();
    }

    public void clearScissor() {
        ensurePass();
        Pass pass = currentPass();
        // 旧调用习惯是在 flush 后立刻 clearScissor；由于现在 flush 延迟到帧尾，
        // 已结束 pass 的裁剪不能在这里被清掉。
        if (!pass.flushed) {
            pass.scissorEnabled = false;
        }
    }

    public void beginFrame() {
        scope.clear();
        batch.clear();
        passIndex = -1;
        passCount = 0;
        frameOpen = true;
    }

    public void beginPass() {
        if (!frameOpen) {
            beginFrame();
        }
        // 旧 Dropdown 通过多次 flush 保证绘制顺序；现在每个 pass 映射到一个 layer。
        passIndex++;
        if (passIndex >= MAX_PASSES) {
            throw new IllegalStateException("exceeded max dropdown passes: " + MAX_PASSES);
        }
        passCount = Math.max(passCount, passIndex + 1);
        Pass pass = currentPass();
        pass.clear();
    }

    /**
     * 结束当前 pass。实际绘制会延迟到 {@link #endFrame()}。
     */
    public void flush() {
        ensurePass();
        currentPass().flushed = true;
    }

    public void endFrame() {
        if (!frameOpen) {
            return;
        }
        // 所有 facade 调用都已经写入 scope；这里只编译一次，减少 renderer draw/clear 次数。
        batch.render(scope.snapshot());
        for (int i = 0; i < passCount; i++) {
            Pass pass = passes[i];
            if (pass != null && pass.scissorEnabled) {
                batch.setLayerScissor(i * 10, pass.scissorX, pass.scissorY, pass.scissorW, pass.scissorH);
            }
        }
        batch.flushAndClear();
        scope.clear();
        frameOpen = false;
    }

    public void close() {
        batch.close();
        measureTextRenderer.close();
    }

    private void ensurePass() {
        if (passIndex < 0) {
            beginPass();
        }
    }

    private int currentLayer() {
        // pass 之间预留 10 个 layer 间隔，后续可在单个 pass 内插入子层而不破坏整体顺序。
        return passIndex * 10;
    }

    private Pass currentPass() {
        Pass pass = passes[passIndex];
        if (pass == null) {
            pass = new Pass();
            passes[passIndex] = pass;
        }
        return pass;
    }

    private PanelUiTree.Scope targetScope() {
        return scope;
    }

    private final Pass[] passes = new Pass[MAX_PASSES];

    private static final class Pass {
        private boolean scissorEnabled;
        private boolean flushed;
        private int scissorX;
        private int scissorY;
        private int scissorW;
        private int scissorH;

        private void clear() {
            scissorEnabled = false;
            flushed = false;
            scissorX = 0;
            scissorY = 0;
            scissorW = 0;
            scissorH = 0;
        }
    }

    public final class ShadowFacade {
        public void addShadow(float x, float y, float width, float height, float radius, float blurRadius, Color color) {
            addShadow(x, y, width, height, radius, radius, radius, radius, blurRadius, color);
        }

        public void addShadow(float x, float y, float width, float height, float topLeft, float topRight,
                              float bottomRight, float bottomLeft, float blurRadius, Color color) {
            targetScope().shadow(currentLayer(), x, y, width, height,
                    topLeft, topRight, bottomRight, bottomLeft, blurRadius, color);
        }
    }

    public final class RoundRectFacade {
        public void addRoundRect(float x, float y, float width, float height, float radius, Color color) {
            addRoundRect(x, y, width, height, radius, radius, radius, radius, color);
        }

        public void addRoundRect(float x, float y, float width, float height, float topLeft, float topRight,
                                 float bottomRight, float bottomLeft, Color color) {
            targetScope().roundRect(currentLayer(), x, y, width, height,
                    topLeft, topRight, bottomRight, bottomLeft, color);
        }

        public void addHorizontalGradient(float x, float y, float width, float height, float radius, Color left, Color right) {
            targetScope().roundRectHorizontalGradient(currentLayer(), x, y, width, height, radius, left, right);
        }
    }

    public final class OutlineFacade {
        public void addOutline(float x, float y, float width, float height, float radius, float outlineWidth, Color color) {
            addOutline(x, y, width, height, radius, radius, radius, radius, outlineWidth, color);
        }

        public void addOutline(float x, float y, float width, float height, float topLeft, float topRight,
                               float bottomRight, float bottomLeft, float outlineWidth, Color color) {
            targetScope().outline(currentLayer(), x, y, width, height,
                    topLeft, topRight, bottomRight, bottomLeft, outlineWidth, color);
        }
    }

    public final class RectFacade {
        public void addRect(float x, float y, float width, float height, Color color) {
            targetScope().rect(currentLayer(), x, y, width, height, color);
        }

        public void addOutline(float x, float y, float width, float height, float outlineWidth, Color color) {
            targetScope().rectOutline(currentLayer(), x, y, width, height, outlineWidth, color);
        }

        public void addRectGradient(float x, float y, float width, float height,
                                    Color topLeft, Color bottomLeft, Color bottomRight, Color topRight) {
            targetScope().rectGradient(currentLayer(), x, y, width, height,
                    topLeft, bottomLeft, bottomRight, topRight);
        }
    }

    public final class TriangleFacade {
        public void addChevronTriangle(float centerX, float centerY, float size, float progress, Color color) {
            targetScope().triangle(currentLayer(), centerX, centerY, size, progress, color);
        }
    }

    public final class TextFacade {
        public void addText(String text, float x, float y, float scale, Color color) {
            targetScope().text(currentLayer(), text, x, y, scale, color);
        }

        public void addText(String text, float x, float y, float scale, Color color, TtfFontLoader fontLoader) {
            targetScope().text(currentLayer(), text, x, y, scale, color, fontLoader);
        }

        public float getHeight(float scale) {
            return measureTextRenderer.getHeight(scale);
        }

        public float getHeight(float scale, TtfFontLoader fontLoader) {
            return measureTextRenderer.getHeight(scale, fontLoader);
        }

        public float getLineHeight(float scale) {
            return measureTextRenderer.getLineHeight(scale);
        }

        public float getLineHeight(float scale, TtfFontLoader fontLoader) {
            return measureTextRenderer.getLineHeight(scale, fontLoader);
        }

        public float getWidth(String text, float scale) {
            return measureTextRenderer.getWidth(text, scale);
        }

        public float getWidth(String text, float scale, TtfFontLoader fontLoader) {
            return measureTextRenderer.getWidth(text, scale, fontLoader);
        }
    }

}
