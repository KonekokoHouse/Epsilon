package com.github.epsilon.gui.panel.panel.clientsettings;

import com.github.epsilon.gui.dsl.PanelRenderBatch;
import com.github.epsilon.gui.panel.PanelLayout;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

public interface ClientSettingTabView {

    void render(GuiGraphicsExtractor guiGraphics, PanelRenderBatch renderBatch, PanelLayout.Rect bounds, int mouseX, int mouseY, float partialTick);

    void flushContent();

    void markDirty();

    boolean hasActiveAnimations();

    boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick);

    boolean mouseReleased(MouseButtonEvent event);

    boolean mouseDragged(MouseButtonEvent event, double mouseX, double mouseY);

    boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY);

    boolean keyPressed(KeyEvent event);

    boolean charTyped(CharacterEvent event);

    default boolean consumesHover(int mouseX, int mouseY) {
        return false;
    }

    default void onActivated() {
    }

    default void onDeactivated() {
    }
}

