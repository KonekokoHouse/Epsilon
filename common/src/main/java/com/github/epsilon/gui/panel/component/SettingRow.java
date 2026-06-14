package com.github.epsilon.gui.panel.component;

import com.github.epsilon.graphics.renderers.RectRenderer;
import com.github.epsilon.graphics.renderers.RoundRectOutlineRenderer;
import com.github.epsilon.graphics.renderers.RoundRectRenderer;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.gui.panel.PanelLayout;
import com.github.epsilon.gui.dsl.PanelUiCompiler;
import com.github.epsilon.gui.dsl.PanelUiTree;
import com.github.epsilon.settings.Setting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import org.jspecify.annotations.Nullable;

public abstract class SettingRow<T extends Setting<?>> {

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

    public void render(GuiGraphicsExtractor GuiGraphicsExtractor, RoundRectRenderer roundRectRenderer, RoundRectOutlineRenderer roundRectOutlineRenderer, RectRenderer rectRenderer, TextRenderer textRenderer, PanelLayout.Rect bounds, float hoverProgress, int mouseX, int mouseY, float partialTick) {
        PanelUiTree tree = PanelUiTree.build(scope -> buildUi(scope, GuiGraphicsExtractor, textRenderer, bounds, hoverProgress, mouseX, mouseY, partialTick));
        PanelUiCompiler.render(tree, null, roundRectRenderer, roundRectOutlineRenderer, rectRenderer, textRenderer);
    }

    public void buildUi(PanelUiTree.Scope scope, GuiGraphicsExtractor guiGraphics, TextRenderer textRenderer,
                        PanelLayout.Rect bounds, float hoverProgress, int mouseX, int mouseY, float partialTick) {
    }

    public boolean mouseClicked(PanelLayout.Rect bounds, MouseButtonEvent event, boolean isDoubleClick) {
        return false;
    }

    public boolean mouseReleased(PanelLayout.Rect bounds, MouseButtonEvent event) {
        return false;
    }

    public boolean mouseScrolled(PanelLayout.Rect bounds, double mouseX, double mouseY, double scrollX, double scrollY) {
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

}
