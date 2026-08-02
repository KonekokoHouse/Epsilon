package com.github.epsilon.gui.panel.view.settings;

import com.github.slmpc.lumingraphics.ui.geometry.UiRect;
import com.github.slmpc.lumingraphics.ui.render.UiRenderBatch;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

public interface ClientSettingTabView extends AutoCloseable {

    void render(GuiGraphicsExtractor guiGraphics, UiRenderBatch renderBatch, UiRect bounds, int mouseX, int mouseY, float partialTick);

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

    @Override
    default void close() {
    }
}

