package com.github.epsilon.gui.dropdown.component;

import com.github.epsilon.gui.dsl.PanelUiTree;
import com.github.epsilon.gui.dsl.UiTextMetrics;

public interface DropdownPanel {

    String getId();

    void startIntro();

    default void beginRenderFrame(int frameId) {
    }

    float getIntroValue();

    void drawBackground(PanelUiTree.Scope scope, UiTextMetrics textMetrics);

    void drawContent(PanelUiTree.Scope scope, UiTextMetrics textMetrics, int mouseX, int mouseY);

    float getContentClipY();

    float getContentClipHeight();

    float getPanelHeight();

    boolean mouseClicked(double mouseX, double mouseY, int button);

    boolean mouseReleased(double mouseX, double mouseY, int button);

    boolean mouseDragged(double mouseX, double mouseY);

    boolean mouseScrolled(double mouseX, double mouseY, double amount);

    boolean keyPressed(int keyCode, int scanCode, int modifiers);

    boolean charTyped(String typedText);

    boolean hasActiveInput();

    void setPosition(float x, float y);

    void setMaxPanelHeight(float maxPanelHeight);

    float getX();

    float getY();

    float getWidth();

    boolean isOpened();

    void setOpened(boolean opened);

    boolean isVisible();

    void setVisible(boolean visible);

}
