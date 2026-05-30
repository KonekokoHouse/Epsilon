package com.github.epsilon.graphics.renderers;

import com.github.epsilon.assets.holders.RendererHolder;
import com.github.epsilon.assets.holders.TextureCacheHolder;
import com.github.epsilon.graphics.LuminRenderPipelines;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.LuminTexture;
import com.github.epsilon.graphics.buffer.LuminRingBuffer;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.*;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.awt.*;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import static com.github.epsilon.Constants.mc;

public class TextureRenderer implements IRenderer {

    private static final int STRIDE = 56;
    private static final long BUFFER_SIZE = 32 * 1024;

    private final Map<Object, Batch> batches = new LinkedHashMap<>();

    private TextureRenderer() {
    }

    public static TextureRenderer create() {
        return RendererHolder.INSTANCE.register(new TextureRenderer());
    }

    public void addQuadTexture(LuminTexture texture, float x, float y, float width, float height, float u0, float v0, float u1, float v1, Color color) {
        addRoundedTexture(texture, x, y, width, height, 0f, u0, v0, u1, v1, color);
    }

    public void addQuadTexture(Identifier texture, float x, float y, float width, float height, float u0, float v0, float u1, float v1, Color color) {
        addRoundedTexture(texture, x, y, width, height, 0f, u0, v0, u1, v1, color, false);
    }

    public void addQuadTexture(Identifier texture, float x, float y, float width, float height, float u0, float v0, float u1, float v1, Color color, boolean useLinearFilter) {
        addRoundedTexture(texture, x, y, width, height, 0f, u0, v0, u1, v1, color, useLinearFilter);
    }

    public void addRoundedTexture(Identifier texture, float x, float y, float width, float height, float radius, float u0, float v0, float u1, float v1, Color color) {
        addRoundedTexture(texture, x, y, width, height, radius, u0, v0, u1, v1, color, false);
    }

    public void addRoundedTexture(Identifier texture, float x, float y, float width, float height, float radius, float u0, float v0, float u1, float v1, Color color, boolean useLinearFilter) {
        addRoundedTexture((Object) texture, x, y, width, height, radius, radius, radius, radius, u0, v0, u1, v1, color, useLinearFilter);
    }

    public void addRoundedTexture(LuminTexture texture, float x, float y, float width, float height, float radius, float u0, float v0, float u1, float v1, Color color) {
        addRoundedTexture(texture, x, y, width, height, radius, radius, radius, radius, u0, v0, u1, v1, color, true);
    }

    public void addRoundedTexture(Identifier texture, float x, float y, float width, float height, float radiusTL, float radiusTR, float radiusBR, float radiusBL, float u0, float v0, float u1, float v1, Color color, boolean useLinearFilter) {
        addRoundedTexture((Object) texture, x, y, width, height, radiusTL, radiusTR, radiusBR, radiusBL, u0, v0, u1, v1, color, useLinearFilter);
    }

    public void addRoundedTexture(LuminTexture texture, float x, float y, float width, float height, float radiusTL, float radiusTR, float radiusBR, float radiusBL, float u0, float v0, float u1, float v1, Color color) {
        addRoundedTexture(texture, x, y, width, height, radiusTL, radiusTR, radiusBR, radiusBL, u0, v0, u1, v1, color, true);
    }

    public void addPlayerHead(LuminTexture texture, float x, float y, float size, float radius, Color color) {
        addRoundedTexture(texture, x, y, size, size, radius, 8f / 64f, 8f / 64f, 16f / 64f, 16f / 64f, color);
        addRoundedTexture(texture, x, y, size, size, radius, 40f / 64f, 8f / 64f, 48f / 64f, 16f / 64f, color);
    }

    public void addPlayerHead(Identifier texture, float x, float y, float size, float radius, Color color) {
        addRoundedTexture(texture, x, y, size, size, radius, 8f / 64f, 8f / 64f, 16f / 64f, 16f / 64f, color);
        addRoundedTexture(texture, x, y, size, size, radius, 40f / 64f, 8f / 64f, 48f / 64f, 16f / 64f, color);
    }

    private void addRoundedTexture(Object textureKey, float x, float y, float width, float height, float rTL, float rTR, float rBR, float rBL, float u0, float v0, float u1, float v1, Color color, boolean useLinearFilter) {
        Batch batch = batches.computeIfAbsent(textureKey, k -> {
            Batch b = new Batch(new LuminRingBuffer(BUFFER_SIZE, GpuBuffer.USAGE_VERTEX));
            b.useLinearFilter = useLinearFilter;
            return b;
        });

        batch.buffer.tryMap();

        if (batch.currentOffset + (long) STRIDE * 4L > BUFFER_SIZE) {
            return;
        }

        int argb = ARGB.toABGR(color.getRGB());

        float x2 = x + width;
        float y2 = y + height;

        long baseAddr = MemoryUtil.memAddress(batch.buffer.getMappedBuffer());
        long p = baseAddr + batch.currentOffset;

        writeVertex(p, x, y, u0, v0, argb, x, y, x2, y2, rTL, rTR, rBR, rBL);
        writeVertex(p + STRIDE, x, y2, u0, v1, argb, x, y, x2, y2, rTL, rTR, rBR, rBL);
        writeVertex(p + STRIDE * 2L, x2, y2, u1, v1, argb, x, y, x2, y2, rTL, rTR, rBR, rBL);
        writeVertex(p + STRIDE * 3L, x2, y, u1, v0, argb, x, y, x2, y2, rTL, rTR, rBR, rBL);

        batch.currentOffset += (long) STRIDE * 4L;
        batch.vertexCount += 4;
    }

