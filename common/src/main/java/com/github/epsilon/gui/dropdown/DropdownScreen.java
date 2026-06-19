package com.github.epsilon.gui.dropdown;

import com.github.epsilon.assets.i18n.EpsilonTranslateComponent;
import com.github.epsilon.assets.i18n.TranslateComponent;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.gui.dropdown.component.*;
import com.github.epsilon.gui.dropdown.widget.DropdownTextField;
import com.github.epsilon.gui.panel.MD3Theme;
import com.github.epsilon.gui.panel.PanelLayout;
import com.github.epsilon.gui.panel.popup.BlockListSelectPopup;
import com.github.epsilon.gui.panel.popup.PanelPopupHost;
import com.github.epsilon.gui.panel.utils.IMEFocusHelper;
import com.github.epsilon.holders.ConfigHolder;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.impl.ClientSetting;
import com.github.epsilon.settings.impl.BlockListSetting;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.IMEPreeditOverlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DropdownScreen extends Screen {

    public static final DropdownScreen INSTANCE = new DropdownScreen();
    private static final TranslateComponent searchComponent = EpsilonTranslateComponent.create("gui", "search");
    private static final TranslateComponent searchHintComponent = EpsilonTranslateComponent.create("gui", "dropdown.hint.search");
    private static final TranslateComponent panelHintComponent = EpsilonTranslateComponent.create("gui", "dropdown.hint.panels");
    private static final TranslateComponent dragHintComponent = EpsilonTranslateComponent.create("gui", "dropdown.hint.drag");

    private final List<DropdownPanel> panels = new ArrayList<>();
    private final DropdownRenderer renderer = new DropdownRenderer();
    private final PanelPopupHost popupHost = new PanelPopupHost();
    private final Animation scrimAnim = new Animation(Easing.EASE_OUT_SINE, 200L);
    private final DropdownTextField searchField = new DropdownTextField(64);
    private final Set<String> visiblePanelIds = new HashSet<>();

    private LuminRenderSystem.LuminRenderTarget renderTarget;
    private IMEPreeditOverlay preeditOverlay;
    private boolean initialized;
    private int sessionId;
    private int renderFrameId;

    private DropdownScreen() {
        super(Component.literal("DropdownGui"));
    }

    @Override
    protected void init() {
        super.init();
        sessionId++;
        scrimAnim.setStartValue(0.0f);
        scrimAnim.run(0.0f);
        scrimAnim.run(1.0f);

        if (!initialized) {
            buildPanels();
            initialized = true;
        }

        for (DropdownPanel panel : panels) {
            panel.setMaxPanelHeight(resolveMaxPanelHeight(panel));
            panel.startIntro();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        final var window = minecraft.getWindow();
        if (renderTarget == null) {
            renderTarget = LuminRenderSystem.LuminRenderTarget.create("dropdown-gui", window.getWidth(), window.getHeight());
        }
        renderTarget.resize(window.getWidth(), window.getHeight());
        renderTarget.clear();
        LuminRenderSystem.setActiveTarget(renderTarget);

        int epsilonMouseX = LuminRenderSystem.toEpsilonMouseX(mouseX);
        int epsilonMouseY = LuminRenderSystem.toEpsilonMouseY(mouseY);
        drawGui(graphics, epsilonMouseX, epsilonMouseY, partialTick);

        LuminRenderSystem.setActiveTarget(null);
        if (preeditOverlay != null) {
            preeditOverlay.updateInputPosition((int) IMEFocusHelper.activeCursorX, (int) IMEFocusHelper.activeCursorY);
            graphics.setPreeditOverlay(preeditOverlay);
        }
        graphics.blit(renderTarget.getIdentifier(), 0, 0, window.getGuiScaledWidth(), window.getGuiScaledHeight(), 0, 1, 1, 0);
        popupHost.extractOverlay(graphics, epsilonMouseX, epsilonMouseY, partialTick);
    }

    private void drawGui(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        scrimAnim.run(1.0f);
        renderer.beginFrame();
        popupHost.setOverlayBounds(new PanelLayout.Rect(0.0f, 0.0f, LuminRenderSystem.getScaledWidth(), LuminRenderSystem.getScaledHeight()));
        updatePanelHeightLimits();
        updateVisiblePanelIds();
        beginPanelFrames();

        renderer.beginPass();
        Color scrim = DropdownTheme.scrim();
        float scrimAlpha = scrimAnim.getValue();
        renderer.rect().addRect(0, 0, LuminRenderSystem.getScaledWidth(), LuminRenderSystem.getScaledHeight(), new Color(scrim.getRed(), scrim.getGreen(), scrim.getBlue(), (int) (scrim.getAlpha() * scrimAlpha)));
        renderer.flush();

        float shadowPad = DropdownTheme.PANEL_SHADOW_BLUR + 4.0f;
        boolean popupHovered = popupHost.getActivePopup() != null && popupHost.getActivePopup().getBounds().contains(mouseX, mouseY);
        int backgroundMouseX = popupHovered ? Integer.MIN_VALUE : mouseX;
        int backgroundMouseY = popupHovered ? Integer.MIN_VALUE : mouseY;

        // 找出鼠标位置处最上层的可见 panel，被遮挡的 panel 不应响应悬浮
        DropdownPanel topmostHovered = null;
        if (!popupHovered) {
            for (int i = panels.size() - 1; i >= 0; i--) {
                DropdownPanel p = panels.get(i);
                if (!p.isVisible()) continue;
                float ph = p.getPanelHeight();
                if (mouseX >= p.getX() && mouseX <= p.getX() + p.getWidth()
                        && mouseY >= p.getY() && mouseY <= p.getY() + ph) {
                    topmostHovered = p;
                    break;
                }
            }
        }

        for (DropdownPanel panel : panels) {
            if (!panel.isVisible()) continue;
            float intro = panel.getIntroValue();
            if (intro < 0.001f) continue;

            float slideOffset = (1.0f - intro) * 10.0f;
            float origY = panel.getY();
            panel.setPosition(panel.getX(), origY - slideOffset);

            float panelH = panel.getPanelHeight();
            float revealedH = panelH * intro;

            renderer.beginPass();
            renderer.setScissor(
                    panel.getX() - shadowPad, panel.getY() - shadowPad,
                    panel.getWidth() + shadowPad * 2, revealedH + shadowPad * 2,
                    LuminRenderSystem.getScaledHeightInt());
            panel.drawBackground(renderer);
            renderer.flush();
            renderer.clearScissor();

            float clipY = panel.getContentClipY();
            float clipH = panel.getContentClipHeight();
            float revealedBottom = panel.getY() + revealedH;
            float actualClipH = Math.min(clipH, revealedBottom - clipY);
            if (actualClipH > 0.5f) {
                renderer.beginPass();
                renderer.setScissor(panel.getX(), clipY, panel.getWidth(), actualClipH, LuminRenderSystem.getScaledHeightInt());
                int hoverMouseX = panel == topmostHovered ? backgroundMouseX : -1;
                int hoverMouseY = panel == topmostHovered ? backgroundMouseY : -1;
                panel.drawContent(renderer, hoverMouseX, hoverMouseY);
                renderer.flush();
                renderer.clearScissor();
            }

            panel.setPosition(panel.getX(), origY);
        }

        drawSearch(backgroundMouseX, backgroundMouseY);
        renderer.endFrame();
        popupHost.render(graphics, mouseX, mouseY, partialTick);
        popupHost.flush();
    }

    private void drawSearch(int mouseX, int mouseY) {
        renderer.beginPass();
        float searchX = getSearchX();
        float searchY = getSearchY();
        searchField.draw(renderer, searchX, searchY, getSearchWidth(), getSearchHeight(), mouseX, mouseY, searchComponent.getTranslatedName(), 0.58f);
        drawHints();
        renderer.flush();
    }

    private void drawHints() {
        if (ClientSetting.INSTANCE.dropdownHints.getValue()) {
            float scale = 0.62f;
            float lineGap = 5.0f;
            float lineHeight = renderer.text().getLineHeight(scale);
            String[] hints = {
                    searchHintComponent.getTranslatedName(),
                    panelHintComponent.getTranslatedName(),
                    dragHintComponent.getTranslatedName()
            };
            float xRight = LuminRenderSystem.getScaledWidth() - DropdownTheme.PANEL_MARGIN_X;
            float y = LuminRenderSystem.getScaledHeight() - DropdownTheme.PANEL_MARGIN_Y - hints.length * lineHeight - (hints.length - 1) * lineGap;
            int alpha = (int) (255 * scrimAnim.getValue());
            if (alpha <= 0) {
                return;
            }
            Color color = MD3Theme.withAlpha(Color.WHITE, alpha);
            for (String hint : hints) {
                float x = xRight - renderer.text().getWidth(hint, scale);
                renderer.text().addText(hint, x, y, scale, color);
                y += lineHeight + lineGap;
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        MouseButtonEvent epsilonEvent = LuminRenderSystem.toEpsilonMouseEvent(event);
        double mx = epsilonEvent.x();
        double my = epsilonEvent.y();
        int button = epsilonEvent.button();

        if (popupHost.mouseClicked(epsilonEvent, isDoubleClick)) {
            ConfigHolder.INSTANCE.saveNow();
            return true;
        }

        if (button == 0 && searchField.focusIfContains(mx, my, getSearchX(), getSearchY(), getSearchWidth(), getSearchHeight())) {
            return true;
        } else if (button == 0 && searchField.isFocused()) {
            searchField.blur();
        }

        for (int i = panels.size() - 1; i >= 0; i--) {
            DropdownPanel panel = panels.get(i);
            if (!panel.isVisible()) continue;
            if (panel.mouseClicked(mx, my, button)) {
                if (i < panels.size() - 1) {
                    panels.remove(i);
                    panels.add(panel);
                }
                DropdownLayoutState.save(panels);
                return true;
            }
        }
        return super.mouseClicked(epsilonEvent, isDoubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        MouseButtonEvent epsilonEvent = LuminRenderSystem.toEpsilonMouseEvent(event);
        double mx = epsilonEvent.x();
        double my = epsilonEvent.y();
        int button = epsilonEvent.button();

        if (popupHost.mouseReleased(epsilonEvent)) {
            ConfigHolder.INSTANCE.saveNow();
            return true;
        }

        for (DropdownPanel panel : panels) {
            if (!panel.isVisible()) continue;
            if (panel.mouseReleased(mx, my, button)) {
                DropdownLayoutState.save(panels);
                return true;
            }
        }
        return super.mouseReleased(epsilonEvent);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double mouseX, double mouseY) {
        MouseButtonEvent epsilonEvent = LuminRenderSystem.toEpsilonMouseEvent(event);
        double epsilonMouseX = LuminRenderSystem.toEpsilonMouseX(mouseX);
        double epsilonMouseY = LuminRenderSystem.toEpsilonMouseY(mouseY);
        if (popupHost.mouseDragged(epsilonEvent, epsilonMouseX, epsilonMouseY)) {
            return true;
        }
        for (DropdownPanel panel : panels) {
            if (!panel.isVisible()) continue;
            panel.mouseDragged(LuminRenderSystem.toEpsilonMouseX(event.x()), LuminRenderSystem.toEpsilonMouseY(event.y()));
        }
        DropdownLayoutState.save(panels);
        return super.mouseDragged(epsilonEvent, LuminRenderSystem.toEpsilonMouseX(event.x()), LuminRenderSystem.toEpsilonMouseY(event.y()));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double epsilonMouseX = LuminRenderSystem.toEpsilonMouseX(mouseX);
        double epsilonMouseY = LuminRenderSystem.toEpsilonMouseY(mouseY);
        if (popupHost.mouseScrolled(epsilonMouseX, epsilonMouseY, scrollX, scrollY)) {
            return true;
        }
        // 从顶层向底层遍历，确保最上层 panel 优先处理滚轮事件
        for (int i = panels.size() - 1; i >= 0; i--) {
            DropdownPanel panel = panels.get(i);
            if (!panel.isVisible()) continue;
            if (panel.mouseScrolled(epsilonMouseX, epsilonMouseY, scrollY)) {
                return true;
            }
        }
        return super.mouseScrolled(epsilonMouseX, epsilonMouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (popupHost.keyPressed(event)) {
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_F && InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL)) {
            searchField.focus();
            return true;
        }
        if (searchField.isFocused()) {
            if (event.isEscape()) {
                searchField.blur();
                return true;
            }
            if (searchField.keyPressed(event)) {
                syncSearchQuery();
                return true;
            }
        }

        boolean hasActiveInput = panels.stream().filter(DropdownPanel::isVisible).anyMatch(DropdownPanel::hasActiveInput);

        if (hasActiveInput) {
            for (DropdownPanel panel : panels) {
                if (!panel.isVisible()) continue;
                if (panel.keyPressed(event.key(), event.scancode(), event.modifiers())) {
                    return true;
                }
            }
        }

        if (event.isEscape()) {
            onClose();
            return true;
        }

        for (DropdownPanel panel : panels) {
            if (!panel.isVisible()) continue;
            if (panel.keyPressed(event.key(), event.scancode(), event.modifiers())) {
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (popupHost.charTyped(event)) {
            return true;
        }
        if (searchField.charTyped(event)) {
            syncSearchQuery();
            return true;
        }
        for (DropdownPanel panel : panels) {
            if (!panel.isVisible()) continue;
            String typed = event.codepointAsString();
            if (!typed.isEmpty() && panel.charTyped(typed)) {
                return true;
            }
        }
        return super.charTyped(event);
    }

    @Override
    public void onClose() {
        IMEFocusHelper.deactivate();
        popupHost.close();
        DropdownLayoutState.save(panels);
        super.onClose();
    }

    @Override
    public boolean preeditUpdated(PreeditEvent event) {
        this.preeditOverlay = event != null ? new IMEPreeditOverlay(event, this.font, 10) : null;
        return true;
    }

    @Override
    public void removed() {
        super.removed();
        if (renderTarget != null) {
            renderTarget.close();
            renderTarget = null;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void buildPanels() {
        panels.clear();
        int index = 0;
        MainDropdownPanel mainPanel = new MainDropdownPanel(index++, this::handleMainPanelAction, this::isPanelVisible);
        mainPanel.setPosition(DropdownTheme.PANEL_MARGIN_X, DropdownTheme.PANEL_MARGIN_Y);
        panels.add(mainPanel);

        float x = DropdownTheme.PANEL_MARGIN_X + mainPanel.getWidth() + DropdownTheme.PANEL_GAP;
        float y = DropdownTheme.PANEL_MARGIN_Y;
        for (Category category : Category.values()) {
            panels.add(createSubPanel(new CategoryPanel(category, index++), x, y));
            y += DropdownTheme.PANEL_HEADER_HEIGHT + DropdownTheme.PANEL_GAP;
        }

        panels.add(createSubPanel(new FriendDropdownPanel(index++), x, y));
        y += DropdownTheme.PANEL_HEADER_HEIGHT + DropdownTheme.PANEL_GAP;
        panels.add(createSubPanel(new ConfigDropdownPanel(index++), x, y));
        y += DropdownTheme.PANEL_HEADER_HEIGHT + DropdownTheme.PANEL_GAP;
        panels.add(createSubPanel(new AddonDropdownPanel(index), x, y));

        DropdownLayoutState.load(panels);
    }

    private DropdownPanel createSubPanel(DropdownPanel panel, float x, float y) {
        panel.setPosition(x, y);
        panel.setVisible(false);
        panel.setOpened(false);
        return panel;
    }

    private void handleMainPanelAction(String panelId) {
        for (DropdownPanel panel : panels) {
            if (panel.getId().equals(panelId)) {
                boolean nextVisible = !panel.isVisible();
                panel.setVisible(nextVisible);
                panel.setOpened(false);
                DropdownLayoutState.save(panels);
                return;
            }
        }
    }

    private boolean isPanelVisible(String panelId) {
        return visiblePanelIds.contains(panelId);
    }

    private void updateVisiblePanelIds() {
        visiblePanelIds.clear();
        for (DropdownPanel panel : panels) {
            if (panel.isVisible()) {
                visiblePanelIds.add(panel.getId());
            }
        }
    }

    private float resolveMaxPanelHeight(DropdownPanel panel) {
        return resolveMaxPanelHeight(panel, LuminRenderSystem.getScaledHeight() * 0.72f);
    }

    private float resolveMaxPanelHeight(DropdownPanel panel, float screenLimited) {
        return switch (panel.getId()) {
            case "main", "addon" -> Math.min(screenLimited, 260.0f);
            case "friend", "config" -> Math.min(screenLimited, 220.0f);
            default -> Math.min(screenLimited, 350.0f);
        };
    }

    private void updatePanelHeightLimits() {
        float screenLimited = LuminRenderSystem.getScaledHeight() * 0.72f;
        for (DropdownPanel panel : panels) {
            panel.setMaxPanelHeight(resolveMaxPanelHeight(panel, screenLimited));
        }
    }

    private void beginPanelFrames() {
        int frameId = ++renderFrameId;
        for (DropdownPanel panel : panels) {
            panel.beginRenderFrame(frameId);
        }
    }

    private void syncSearchQuery() {
        String query = searchField.getText();
        for (DropdownPanel panel : panels) {
            if (panel instanceof CategoryPanel categoryPanel) {
                categoryPanel.setSearchQuery(query);
            }
        }
    }

    private float getSearchX() {
        return DropdownTheme.PANEL_MARGIN_X;
    }

    private float getSearchY() {
        return LuminRenderSystem.getScaledHeight() - DropdownTheme.PANEL_MARGIN_Y - getSearchHeight();
    }

    private float getSearchWidth() {
        return Mth.clamp(LuminRenderSystem.getScaledWidth() - DropdownTheme.PANEL_MARGIN_X * 2.0f, 140.0f, 200.0f);
    }

    private float getSearchHeight() {
        return 20.0f;
    }

    public int getSessionId() {
        return sessionId;
    }

    public void openBlockListPopup(BlockListSetting setting) {
        PanelLayout.Rect bounds = popupHost.getCenteredBounds(
                Math.min(360.0f, LuminRenderSystem.getScaledWidth() - 28.0f),
                Math.min(300.0f, LuminRenderSystem.getScaledHeight() - 28.0f)
        );
        popupHost.open(new BlockListSelectPopup(bounds, setting));
    }

}
