package com.github.epsilon.graphics.text.ttf;

import com.github.epsilon.assets.resources.ResourceLocationUtils;
import net.minecraft.resources.Identifier;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public class TtfFontFile {

    private final ByteBuffer fontData;
    private final STBTTFontinfo fontInfo;

    private final int padding;

    public final float scale;
    public final int pixelAscent;
    public final int fontHeight;

    public TtfFontFile(Identifier ttfFile, int totalHeight, int padding) {
        fontData = ResourceLocationUtils.loadResource(ttfFile);

        fontInfo = STBTTFontinfo.create();

        if (!STBTruetype.stbtt_InitFont(fontInfo, fontData)) {
            MemoryUtil.memFree(fontData);
            throw new IllegalStateException("STB TrueType failed to load ttf font: " + ttfFile);
        }

        this.padding = padding;

        this.scale = STBTruetype.stbtt_ScaleForPixelHeight(fontInfo, totalHeight - padding * 2);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            final var ascentBuf = stack.callocInt(1);
            final var descentBuf = stack.callocInt(1);
            final var lineGapBuf = stack.mallocInt(1);
            STBTruetype.stbtt_GetFontVMetrics(fontInfo, ascentBuf, descentBuf, lineGapBuf);

            final var ascent = ascentBuf.get();
            final var descent = descentBuf.get();
            final var lineGap = lineGapBuf.get();

            this.pixelAscent = (int) (ascent * scale);
            this.fontHeight = (int) ((ascent - descent + lineGap) * scale);
        }

//        this.fontHeight = totalHeight - padding * 2;

    }

    public synchronized TtfGlyph generateGlyph(char ch) {
        final var glyphIndex = STBTruetype.stbtt_FindGlyphIndex(fontInfo, ch);

        byte onEdgeValue = (byte) 128;
        float pixelDistScale = (float) onEdgeValue / padding;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            final var width = stack.callocInt(1);
            final var height = stack.callocInt(1);
            final var xOff = stack.callocInt(1);
            final var yOff = stack.callocInt(1);

            ByteBuffer sdfPixels = STBTruetype.stbtt_GetGlyphSDF(
                    fontInfo,
                    scale,
                    glyphIndex,
                    padding,
                    onEdgeValue,
                    pixelDistScale,
                    width,
                    height,
                    xOff, yOff
            );

            final var advance = stack.callocInt(1);
            final var lsb = stack.callocInt(1);
            STBTruetype.stbtt_GetGlyphHMetrics(fontInfo, glyphIndex, advance, lsb);

            return new TtfGlyph(sdfPixels, width.get(), height.get(), xOff.get(), yOff.get(), (int) (advance.get() * scale));
        }
    }

    public void destroy() {
        MemoryUtil.memFree(fontData);
    }

}
