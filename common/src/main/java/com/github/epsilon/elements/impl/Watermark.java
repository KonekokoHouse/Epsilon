package com.github.epsilon.elements.impl;

import com.github.epsilon.elements.HudModule;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import net.minecraft.client.DeltaTracker;

import java.awt.*;

public class Watermark extends HudModule {

    public static final Watermark INSTANCE = new Watermark();
    private static final String OSAKA_FONT = "epsilon-osakachips";

    private Watermark() {
        super("Watermark", 0f, 0f, 200f, 28f);
    }

    private final DoubleSetting scale = doubleSetting("Scale", 1.0, 0.5, 2.0, 0.1);
    private final ColorSetting textColor = colorSetting("Text Color", new Color(255, 255, 255, 235));

    @Override
    public void render(DeltaTracker deltaTracker) {
        String traditionText = "EPSILON";
        float scaledScale = scale.getValue().floatValue() * 2f; // 这个命名给我自己整笑了

        renderScope().text(traditionText, this.x, this.y, scaledScale, lumin(textColor.getValue()), OSAKA_FONT);

        float totalWidth = textWidth(traditionText, scaledScale, OSAKA_FONT);
        float totalHeight = textHeight(scaledScale, OSAKA_FONT);

        setBounds(totalWidth, totalHeight);
    }

}
