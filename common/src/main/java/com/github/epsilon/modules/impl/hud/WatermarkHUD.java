package com.github.epsilon.modules.impl.hud;

import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.graphics.text.StaticFontLoader;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.HudModule;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.google.common.base.Suppliers;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.awt.*;
import java.util.function.Supplier;

public class WatermarkHUD extends HudModule {

    public static final WatermarkHUD INSTANCE = new WatermarkHUD();

    private WatermarkHUD() {
        super("Watermark HUD", Category.HUD, 0f, 0f, 200f, 28f);
    }

    private final DoubleSetting scale = doubleSetting("Scale", 1.0, 0.5, 2.0, 0.1);
    private final ColorSetting textColor = colorSetting("Text Color", new Color(255, 255, 255, 235));

    private final Supplier<TextRenderer> textRendererSupplier = Suppliers.memoize(TextRenderer::create);

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (nullCheck()) return;

        TextRenderer textRenderer = textRendererSupplier.get();

        String traditionText = "EPSILON";
        float scaledScale = scale.getValue().floatValue() * 2f; // 这个命名给我自己整笑了

        textRenderer.addText(traditionText, this.x, this.y, scaledScale, textColor.getValue(), StaticFontLoader.OSAKA_CHIPS);

        float totalWidth = textRenderer.getWidth(traditionText, scaledScale, StaticFontLoader.OSAKA_CHIPS) + 3f * scaledScale;
        float totalHeight = textRenderer.getHeight(scaledScale, StaticFontLoader.OSAKA_CHIPS) + 3f * scaledScale;

        textRenderer.drawAndClear();

        setBounds(totalWidth, totalHeight);
    }

}
