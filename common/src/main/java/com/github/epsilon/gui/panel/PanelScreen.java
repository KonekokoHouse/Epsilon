package com.github.epsilon.gui.panel;

import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.renderers.*;
import com.github.epsilon.gui.dsl.PanelRenderBatch;
import com.github.epsilon.gui.dsl.PanelUiTree;
import com.github.epsilon.gui.panel.input.PanelInputRouter;
import com.github.epsilon.gui.panel.panel.CategoryRailPanel;
import com.github.epsilon.gui.panel.panel.ClientSettingPanel;
import com.github.epsilon.gui.panel.panel.ModuleDetailPanel;
import com.github.epsilon.gui.panel.panel.ModuleListPanel;
import com.github.epsilon.gui.panel.popup.PanelPopupHost;
import com.github.epsilon.gui.panel.utils.IMEFocusHelper;
import com.github.epsilon.holders.TranslateHolder;
import com.github.epsilon.modules.impl.ClientSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.IMEPreeditOverlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.network.chat.Component;

/**
 * 面板 UI 的主屏幕宿主。
 * <p>
 * 它负责维护全局状态、调度各子面板的 extract 阶段、统一 flush renderer，
 * 并将输入事件路由到 rail、模块列表、详情面板、客户端设置面板和弹窗宿主。
 */
public class PanelScreen extends Screen {

    public static final PanelScreen INSTANCE = new PanelScreen();

    private final PanelState state = new PanelState();
    private final PanelDirtyState dirtyState = new PanelDirtyState();
    private final TextRenderer textRenderer = TextRenderer.create();
    private final RectRenderer rectRenderer = RectRenderer.create();
    private final RoundRectRenderer roundRectRenderer = RoundRectRenderer.create();
    private final RoundRectOutlineRenderer roundRectOutlineRenderer = RoundRectOutlineRenderer.create();
    private final ShadowRenderer shadowRenderer = ShadowRenderer.create();
    private final PanelRenderBatch renderBatch = new PanelRenderBatch(shadowRenderer, roundRectRenderer, roundRectOutlineRenderer, rectRenderer, textRenderer);
    private final PanelPopupHost popupHost = new PanelPopupHost();
    private final PanelInputRouter inputRouter = new PanelInputRouter();
    private final CategoryRailPanel categoryRailPanel = new CategoryRailPanel(state, rectRenderer, roundRectRenderer, textRenderer);
    private final ModuleListPanel moduleListPanel = new ModuleListPanel(state, roundRectRenderer, rectRenderer, shadowRenderer, textRenderer);
    private final ModuleDetailPanel moduleDetailPanel = new ModuleDetailPanel(state, roundRectRenderer, rectRenderer, shadowRenderer, textRenderer, popupHost);
    private final ClientSettingPanel clientSettingPanel = new ClientSettingPanel(state, roundRectRenderer, rectRenderer, shadowRenderer, textRenderer, popupHost);
    private int lastWidth = -1;
    private int lastHeight = -1;
    private String lastSelectedCategory = "";
    private String lastSelectedModule = "";
    private String lastSearchQuery = "";
    private ClientSetting.ModuleSort lastModuleSort;
    private boolean lastSidebarExpanded;
    private boolean lastClientSettingMode;
    private long lastI18nRevision = Long.MIN_VALUE;

    private IMEPreeditOverlay preeditOverlay;

    private LuminRenderSystem.LuminRenderTarget renderTarget;

