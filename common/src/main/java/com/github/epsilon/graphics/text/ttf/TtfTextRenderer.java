package com.github.epsilon.graphics.text.ttf;

import com.github.epsilon.graphics.LuminRenderPipelines;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.buffer.BufferUtils;
import com.github.epsilon.graphics.buffer.LuminRingBuffer;
import com.github.epsilon.graphics.text.GlyphDescriptor;
import com.github.epsilon.graphics.text.ITextRenderer;
import com.github.epsilon.modules.impl.ClientSetting;
import com.github.epsilon.utils.render.ColorUtils;
import com.github.epsilon.utils.render.ScissorUtils;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.util.ARGB;
import org.lwjgl.system.MemoryUtil;

import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public class TtfTextRenderer implements ITextRenderer {

    private static final float DEFAULT_SCALE = 0.27f;
    private static final float SPACING = 0f;
    private static final int STRIDE = 24;
    private static final long GLYPH_BYTES = STRIDE * 4L;
    private final long bufferSize;

    private final Map<TtfGlyphAtlas, Batch> batches = new LinkedHashMap<>();

    private boolean scissorEnabled = false;
    private int scissorX, scissorY, scissorW, scissorH;
    private GpuBufferSlice sharedDynamicUniforms;
    private int sharedMaxIndexCount;

    public TtfTextRenderer(long bufferSize) {
        this.bufferSize = bufferSize;
    }

    public TtfTextRenderer() {
        this(256 * 1024);
    }

    @Override
    public void addText(String text, float x, float y, float scale, Color color, TtfFontLoader fontLoader) {
        final var finalScale = scale * DEFAULT_SCALE;
        fontLoader.requestChars(text);
        fontLoader.drainReadyGlyphs();
        int argb = ARGB.toABGR(color.getRGB());

        float xOffset = 0f;
        float yOffset = 0f;

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == ' ') {
                xOffset += 3.0f * scale;
                continue;
            }
            if (ch == '\n') {
                xOffset = 0f;
                yOffset += fontLoader.fontFile.fontHeight * finalScale;
                continue;
            }

            GlyphDescriptor glyph = fontLoader.getGlyph(ch);
            if (glyph == null) continue;

            TtfGlyphAtlas atlas = glyph.atlas();

            Batch batch = batches.computeIfAbsent(atlas, k -> new Batch(new LuminRingBuffer(bufferSize, GpuBuffer.USAGE_VERTEX)));
            batch.buffer.ensureCapacity(batch.offsetInAtlas + GLYPH_BYTES);
            batch.buffer.tryMap();

            float baselineY = yOffset + y + (fontLoader.fontFile.pixelAscent * finalScale);
            float x1 = x + xOffset;
            float x2 = x1 + glyph.width() * finalScale;
            float y1 = baselineY + glyph.yOffset() * finalScale;
            float y2 = y1 + glyph.height() * finalScale;

            long baseAddr = MemoryUtil.memAddress(batch.buffer.getMappedBuffer());
            long p = baseAddr + batch.offsetInAtlas;

            BufferUtils.writeUvRectToAddr(p, x1, y1, glyph.uv().u0(), glyph.uv().v0(), argb);
            BufferUtils.writeUvRectToAddr(p + STRIDE, x1, y2, glyph.uv().u0(), glyph.uv().v1(), argb);
            BufferUtils.writeUvRectToAddr(p + STRIDE * 2, x2, y2, glyph.uv().u1(), glyph.uv().v1(), argb);
            BufferUtils.writeUvRectToAddr(p + STRIDE * 3, x2, y1, glyph.uv().u1(), glyph.uv().v0(), argb);

            batch.offsetInAtlas += GLYPH_BYTES;
            xOffset += glyph.advance() * finalScale + SPACING * scale;
        }
    }

    @Override
    public void addGradientText(String text, float x, float y, float scale, Color startColor, Color endColor, TtfFontLoader fontLoader) {
        final var finalScale = scale * DEFAULT_SCALE;
        fontLoader.requestChars(text);
        fontLoader.drainReadyGlyphs();

        float totalWidth = Math.max(getWidth(text, scale, fontLoader), 1.0f);
        float xOffset = 0f;
        float yOffset = 0f;

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == ' ') {
                xOffset += 3.0f * scale;
                continue;
            }
            if (ch == '\n') {
                xOffset = 0f;
                yOffset += fontLoader.fontFile.fontHeight * finalScale;
                continue;
            }

            GlyphDescriptor glyph = fontLoader.getGlyph(ch);
            if (glyph == null) continue;

            TtfGlyphAtlas atlas = glyph.atlas();
            Batch batch = batches.computeIfAbsent(atlas, k -> new Batch(new LuminRingBuffer(bufferSize, GpuBuffer.USAGE_VERTEX)));
            batch.buffer.ensureCapacity(batch.offsetInAtlas + GLYPH_BYTES);
            batch.buffer.tryMap();

            float baselineY = yOffset + y + (fontLoader.fontFile.pixelAscent * finalScale);
            float x1 = x + xOffset;
            float x2 = x1 + glyph.width() * finalScale;
            float y1 = baselineY + glyph.yOffset() * finalScale;
            float y2 = y1 + glyph.height() * finalScale;

            float leftProgress = Math.clamp(xOffset / totalWidth, 0.0f, 1.0f);
            float rightProgress = Math.clamp(((xOffset + glyph.advance() * finalScale) / totalWidth), 0.0f, 1.0f);
            int leftArgb = ARGB.toABGR(ColorUtils.interpolateColor(startColor, endColor, leftProgress).getRGB());
            int rightArgb = ARGB.toABGR(ColorUtils.interpolateColor(startColor, endColor, rightProgress).getRGB());

            long baseAddr = MemoryUtil.memAddress(batch.buffer.getMappedBuffer());
            long p = baseAddr + batch.offsetInAtlas;

            BufferUtils.writeUvRectToAddr(p, x1, y1, glyph.uv().u0(), glyph.uv().v0(), leftArgb);
            BufferUtils.writeUvRectToAddr(p + STRIDE, x1, y2, glyph.uv().u0(), glyph.uv().v1(), leftArgb);
            BufferUtils.writeUvRectToAddr(p + STRIDE * 2, x2, y2, glyph.uv().u1(), glyph.uv().v1(), rightArgb);
            BufferUtils.writeUvRectToAddr(p + STRIDE * 3, x2, y1, glyph.uv().u1(), glyph.uv().v0(), rightArgb);

            batch.offsetInAtlas += GLYPH_BYTES;
            xOffset += glyph.advance() * finalScale + SPACING * scale;
        }
    }

    @Override
    public void draw() {
        if (batches.isEmpty()) return;

        LuminRenderSystem.applyOrthoProjection();

        GpuTextureView colorView = LuminRenderSystem.resolveColorView();
        GpuTextureView depthView = LuminRenderSystem.resolveDepthView();
        if (colorView == null) return;
        if (scissorEnabled && !ScissorUtils.isVisible(scissorW, scissorH)) return;

        int maxIndexCount = prepareTextBatches();
        if (maxIndexCount == 0) return;

        GpuBufferSlice dynamicUniforms = LuminRenderSystem.writeDefaultGuiTransform();
        GpuBuffer ibo = LuminRenderSystem.getQuadIndexBuffer(maxIndexCount);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Lumin TTF Draws",
                colorView, OptionalInt.empty(),
                depthView, OptionalDouble.empty())
        ) {
            pass.setPipeline(ClientSetting.INSTANCE.fontAntiAliasing.getValue()
                    ? LuminRenderPipelines.TTF_FONT_AA
                    : LuminRenderPipelines.TTF_FONT_NO_AA);
            if (scissorEnabled) {
                ScissorUtils.enableScissor(pass, scissorX, scissorY, scissorW, scissorH);
            }

            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", dynamicUniforms);
            pass.setIndexBuffer(ibo, LuminRenderSystem.getQuadIndexType());

            drawPrepared(pass);
        }
    }

    @Override
    public boolean prepareSharedDraw() {
        sharedDynamicUniforms = null;
        sharedMaxIndexCount = 0;
        if (batches.isEmpty()) return false;
        if (scissorEnabled && !ScissorUtils.isVisible(scissorW, scissorH)) return false;

        sharedMaxIndexCount = prepareTextBatches();
        if (sharedMaxIndexCount == 0) return false;

        LuminRenderSystem.getQuadIndexBuffer(sharedMaxIndexCount);
        sharedDynamicUniforms = LuminRenderSystem.writeDefaultGuiTransform();
        return sharedDynamicUniforms != null;
    }

    @Override
    public void draw(RenderPass pass) {
        if (sharedDynamicUniforms == null || sharedMaxIndexCount == 0) return;

        GpuBuffer ibo = LuminRenderSystem.getQuadIndexBuffer(sharedMaxIndexCount);
        pass.setIndexBuffer(ibo, LuminRenderSystem.getQuadIndexType());
        pass.setUniform("DynamicTransforms", sharedDynamicUniforms);
        drawPrepared(pass);
    }

    private int prepareTextBatches() {
        int maxIndexCount = 0;
        for (Batch batch : batches.values()) {
            if (batch.offsetInAtlas == 0) continue;
            if (batch.buffer.isMapped()) {
                batch.buffer.unmap();
            }

            int vertexCount = (int) (batch.offsetInAtlas / STRIDE);
            maxIndexCount = Math.max(maxIndexCount, (vertexCount / 4) * 6);
        }
        return maxIndexCount;
    }

    private void drawPrepared(RenderPass pass) {
        if (scissorEnabled) {
            if (!ScissorUtils.enableScissor(pass, scissorX, scissorY, scissorW, scissorH)) {
                return;
            }
        } else {
            pass.disableScissor();
        }

        // 不同 atlas 共享同一字体 pipeline，在同一个 pass 内只切换纹理并连续 draw。
        for (Map.Entry<TtfGlyphAtlas, Batch> entry : batches.entrySet()) {
            final var atlas = entry.getKey();
            final var batch = entry.getValue();

            if (batch.offsetInAtlas == 0) continue;

            int vertexCount = (int) (batch.offsetInAtlas / STRIDE);
            int indexCount = (vertexCount / 4) * 6;

            pass.setVertexBuffer(0, batch.buffer.getGpuBuffer());
            pass.bindTexture("Sampler0", atlas.getTexture().getTextureView(), atlas.getTexture().getSampler());

            pass.drawIndexed(0, 0, indexCount, 1);
        }
    }

    @Override
    public void clear() {
        for (Batch batch : batches.values()) {
            if (batch.offsetInAtlas > 0) {
                if (batch.buffer.isMapped()) {
                    batch.buffer.unmap();
                }
                batch.buffer.rotate();
            }
            batch.offsetInAtlas = 0;
        }
        sharedDynamicUniforms = null;
        sharedMaxIndexCount = 0;
    }

    @Override
    public void close() {
        clear();
        for (Batch batch : batches.values()) {
            batch.buffer.close();
        }
        batches.clear();
    }

    @Override
    public float getHeight(float scale, TtfFontLoader fontLoader) {
        return fontLoader.fontFile.fontHeight * DEFAULT_SCALE * scale;
    }

    @Override
    public float getWidth(String text, float scale, TtfFontLoader fontLoader) {
        fontLoader.checkAndLoadChars(text);
        final var finalScale = scale * DEFAULT_SCALE;
        float maxLine = 0.0f;
        float currentLine = 0.0f;

        for (char ch : text.toCharArray()) {
            if (ch == ' ') {
                currentLine += 3.0f * scale;
            } else if (ch == '\n') {
                maxLine = Math.max(maxLine, currentLine);
                currentLine = 0.0f;
            } else {
                GlyphDescriptor glyph = fontLoader.getGlyph(ch);
                if (glyph != null) {
                    currentLine += glyph.advance() * finalScale + SPACING * scale;
                }
            }
        }
        return Math.max(maxLine, currentLine);
    }

    @Override
    public void setScissor(int x, int y, int width, int height) {
        LuminRenderSystem.ScissorRect scissor = ScissorUtils.clampFramebufferScissor(x, y, width, height);
        scissorEnabled = true;
        scissorX = scissor.x();
        scissorY = scissor.y();
        scissorW = scissor.width();
        scissorH = scissor.height();
    }

    @Override
    public void clearScissor() {
        scissorEnabled = false;
    }

    private static final class Batch {
        final LuminRingBuffer buffer;
        long offsetInAtlas = 0;

        private Batch(LuminRingBuffer buffer) {
            this.buffer = buffer;
        }
    }

}
