package com.github.epsilon.gui.panel.panel;

import com.github.epsilon.assets.i18n.EpsilonTranslateComponent;
import com.github.epsilon.assets.i18n.TranslateComponent;
import com.github.epsilon.graphics.renderers.RectRenderer;
import com.github.epsilon.graphics.renderers.RoundRectRenderer;
import com.github.epsilon.graphics.renderers.ShadowRenderer;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.gui.dsl.PanelUiCompiler;
import com.github.epsilon.gui.dsl.PanelUiTree;
import com.github.epsilon.gui.panel.MD3Theme;
import com.github.epsilon.gui.panel.PanelLayout;
import com.github.epsilon.gui.panel.PanelState;
import com.github.epsilon.gui.panel.panel.clientsettings.*;
import com.github.epsilon.gui.panel.popup.PanelPopupHost;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.EnumMap;
import java.util.List;

public class ClientSettingPanel {

    private static final TranslateComponent titleComponent = EpsilonTranslateComponent.create("gui", "clientsettings");
    private static final TranslateComponent generalTabComponent = EpsilonTranslateComponent.create("gui", "tab.general");
    private static final TranslateComponent friendTabComponent = EpsilonTranslateComponent.create("gui", "tab.friend");
    private static final TranslateComponent configTabComponent = EpsilonTranslateComponent.create("gui", "tab.config");
    private static final TranslateComponent addonTabComponent = EpsilonTranslateComponent.create("gui", "tab.addon");

    private static final List<TabDefinition> TABS = List.of(
            new TabDefinition(PanelState.ClientSettingTab.GENERAL, generalTabComponent),
            new TabDefinition(PanelState.ClientSettingTab.FRIEND, friendTabComponent),
            new TabDefinition(PanelState.ClientSettingTab.CONFIG, configTabComponent),
            new TabDefinition(PanelState.ClientSettingTab.ADDON, addonTabComponent)
    );

    private static final float TAB_BAR_HEIGHT = 26.0f;
    private static final float TAB_INDICATOR_HEIGHT = 2.5f;

    protected final PanelState state;
    private final RoundRectRenderer roundRectRenderer;
    private final RectRenderer rectRenderer;
    private final TextRenderer textRenderer;
    private final EnumMap<PanelState.ClientSettingTab, ClientSettingTabView> tabViews = new EnumMap<>(PanelState.ClientSettingTab.class);
    private final EnumMap<PanelState.ClientSettingTab, Animation> tabHoverAnimations = new EnumMap<>(PanelState.ClientSettingTab.class);
    private final Animation tabIndicatorAnimation = new Animation(Easing.EASE_OUT_CUBIC, 200L);

    private PanelLayout.Rect bounds;

    public ClientSettingPanel(PanelState state, RoundRectRenderer roundRectRenderer, RectRenderer rectRenderer, ShadowRenderer shadowRenderer, TextRenderer textRenderer, PanelPopupHost popupHost) {
        this.state = state;
        this.roundRectRenderer = roundRectRenderer;
        this.rectRenderer = rectRenderer;
        this.textRenderer = textRenderer;

        tabViews.put(PanelState.ClientSettingTab.GENERAL, new GeneralClientSettingTab(state, roundRectRenderer, rectRenderer, textRenderer, popupHost));
        tabViews.put(PanelState.ClientSettingTab.FRIEND, new FriendClientSettingTab(state, roundRectRenderer, rectRenderer, textRenderer));
        tabViews.put(PanelState.ClientSettingTab.CONFIG, new ConfigClientSettingTab(state, roundRectRenderer, rectRenderer, textRenderer, popupHost));
        tabViews.put(PanelState.ClientSettingTab.ADDON, new AddonClientSettingTab(state, roundRectRenderer, rectRenderer, textRenderer, popupHost));

        for (PanelState.ClientSettingTab tab : PanelState.ClientSettingTab.values()) {
            Animation animation = new Animation(Easing.EASE_OUT_CUBIC, 120L);
            animation.setStartValue(0.0f);
            tabHoverAnimations.put(tab, animation);
        }
        tabIndicatorAnimation.setStartValue(getTabIndex(state.getClientSettingTab()));
    }

    public void render(GuiGraphicsExtractor guiGraphics, PanelLayout.Rect bounds, int mouseX, int mouseY, float partialTick) {
        this.bounds = bounds;

        ClientSettingTabView activeTab = getCurrentTabView();
        boolean popupConsumesHover = activeTab.consumesHover(mouseX, mouseY);
        int effectiveMouseX = popupConsumesHover ? Integer.MIN_VALUE : mouseX;
        int effectiveMouseY = popupConsumesHover ? Integer.MIN_VALUE : mouseY;

        PanelUiTree tree = PanelUiTree.build(scope -> {
            scope.text(titleComponent.getTranslatedName(), bounds.x() + MD3Theme.PANEL_TITLE_INSET, bounds.y() + 10.0f, 0.78f, MD3Theme.TEXT_PRIMARY);
            buildTabs(scope, effectiveMouseX, effectiveMouseY);
        });
        PanelUiCompiler.render(tree, null, roundRectRenderer, rectRenderer, textRenderer);

        activeTab.render(guiGraphics, getContentBounds(), effectiveMouseX, effectiveMouseY, partialTick);
    }

    public void flushContent() {
        getCurrentTabView().flushContent();
    }

    public void markDirty() {
        tabViews.values().forEach(ClientSettingTabView::markDirty);
    }

