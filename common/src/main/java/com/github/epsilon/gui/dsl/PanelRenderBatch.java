package com.github.epsilon.gui.dsl;

import com.github.epsilon.graphics.renderers.*;

import java.util.Map;
import java.util.TreeMap;

/**
 * 面板 UI 的通用 renderer 批次封装。
 * <p>
 * 批次按 layer 管理 renderer：同一 layer 内按 renderer 类型合批，flush 时按 layer
 * 从小到大输出，从而兼顾绘制顺序和批处理效率。
 */
public final class PanelRenderBatch {

    // layer 0 复用调用方传入的根 renderer，便于 PanelScreen 等旧路径继续共享已有资源。
    private final LayerRenderers rootLayer;
    // 非 0 layer 按需创建 renderer 组，TreeMap 保证输出顺序稳定。
    private final TreeMap<Integer, LayerRenderers> extraLayers = new TreeMap<>();

    public PanelRenderBatch() {
        this(ShadowRenderer.create(), RoundRectRenderer.create(), RoundRectOutlineRenderer.create(), RectRenderer.create(),
                TriangleRenderer.create(), TextRenderer.create());
    }

    public PanelRenderBatch(ShadowRenderer shadowRenderer, RoundRectRenderer roundRectRenderer,
                            RoundRectOutlineRenderer roundRectOutlineRenderer, RectRenderer rectRenderer,
                            TextRenderer textRenderer) {
        this(shadowRenderer, roundRectRenderer, roundRectOutlineRenderer, rectRenderer,
                TriangleRenderer.create(), textRenderer);
    }

    public PanelRenderBatch(ShadowRenderer shadowRenderer, RoundRectRenderer roundRectRenderer,
                            RoundRectOutlineRenderer roundRectOutlineRenderer, RectRenderer rectRenderer,
                            TriangleRenderer triangleRenderer, TextRenderer textRenderer) {
        this.rootLayer = new LayerRenderers(shadowRenderer, roundRectRenderer, roundRectOutlineRenderer, rectRenderer, triangleRenderer, textRenderer);
    }

    public ShadowRenderer shadowRenderer() {
        return rootLayer.shadowRenderer();
    }

    public RoundRectRenderer roundRectRenderer() {
        return rootLayer.roundRectRenderer();
    }

    public RoundRectOutlineRenderer roundRectOutlineRenderer() {
        return rootLayer.roundRectOutlineRenderer();
    }

    public RectRenderer rectRenderer() {
        return rootLayer.rectRenderer();
    }

    public TriangleRenderer triangleRenderer() {
        return rootLayer.triangleRenderer();
    }

    public TextRenderer textRenderer() {
        return rootLayer.textRenderer();
    }

    LayerRenderers layer(int layer) {
        if (layer == 0) {
            return rootLayer;
        }
        // 只有真正写入额外层时才分配 renderer，避免普通 GUI 帧产生多余 GPU 资源。
        return extraLayers.computeIfAbsent(layer, ignored -> LayerRenderers.create());
    }

    public void render(PanelUiTree tree) {
        render(tree, 0);
    }

    public void render(PanelUiTree tree, int baseLayer) {
        PanelUiCompiler.renderIntoLayeredBatch(tree, this, baseLayer);
    }

    public void setLayerScissor(int layer, int x, int y, int width, int height) {
        // scissor 绑定在整个 layer 上，Dropdown 的一个 pass 会对应一个独立 layer。
        layer(layer).setScissor(x, y, width, height);
    }

    public void clearLayerScissor(int layer) {
        layer(layer).clearScissor();
    }

    public void flush() {
        // layer 0 使用外部传入的根 renderer，需要夹在负层和正层之间输出。
        for (Map.Entry<Integer, LayerRenderers> entry : extraLayers.entrySet()) {
            if (entry.getKey() < 0) {
                entry.getValue().draw();
            }
        }
        rootLayer.draw();
        for (Map.Entry<Integer, LayerRenderers> entry : extraLayers.entrySet()) {
            if (entry.getKey() > 0) {
                entry.getValue().draw();
            }
        }
    }

    public void clear() {
        rootLayer.clear();
        for (LayerRenderers renderers : extraLayers.values()) {
            renderers.clear();
        }
    }

    public void flushAndClear() {
        flush();
        clear();
    }

    public void close() {
        rootLayer.close();
        for (LayerRenderers renderers : extraLayers.values()) {
            renderers.close();
        }
        extraLayers.clear();
    }

    public static final class LayerRenderers {
        // 每个 layer 拥有一套 renderer，同层同类型图元可以继续合批。
        private final ShadowRenderer shadowRenderer;
        private final RoundRectRenderer roundRectRenderer;
        private final RoundRectOutlineRenderer roundRectOutlineRenderer;
        private final RectRenderer rectRenderer;
        private final TriangleRenderer triangleRenderer;
        private final TextRenderer textRenderer;

        private LayerRenderers(ShadowRenderer shadowRenderer, RoundRectRenderer roundRectRenderer,
                               RoundRectOutlineRenderer roundRectOutlineRenderer, RectRenderer rectRenderer,
                               TriangleRenderer triangleRenderer, TextRenderer textRenderer) {
            this.shadowRenderer = shadowRenderer;
            this.roundRectRenderer = roundRectRenderer;
            this.roundRectOutlineRenderer = roundRectOutlineRenderer;
            this.rectRenderer = rectRenderer;
            this.triangleRenderer = triangleRenderer;
            this.textRenderer = textRenderer;
        }

        private static LayerRenderers create() {
            return new LayerRenderers(ShadowRenderer.create(), RoundRectRenderer.create(), RoundRectOutlineRenderer.create(),
                    RectRenderer.create(), TriangleRenderer.create(), TextRenderer.create());
        }

        public ShadowRenderer shadowRenderer() {
            return shadowRenderer;
        }

        public RoundRectRenderer roundRectRenderer() {
            return roundRectRenderer;
        }

        public RoundRectOutlineRenderer roundRectOutlineRenderer() {
            return roundRectOutlineRenderer;
        }

        public RectRenderer rectRenderer() {
            return rectRenderer;
        }

        public TriangleRenderer triangleRenderer() {
            return triangleRenderer;
        }

        public TextRenderer textRenderer() {
            return textRenderer;
        }

        private void draw() {
            shadowRenderer.draw();
            roundRectRenderer.draw();
            roundRectOutlineRenderer.draw();
            rectRenderer.draw();
            triangleRenderer.draw();
            textRenderer.draw();
        }

        private void setScissor(int x, int y, int width, int height) {
            shadowRenderer.setScissor(x, y, width, height);
            roundRectRenderer.setScissor(x, y, width, height);
            roundRectOutlineRenderer.setScissor(x, y, width, height);
            rectRenderer.setScissor(x, y, width, height);
            triangleRenderer.setScissor(x, y, width, height);
            textRenderer.setScissor(x, y, width, height);
        }

        private void clearScissor() {
            shadowRenderer.clearScissor();
            roundRectRenderer.clearScissor();
            roundRectOutlineRenderer.clearScissor();
            rectRenderer.clearScissor();
            triangleRenderer.clearScissor();
            textRenderer.clearScissor();
        }

        private void clear() {
            clearScissor();
            shadowRenderer.clear();
            roundRectRenderer.clear();
            roundRectOutlineRenderer.clear();
            rectRenderer.clear();
            triangleRenderer.clear();
            textRenderer.clear();
        }

        private void close() {
            shadowRenderer.close();
            roundRectRenderer.close();
            roundRectOutlineRenderer.close();
            rectRenderer.close();
            triangleRenderer.close();
            textRenderer.close();
        }
    }

}