    private PanelScreen() {
        super(Component.literal("PanelGui"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * 提取面板当前帧的渲染状态。
     * <p>
     * 该方法会计算布局、推动动画、让各个子面板把 UI 编译进共享批次，
     * 最后在统一的 render 提交阶段执行 flush。
     */
    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {

        final var window = minecraft.getWindow();
        if (renderTarget == null) {
            renderTarget = LuminRenderSystem.LuminRenderTarget.create("click-gui", window.getWidth(), window.getHeight());
        }
        renderTarget.clear();
        renderTarget.resize(window.getWidth(), window.getHeight());

        LuminRenderSystem.setActiveTarget(renderTarget);

        String currentCategory = state.getSelectedCategory().name();
        String currentModule = state.getSelectedModule() == null ? "" : state.getSelectedModule().getName();
        String currentQuery = state.getSearchQuery();
        ClientSetting.ModuleSort currentModuleSort = ClientSetting.INSTANCE.moduleSort.getValue();
        boolean sidebarExpanded = state.isSidebarExpanded();
        boolean clientSettingMode = state.isClientSettingMode();
        long currentI18nRevision = TranslateHolder.INSTANCE.getRevision();
        if (!lastSelectedCategory.equals(currentCategory)
                || !lastSelectedModule.equals(currentModule)
                || !lastSearchQuery.equals(currentQuery)
                || lastModuleSort != currentModuleSort
                || lastSidebarExpanded != sidebarExpanded
                || lastClientSettingMode != clientSettingMode
                || lastI18nRevision != currentI18nRevision) {
            dirtyState.markAllDirty();
            lastSelectedCategory = currentCategory;
            lastSelectedModule = currentModule;
            lastSearchQuery = currentQuery;
            lastModuleSort = currentModuleSort;
            lastSidebarExpanded = sidebarExpanded;
            lastClientSettingMode = clientSettingMode;
            lastI18nRevision = currentI18nRevision;
        }

        if (categoryRailPanel.hasActiveAnimations()
                || moduleListPanel.hasActiveAnimations()
                || moduleDetailPanel.hasActiveAnimations()
                || clientSettingPanel.hasActiveAnimations()) {
            dirtyState.markAllDirty();
        }

        if (width != lastWidth || height != lastHeight) {
            dirtyState.markLayoutDirty();
            lastWidth = width;
            lastHeight = height;
        }

        if (dirtyState.consumeModuleListDirty()) {
            moduleListPanel.markDirty();
        }
        if (dirtyState.consumeDetailDirty()) {
            moduleDetailPanel.markDirty();
        }
        if (dirtyState.consumeClientSettingDirty()) {
            clientSettingPanel.markDirty();
        }

        MD3Theme.syncFromSettings();
        float railWidth = categoryRailPanel.getAnimatedWidth();
        PanelLayout.Layout layout = PanelLayout.compute(LuminRenderSystem.getScaledWidthInt(), LuminRenderSystem.getScaledHeightInt(), railWidth);
        popupHost.setOverlayBounds(layout.panel());

        drawChrome(layout);
        int epsilonMouseX = LuminRenderSystem.toEpsilonMouseX(mouseX);
        int epsilonMouseY = LuminRenderSystem.toEpsilonMouseY(mouseY);
        categoryRailPanel.render(guiGraphics, layout.rail(), epsilonMouseX, epsilonMouseY, partialTick);
        if (state.isClientSettingMode()) {
            PanelLayout.Rect clientSettingsBounds = new PanelLayout.Rect(
                    layout.modules().x(), layout.modules().y(),
                    layout.detail().right() - layout.modules().x(),
                    layout.modules().height()
            );
            clientSettingPanel.render(guiGraphics, clientSettingsBounds, epsilonMouseX, epsilonMouseY, partialTick);
        } else {
            moduleListPanel.render(guiGraphics, layout.modules(), epsilonMouseX, epsilonMouseY, partialTick);
            moduleDetailPanel.render(guiGraphics, layout.detail(), epsilonMouseX, epsilonMouseY, partialTick);
        }

        popupHost.render(guiGraphics, epsilonMouseX, epsilonMouseY, partialTick);

        flushQueuedRenderers();

        LuminRenderSystem.setActiveTarget(null);

        if (preeditOverlay != null) {
            this.preeditOverlay.updateInputPosition((int) IMEFocusHelper.activeCursorX, (int) IMEFocusHelper.activeCursorY);
            guiGraphics.setPreeditOverlay(this.preeditOverlay);
        }
        guiGraphics.blit(renderTarget.getIdentifier(), 0, 0, window.getGuiScaledWidth(), window.getGuiScaledHeight(), 0, 1, 1, 0);
    }

    private void drawChrome(PanelLayout.Layout layout) {
        PanelUiTree tree = PanelUiTree.build(scope -> {
            scope.shadow(layout.panel().x(), layout.panel().y(), layout.panel().width(), layout.panel().height(),
                    MD3Theme.PANEL_RADIUS, 18.0f, MD3Theme.withAlpha(MD3Theme.SHADOW, MD3Theme.PANEL_SHADOW_ALPHA));
            scope.roundRect(layout.panel().x(), layout.panel().y(), layout.panel().width(), layout.panel().height(),
                    MD3Theme.PANEL_RADIUS, MD3Theme.SURFACE);
            scope.roundRect(layout.rail().x(), layout.rail().y(), layout.rail().width(), layout.rail().height(),
                    MD3Theme.SECTION_RADIUS, MD3Theme.SURFACE_DIM);
            if (state.isClientSettingMode()) {
                float csX = layout.modules().x();
                float csY = layout.modules().y();
                float csW = layout.detail().right() - layout.modules().x();
                float csH = layout.modules().height();
                scope.roundRect(csX, csY, csW, csH, MD3Theme.SECTION_RADIUS, MD3Theme.SURFACE_DIM);
            } else {
                scope.roundRect(layout.modules().x(), layout.modules().y(), layout.modules().width(), layout.modules().height(),
                        MD3Theme.SECTION_RADIUS, MD3Theme.SURFACE_DIM);
                scope.roundRect(layout.detail().x(), layout.detail().y(), layout.detail().width(), layout.detail().height(),
                        MD3Theme.SECTION_RADIUS, MD3Theme.SURFACE_DIM);
            }
        });
        renderBatch.render(tree, -20);
    }

    private void flushQueuedRenderers() {
        renderBatch.flushAndClear();
        if (state.isClientSettingMode()) {
            clientSettingPanel.flushContent();
        } else {
            moduleListPanel.flushContent();
            moduleDetailPanel.flushContent();
        }
        categoryRailPanel.flushClippedText();
        popupHost.flush();
    }


    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        MouseButtonEvent epsilonEvent = LuminRenderSystem.toEpsilonMouseEvent(event);
        double mouseX = epsilonEvent.x();
        double mouseY = epsilonEvent.y();
        if (event.button() != 0) {
            if (state.getListeningKeyBindModule() != null && moduleDetailPanel.mouseClicked(epsilonEvent, isDoubleClick)) {
                dirtyState.markAllDirty();
                return true;
            }
            if (state.getListeningKeybindSetting() != null) {
                boolean handledListening = state.isClientSettingMode() ? clientSettingPanel.mouseClicked(epsilonEvent, isDoubleClick) : moduleDetailPanel.mouseClicked(epsilonEvent, isDoubleClick);
                if (handledListening) {
                    dirtyState.markAllDirty();
                    return true;
                }
            }
            return super.mouseClicked(epsilonEvent, isDoubleClick);
        }

        if (popupHost.getActivePopup() != null) {
            return inputRouter.routeMouseClicked(epsilonEvent, isDoubleClick, popupHost, moduleDetailPanel, moduleListPanel, categoryRailPanel, clientSettingPanel, state.isClientSettingMode())
                    || super.mouseClicked(epsilonEvent, isDoubleClick);
        }

        PanelLayout.Layout layout = PanelLayout.compute(LuminRenderSystem.getScaledWidthInt(), LuminRenderSystem.getScaledHeightInt(), categoryRailPanel.getAnimatedWidth());
        if (!layout.panel().contains(mouseX, mouseY)) {
            if (ClientSetting.INSTANCE.closeOnOutside.getValue()) minecraft.setScreen(null);
            return true;
        }
        if (!state.isClientSettingMode()) {
            moduleListPanel.handleGlobalClick(mouseX, mouseY);
        }
        boolean handled = inputRouter.routeMouseClicked(epsilonEvent, isDoubleClick, popupHost, moduleDetailPanel, moduleListPanel, categoryRailPanel, clientSettingPanel, state.isClientSettingMode());
        if (handled) {
            dirtyState.markAllDirty();
        }
        return handled || super.mouseClicked(epsilonEvent, isDoubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double epsilonMouseX = LuminRenderSystem.toEpsilonMouseX(mouseX);
        double epsilonMouseY = LuminRenderSystem.toEpsilonMouseY(mouseY);
        if (popupHost.mouseScrolled(epsilonMouseX, epsilonMouseY, scrollX, scrollY)) {
            dirtyState.markAllDirty();
            return true;
        }
        if (state.isClientSettingMode()) {
            if (clientSettingPanel.mouseScrolled(epsilonMouseX, epsilonMouseY, scrollX, scrollY)) {
                dirtyState.markClientSettingDirty();
                return true;
            }
        } else {
            if (moduleListPanel.mouseScrolled(epsilonMouseX, epsilonMouseY, scrollX, scrollY)) {
                dirtyState.markModuleListDirty();
                return true;
            }
            if (moduleDetailPanel.mouseScrolled(epsilonMouseX, epsilonMouseY, scrollX, scrollY)) {
                dirtyState.markDetailDirty();
                return true;
            }
        }
        return super.mouseScrolled(epsilonMouseX, epsilonMouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        MouseButtonEvent epsilonEvent = LuminRenderSystem.toEpsilonMouseEvent(event);
        if (inputRouter.routeMouseReleased(epsilonEvent, popupHost, moduleDetailPanel, moduleListPanel, clientSettingPanel, state.isClientSettingMode())) {
            dirtyState.markAllDirty();
            return true;
        }
        return super.mouseReleased(epsilonEvent);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double mouseX, double mouseY) {
        MouseButtonEvent epsilonEvent = LuminRenderSystem.toEpsilonMouseEvent(event);
        double epsilonMouseX = LuminRenderSystem.toEpsilonMouseX(mouseX);
        double epsilonMouseY = LuminRenderSystem.toEpsilonMouseY(mouseY);
        if (inputRouter.routeMouseDragged(epsilonEvent, epsilonMouseX, epsilonMouseY, popupHost, moduleDetailPanel, moduleListPanel, clientSettingPanel, state.isClientSettingMode())) {
            dirtyState.markAllDirty();
            return true;
        }
        return super.mouseDragged(epsilonEvent, epsilonMouseX, epsilonMouseY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (inputRouter.routeKeyPressed(event, popupHost, moduleDetailPanel, moduleListPanel, clientSettingPanel, state.isClientSettingMode())) {
            dirtyState.markAllDirty();
            return true;
        }
        if (event.key() == 256) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (inputRouter.routeCharTyped(event, popupHost, moduleDetailPanel, moduleListPanel, clientSettingPanel, state.isClientSettingMode())) {
            dirtyState.markAllDirty();
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean preeditUpdated(PreeditEvent event) {
        this.preeditOverlay = event != null ? new IMEPreeditOverlay(event, this.font, 10) : null;
        return true;
    }

    @Override
    public void onClose() {
        IMEFocusHelper.deactivate();
        super.onClose();
    }

    /**
     * 返回当前面板使用的离屏渲染目标。
     *
     * @return 当前渲染目标；首次渲染前可能为 {@code null}
     */
    public LuminRenderSystem.LuminRenderTarget getRenderTarget() {
        return renderTarget;
    }
}
