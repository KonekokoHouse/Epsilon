package com.github.epsilon.gui.dsl;

import com.github.epsilon.graphics.schedulers.Render2DScheduler;
import com.github.epsilon.graphics.schedulers.Render2DTexture;
import com.github.epsilon.graphics.text.ttf.TtfFontLoader;
import com.github.epsilon.graphics.LuminTexture;
import net.minecraft.resources.Identifier;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 面板 UI 的调度批次门面。
 * <p>
 * 旧 GUI 仍通过 renderer 风格的 facade 提交图元，内部会转成 {@link Render2DScheduler}
 * 命令。这样上层可以逐步迁移到自动布局，而底层已经统一进入新的 layer/scheduler 管线。
 */
public final class PanelRenderBatch implements AutoCloseable {

    private final Render2DScheduler scheduler;
    private final boolean ownsScheduler;
    private final int baseLayer;
    private final LayerRenderers rootLayer;
    private final Set<Integer> touchedLayers = new HashSet<>();

    public PanelRenderBatch() {
        this(new Render2DScheduler(), true, 0);
    }

    public PanelRenderBatch(Render2DScheduler scheduler, int baseLayer) {
        this(scheduler, false, baseLayer);
    }

    private PanelRenderBatch(Render2DScheduler scheduler, boolean ownsScheduler, int baseLayer) {
        this.scheduler = scheduler;
        this.ownsScheduler = ownsScheduler;
        this.baseLayer = baseLayer;
        this.rootLayer = new LayerRenderers(scheduler, baseLayer);
    }

    public PanelRenderBatch view(int relativeBaseLayer) {
        return new PanelRenderBatch(scheduler, false, baseLayer + relativeBaseLayer);
    }

    public Render2DScheduler scheduler() {
        return scheduler;
    }

    public int baseLayer() {
        return baseLayer;
    }

    public boolean ownsScheduler() {
        return ownsScheduler;
    }

    public ShadowFacade shadowRenderer() {
        return rootLayer.shadowRenderer();
    }

    public RoundRectFacade roundRectRenderer() {
        return rootLayer.roundRectRenderer();
    }

    public RoundRectOutlineFacade roundRectOutlineRenderer() {
        return rootLayer.roundRectOutlineRenderer();
    }

    public RectFacade rectRenderer() {
        return rootLayer.rectRenderer();
    }

    public TriangleFacade triangleRenderer() {
        return rootLayer.triangleRenderer();
    }

    public TextureFacade textureRenderer() {
        return rootLayer.textureRenderer();
    }

    public TextFacade textRenderer() {
        return rootLayer.textRenderer();
    }

    LayerRenderers layer(int layer) {
        touchedLayers.add(layer);
        if (layer == baseLayer) {
            return rootLayer;
        }
        // LayerRenderers 很薄，只保存 scheduler 的 layer handle facade；按需创建不会分配 GPU 资源。
        return new LayerRenderers(scheduler, layer);
    }

    public void render(PanelUiTree tree) {
        render(tree, 0);
    }

    public void render(PanelUiTree tree, int baseLayer) {
        PanelUiCompiler.renderIntoLayeredBatch(tree, this, this.baseLayer + baseLayer);
    }

    public void setLayerScissor(int layer, int x, int y, int width, int height) {
        int resolvedLayer = baseLayer + layer;
        touchedLayers.add(resolvedLayer);
        scheduler.layer(resolvedLayer).setScissor(x, y, width, height);
    }

    public void clearLayerScissor(int layer) {
        int resolvedLayer = baseLayer + layer;
        touchedLayers.add(resolvedLayer);
        scheduler.layer(resolvedLayer).clearScissor();
    }

    public void flush() {
        if (ownsScheduler) {
            scheduler.flush();
        } else {
            // 非 owning batch 只刷自己触碰过的 layer，避免干扰同一 scene 中其他 GUI 区域。
            for (int layer : sortedTouchedLayers()) {
                scheduler.flushLayer(layer);
            }
        }
    }

    public void clear() {
        if (ownsScheduler) {
            scheduler.clear();
        } else {
            for (int layer : sortedTouchedLayers()) {
                scheduler.clearLayer(layer);
            }
            touchedLayers.clear();
        }
    }

    public void flushAndClear() {
        flush();
        clear();
    }

    @Override
    public void close() {
        if (ownsScheduler) {
            scheduler.close();
        }
    }

    private List<Integer> sortedTouchedLayers() {
        List<Integer> layers = new ArrayList<>(touchedLayers);
        Collections.sort(layers);
        return layers;
    }

