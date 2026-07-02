package com.github.epsilon.gui.dsl;

import com.github.epsilon.graphics.schedulers.render2d.Render2DScheduler;

import java.util.*;

/**
 * 面板 UI 的 2D 调度批次。
 * <p>
 * 上层只提交 {@link PanelUiTree}，本类负责把树写入共享的 layer/scheduler 管线，并维护局部视图触碰过的 layer。
 */
public final class PanelRenderBatch implements AutoCloseable {

    private final Render2DScheduler scheduler;
    private final boolean ownsScheduler;
    private final int baseLayer;
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

    /**
     * 返回相对当前 batch 的 layer 句柄。
     * <p>
     * 该入口只用于底层编译器和少量需要设置 scissor 的容器，业务 UI 应优先提交 {@link PanelUiTree}。
     */
    public Render2DScheduler.LayerHandle layerHandle(int relativeLayer) {
        return absoluteLayer(baseLayer + relativeLayer);
    }

    Render2DScheduler.LayerHandle absoluteLayer(int layer) {
        touchedLayers.add(layer);
        return scheduler.layer(layer);
    }

    public void render(PanelUiTree tree) {
        render(tree, 0);
    }

    public void render(PanelUiTree tree, int baseLayer) {
        PanelUiCompiler.renderIntoLayeredBatch(tree, this, this.baseLayer + baseLayer);
    }

    public void flush() {
        if (ownsScheduler) {
            scheduler.flush();
            return;
        }
        for (int layer : sortedTouchedLayers()) {
            scheduler.flushLayer(layer);
        }
    }

    public void clear() {
        if (ownsScheduler) {
            scheduler.clear();
            return;
        }
        for (int layer : sortedTouchedLayers()) {
            scheduler.clearLayer(layer);
        }
        touchedLayers.clear();
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

}