    public boolean hasActiveAnimations() {
        boolean tabsAnimating = !tabIndicatorAnimation.isFinished()
                || tabHoverAnimations.values().stream().anyMatch(animation -> !animation.isFinished());
        return tabsAnimating || getCurrentTabView().hasActiveAnimations();
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        if (bounds == null || event.button() != 0) {
            return false;
        }

        if (state.getListeningKeybindSetting() != null) {
            state.setListeningKeybindSetting(null);
            markDirty();
        }

        PanelLayout.Rect tabBar = getTabBarRect();
        if (tabBar.contains(event.x(), event.y())) {
            switchToTab(resolveClickedTab(event.x(), tabBar));
            return true;
        }

        return getCurrentTabView().mouseClicked(event, isDoubleClick);
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        return getCurrentTabView().mouseReleased(event);
    }

    public boolean mouseDragged(MouseButtonEvent event, double mouseX, double mouseY) {
        return getCurrentTabView().mouseDragged(event, mouseX, mouseY);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (bounds == null) {
            return false;
        }
        return getCurrentTabView().mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    public boolean keyPressed(KeyEvent event) {
        return getCurrentTabView().keyPressed(event);
    }

    public boolean charTyped(CharacterEvent event) {
        return getCurrentTabView().charTyped(event);
    }

    private void buildTabs(PanelUiTree.Scope scope, int mouseX, int mouseY) {
        PanelLayout.Rect tabBar = getTabBarRect();
        float segmentWidth = tabBar.width() / TABS.size();
        float labelScale = 0.62f;
        float textHeight = textRenderer.getHeight(labelScale);
        int activeIndex = getTabIndex(state.getClientSettingTab());
        float indicatorProgress = scope.animate(tabIndicatorAnimation, activeIndex);

        for (int index = 0; index < TABS.size(); index++) {
            TabDefinition tab = TABS.get(index);
            PanelLayout.Rect tabBounds = new PanelLayout.Rect(tabBar.x() + segmentWidth * index, tabBar.y(), segmentWidth, tabBar.height());
            boolean active = tab.tab() == state.getClientSettingTab();

            Animation hoverAnimation = tabHoverAnimations.get(tab.tab());
            float hover = scope.animate(hoverAnimation, tabBounds.contains(mouseX, mouseY));
            if (hover > 0.01f) {
                scope.roundRect(tabBounds.x(), tabBounds.y(), tabBounds.width(), tabBounds.height(), 6.0f,
                        MD3Theme.stateLayer(MD3Theme.TEXT_PRIMARY, hover, 8));
            }

            String label = tab.component().getTranslatedName();
            float textWidth = textRenderer.getWidth(label, labelScale);
            float textX = tabBounds.x() + (tabBounds.width() - textWidth) / 2.0f;
            float textY = tabBounds.y() + (tabBounds.height() - TAB_INDICATOR_HEIGHT - textHeight) / 2.0f;
            scope.text(label, textX, textY, labelScale, active ? MD3Theme.PRIMARY : MD3Theme.TEXT_MUTED);
        }

        float dividerY = tabBar.bottom() - 1.0f;
        scope.rect(tabBar.x(), dividerY, tabBar.width(), 1.0f, MD3Theme.withAlpha(MD3Theme.OUTLINE, 40));

        float indicatorWidth = Math.min(56.0f, segmentWidth - 24.0f);
        float indicatorX = tabBar.x() + indicatorProgress * segmentWidth + (segmentWidth - indicatorWidth) / 2.0f;
        float indicatorY = tabBar.bottom() - TAB_INDICATOR_HEIGHT;
        scope.roundRect(indicatorX, indicatorY, indicatorWidth, TAB_INDICATOR_HEIGHT,
                TAB_INDICATOR_HEIGHT / 2.0f, MD3Theme.PRIMARY);
    }

    private void switchToTab(PanelState.ClientSettingTab targetTab) {
        if (targetTab == state.getClientSettingTab()) {
            return;
        }
        getCurrentTabView().onDeactivated();
        state.setClientSettingTab(targetTab);
        getCurrentTabView().onActivated();
        markDirty();
    }

    private PanelState.ClientSettingTab resolveClickedTab(double mouseX, PanelLayout.Rect tabBar) {
        float segmentWidth = tabBar.width() / TABS.size();
        int index = Math.clamp((int) ((mouseX - tabBar.x()) / segmentWidth), 0, TABS.size() - 1);
        return TABS.get(index).tab();
    }

    private ClientSettingTabView getCurrentTabView() {
        return tabViews.get(state.getClientSettingTab());
    }


    private int getTabIndex(PanelState.ClientSettingTab tab) {
        return switch (tab) {
            case GENERAL -> 0;
            case FRIEND -> 1;
            case CONFIG -> 2;
            case ADDON -> 3;
        };
    }

    private PanelLayout.Rect getTabBarRect() {
        return new PanelLayout.Rect(
                bounds.x() + MD3Theme.PANEL_VIEWPORT_INSET,
                bounds.y() + 28.0f,
                bounds.width() - MD3Theme.PANEL_VIEWPORT_INSET * 2.0f,
                TAB_BAR_HEIGHT
        );
    }

    private PanelLayout.Rect getContentBounds() {
        float tabBottom = bounds.y() + 28.0f + TAB_BAR_HEIGHT + 4.0f;
        return new PanelLayout.Rect(
                bounds.x() + MD3Theme.PANEL_VIEWPORT_INSET,
                tabBottom,
                bounds.width() - MD3Theme.PANEL_VIEWPORT_INSET * 2.0f,
                bounds.bottom() - tabBottom - 6.0f
        );
    }

    private record TabDefinition(PanelState.ClientSettingTab tab, TranslateComponent component) {
    }

}
