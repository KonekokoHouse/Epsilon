package com.github.epsilon.gui.hudeditor;

import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.gui.dropdown.DropdownScreen;
import com.github.epsilon.gui.dsl.PanelRenderBatch;
import com.github.epsilon.gui.dsl.PanelUiTree;
import com.github.epsilon.gui.panel.PanelScreen;
import com.github.epsilon.gui.panel.utils.IMEFocusHelper;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.modules.HudModule;
import com.github.epsilon.modules.impl.ClientSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.IMEPreeditOverlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.network.chat.Component;

import java.awt.*;
import java.util.List;

public class HudEditorScreen extends Screen {

    private static final Color BOX_COLOR = new Color(0, 0, 0, 100);
    private static final Color SELECTED_COLOR = new Color(188, 224, 255, 56);
    private static final Color HOVER_COLOR = new Color(255, 255, 255, 70);
    private static final Color DRAGGING_COLOR = new Color(120, 190, 255, 80);

    public static final HudEditorScreen INSTANCE = new HudEditorScreen();

    private final PanelRenderBatch renderBatch = new PanelRenderBatch();
    private final HudEditorOverlayRenderer guideOverlayRenderer = new HudEditorOverlayRenderer();
    private final HudEditorOverlayRenderer foregroundOverlayRenderer = new HudEditorOverlayRenderer();
    private final HudEditorInspector inspector = new HudEditorInspector();

    private HudModule dragging;
    private HudModule selected;
    private double dragOffsetX;
    private double dragOffsetY;
    private Float snapPreviewX;
    private Float snapPreviewY;

    private LuminRenderSystem.LuminRenderTarget renderTarget;
    private IMEPreeditOverlay preeditOverlay;

    private HudEditorScreen() {
        super(Component.literal("HUDEditor"));
    }

