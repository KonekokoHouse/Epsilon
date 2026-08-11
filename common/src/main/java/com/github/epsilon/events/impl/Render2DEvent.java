package com.github.epsilon.events.impl;

import net.minecraft.client.gui.GuiGraphics;

public class Render2DEvent {

    private final GuiGraphics guiGraphics;

    protected Render2DEvent(GuiGraphics guiGraphics) {
        this.guiGraphics = guiGraphics;
    }

    public GuiGraphics getGuiGraphics() {
        return guiGraphics;
    }

    public static final class Level extends Render2DEvent {
        public Level(GuiGraphics guiGraphics) {
            super(guiGraphics);
        }
    }

    public static final class HUD extends Render2DEvent {
        public HUD(GuiGraphics guiGraphics) {
            super(guiGraphics);
        }
    }

}
