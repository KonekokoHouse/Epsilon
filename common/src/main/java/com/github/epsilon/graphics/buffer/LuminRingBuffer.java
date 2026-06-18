package com.github.epsilon.graphics.buffer;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.MappableRingBuffer;

import java.nio.ByteBuffer;

/**
 * 1) 在支持 GL_MAP_PERSISTENT_BIT GL_MAP_FLUSH_EXPLICIT_BIT 的情况下不会执行 glUnmapBuffer 只会调用 Flush
 * <p>
 * 2) 在均不支持的情况下 会退化至 glBufferData + glMapBufferRange + glUnmapBuffer
 */
public class LuminRingBuffer {

    private final MappableRingBuffer ringBuffer;

    private GpuBufferSlice.MappedView mappedBuffer;

    private boolean mapped;

    public LuminRingBuffer(long size, @GpuBuffer.Usage int usage) {
        ringBuffer = new MappableRingBuffer(() -> "lumin-ring-buffer",
                GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_COPY_DST | usage,
                (int) size);

        tryMap();
    }

    public boolean isMapped() {
        return mapped;
    }

    public ByteBuffer getMappedBuffer() {
        return mappedBuffer.data();
    }

    /**
     * 尝试 Map 此 Buffer
     * 如已经 Map 则不会执行
     */
    public void tryMap() {
        if (mapped) return;
        mappedBuffer = ringBuffer.currentBuffer().map(
                false, true
        );
        mapped = true;
    }

    /**
     * 调用 Blaze3D 的 unmap
     * 在支持 GL_MAP_PERSISTENT_BIT GL_MAP_FLUSH_EXPLICIT_BIT 的情况下不会执行 Unmap 只会调用 Flush
     * 在均不支持的情况下 会退化至 glBufferData + glMapBufferRange + glUnmapBuffer
     */
    public void unmap() {
        if (!mapped) return;
        mappedBuffer.close();
        mappedBuffer = null;
        mapped = false;
    }

    public void rotate() {
        ringBuffer.rotate();
    }

    /**
     * @see #unmap()
     */
    public GpuBuffer unmapAndRotate() {
        final GpuBuffer lastGpuBuffer = ringBuffer.currentBuffer();
        mappedBuffer.close();
        mapped = false;
        ringBuffer.rotate();
        return lastGpuBuffer;
    }

    public GpuBuffer getGpuBuffer() {
        return ringBuffer.currentBuffer();
    }

    public void close() {
        if (mapped) unmap();
        ringBuffer.close();
    }

}
