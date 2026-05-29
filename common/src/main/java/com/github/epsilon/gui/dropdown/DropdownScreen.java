package com.github.epsilon.gui.dropdown;

import com.github.epsilon.assets.i18n.EpsilonTranslateComponent;
import com.github.epsilon.assets.i18n.TranslateComponent;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.gui.dropdown.component.*;
import com.github.epsilon.gui.dropdown.widget.DropdownTextField;
import com.github.epsilon.gui.panel.MD3Theme;
import com.github.epsilon.gui.panel.utils.IMEFocusHelper;
import com.github.epsilon.modules.Category;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.IMEPreeditOverlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class DropdownScreen extends Screen {

    public static final DropdownScreen INSTANCE = new DropdownScreen();
    private static final TranslateComponent searchComponent = EpsilonTranslateComponent.create("gui", "search");

    private final List<DropdownPanel> panels = new ArrayList<>();
    private final DropdownRenderer renderer = new DropdownRenderer();
    private final Animation scrimAnim = new Animation(Easing.EASE_OUT_SINE, 200L);
    private final DropdownTextField searchField = new DropdownTextField(64);

    private LuminRenderSystem.LuminRenderTarget renderTarget;
    private IMEPreeditOverlay preeditOverlay;
    private boolean initialized;
    private int sessionId;

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
        renderTarget.clear();
        renderTarget.resize(window.getWidth(), window.getHeight());
        LuminRenderSystem.setActiveTarget(renderTarget);

        MD3Theme.syncFromSettings();
        drawGui(mouseX, mouseY);

        LuminRenderSystem.setActiveTarget(null);
        if (preeditOverlay != null) {
            preeditOverlay.updateInputPosition((int) IMEFocusHelper.activeCursorX, (int) IMEFocusHelper.activeCursorY);
            graphics.setPreeditOverlay(preeditOverlay);
        }
        graphics.blit(renderTarget.getIdentifier(), 0, 0, window.getGuiScaledWidth(), window.getGuiScaledHeight(), 0, 1, 1, 0);
    }

    private void drawGui(int mouseX, int mouseY) {
        scrimAnim.run(1.0f);
        renderer.beginFrame();

        renderer.beginPass();
        Color scrim = DropdownTheme.scrim();
        float scrimAlpha = scrimAnim.getValue();
        renderer.rect().addRect(0, 0, width, height, new Color(scrim.getRed(), scrim.getGreen(), scrim.getBlue(), (int) (scrim.getAlpha() * scrimAlpha)));
        renderer.flush();

        float shadowPad = DropdownTheme.PANEL_SHADOW_BLUR + 4.0f;

        // 找出鼠标位置处最上层的可见 panel，被遮挡的 panel 不应响应悬浮
        DropdownPanel topmostHovered = null;
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
                    height);
            panel.drawBackground(renderer);
            renderer.flush();
            renderer.clearScissor();

            float clipY = panel.getContentClipY();
            float clipH = panel.getContentClipHeight();
            float revealedBottom = panel.getY() + revealedH;
            float actualClipH = Math.min(clipH, revealedBottom - clipY);
            if (actualClipH > 0.5f) {
                renderer.beginPass();
                renderer.setScissor(panel.getX(), clipY, panel.getWidth(), actualClipH, height);
                int hoverMouseX = panel == topmostHovered ? mouseX : -1;
                int hoverMouseY = panel == topmostHovered ? mouseY : -1;
                panel.drawContent(renderer, hoverMouseX, hoverMouseY);
                renderer.flush();
                renderer.clearScissor();
            }

            panel.setPosition(panel.getX(), origY);
        }

        drawSearch(mouseX, mouseY);
    }

    private void drawSearch(int mouseX, int mouseY) {
        renderer.beginPass();
        float searchX = getSearchX();
        float searchY = getSearchY();
        searchField.draw(renderer, searchX, searchY, getSearchWidth(), getSearchHeight(), mouseX, mouseY, searchComponent.getTranslatedName(), 0.58f);
        renderer.flush();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        double mx = event.x();
        double my = event.y();
        int button = event.button();

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
        return super.mouseClicked(event, isDoubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        double mx = event.x();
        double my = event.y();
        int button = event.button();

        for (DropdownPanel panel : panels) {
            if (!panel.isVisible()) continue;
            if (panel.mouseReleased(mx, my, button)) {
                DropdownLayoutState.save(panels);
                return true;
            }
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double mouseX, double mouseY) {
        for (DropdownPanel panel : panels) {
            if (!panel.isVisible()) continue;
            panel.mouseDragged(event.x(), event.y());
        }
        DropdownLayoutState.save(panels);
        return super.mouseDragged(event, event.x(), event.y());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // 从顶层向底层遍历，确保最上层 panel 优先处理滚轮事件
        for (int i = panels.size() - 1; i >= 0; i--) {
            DropdownPanel panel = panels.get(i);
            if (!panel.isVisible()) continue;
            if (panel.mouseScrolled(mouseX, mouseY, scrollY)) {
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
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
        return panels.stream().anyMatch(panel -> panel.getId().equals(panelId) && panel.isVisible());
    }

    private float resolveMaxPanelHeight(DropdownPanel panel) {
        float screenLimited = height * 0.72f;
        return switch (panel.getId()) {
            case "main", "addon" -> Math.min(screenLimited, 260.0f);
            case "friend", "config" -> Math.min(screenLimited, 220.0f);
            default -> Math.min(screenLimited, 350.0f);
        };
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
        return height - DropdownTheme.PANEL_MARGIN_Y - getSearchHeight();
    }

    private float getSearchWidth() {
        return Mth.clamp(width - DropdownTheme.PANEL_MARGIN_X * 2.0f, 140.0f, 200.0f);
    }

    private float getSearchHeight() {
        return 20.0f;
    }

    public int getSessionId() {
        return sessionId;
    }

}