    public final class LayerRenderers {
        private final Render2DScheduler.LayerHandle layer;
        private final ShadowFacade shadowRenderer;
        private final RoundRectFacade roundRectRenderer;
        private final RoundRectOutlineFacade roundRectOutlineRenderer;
        private final RectFacade rectRenderer;
        private final TriangleFacade triangleRenderer;
        private final TextureFacade textureRenderer;
        private final TextFacade textRenderer;
        private final int layerIndex;

        private LayerRenderers(Render2DScheduler scheduler, int layer) {
            this.layer = scheduler.layer(layer);
            this.layerIndex = layer;
            this.shadowRenderer = new ShadowFacade(this.layer);
            this.roundRectRenderer = new RoundRectFacade(this.layer);
            this.roundRectOutlineRenderer = new RoundRectOutlineFacade(this.layer);
            this.rectRenderer = new RectFacade(this.layer);
            this.triangleRenderer = new TriangleFacade(this.layer);
            this.textureRenderer = new TextureFacade(this.layer);
            this.textRenderer = new TextFacade(this.layer, scheduler);
        }

        public ShadowFacade shadowRenderer() {
            return shadowRenderer;
        }

        public RoundRectFacade roundRectRenderer() {
            return roundRectRenderer;
        }

        public RoundRectOutlineFacade roundRectOutlineRenderer() {
            return roundRectOutlineRenderer;
        }

        public RectFacade rectRenderer() {
            return rectRenderer;
        }

        public TriangleFacade triangleRenderer() {
            return triangleRenderer;
        }

        public TextureFacade textureRenderer() {
            return textureRenderer;
        }

        public TextFacade textRenderer() {
            return textRenderer;
        }

        void setScissor(int x, int y, int width, int height) {
            touch();
            layer.setScissor(x, y, width, height);
        }

        void clearScissor() {
            touch();
            layer.clearScissor();
        }

        private void touch() {
            touchedLayers.add(layerIndex);
        }
    }

    public final class ShadowFacade {
        private final Render2DScheduler.LayerHandle layer;

        private ShadowFacade(Render2DScheduler.LayerHandle layer) {
            this.layer = layer;
        }

        public void addShadow(float x, float y, float width, float height, float radius, float blurRadius, Color color) {
            touchLayer(layer);
            layer.addShadow(x, y, width, height, radius, blurRadius, color);
        }

        public void addShadow(float x, float y, float width, float height, float topLeft, float topRight,
                              float bottomRight, float bottomLeft, float blurRadius, Color color) {
            touchLayer(layer);
            layer.addShadow(x, y, width, height, topLeft, topRight, bottomRight, bottomLeft, blurRadius, color);
        }
    }

    public final class RoundRectFacade {
        private final Render2DScheduler.LayerHandle layer;

        private RoundRectFacade(Render2DScheduler.LayerHandle layer) {
            this.layer = layer;
        }

        public void addRoundRect(float x, float y, float width, float height, float radius, Color color) {
            touchLayer(layer);
            layer.addRoundRect(x, y, width, height, radius, color);
        }

        public void addRoundRect(float x, float y, float width, float height, float topLeft, float topRight,
                                 float bottomRight, float bottomLeft, Color color) {
            touchLayer(layer);
            layer.addRoundRect(x, y, width, height, topLeft, topRight, bottomRight, bottomLeft, color);
        }

        public void addRoundRectGradient(float x, float y, float width, float height, float topLeftRadius,
                                         float topRightRadius, float bottomRightRadius, float bottomLeftRadius,
                                         Color topLeft, Color bottomLeft, Color bottomRight, Color topRight) {
            touchLayer(layer);
            layer.addRoundRectGradient(x, y, width, height, topLeftRadius, topRightRadius,
                    bottomRightRadius, bottomLeftRadius, topLeft, bottomLeft, bottomRight, topRight);
        }
    }

    public final class RoundRectOutlineFacade {
        private final Render2DScheduler.LayerHandle layer;

        private RoundRectOutlineFacade(Render2DScheduler.LayerHandle layer) {
            this.layer = layer;
        }

        public void addOutline(float x, float y, float width, float height, float radius, float outlineWidth, Color color) {
            touchLayer(layer);
            layer.addOutline(x, y, width, height, radius, outlineWidth, color);
        }

        public void addOutline(float x, float y, float width, float height, float topLeft, float topRight,
                               float bottomRight, float bottomLeft, float outlineWidth, Color color) {
            touchLayer(layer);
            layer.addOutline(x, y, width, height, topLeft, topRight, bottomRight, bottomLeft, outlineWidth, color);
        }
    }

    public final class RectFacade {
        private final Render2DScheduler.LayerHandle layer;

        private RectFacade(Render2DScheduler.LayerHandle layer) {
            this.layer = layer;
        }

        public void addRect(float x, float y, float width, float height, Color color) {
            touchLayer(layer);
            layer.addRect(x, y, width, height, color);
        }

