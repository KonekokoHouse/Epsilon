package com.github.epsilon.elements.impl;

import com.github.epsilon.elements.HudModule;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import net.minecraft.client.DeltaTracker;

import java.awt.*;

public class MTF extends HudModule {

    public static final MTF INSTANCE = new MTF();

    private MTF() {
        super("MTF", 0f, 0f, 36f, 36f);
    }

    private final DoubleSetting size = doubleSetting("Size", 32.0, 12.0, 96.0, 1.0);
    private final DoubleSetting speed = doubleSetting("Speed", 160.0, -720.0, 720.0, 5.0);
    private final ColorSetting color = colorSetting("Color", new Color(255, 255, 255, 255), true);

    @Override
    public void render(DeltaTracker deltaTracker) {
        float boxSize = size.getValue().floatValue();
        setBounds(boxSize, boxSize);

        final String fishcake = "\uD83C\uDF65";
        renderScope().text(fishcake, this.x, this.y, boxSize / 16.0f, lumin(color.getValue()));
    }

}
