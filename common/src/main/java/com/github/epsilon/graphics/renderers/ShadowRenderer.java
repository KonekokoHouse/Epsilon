package com.github.epsilon.graphics.renderers;

import com.github.epsilon.graphics.LuminRenderPipelines;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.buffer.LuminRingBuffer;
import com.github.epsilon.holders.RendererHolder;
import com.github.epsilon.utils.render.ScissorUtils;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.util.ARGB;
import org.lwjgl.system.MemoryUtil;

import java.awt.*;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public class ShadowRenderer implements IRenderer {

    private static final long BUFFER_SIZE = 256 * 1024;
    private final LuminRingBuffer buffer = new LuminRingBuffer(BUFFER_SIZE, GpuBuffer.USAGE_VERTEX);

    private boolean scissorEnabled = false;
    private int scissorX, scissorY, scissorW, scissorH;
    private long currentOffset = 0;
    private int vertexCount = 0;

    private ShadowRenderer() {
    }

    public static ShadowRenderer create() {
        return RendererHolder.INSTANCE.register(new ShadowRenderer());
    }

    public void addShadow(float x, float y, float width, float height, float radius, float blurRadius, Color color) {
        addShadow(x, y, width, height, radius, radius, radius, radius, blurRadius, color);
    }

    public void addShadow(float x, float y, float width, float height, float rTL, float rTR, float rBR, float rBL, float blurRadius, Color color) {
        buffer.tryMap();

        float vx = x - blurRadius;
        float vy = y - blurRadius;
        float vx2 = x + width + blurRadius;
        float vy2 = y + height + blurRadius;

        float bx2 = x + width;
        float by2 = y + height;

        int argb = ARGB.toABGR(color.getRGB());

        addVertex(vx, vy, x, y, bx2, by2, rTL, rTR, rBR, rBL, blurRadius, argb);
        addVertex(vx, vy2, x, y, bx2, by2, rTL, rTR, rBR, rBL, blurRadius, argb);
        addVertex(vx2, vy2, x, y, bx2, by2, rTL, rTR, rBR, rBL, blurRadius, argb);
        addVertex(vx2, vy, x, y, bx2, by2, rTL, rTR, rBR, rBL, blurRadius, argb);
    }

    private void addVertex(float vx, float vy, float rx1, float ry1, float rx2, float ry2, float r1, float r2, float r3, float r4, float blurRadius, int color) {
        long baseAddr = MemoryUtil.memAddress(buffer.getMappedBuffer());
        long p = baseAddr + currentOffset;

        MemoryUtil.memPutFloat(p, vx);
        MemoryUtil.memPutFloat(p + 4, vy);
        MemoryUtil.memPutFloat(p + 8, blurRadius);
        MemoryUtil.memPutInt(p + 12, color);

        MemoryUtil.memPutFloat(p + 16, rx1);
        MemoryUtil.memPutFloat(p + 20, ry1);
        MemoryUtil.memPutFloat(p + 24, rx2);
        MemoryUtil.memPutFloat(p + 28, ry2);

        MemoryUtil.memPutFloat(p + 32, r1);
        MemoryUtil.memPutFloat(p + 36, r2);
        MemoryUtil.memPutFloat(p + 40, r3);
        MemoryUtil.memPutFloat(p + 44, r4);

        currentOffset += 48;
        vertexCount++;
    }

    public void setScissor(int x, int y, int width, int height) {
        LuminRenderSystem.ScissorRect scissor = ScissorUtils.clampFramebufferScissor(x, y, width, height);
        scissorEnabled = true;
        scissorX = scissor.x();
        scissorY = scissor.y();
        scissorW = scissor.width();
        scissorH = scissor.height();
    }

    public void clearScissor() {
        scissorEnabled = false;
    }

    @Override
    public void draw() {
        if (vertexCount == 0) return;
        if (buffer.isMapped()) buffer.unmap();

        LuminRenderSystem.QuadRenderingInfo info = LuminRenderSystem.prepareQuadRendering(vertexCount);
        if (info == null || info.colorView() == null) return;
        if (scissorEnabled && !ScissorUtils.isVisible(scissorW, scissorH)) return;

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Lumin Shadow Draw", info.colorView(), OptionalInt.empty(),
                info.depthView(), OptionalDouble.empty())
        ) {
            pass.setPipeline(LuminRenderPipelines.SHADOW);
            if (scissorEnabled) ScissorUtils.enableScissor(pass, scissorX, scissorY, scissorW, scissorH);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", info.dynamicUniforms());
            pass.setVertexBuffer(0, buffer.getGpuBuffer());
            pass.setIndexBuffer(info.ibo(), info.indexType());
            pass.drawIndexed(0, 0, info.indexCount(), 1);
        }
    }

    @Override
    public void clear() {
        if (vertexCount > 0) {
            if (buffer.isMapped()) buffer.unmap();
            buffer.rotate();
        }
        vertexCount = 0;
        currentOffset = 0;
    }

    @Override
    public void close() {
        buffer.close();
    }

}