        public void addOutline(float x, float y, float width, float height, float outlineWidth, Color color) {
            touchLayer(layer);
            layer.addRectOutline(x, y, width, height, outlineWidth, color);
        }

        public void addRectGradient(float x, float y, float width, float height,
                                    Color topLeft, Color bottomLeft, Color bottomRight, Color topRight) {
            touchLayer(layer);
            layer.addRectGradient(x, y, width, height, topLeft, bottomLeft, bottomRight, topRight);
        }
    }

    public final class TriangleFacade {
        private final Render2DScheduler.LayerHandle layer;

        private TriangleFacade(Render2DScheduler.LayerHandle layer) {
            this.layer = layer;
        }

        public void addChevronTriangle(float centerX, float centerY, float size, float progress, Color color) {
            touchLayer(layer);
            layer.addChevronTriangle(centerX, centerY, size, progress, color);
        }
    }

    public final class TextureFacade {
        private final Render2DScheduler.LayerHandle layer;

        private TextureFacade(Render2DScheduler.LayerHandle layer) {
            this.layer = layer;
        }

        public void addQuadTexture(Identifier texture, float x, float y, float width, float height,
                                   float u0, float v0, float u1, float v1, Color color) {
            addQuadTexture(texture, x, y, width, height, u0, v0, u1, v1, color, false);
        }

        public void addQuadTexture(Identifier texture, float x, float y, float width, float height,
                                   float u0, float v0, float u1, float v1, Color color, boolean linearFilter) {
            touchLayer(layer);
            layer.addTexture(new Render2DTexture.IdentifierRef(texture, linearFilter), x, y, width, height, u0, v0, u1, v1, color);
        }

        public void addQuadTexture(LuminTexture texture, float x, float y, float width, float height,
                                   float u0, float v0, float u1, float v1, Color color) {
            touchLayer(layer);
            layer.addTexture(new Render2DTexture.LuminRef(texture), x, y, width, height, u0, v0, u1, v1, color);
        }

        public void addRoundedTexture(Identifier texture, float x, float y, float width, float height, float radius,
                                      float u0, float v0, float u1, float v1, Color color) {
            addRoundedTexture(texture, x, y, width, height, radius, u0, v0, u1, v1, color, false);
        }

        public void addRoundedTexture(Identifier texture, float x, float y, float width, float height, float radius,
                                      float u0, float v0, float u1, float v1, Color color, boolean linearFilter) {
            touchLayer(layer);
            layer.addRoundedTexture(new Render2DTexture.IdentifierRef(texture, linearFilter), x, y, width, height, radius, u0, v0, u1, v1, color);
        }

        public void addRoundedTexture(LuminTexture texture, float x, float y, float width, float height, float radius,
                                      float u0, float v0, float u1, float v1, Color color) {
            touchLayer(layer);
            layer.addRoundedTexture(new Render2DTexture.LuminRef(texture), x, y, width, height, radius, u0, v0, u1, v1, color);
        }

        public void addPlayerHead(LuminTexture texture, float x, float y, float size, float radius, Color color) {
            addRoundedTexture(texture, x, y, size, size, radius, 8.0f / 64.0f, 8.0f / 64.0f, 16.0f / 64.0f, 16.0f / 64.0f, color);
            addRoundedTexture(texture, x, y, size, size, radius, 40.0f / 64.0f, 8.0f / 64.0f, 48.0f / 64.0f, 16.0f / 64.0f, color);
        }
    }

    public final class TextFacade {
        private final Render2DScheduler.LayerHandle layer;
        private final Render2DScheduler scheduler;

        private TextFacade(Render2DScheduler.LayerHandle layer, Render2DScheduler scheduler) {
            this.layer = layer;
            this.scheduler = scheduler;
        }

        public void addText(String text, float x, float y, float scale, Color color) {
            touchLayer(layer);
            layer.addText(text, x, y, scale, color);
        }

        public void addText(String text, float x, float y, float scale, Color color, TtfFontLoader fontLoader) {
            touchLayer(layer);
            layer.addText(text, x, y, scale, color, fontLoader);
        }

        public float getHeight(float scale) {
            return scheduler.textMetrics().getHeight(scale);
        }

        public float getHeight(float scale, TtfFontLoader fontLoader) {
            return scheduler.textMetrics().getHeight(scale, fontLoader);
        }

        public float getWidth(String text, float scale) {
            return scheduler.textMetrics().getWidth(text, scale);
        }

        public float getWidth(String text, float scale, TtfFontLoader fontLoader) {
            return scheduler.textMetrics().getWidth(text, scale, fontLoader);
        }
    }

    private void touchLayer(Render2DScheduler.LayerHandle layer) {
        touchedLayers.add(layer.layer());
    }

}
