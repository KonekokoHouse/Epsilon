package com.github.epsilon.gui.panel.popup;

import com.github.slmpc.lumingraphics.ui.geometry.UiRect;
import com.github.slmpc.lumingraphics.ui.render.UiRenderBatch;
import com.github.epsilon.gui.panel.utils.IMEFocusHelper;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * 面板弹窗宿主。
 * <p>
 * 它负责管理当前活动弹窗、提供相对面板的居中布局能力，并把弹窗内容接入主屏幕的渲染批次。
 */
public class PanelPopupHost {

    private Popup activePopup;
    private UiRect overlayBounds;
    private UiRenderBatch pendingBatch;

    /**
     * 打开一个新的活动弹窗。
     *
     * @param popup 需要显示的弹窗实例
     */
    public void open(Popup popup) {
        close();
        this.activePopup = popup;
        IMEFocusHelper.activate();
    }

    /**
     * 关闭当前活动弹窗。
     */
    public void close() {
        if (this.activePopup != null) {
            this.activePopup.close();
        }
        this.activePopup = null;
        this.pendingBatch = null;
        IMEFocusHelper.deactivate();
    }

    /**
     * 返回当前活动弹窗。
     *
     * @return 当前活动弹窗；若没有则为 {@code null}
     */
    public Popup getActivePopup() {
        return activePopup;
    }

    public void setOverlayBounds(UiRect overlayBounds) {
        this.overlayBounds = overlayBounds;
    }

    /**
     * 根据宿主覆盖区域计算一个居中的弹窗矩形。
     *
     * @param width  期望宽度
     * @param height 期望高度
     * @return 限制在宿主覆盖区域内的居中弹窗区域
     */
    public UiRect getCenteredBounds(float width, float height) {
        UiRect baseBounds = overlayBounds != null
                ? overlayBounds
                : new UiRect(0.0f, 0.0f, width, height);
        float popupWidth = Math.min(width, baseBounds.width());
        float popupHeight = Math.min(height, baseBounds.height());
        return new UiRect(
                baseBounds.x() + (baseBounds.width() - popupWidth) / 2.0f,
                baseBounds.y() + (baseBounds.height() - popupHeight) / 2.0f,
                popupWidth,
                popupHeight
        );
    }

    /**
     * 让当前弹窗提取本帧 UI，并写入 scene 当前批次。
     */
    public void render(GuiGraphicsExtractor guiGraphics, UiRenderBatch renderBatch, int mouseX, int mouseY, float partialTick) {
        if (activePopup == null) {
            pendingBatch = null;
            return;
        }
        activePopup.extractGui(guiGraphics, renderBatch, mouseX, mouseY, partialTick);
        pendingBatch = renderBatch;
    }

    /** 保留给尚未迁移到 runtime scene 帧生命周期的共享调用方。 */
    public void flush() {
        if (pendingBatch == null || activePopup == null) {
            return;
        }
        activePopup.flush(pendingBatch);
        pendingBatch = null;
    }

    public void extractOverlay(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (activePopup != null) {
            activePopup.extractOverlay(guiGraphics, mouseX, mouseY, partialTick);
        }
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
            activePopup.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
            return true;
        }
        return false;
    }

    /**
     * 面板弹窗协议。
     * <p>
     * 弹窗需要实现几何区域、UI 提取以及输入事件处理；普通图元进入主 scene。
     */
    public interface Popup extends AutoCloseable {
        /**
         * 返回当前弹窗的命中与布局区域。
         */
        UiRect getBounds();

        /**
         * 将当前弹窗的 UI 内容提取到给定批次中。
         *
         * @param GuiGraphicsExtractor 当前 GUI 提取器
         * @param renderBatch          目标渲染批次
         * @param mouseX               鼠标 X 坐标
         * @param mouseY               鼠标 Y 坐标
         * @param partialTick          局部时间
         */
        void extractGui(GuiGraphicsExtractor GuiGraphicsExtractor, UiRenderBatch renderBatch, int mouseX, int mouseY, float partialTick);

        /** 保留给需要显式提交附加缓冲的兼容实现。 */
        default void flush(UiRenderBatch renderBatch) {
        }

        default void extractOverlay(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
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

        @Override
        default void close() {
        }
    }

}
