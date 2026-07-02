package com.github.epsilon.gui.scene;

import com.github.epsilon.graphics.schedulers.render2d.Render2DScheduler;
import com.github.epsilon.gui.dsl.PanelRenderBatch;
import com.github.epsilon.gui.dsl.PanelUiTree;

/**
 * 单帧 GUI 渲染场景。
 * <p>
 * 一个 scene 拥有一个 {@link Render2DScheduler}。Panel、Dropdown、popup 只向 scene
 * 提交 UI 树，最后由 scene 统一 flush，从而减少 scheduler/buffer 的创建和帧内 draw 次数。
 */
public final class GuiScene implements AutoCloseable {

    private final Render2DScheduler scheduler = new Render2DScheduler();
    private final GuiLayerStack layers = new GuiLayerStack();

    public void beginFrame() {
        // 每个 Screen/HUD editor 帧从一个干净的命令流开始，具体 renderer 由 scheduler 池化复用。
        scheduler.clear();
    }

    public PanelRenderBatch batch(GuiLayer layer) {
        return new PanelRenderBatch(scheduler, layers.resolve(layer));
    }

    public PanelRenderBatch batch(GuiLayer layer, int relativeLayer) {
        // 返回同一个 scheduler 的局部视图，调用方只通过 UI 树写入自己的 base layer。
        return new PanelRenderBatch(scheduler, layers.resolve(layer, relativeLayer));
    }

    public void submit(GuiLayer layer, PanelUiTree tree) {
        batch(layer).render(tree);
    }

    public void submit(GuiLayer layer, int relativeLayer, PanelUiTree tree) {
        batch(layer, relativeLayer).render(tree);
    }

    public void flush() {
        scheduler.flush();
    }

    public void flushAndClear() {
        scheduler.flushAndClear();
    }

    public void clear() {
        scheduler.clear();
    }

    public void endFrame() {
        flush();
        scheduler.clear();
    }

    public Render2DScheduler scheduler() {
        return scheduler;
    }

    public GuiLayerStack layers() {
        return layers;
    }

    @Override
    public void close() {
        scheduler.close();
    }
}
