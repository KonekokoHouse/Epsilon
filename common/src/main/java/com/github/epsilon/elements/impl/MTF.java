package com.github.epsilon.elements.impl;

import com.github.epsilon.elements.HudModule;
import com.github.slmpc.lumingraphics.mc.v2612.runtime.MinecraftGlyphAtlasTexture2612;
import com.github.slmpc.lumingraphics.mc.v2612.runtime.MinecraftUiRuntime2612;
import com.github.slmpc.lumingraphics.text.emoji.EmojiGlyph;
import com.github.slmpc.lumingraphics.ui.geometry.UiRect;
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

        float originX = this.x + boxSize / 2.0f;
        float originY = this.y + boxSize / 2.0f;
        float rotation = (System.currentTimeMillis() % 3_600_000L) / 1000.0f * speed.getValue().floatValue();

        final String fishcake = "\uD83C\uDF65";
        EmojiGlyph glyph = MinecraftUiRuntime2612.current().systemEmojiAtlas().require(fishcake.codePointAt(0));
        MinecraftGlyphAtlasTexture2612 texture =
                (MinecraftGlyphAtlasTexture2612) glyph.atlas().upload().texture();

        renderScope().rotatedTexture(texture.minecraftId().toString(), new UiRect(this.x, this.y, boxSize, boxSize),
                glyph.uv().u0(), glyph.uv().v0(), glyph.uv().u1(), glyph.uv().v1(),
                lumin(color.getValue()), originX, originY, rotation);
    }

}
