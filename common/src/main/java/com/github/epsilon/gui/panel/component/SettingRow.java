package com.github.epsilon.gui.panel.component;

import com.github.slmpc.lumingraphics.ui.text.UiTextMetrics;
import com.github.slmpc.lumingraphics.ui.geometry.UiRect;
import com.github.slmpc.lumingraphics.ui.tree.UiTree;
import com.github.epsilon.settings.Setting;
import net.minecraft.client.gui.GuiGraphics;
import com.github.epsilon.gui.input.CharacterEvent;
import com.github.epsilon.gui.input.KeyEvent;
import com.github.epsilon.gui.input.MouseButtonEvent;
import com.github.epsilon.gui.input.PreeditEvent;
import javax.annotation.Nullable;

public abstract class SettingRow<T extends Setting<?>> implements AutoCloseable {

    protected final T setting;

    protected SettingRow(T setting) {
        this.setting = setting;
    }

    public T getSetting() {
        return setting;
    }

    public float getHeight() {
        return 28.0f;
    }

    public void buildUi(UiTree.Scope scope, GuiGraphics guiGraphics, UiTextMetrics textRenderer,
                        UiRect bounds, float hoverProgress, int mouseX, int mouseY, float partialTick) {
    }

    public boolean mouseClicked(UiRect bounds, MouseButtonEvent event, boolean isDoubleClick) {
        return false;
    }

    public boolean mouseReleased(UiRect bounds, MouseButtonEvent event) {
        return false;
    }

    public boolean mouseScrolled(UiRect bounds, double mouseX, double mouseY, double scrollX, double scrollY) {
        return false;
    }

    public boolean keyPressed(KeyEvent event) {
        return false;
    }

    public boolean charTyped(CharacterEvent event) {
        return false;
    }

    public boolean preeditUpdated(@Nullable PreeditEvent event) {
        return false;
    }

    public void setFocused(boolean focused) {
    }

    public boolean isFocused() {
        return false;
    }

    public boolean hasActiveAnimation() {
        return false;
    }

    @Override
    public void close() {
    }

}
