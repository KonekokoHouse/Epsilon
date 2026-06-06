package com.github.epsilon.graphics.text.ttf;

import com.github.epsilon.graphics.LuminTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;

import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicInteger;

public class TtfGlyphAtlas {

    private static final int SIZE = 512;
    private static final AtomicInteger NEXT_TEXTURE_ID = new AtomicInteger();
    private final LuminTexture texture;
    private final Identifier textureId;

    private int currentX = 0;
    private int currentY = 0;
    private int currentRowHeight = 0;

    public TtfGlyphAtlas(int atlasId) {
        this.textureId = Identifier.fromNamespaceAndPath("epsilon", "ttf_atlas/" + NEXT_TEXTURE_ID.getAndIncrement());

        final var texture = RenderSystem.getDevice().createTexture(
                () -> "Lumin-TtfGlyphAtlas",
                GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST,
                TextureFormat.RED8,
                SIZE, SIZE,
                1, 1
        );

        final var textureView = RenderSystem.getDevice().createTextureView(texture);
        final var sampler = RenderSystem.getDevice().createSampler(
                AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                FilterMode.LINEAR, FilterMode.LINEAR,
                1, OptionalDouble.empty()
        );

        this.texture = new LuminTexture(texture, textureView, sampler);
        Minecraft.getInstance().getTextureManager().register(this.textureId, this.texture);
    }

    /**
     * Try to append a glyph to atlas
     * <p>
     * Return null if glyph atlas is full
     */
    public GlyphUV appendGlyph(TtfGlyph glyph) {
        if (glyph.glyphData() == null) return null;

        if (currentX + glyph.width() >= SIZE) {
            currentX = 0;
            currentY += currentRowHeight;
            currentRowHeight = 0;
        }

        // Return null if glyph atlas is full
        if (currentY + glyph.height() >= SIZE) {
            return null;
        }

        RenderSystem.getDevice().createCommandEncoder().writeToTexture(
                this.texture.getTexture(),
                glyph.glyphData(),
                NativeImage.Format.LUMINANCE,
                0,
                0,
                currentX, currentY,
                glyph.width(),
                glyph.height()
        );

        int spacePixel = 1;

        GlyphUV uv = new GlyphUV(
                (float) (currentX + spacePixel) / SIZE,
                (float) (currentY + spacePixel) / SIZE,
                (float) (currentX + glyph.width() - 2 * spacePixel) / SIZE,
                (float) (currentY + glyph.height() - 2 * spacePixel) / SIZE
        );

        currentX += glyph.width();
        currentRowHeight = Math.max(currentRowHeight, glyph.height());

        return uv;
    }

    public LuminTexture getTexture() {
        return texture;
    }

    public Identifier getTextureId() {
        return textureId;
    }

    public void destroy() {
        Minecraft.getInstance().getTextureManager().release(this.textureId);
    }

    public record GlyphUV(float u0, float v0, float u1, float v1) {
    }

}
