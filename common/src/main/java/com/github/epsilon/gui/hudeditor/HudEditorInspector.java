package com.github.epsilon.gui.hudeditor;

import com.github.epsilon.assets.i18n.EpsilonTranslateComponent;
import com.github.epsilon.assets.i18n.TranslateComponent;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.gui.panel.MD3Theme;
import com.github.epsilon.gui.panel.PanelLayout;
import com.github.epsilon.gui.panel.adapter.SettingListController;
import com.github.epsilon.gui.dsl.PanelRenderBatch;
import com.github.epsilon.gui.dsl.PanelUiTree;
import com.github.epsilon.gui.panel.popup.PanelPopupHost;
import com.github.epsilon.gui.panel.utils.PanelContentBuffer;
import com.github.epsilon.gui.panel.utils.ScrollBarDragState;
import com.github.epsilon.gui.panel.utils.ScrollBarUtils;
import com.github.epsilon.modules.HudModule;
import com.github.epsilon.settings.Setting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.Mth;

import java.util.List;

public class HudEditorInspector {

    private static final float PANEL_MARGIN = 10.0f;
    private static final float PANEL_WIDTH = 220.0f;
    private static final float PANEL_MIN_HEIGHT = 116.0f;
    private static final float PANEL_COLLAPSED_HEIGHT = 40.0f;
    private static final float PANEL_EXPANDED_MAX_HEIGHT = 320.0f;
    private static final float PANEL_EXPANDED_MAX_SCREEN_RATIO = 0.72f;
    private static final float TITLE_SCALE = 0.78f;
    private static final float SUBTITLE_SCALE = 0.56f;
    private static final float HEADER_TOP = 10.0f;
    private static final float HEADER_HEIGHT = 32.0f;
    private static final float HEADER_GAP = 28.0f;
    private static final float CONTENT_TOP = 40.0f;
    private static final float COLLAPSE_BUTTON_SIZE = 12.0f;

    private final PanelRenderBatch renderBatch = new PanelRenderBatch();
    private final TextRenderer textRenderer = TextRenderer.create();
    private final PanelContentBuffer contentBuffer = new PanelContentBuffer();
    private final PanelPopupHost popupHost = new PanelPopupHost();
    private final SettingListController settingList = new SettingListController(popupHost);

    private PanelLayout.Rect bounds;
    private PanelLayout.Rect viewport;
    private PanelLayout.Rect headerBounds;
    private PanelLayout.Rect collapseButtonBounds;
    private HudModule selectedModule;
    private float scroll;
    private float maxScroll;
    private String selectedModuleName = "";
    private boolean collapsed;
    private boolean windowDragging;
    private float panelX = Float.NaN;
    private float panelY = Float.NaN;
    private float dragOffsetX;
    private float dragOffsetY;
    private int lastScreenWidth;
    private int lastScreenHeight;
    private final ScrollBarDragState scrollBarDrag = new ScrollBarDragState();

    private static final TranslateComponent titleComponent = EpsilonTranslateComponent.create("gui", "inspector");
    private static final TranslateComponent selectComponent = EpsilonTranslateComponent.create("gui", "inspector.select");

