package com.github.epsilon.gui.panel.popup;

import com.github.epsilon.gui.panel.PanelLayout;
import com.github.epsilon.gui.dsl.PanelRenderBatch;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * 面板弹窗宿主。
 * <p>
 * 它负责管理当前活动弹窗、提供相对面板的居中布局能力，并把弹窗的 extract/flush
 * 生命周期接入主屏幕统一的渲染批次提交流程。
 */
public class PanelPopupHost {

    private Popup activePopup;
    private PanelLayout.Rect overlayBounds;
    private final PanelRenderBatch renderBatch = new PanelRenderBatch();
    private boolean pending;

    /**
     * 打开一个新的活动弹窗。
     *
     * @param popup 需要显示的弹窗实例
     */
    public void open(Popup popup) {
        this.activePopup = popup;
    }

    /**
     * 关闭当前活动弹窗。
     */
    public void close() {
        this.activePopup = null;
    }

    /**
     * 返回当前活动弹窗。
     *
     * @return 当前活动弹窗；若没有则为 {@code null}
     */
    public Popup getActivePopup() {
        return activePopup;
    }

    public void setOverlayBounds(PanelLayout.Rect overlayBounds) {
        this.overlayBounds = overlayBounds;
    }

    /**
     * 根据宿主覆盖区域计算一个居中的弹窗矩形。
     *
     * @param width  期望宽度
     * @param height 期望高度
     * @return 限制在宿主覆盖区域内的居中弹窗区域
     */
    public PanelLayout.Rect getCenteredBounds(float width, float height) {
        PanelLayout.Rect baseBounds = overlayBounds != null
                ? overlayBounds
                : new PanelLayout.Rect(0.0f, 0.0f, width, height);
        float popupWidth = Math.min(width, baseBounds.width());
        float popupHeight = Math.min(height, baseBounds.height());
        return new PanelLayout.Rect(
                baseBounds.x() + (baseBounds.width() - popupWidth) / 2.0f,
                baseBounds.y() + (baseBounds.height() - popupHeight) / 2.0f,
                popupWidth,
                popupHeight
        );
    }

    /**
     * 让当前弹窗提取本帧 UI，并写入宿主内部批次。
     * <p>
     * 该阶段只做 extract，不直接 flush；真正的输出由主屏幕统一调度。
     */
    public void render(GuiGraphicsExtractor GuiGraphicsExtractor, int mouseX, int mouseY, float partialTick) {
        if (activePopup == null) {
            renderBatch.clear();
            pending = false;
            return;
        }
        renderBatch.clear();
        activePopup.extractGui(GuiGraphicsExtractor, renderBatch, mouseX, mouseY, partialTick);
        pending = true;
    }

    /**
     * 输出当前活动弹窗的批次内容。
     * <p>
     * 若弹窗拥有额外的 viewport 或私有缓冲，也会在其 {@link Popup#flush(PanelRenderBatch)}
     * 中一并处理。
     */
    public void flush() {
        if (!pending || activePopup == null) {
            renderBatch.clear();
            pending = false;
            return;
        }
        activePopup.flush(renderBatch);
        pending = false;
    }

    /**
     * 处理鼠标点击事件。
     * <p>
     * 当点击发生在弹窗外部时，宿主会直接关闭当前弹窗。
     */
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        if (activePopup == null) {
            return false;
        }
        if (!activePopup.getBounds().contains(event.x(), event.y())) {
            close();
            return true;
        }
        boolean handled = activePopup.mouseClicked(event, isDoubleClick);
        if (handled && activePopup.shouldCloseAfterClick()) {
            close();
        }
        return handled;
    }

    public boolean keyPressed(KeyEvent event) {
        if (activePopup == null) {
            return false;
        }
        if (event.key() == 256) {
            close();
            return true;
        }
        boolean handled = activePopup.keyPressed(event);
        if (handled && activePopup.shouldCloseAfterClick()) {
            close();
        }
        return handled;
    }

    public boolean charTyped(CharacterEvent event) {
        if (activePopup == null) {
            return false;
        }
        return activePopup.charTyped(event);
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        if (activePopup == null) {
            return false;
        }
        activePopup.mouseReleased(event);
        return true;
    }

    public boolean mouseDragged(MouseButtonEvent event, double mouseX, double mouseY) {
        if (activePopup == null) {
            return false;
        }
        activePopup.mouseDragged(event, mouseX, mouseY);
        return true;
    }

    /**
     * 仅当滚轮事件位于弹窗内部时才转发给活动弹窗。
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (activePopup == null) {
            return false;
        }
        if (activePopup.getBounds().contains(mouseX, mouseY)) {
            return activePopup.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        return false;
    }

    /**
     * 面板弹窗协议。
     * <p>
     * 弹窗需要实现几何区域、UI 提取以及输入事件处理；默认情况下会在 flush 时直接输出并清空给定批次。
     */
    public interface Popup {
        float POPUP_SHADOW_RADIUS = 2.5f;

        /**
         * 返回当前弹窗的命中与布局区域。
         */
        PanelLayout.Rect getBounds();

        /**
         * 将当前弹窗的 UI 内容提取到给定批次中。
         *
         * @param GuiGraphicsExtractor 当前 GUI 提取器
         * @param renderBatch          目标渲染批次
         * @param mouseX               鼠标 X 坐标
         * @param mouseY               鼠标 Y 坐标
         * @param partialTick          局部时间
         */
        void extractGui(GuiGraphicsExtractor GuiGraphicsExtractor, PanelRenderBatch renderBatch, int mouseX, int mouseY, float partialTick);

        /**
         * 输出并清空弹窗写入的渲染批次。
         * <p>
         * 大多数弹窗可直接使用默认实现；若弹窗还持有额外内容缓冲，可在此方法中扩展 flush 流程。
         */
        default void flush(PanelRenderBatch renderBatch) {
            renderBatch.flushAndClear();
        }

        /**
         * 处理鼠标点击事件。
         *
         * @return 是否已消费该事件
         */
        boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick);

        /**
         * 指示宿主在本次点击处理后是否需要关闭弹窗。
         */
        default boolean shouldCloseAfterClick() {
            return false;
        }

        default boolean keyPressed(KeyEvent event) {
            return false;
        }

        default boolean charTyped(CharacterEvent event) {
            return false;
        }

        default boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            return false;
        }

        default boolean mouseReleased(MouseButtonEvent event) {
            return false;
        }

        default boolean mouseDragged(MouseButtonEvent event, double mouseX, double mouseY) {
            return false;
        }
    }

}
