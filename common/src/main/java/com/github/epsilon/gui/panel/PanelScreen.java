package com.github.epsilon.gui.panel;

import com.github.epsilon.gui.utils.UiCoordinateMapper;
import com.github.slmpc.lumingraphics.mc.v2612.runtime.MinecraftUiRuntime2612;
import com.github.slmpc.lumingraphics.ui.geometry.UiRect;
import com.github.slmpc.lumingraphics.ui.tree.UiTree;
import com.github.slmpc.lumingraphics.ui.scene.UiLayer;
import com.github.slmpc.lumingraphics.ui.scene.UiScene;
import com.github.slmpc.lumingraphics.ui.text.UiTextMetrics;
import com.github.epsilon.gui.panel.input.PanelInputRouter;
import com.github.epsilon.gui.panel.popup.PanelPopupHost;
import com.github.epsilon.gui.panel.utils.IMEFocusHelper;
import com.github.epsilon.gui.panel.view.CategoryRailPanel;
import com.github.epsilon.gui.panel.view.ClientSettingPanel;
import com.github.epsilon.gui.panel.view.ModuleDetailPanel;
import com.github.epsilon.gui.panel.view.ModuleListPanel;
import com.github.epsilon.gui.theme.EpsilonUiTheme;
import com.github.epsilon.gui.theme.MD3Theme;
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
 * 它负责维护全局状态，并在 Minecraft UI runtime 的统一 scene 帧中调度各子面板，
 * 并将输入事件路由到 rail、模块列表、详情面板、客户端设置面板和弹窗宿主。
 */
public class PanelScreen extends Screen {

    public static final PanelScreen INSTANCE = new PanelScreen();

    private final PanelState state = new PanelState();
    private final PanelDirtyState dirtyState = new PanelDirtyState();
    private UiTextMetrics textMetrics;
    private UiScene scene;
    private MinecraftUiRuntime2612 sceneRuntime;
    private final PanelPopupHost popupHost = new PanelPopupHost();
    private final PanelInputRouter inputRouter = new PanelInputRouter();
    private CategoryRailPanel categoryRailPanel;
    private ModuleListPanel moduleListPanel;
    private ModuleDetailPanel moduleDetailPanel;
    private ClientSettingPanel clientSettingPanel;
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
     * 最后由 runtime 统一提交 scene。
     */
    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {

        MinecraftUiRuntime2612 runtime = MinecraftUiRuntime2612.current();
        ClientSetting.INSTANCE.configureMinecraftFonts(runtime);
        int epsilonMouseX = UiCoordinateMapper.toProjectionX(mouseX);
        int epsilonMouseY = UiCoordinateMapper.toProjectionY(mouseY);
        if (scene == null || sceneRuntime != runtime) {
            releaseScene();
            scene = runtime.createScene(EpsilonUiTheme.lumin());
            sceneRuntime = runtime;
            textMetrics = runtime.textMetrics();
            categoryRailPanel = new CategoryRailPanel(state, textMetrics);
            moduleListPanel = new ModuleListPanel(state, textMetrics);
            moduleDetailPanel = new ModuleDetailPanel(state, textMetrics, popupHost);
            clientSettingPanel = new ClientSettingPanel(state, textMetrics, popupHost);
        }

        runtime.render(scene, activeScene -> extractPanelFrame(guiGraphics, activeScene,
                epsilonMouseX, epsilonMouseY, partialTick));

        if (preeditOverlay != null) {
            this.preeditOverlay.updateInputPosition(
                    (int) UiCoordinateMapper.toMinecraftX(IMEFocusHelper.activeCursorX),
                    (int) UiCoordinateMapper.toMinecraftY(IMEFocusHelper.activeCursorY));
            guiGraphics.setPreeditOverlay(this.preeditOverlay);
        }
        popupHost.extractOverlay(guiGraphics, epsilonMouseX, epsilonMouseY, partialTick);
    }

