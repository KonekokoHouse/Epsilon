package com.github.epsilon.graphics.text.ttf;

import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.LuminTexture;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicInteger;

public class TtfGlyphAtlas {

    private static final int SIZE = 512;
    private static final int GLYPH_GUTTER = 2;
    private static final int UV_INSET = 1;
    private static final long TEXTURE_UPLOAD_ALIGNMENT = 4L;
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
                GpuFormat.R8_UNORM,
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
        fillTextureWithTransparentDistance(texture);
        Minecraft.getInstance().getTextureManager().register(this.textureId, this.texture);
    }

    private static void fillTextureWithTransparentDistance(GpuTexture texture) {
        ByteBuffer transparent = MemoryUtil.memAlloc(SIZE * SIZE);
        try {
            MemoryUtil.memSet(MemoryUtil.memAddress(transparent), 0xFF, SIZE * SIZE);
            uploadTexture(texture, transparent, 0, 0, SIZE, SIZE);
        } finally {
            MemoryUtil.memFree(transparent);
        }
    }

    private static void uploadTexture(GpuTexture texture, ByteBuffer source, int destX, int destY, int width, int height) {
        int byteCount = width * height * texture.getFormat().blockSize();
        long uploadSize = roundToward(byteCount, TEXTURE_UPLOAD_ALIGNMENT);
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

        if (LuminRenderSystem.isVulkan()) {
            // 不使用 CommandEncoder.writeToTexture 因为 26.2 的 Vulkan 后端会以 1 字节对齐分配 staging，
            // 连续上传 R8 字体 atlas 后可能让后续 RGBA 纹理拷贝的 bufferOffset 不是 4 字节对齐，触发错误导致其他纹理无法上传。
            try (GpuBufferSlice.MappedView staging = encoder.transientMemory().allocateStaging(
                    uploadSize,
                    TEXTURE_UPLOAD_ALIGNMENT,
                    GpuBuffer.USAGE_COPY_SRC,
                    uploadSize,
                    TEXTURE_UPLOAD_ALIGNMENT
            )) {
                MemoryUtil.memCopy(MemoryUtil.memAddress(source), MemoryUtil.memAddress(staging.data()), byteCount);
                encoder.copyBufferToTexture(
                        staging.slice(),
                        0,
                        0,
                        width,
                        height,
                        texture,
                        destX,
                        destY,
                        width,
                        height,
                        0,
                        0
                );
            }
        } else {
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(
                    texture,
                    source,
                    0,
                    0,
                    destX, destY,
                    width,
                    height
            );
        }


    }

    private static long roundToward(long value, long alignment) {
        long remainder = value % alignment;
        return remainder == 0 ? value : value + alignment - remainder;
    }

    /**
     * Try to append a glyph to atlas
     * <p>
     * Return null if glyph atlas is full
     */
    public GlyphUV appendGlyph(TtfGlyph glyph) {
        if (glyph.glyphData() == null) return null;

        int cellWidth = glyph.width() + GLYPH_GUTTER * 2;
        int cellHeight = glyph.height() + GLYPH_GUTTER * 2;

        if (currentX + cellWidth >= SIZE) {
            currentX = 0;
            currentY += currentRowHeight;
            currentRowHeight = 0;
        }

        // Return null if glyph atlas is full
        if (currentY + cellHeight >= SIZE) {
            return null;
        }

        int glyphX = currentX + GLYPH_GUTTER;
        int glyphY = currentY + GLYPH_GUTTER;

        uploadTexture(
                this.texture.getTexture(),
                glyph.glyphData(),
                glyphX, glyphY,
                glyph.width(),
                glyph.height()
        );

        GlyphUV uv = new GlyphUV(
                (float) (glyphX + UV_INSET) / SIZE,
                (float) (glyphY + UV_INSET) / SIZE,
                (float) (glyphX + glyph.width() - UV_INSET) / SIZE,
                (float) (glyphY + glyph.height() - UV_INSET) / SIZE
        );

        currentX += cellWidth;
        currentRowHeight = Math.max(currentRowHeight, cellHeight);

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