    public void queueRender(GuiGraphicsExtractor graphics, HudModule selectedModule, int screenWidth, int screenHeight, int mouseX, int mouseY, float partialTick, int guiHeight) {
        this.selectedModule = selectedModule;
        this.lastScreenWidth = screenWidth;
        this.lastScreenHeight = screenHeight;
        this.bounds = computeBounds(screenWidth, screenHeight);
        popupHost.setOverlayBounds(bounds);
        this.headerBounds = new PanelLayout.Rect(bounds.x(), bounds.y(), bounds.width(), HEADER_HEIGHT);
        float buttonX = bounds.x() + bounds.width() - MD3Theme.PANEL_TITLE_INSET - COLLAPSE_BUTTON_SIZE;
        float buttonY = bounds.y() + (HEADER_HEIGHT - COLLAPSE_BUTTON_SIZE) * 0.5f;
        this.collapseButtonBounds = new PanelLayout.Rect(buttonX, buttonY, COLLAPSE_BUTTON_SIZE, COLLAPSE_BUTTON_SIZE);

        float titleX = bounds.x() + MD3Theme.PANEL_TITLE_INSET;

        boolean collapseHovered = collapseButtonBounds.contains(mouseX, mouseY);
        String collapseGlyph = collapsed ? "+" : "-";
        PanelUiTree chromeTree = PanelUiTree.build(scope -> {
            scope.layer(0, layer -> {
                layer.shadow(bounds.x(), bounds.y(), bounds.width(), bounds.height(), MD3Theme.SECTION_RADIUS, 16.0f,
                        MD3Theme.withAlpha(MD3Theme.SHADOW, MD3Theme.PANEL_SHADOW_ALPHA));
                layer.roundRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), MD3Theme.SECTION_RADIUS, MD3Theme.SURFACE_DIM);
                layer.roundRect(bounds.x() + 1.0f, bounds.y() + 1.0f, bounds.width() - 2.0f, bounds.height() - 2.0f,
                        MD3Theme.SECTION_RADIUS - 1.0f, MD3Theme.SURFACE_CONTAINER_LOW);
                layer.roundRect(collapseButtonBounds.x(), collapseButtonBounds.y(),
                        collapseButtonBounds.width(), collapseButtonBounds.height(), 5.0f,
                        collapseHovered ? MD3Theme.SURFACE_CONTAINER_HIGH : MD3Theme.SURFACE_CONTAINER);
            });
            scope.layer(10, layer -> {
                layer.text(titleComponent.getTranslatedName(), titleX, bounds.y() + HEADER_TOP, TITLE_SCALE, MD3Theme.TEXT_PRIMARY);
                layer.text(collapseGlyph, collapseButtonBounds.x() + 4.0f, collapseButtonBounds.y() + 2.0f, 0.68f, MD3Theme.TEXT_PRIMARY);
            });
        });
        renderBatch.render(chromeTree);

        if (collapsed) {
            flushChrome();
            clearContentState(false);
            return;
        }

        if (selectedModule == null) {
            renderBatch.render(PanelUiTree.build(scope -> scope.layer(10, layer ->
                    layer.text(selectComponent.getTranslatedName(), titleX, bounds.y() + HEADER_GAP, SUBTITLE_SCALE, MD3Theme.TEXT_SECONDARY))));
            flushChrome();
            clearContentState(true);
            return;
        }

        if (!selectedModule.getName().equals(selectedModuleName)) {
            scroll = 0.0f;
            selectedModuleName = selectedModule.getName();
        }

        renderBatch.render(PanelUiTree.build(scope -> scope.layer(10, layer ->
                layer.text(selectedModule.getTranslatedName(), titleX, bounds.y() + HEADER_GAP, SUBTITLE_SCALE, MD3Theme.TEXT_SECONDARY))));

        viewport = new PanelLayout.Rect(
                bounds.x() + MD3Theme.PANEL_VIEWPORT_INSET,
                bounds.y() + CONTENT_TOP,
                bounds.width() - MD3Theme.PANEL_VIEWPORT_INSET * 2.0f,
                bounds.height() - CONTENT_TOP - MD3Theme.PANEL_VIEWPORT_INSET
        );

        List<Setting<?>> settings = selectedModule.getSettings().stream().filter(Setting::isAvailable).toList();

        if (settings.isEmpty()) {
            renderBatch.render(PanelUiTree.build(scope -> scope.layer(10, layer ->
                    layer.text("This HUD has no settings", titleX, viewport.y(), SUBTITLE_SCALE, MD3Theme.TEXT_MUTED))));
            flushChrome();
            clearContentState(true);
            return;
        }

        float contentHeight = settingList.getContentHeight(settings);
        maxScroll = Math.max(0.0f, contentHeight - viewport.height());
        scroll = Mth.clamp(scroll, 0.0f, maxScroll);
        boolean hasScrollBar = maxScroll > 0.0f;
        float rowWidth = hasScrollBar ? viewport.width() - ScrollBarUtils.TOTAL_WIDTH : viewport.width();
        boolean popupConsumesHover = settingList.isPopupHovered(mouseX, mouseY);
        int effectiveMouseX = popupConsumesHover ? Integer.MIN_VALUE : mouseX;
        int effectiveMouseY = popupConsumesHover ? Integer.MIN_VALUE : mouseY;

        PanelUiTree settingsTree = PanelUiTree.build(scope -> scope.viewport(contentBuffer, viewport, guiHeight, scroll, maxScroll, contentHeight, content ->
                settingList.layoutRows(settings, viewport, scroll, rowWidth, content, textRenderer, effectiveMouseX, effectiveMouseY, (_, row, rowBounds) -> {
                    float hover = rowBounds.contains(effectiveMouseX, effectiveMouseY) ? 1.0f : 0.0f;
                    row.buildUi(content, graphics, textRenderer, rowBounds, hover, effectiveMouseX, effectiveMouseY, partialTick);
                })
        ));
        renderBatch.render(settingsTree);

        flushChrome();

        contentBuffer.flushAndClear();
    }

    public void renderPopups(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        settingList.getPopupHost().render(graphics, mouseX, mouseY, partialTick);
        settingList.getPopupHost().flush();
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        if (settingList.getPopupHost().mouseClicked(event, isDoubleClick)) {
            return true;
        }
        if (bounds == null || !bounds.contains(event.x(), event.y())) {
            settingList.clearFocus();
            return false;
        }
        if (event.button() != 0) {
            return true;
        }

        if (collapseButtonBounds != null && collapseButtonBounds.contains(event.x(), event.y())) {
            collapsed = !collapsed;
            scrollBarDrag.reset();
            popupHost.close();
            settingList.clearFocus();
            return true;
        }

        if (isHeaderDragHandle(event.x(), event.y())) {
            windowDragging = true;
            dragOffsetX = (float) (event.x() - bounds.x());
            dragOffsetY = (float) (event.y() - bounds.y());
            return true;
        }

        if (collapsed || selectedModule == null) {
            return true;
        }

        // Scrollbar drag
        if (viewport != null && maxScroll > 0) {
            if (scrollBarDrag.mouseClicked(event.x(), event.y(), viewport, scroll, maxScroll)) {
                float newScroll = scrollBarDrag.mouseDragged(event.y(), viewport, maxScroll);
                if (newScroll >= 0) {
                    scroll = Mth.clamp(newScroll, 0.0f, maxScroll);
                }
                return true;
            }
        }

        settingList.mouseClicked(event, isDoubleClick, bounds);
        return true;
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        if (windowDragging) {
            windowDragging = false;
            return true;
        }
        if (scrollBarDrag.mouseReleased()) {
            return true;
        }
        if (settingList.getPopupHost().mouseReleased(event)) {
            return true;
        }
        return settingList.mouseReleased(event);
    }

    public boolean mouseDragged(MouseButtonEvent event, double mouseX, double mouseY) {
        if (windowDragging) {
            panelX = (float) (event.x() - dragOffsetX);
            panelY = (float) (event.y() - dragOffsetY);
            float width = Math.clamp(lastScreenWidth - PANEL_MARGIN * 2.0f, 164.0f, PANEL_WIDTH);
            float height = computePanelHeight(lastScreenHeight);
            clampPanelPosition(lastScreenWidth, lastScreenHeight, width, height);
            return true;
        }
        if (scrollBarDrag.isDragging() && viewport != null) {
            float newScroll = scrollBarDrag.mouseDragged(mouseY, viewport, maxScroll);
            if (newScroll >= 0) {
                scroll = Mth.clamp(newScroll, 0.0f, maxScroll);
            }
            return true;
        }
        if (settingList.getPopupHost().mouseDragged(event, mouseX, mouseY)) {
            return true;
        }
        return settingList.mouseDragged(event, mouseX, mouseY);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (settingList.getPopupHost().mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        if (viewport != null && viewport.contains(mouseX, mouseY) && maxScroll > 0.0f) {
            scroll = Mth.clamp(scroll - (float) scrollY * 20.0f, 0.0f, maxScroll);
            return true;
        }
        return bounds != null && bounds.contains(mouseX, mouseY);
    }

    public boolean keyPressed(KeyEvent event) {
        if (settingList.getPopupHost().keyPressed(event)) {
            return true;
        }
        return settingList.keyPressed(event);
    }

    public boolean charTyped(CharacterEvent event) {
        if (settingList.getPopupHost().charTyped(event)) {
            return true;
        }
        return settingList.charTyped(event);
    }

    public void clearFocus() {
        settingList.clearFocus();
    }

    private PanelLayout.Rect computeBounds(int screenWidth, int screenHeight) {
        float width = Math.clamp(screenWidth - PANEL_MARGIN * 2.0f, 164.0f, PANEL_WIDTH);
        float height = computePanelHeight(screenHeight);

        if (Float.isNaN(panelX) || Float.isNaN(panelY)) {
            panelX = screenWidth - width - PANEL_MARGIN;
            panelY = PANEL_MARGIN;
        }

        clampPanelPosition(screenWidth, screenHeight, width, height);
        return new PanelLayout.Rect(panelX, panelY, width, height);
    }

    private float computePanelHeight(int screenHeight) {
        if (collapsed) {
            return PANEL_COLLAPSED_HEIGHT;
        }
        float maxExpandedHeight = Math.max(PANEL_MIN_HEIGHT, screenHeight - PANEL_MARGIN * 2.0f);
        float preferredHeight = Math.min(PANEL_EXPANDED_MAX_HEIGHT, screenHeight * PANEL_EXPANDED_MAX_SCREEN_RATIO);
        return Mth.clamp(preferredHeight, PANEL_MIN_HEIGHT, maxExpandedHeight);
    }

    private void clampPanelPosition(int screenWidth, int screenHeight, float width, float height) {
        float minX = PANEL_MARGIN;
        float minY = PANEL_MARGIN;
        float maxX = Math.max(minX, screenWidth - width - PANEL_MARGIN);
        float maxY = Math.max(minY, screenHeight - height - PANEL_MARGIN);
        panelX = Mth.clamp(panelX, minX, maxX);
        panelY = Mth.clamp(panelY, minY, maxY);
    }

    private boolean isHeaderDragHandle(double mouseX, double mouseY) {
        if (headerBounds == null || !headerBounds.contains(mouseX, mouseY)) {
            return false;
        }
        return collapseButtonBounds == null || !collapseButtonBounds.contains(mouseX, mouseY);
    }

    private void flushChrome() {
        renderBatch.flushAndClear();
    }

    private void clearContentState(boolean resetSelectionState) {
        viewport = null;
        maxScroll = 0.0f;
        scrollBarDrag.reset();
        popupHost.close();
        settingList.clearAll();
        contentBuffer.clear();
        if (resetSelectionState) {
            scroll = 0.0f;
            selectedModuleName = "";
        }
    }

}
