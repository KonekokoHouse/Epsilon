package com.github.epsilon.gui.hudeditor;

import com.github.epsilon.gui.dropdown.DropdownScreen;
import com.github.epsilon.gui.panel.PanelScreen;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.modules.impl.ClientSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

public class HudEditorScreen extends Screen {

    public static final HudEditorScreen INSTANCE = new HudEditorScreen();

    private HudEditorScreen() {
        super(Component.literal("HudEditor"));
    }

    @Override
    protected void init() {
        Managers.NOTIFICATION.clearAll();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {

    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
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
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        if (this.minecraft.level == null) {
            this.extractPanorama(graphics, a);
        }
    }

}