    private void writeVertex(long addr, float x, float y, float u, float v, int color, float rx1, float ry1, float rx2, float ry2, float r1, float r2, float r3, float r4) {
        MemoryUtil.memPutFloat(addr, x);
        MemoryUtil.memPutFloat(addr + 4, y);
        MemoryUtil.memPutFloat(addr + 8, 0.0f); // z
        MemoryUtil.memPutInt(addr + 12, color);
        MemoryUtil.memPutFloat(addr + 16, u);
        MemoryUtil.memPutFloat(addr + 20, v);
        MemoryUtil.memPutFloat(addr + 24, rx1);
        MemoryUtil.memPutFloat(addr + 28, ry1);
        MemoryUtil.memPutFloat(addr + 32, rx2);
        MemoryUtil.memPutFloat(addr + 36, ry2);
        // Radius vector (TL, TR, BR, BL)
        MemoryUtil.memPutFloat(addr + 40, r1);
        MemoryUtil.memPutFloat(addr + 44, r2);
        MemoryUtil.memPutFloat(addr + 48, r3);
        MemoryUtil.memPutFloat(addr + 52, r4);
    }

    @Override
    public void draw() {
        if (batches.isEmpty()) return;

        LuminRenderSystem.applyOrthoProjection();

        GpuTextureView colorView = LuminRenderSystem.resolveColorView();
        if (colorView == null) return;

        GpuBufferSlice dynamicUniforms = LuminRenderSystem.writeTransform(
                RenderSystem.getModelViewMatrix(),
                new Vector4f(1, 1, 1, 1),
                new Vector3f(0, 0, 0),
                TextureTransform.DEFAULT_TEXTURING.getMatrix()
        );

        for (Map.Entry<Object, Batch> entry : batches.entrySet()) {
            Object textureKey = entry.getKey();
            Batch batch = entry.getValue();
            if (batch.vertexCount == 0) continue;

            if (batch.buffer.isMapped()) {
                batch.buffer.unmap();
            }

            int indexCount = (batch.vertexCount / 4) * 6;
            GpuBuffer ibo = LuminRenderSystem.getQuadIndexBuffer(indexCount);

            LuminTexture texture;
            if (textureKey instanceof Identifier id) {
                texture = TextureCacheHolder.INSTANCE.textureCache.computeIfAbsent(
                        id, key -> loadTexture(key, batch.useLinearFilter)
                );
            } else if (textureKey instanceof LuminTexture tex) {
                texture = tex;
            } else {
                continue;
            }

            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "Rounded Texture Draw",
                    colorView, OptionalInt.empty(),
                    null, OptionalDouble.empty())
            ) {
                pass.setPipeline(LuminRenderPipelines.TEXTURE);

                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", dynamicUniforms);

                // 使用 RingBuffer 当前指向的 GpuBuffer
                pass.setVertexBuffer(0, batch.buffer.getGpuBuffer());
                pass.setIndexBuffer(ibo, LuminRenderSystem.getQuadIndexType());
                pass.bindTexture("Sampler0", texture.getTextureView(), texture.getSampler());

                pass.drawIndexed(0, 0, indexCount, 1);
            }
        }
    }

    private LuminTexture loadTexture(Identifier identifier, boolean useLinearFilter) {
        AbstractTexture abstractTexture = mc.getTextureManager().getTexture(identifier);
        try {
            GpuTexture texture = abstractTexture.getTexture();
            GpuTextureView view = abstractTexture.getTextureView();
            GpuSampler sampler = abstractTexture.getSampler();
            return new LuminTexture(texture, view, sampler, false, false);
        } catch (Exception ignored) {
        }

        NativeImage image;
        try {
            var manager = mc.getResourceManager();
            var resource = manager.getResourceOrThrow(identifier);
            try (var stream = resource.open()) {
                image = NativeImage.read(stream);
            }
        } catch (IOException e) {
            image = MissingTextureAtlasSprite.generateMissingImage();
        }

        var device = RenderSystem.getDevice();
        GpuTexture texture = device.createTexture(identifier.toString(), GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, TextureFormat.RGBA8, image.getWidth(), image.getHeight(), 1, 1);

        device.createCommandEncoder().writeToTexture(texture, image);

        GpuTextureView view = device.createTextureView(texture);
        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(useLinearFilter ? FilterMode.LINEAR : FilterMode.NEAREST);

        image.close();

        return new LuminTexture(texture, view, sampler, true, false);
    }

    @Override
    public void clear() {
        for (Batch batch : batches.values()) {
            if (batch.vertexCount > 0) {
                if (batch.buffer.isMapped()) {
                    batch.buffer.unmap();
                }
                batch.buffer.rotate();
            }
            batch.currentOffset = 0;
            batch.vertexCount = 0;
        }
    }

    @Override
    public void close() {
        clear();
        for (Batch batch : batches.values()) {
            batch.buffer.close();
        }
        batches.clear();
        TextureCacheHolder.INSTANCE.clearCache();
    }

    private static final class Batch {
        final LuminRingBuffer buffer;
        long currentOffset = 0;
        int vertexCount = 0;
        boolean useLinearFilter;

        private Batch(LuminRingBuffer buffer) {
            this.buffer = buffer;
        }
    }

}