    @Override
    protected void init() {
        Managers.NOTIFICATION.clear();
        super.init();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        var window = minecraft.getWindow();
        if (renderTarget == null) {
            renderTarget = LuminRenderSystem.LuminRenderTarget.create("hud-editor", window.getWidth(), window.getHeight());
        }
        renderTarget.clear();
        renderTarget.resize(window.getWidth(), window.getHeight());

        LuminRenderSystem.setActiveTarget(renderTarget);

        var delta = minecraft.getDeltaTracker();

        int screenWidth = LuminRenderSystem.getScaledWidthInt();
        int screenHeight = LuminRenderSystem.getScaledHeightInt();
        List<HudModule> hudModules = HudEditorModules.collectEnabledHudModules();
        syncSelectionState(hudModules);

        HudModule hovered = HudEditorModules.findTopmost(hudModules, LuminRenderSystem.toEpsilonMouseX(mouseX), LuminRenderSystem.toEpsilonMouseY(mouseY));
        HudModule focus = dragging != null ? dragging : (selected != null ? selected : hovered);
        boolean draggingFocus = focus != null && focus == dragging;

        if (focus != null) {
            guideOverlayRenderer.addThirdGuides(focus, draggingFocus, screenWidth, screenHeight);
            guideOverlayRenderer.flushRenderer();
        }

        PanelUiTree moduleBoxes = PanelUiTree.build(scope -> {
            for (HudModule hudModule : hudModules) {
                scope.rect(hudModule.x, hudModule.y, hudModule.width, hudModule.height, BOX_COLOR);
                if (hudModule == selected) {
                    scope.rect(hudModule.x, hudModule.y, hudModule.width, hudModule.height, SELECTED_COLOR);
                }
                if (hudModule == hovered) {
                    scope.rect(hudModule.x, hudModule.y, hudModule.width, hudModule.height, HOVER_COLOR);
                }
                if (hudModule == dragging) {
                    scope.rect(hudModule.x, hudModule.y, hudModule.width, hudModule.height, DRAGGING_COLOR);
                }
            }
        });
        renderBatch.render(moduleBoxes, 10);
        renderBatch.flushAndClear();

        for (HudModule hudModule : hudModules) {
            hudModule.render(graphics, delta);
        }

        if (focus != null) {
            foregroundOverlayRenderer.addAnchorOverlay(focus, draggingFocus, screenWidth, screenHeight);
        }

        foregroundOverlayRenderer.addSnapPreview(snapPreviewX, snapPreviewY, screenWidth, screenHeight);
        foregroundOverlayRenderer.flushRenderer();
        inspector.queueRender(graphics, selected, screenWidth, screenHeight, LuminRenderSystem.toEpsilonMouseX(mouseX), LuminRenderSystem.toEpsilonMouseY(mouseY), a, screenHeight);

        inspector.renderPopups(graphics, LuminRenderSystem.toEpsilonMouseX(mouseX), LuminRenderSystem.toEpsilonMouseY(mouseY), a);

        LuminRenderSystem.setActiveTarget(null);

        if (preeditOverlay != null) {
            preeditOverlay.updateInputPosition((int) IMEFocusHelper.activeCursorX, (int) IMEFocusHelper.activeCursorY);
            graphics.setPreeditOverlay(preeditOverlay);
        }

        graphics.blit(renderTarget.getIdentifier(), 0, 0, window.getGuiScaledWidth(), window.getGuiScaledHeight(), 0, 1, 1, 0);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        MouseButtonEvent epsilonEvent = LuminRenderSystem.toEpsilonMouseEvent(event);
        if (inspector.mouseClicked(epsilonEvent, isDoubleClick)) {
            return true;
        }

        if (event.button() == 0) {
            double mx = epsilonEvent.x();
            double my = epsilonEvent.y();
            List<HudModule> hudModules = HudEditorModules.collectEnabledHudModules();
            syncSelectionState(hudModules);
            HudModule hovered = HudEditorModules.findTopmost(hudModules, mx, my);
            if (hovered != null) {
                inspector.clearFocus();
                selected = hovered;
                dragging = hovered;
                dragOffsetX = mx - hovered.x;
                dragOffsetY = my - hovered.y;
                clearSnapPreview();
                return true;
            }

            clearSelection();
            return true;
        }

        return super.mouseClicked(epsilonEvent, isDoubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double mouseX, double mouseY) {
        MouseButtonEvent epsilonEvent = LuminRenderSystem.toEpsilonMouseEvent(event);
        double epsilonMouseX = LuminRenderSystem.toEpsilonMouseX(mouseX);
        double epsilonMouseY = LuminRenderSystem.toEpsilonMouseY(mouseY);
        if (inspector.mouseDragged(epsilonEvent, epsilonMouseX, epsilonMouseY)) {
            return true;
        }

        if (dragging != null && epsilonEvent.button() == 0) {
            int screenWidth = LuminRenderSystem.getScaledWidthInt();
            int screenHeight = LuminRenderSystem.getScaledHeightInt();
            List<HudModule> hudModules = HudEditorModules.collectEnabledHudModules();
            float targetX = (float) (epsilonEvent.x() - dragOffsetX);
            float targetY = (float) (epsilonEvent.y() - dragOffsetY);
            HudEditorSnapper.SnapPosition snap = epsilonEvent.hasAltDown() ? new HudEditorSnapper.SnapPosition(targetX, targetY, null, null) : HudEditorSnapper.snapPosition(dragging, targetX, targetY, screenWidth, screenHeight, hudModules);

            dragging.moveTo(snap.renderX(), snap.renderY());
            snapPreviewX = snap.guideX();
            snapPreviewY = snap.guideY();
            return true;
        }

        return super.mouseDragged(epsilonEvent, epsilonMouseX, epsilonMouseY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        MouseButtonEvent epsilonEvent = LuminRenderSystem.toEpsilonMouseEvent(event);
        if (inspector.mouseReleased(epsilonEvent)) {
            return true;
        }

        if (dragging != null && epsilonEvent.button() == 0) {
            dragging = null;
            clearSnapPreview();
            return true;
        }

        return super.mouseReleased(epsilonEvent);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double epsilonMouseX = LuminRenderSystem.toEpsilonMouseX(mouseX);
        double epsilonMouseY = LuminRenderSystem.toEpsilonMouseY(mouseY);
        if (inspector.mouseScrolled(epsilonMouseX, epsilonMouseY, scrollX, scrollY)) {
            return true;
        }
        return super.mouseScrolled(epsilonMouseX, epsilonMouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) {
            if (inspector.keyPressed(event)) {
                return true;
            }
            if (dragging != null) {
                dragging = null;
                clearSnapPreview();
                return true;
            }
            if (selected != null) {
                clearSelection();
                return true;
            }
            onClose();
            return true;
        }
        if (inspector.keyPressed(event)) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (inspector.charTyped(event)) {
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
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        dragging = null;
        clearSnapPreview();
        IMEFocusHelper.deactivate();
        super.onClose();

        minecraft.setScreen(switch (ClientSetting.INSTANCE.guiMode.getValue()) {
            case Panel -> PanelScreen.INSTANCE;
            case Dropdown -> DropdownScreen.INSTANCE;
        });
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        if (this.minecraft.level == null) {
            this.extractPanorama(graphics, a);
        }
    }

    private void clearSnapPreview() {
        snapPreviewX = null;
        snapPreviewY = null;
    }

    private void clearSelection() {
        selected = null;
        inspector.clearFocus();
    }

    private void syncSelectionState(List<HudModule> hudModules) {
        if (dragging != null && !hudModules.contains(dragging)) {
            dragging = null;
            clearSnapPreview();
        }
        if (selected != null && !hudModules.contains(selected)) {
            clearSelection();
        }
    }

}