    private void extractPanelFrame(GuiGraphicsExtractor guiGraphics, UiScene scene, int mouseX, int mouseY, float partialTick) {

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

        int uiWidth = UiCoordinateMapper.getProjectionWidthInt();
        int uiHeight = UiCoordinateMapper.getProjectionHeightInt();
        if (uiWidth != lastWidth || uiHeight != lastHeight) {
            dirtyState.markLayoutDirty();
            lastWidth = uiWidth;
            lastHeight = uiHeight;
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

        float railWidth = categoryRailPanel.getAnimatedWidth();
        PanelLayout.Layout layout = PanelLayout.compute(uiWidth, uiHeight, railWidth);
        popupHost.setOverlayBounds(layout.panel());

        drawChrome(layout);
        int epsilonMouseX = mouseX;
        int epsilonMouseY = mouseY;
        boolean popupActive = popupHost.getActivePopup() != null;
        int panelMouseX = popupActive ? Integer.MIN_VALUE : epsilonMouseX;
        int panelMouseY = popupActive ? Integer.MIN_VALUE : epsilonMouseY;
        categoryRailPanel.render(guiGraphics, scene.batch(UiLayer.CONTENT, -20), layout.rail(), panelMouseX, panelMouseY, partialTick);
        if (state.isClientSettingMode()) {
            UiRect clientSettingsBounds = new UiRect(
                    layout.modules().x(), layout.modules().y(),
                    layout.detail().right() - layout.modules().x(),
                    layout.modules().height()
            );
            clientSettingPanel.render(guiGraphics, scene.batch(UiLayer.CONTENT, 10), clientSettingsBounds, panelMouseX, panelMouseY, partialTick);
        } else {
            moduleListPanel.render(guiGraphics, scene.batch(UiLayer.CONTENT, 0), layout.modules(), panelMouseX, panelMouseY, partialTick);
            moduleDetailPanel.render(guiGraphics, scene.batch(UiLayer.CONTENT, 20), layout.detail(), panelMouseX, panelMouseY, partialTick);
        }

        renderPopup(guiGraphics, epsilonMouseX, epsilonMouseY, partialTick);
    }

    private void drawChrome(PanelLayout.Layout layout) {
        UiTree tree = UiTree.build(scope -> {
            scope.pushAbsolute(layout.panel(), panel -> {
                panel.shadow(0.0f, 0.0f, layout.panel().width(), layout.panel().height(),
                        MD3Theme.PANEL_RADIUS, MD3Theme.PANEL_SHADOW_BLUR,
                        MD3Theme.withAlpha(MD3Theme.SHADOW, MD3Theme.PANEL_SHADOW_ALPHA));
                panel.roundRect(0.0f, 0.0f, layout.panel().width(), layout.panel().height(),
                        MD3Theme.PANEL_RADIUS, MD3Theme.SURFACE);
            });
            scope.pushAbsolute(layout.rail(), rail -> rail.roundRect(0.0f, 0.0f, layout.rail().width(), layout.rail().height(),
                    MD3Theme.SECTION_RADIUS, MD3Theme.SURFACE_DIM));
            if (state.isClientSettingMode()) {
                float csW = layout.detail().right() - layout.modules().x();
                float csH = layout.modules().height();
                scope.pushAbsolute(layout.modules().x(), layout.modules().y(), clientSettings ->
                        clientSettings.roundRect(0.0f, 0.0f, csW, csH, MD3Theme.SECTION_RADIUS, MD3Theme.SURFACE_DIM));
            } else {
                scope.pushAbsolute(layout.modules(), modules -> modules.roundRect(0.0f, 0.0f, layout.modules().width(), layout.modules().height(),
                        MD3Theme.SECTION_RADIUS, MD3Theme.SURFACE_DIM));
                scope.pushAbsolute(layout.detail(), detail -> detail.roundRect(0.0f, 0.0f, layout.detail().width(), layout.detail().height(),
                        MD3Theme.SECTION_RADIUS, MD3Theme.SURFACE_DIM));
            }
        });
        scene.submit(UiLayer.CHROME, -20, tree);
    }

    private void renderPopup(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (popupHost.getActivePopup() == null) {
            return;
        }
        popupHost.render(guiGraphics, scene.batch(UiLayer.POPUP), mouseX, mouseY, partialTick);
    }


    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        MouseButtonEvent epsilonEvent = UiCoordinateMapper.toProjectionEvent(event);
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

        PanelLayout.Layout layout = PanelLayout.compute(
                UiCoordinateMapper.getProjectionWidthInt(),
                UiCoordinateMapper.getProjectionHeightInt(),
                categoryRailPanel.getAnimatedWidth());
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

    private void releaseScene() {
        UiScene previous = scene;
        scene = null;
        sceneRuntime = null;
        textMetrics = null;
        if (previous != null) previous.close();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double epsilonMouseX = UiCoordinateMapper.toProjectionX(mouseX);
        double epsilonMouseY = UiCoordinateMapper.toProjectionY(mouseY);
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
        MouseButtonEvent epsilonEvent = UiCoordinateMapper.toProjectionEvent(event);
        if (inputRouter.routeMouseReleased(epsilonEvent, popupHost, moduleDetailPanel, moduleListPanel, clientSettingPanel, state.isClientSettingMode())) {
            dirtyState.markAllDirty();
            return true;
        }
        return super.mouseReleased(epsilonEvent);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        MouseButtonEvent epsilonEvent = UiCoordinateMapper.toProjectionEvent(event);
        double epsilonDeltaX = UiCoordinateMapper.toProjectionX(deltaX);
        double epsilonDeltaY = UiCoordinateMapper.toProjectionY(deltaY);
        if (inputRouter.routeMouseDragged(epsilonEvent, epsilonDeltaX, epsilonDeltaY,
                popupHost, moduleDetailPanel, moduleListPanel, clientSettingPanel, state.isClientSettingMode())) {
            dirtyState.markAllDirty();
            return true;
        }
        return super.mouseDragged(epsilonEvent, epsilonDeltaX, epsilonDeltaY);
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
        IMEFocusHelper.forceDeactivate();
        super.onClose();
    }

    @Override
    public void removed() {
        super.removed();
        popupHost.close();
        releaseScene();
        moduleListPanel.resetTransientState();
        moduleDetailPanel.resetTransientState();
        clientSettingPanel.resetTransientState();
        state.setListeningKeyBindModule(null);
        state.setListeningKeybindSetting(null);
        IMEFocusHelper.forceDeactivate();
        preeditOverlay = null;
    }

}
