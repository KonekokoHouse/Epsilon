package com.github.epsilon.graphics.buffer;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;

import java.nio.ByteBuffer;

public class LuminRingBuffer {

    private static final int BUFFER_COUNT = 8;

    private final GpuBuffer[] buffers = new GpuBuffer[BUFFER_COUNT];

    private GpuBufferSlice.MappedView mappedBuffer;
    private int current;
    private boolean mapped;

    public LuminRingBuffer(long size, @GpuBuffer.Usage int usage) {
        int bufferUsage = GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_COPY_DST | usage;
        for (int i = 0; i < buffers.length; i++) {
            int index = i;
            buffers[i] = RenderSystem.getDevice().createBuffer(() -> "lumin-ring-buffer #" + index, bufferUsage, size);
        }
    }

    public boolean isMapped() {
        return mapped;
    }

    public ByteBuffer getMappedBuffer() {
        return mappedBuffer.data();
    }

    public void tryMap() {
        if (mapped) return;
        mappedBuffer = getGpuBuffer().map(false, true);
        mapped = true;
    }

    public void unmap() {
        if (!mapped) return;
        mappedBuffer.close();
        mappedBuffer = null;
        mapped = false;
    }

    public void rotate() {
        current = (current + 1) % buffers.length;
    }

    public GpuBuffer unmapAndRotate() {
        GpuBuffer lastGpuBuffer = getGpuBuffer();
        unmap();
        rotate();
        return lastGpuBuffer;
    }

    public GpuBuffer getGpuBuffer() {
        return buffers[current];
    }

    public void close() {
        if (mapped) unmap();
        for (GpuBuffer buffer : buffers) {
            buffer.close();
        }
    }

}
