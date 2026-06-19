package com.github.epsilon.gui.hudeditor;

import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.gui.dropdown.DropdownRenderer;
import com.github.epsilon.gui.dropdown.DropdownScreen;
import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.epsilon.gui.dropdown.component.CategoryPanel;
import com.github.epsilon.gui.panel.PanelScreen;
import com.github.epsilon.holders.HudElementHolder;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.modules.impl.ClientSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class HudEditorScreen extends Screen {

    public static final HudEditorScreen INSTANCE = new HudEditorScreen();

    private CategoryPanel hudPanel;
    private LuminRenderSystem.LuminRenderTarget renderTarget;
    private int renderFrameId;
    private int panelElementCount = -1;

    private final DropdownRenderer renderer = new DropdownRenderer();

    private HudEditorScreen() {
        super(Component.literal("HudEditor"));
    }

    @Override
    protected void init() {
        Managers.NOTIFICATION.clearAll();
        ensureHudPanel();
        hudPanel.setVisible(true);
        hudPanel.setOpened(true);
        hudPanel.setMaxPanelHeight(resolveMaxPanelHeight());
        hudPanel.startIntro();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        final var window = minecraft.getWindow();
        if (renderTarget == null) {
            renderTarget = LuminRenderSystem.LuminRenderTarget.create("hud-editor-gui", window.getWidth(), window.getHeight());
        }
        renderTarget.resize(window.getWidth(), window.getHeight());
        renderTarget.clear();

        LuminRenderSystem.setActiveTarget(renderTarget);

        int epsilonMouseX = LuminRenderSystem.toEpsilonMouseX(mouseX);
        int epsilonMouseY = LuminRenderSystem.toEpsilonMouseY(mouseY);
        drawPanel(epsilonMouseX, epsilonMouseY);

        LuminRenderSystem.setActiveTarget(null);
        graphics.blit(renderTarget.getIdentifier(), 0, 0, window.getGuiScaledWidth(), window.getGuiScaledHeight(), 0, 1, 1, 0);
    }

    private void drawPanel(int mouseX, int mouseY) {
        ensureHudPanel();
        renderer.beginFrame();
        hudPanel.setMaxPanelHeight(resolveMaxPanelHeight());
        hudPanel.beginRenderFrame(++renderFrameId);

        float shadowPad = DropdownTheme.PANEL_SHADOW_BLUR + 4.0f;
        float intro = hudPanel.getIntroValue();
        if (intro > 0.001f) {
            float slideOffset = (1.0f - intro) * 10.0f;
            float origY = hudPanel.getY();
            hudPanel.setPosition(hudPanel.getX(), origY - slideOffset);

            float panelH = hudPanel.getPanelHeight();
            float revealedH = panelH * intro;

            renderer.beginPass();
            renderer.setScissor(
                    hudPanel.getX() - shadowPad,
                    hudPanel.getY() - shadowPad,
                    hudPanel.getWidth() + shadowPad * 2.0f,
                    revealedH + shadowPad * 2.0f,
                    LuminRenderSystem.getScaledHeightInt()
            );
            hudPanel.drawBackground(renderer);
            renderer.flush();
            renderer.clearScissor();

            float clipY = hudPanel.getContentClipY();
            float clipH = hudPanel.getContentClipHeight();
            float revealedBottom = hudPanel.getY() + revealedH;
            float actualClipH = Math.min(clipH, revealedBottom - clipY);
            if (actualClipH > 0.5f) {
                renderer.beginPass();
                renderer.setScissor(hudPanel.getX(), clipY, hudPanel.getWidth(), actualClipH, LuminRenderSystem.getScaledHeightInt());
                hudPanel.drawContent(renderer, mouseX, mouseY);
                renderer.flush();
                renderer.clearScissor();
            }

            hudPanel.setPosition(hudPanel.getX(), origY);
        }

        renderer.endFrame();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (hudPanel != null && hudPanel.hasActiveInput() && hudPanel.keyPressed(event.key(), event.scancode(), event.modifiers())) {
            return true;
        }
        if (event.isEscape()) {
            onClose();
            return true;
        }
        if (hudPanel != null && hudPanel.keyPressed(event.key(), event.scancode(), event.modifiers())) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        String typed = event.codepointAsString();
        if (hudPanel != null && !typed.isEmpty() && hudPanel.charTyped(typed)) {
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        MouseButtonEvent epsilonEvent = LuminRenderSystem.toEpsilonMouseEvent(event);
        if (hudPanel != null && hudPanel.mouseClicked(epsilonEvent.x(), epsilonEvent.y(), epsilonEvent.button())) {
            return true;
        }
        return super.mouseClicked(epsilonEvent, isDoubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        MouseButtonEvent epsilonEvent = LuminRenderSystem.toEpsilonMouseEvent(event);
        if (hudPanel != null && hudPanel.mouseReleased(epsilonEvent.x(), epsilonEvent.y(), epsilonEvent.button())) {
            return true;
        }
        return super.mouseReleased(epsilonEvent);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double mouseX, double mouseY) {
        MouseButtonEvent epsilonEvent = LuminRenderSystem.toEpsilonMouseEvent(event);
        if (hudPanel != null) {
            hudPanel.mouseDragged(LuminRenderSystem.toEpsilonMouseX(event.x()), LuminRenderSystem.toEpsilonMouseY(event.y()));
        }
        return super.mouseDragged(epsilonEvent, LuminRenderSystem.toEpsilonMouseX(mouseX), LuminRenderSystem.toEpsilonMouseY(mouseY));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double epsilonMouseX = LuminRenderSystem.toEpsilonMouseX(mouseX);
        double epsilonMouseY = LuminRenderSystem.toEpsilonMouseY(mouseY);
        if (hudPanel != null && hudPanel.mouseScrolled(epsilonMouseX, epsilonMouseY, scrollY)) {
            return true;
        }
        return super.mouseScrolled(epsilonMouseX, epsilonMouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        super.onClose();

        minecraft.setScreen(switch (ClientSetting.INSTANCE.guiMode.getValue()) {
            case Panel -> PanelScreen.INSTANCE;
            case Dropdown -> DropdownScreen.INSTANCE;
        });
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
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        if (this.minecraft.level == null) {
            this.extractPanorama(graphics, a);
        }
    }

    private float resolveMaxPanelHeight() {
        return Math.min(LuminRenderSystem.getScaledHeight() * 0.72f, 350.0f);
    }

    private void ensureHudPanel() {
        int elementCount = HudElementHolder.INSTANCE.getElements().size();
        if (hudPanel != null && panelElementCount == elementCount) return;

        float x = hudPanel == null ? DropdownTheme.PANEL_MARGIN_X : hudPanel.getX();
        float y = hudPanel == null ? DropdownTheme.PANEL_MARGIN_Y : hudPanel.getY();
        hudPanel = new CategoryPanel("hud_elements", "HUD", "E", 0, HudElementHolder.INSTANCE.getElements());
        hudPanel.setVisible(true);
        hudPanel.setOpened(true);
        hudPanel.setPosition(x, y);
        hudPanel.setMaxPanelHeight(resolveMaxPanelHeight());
        panelElementCount = elementCount;
    }

}
