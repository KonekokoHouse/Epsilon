package com.github.epsilon.graphics.buffer;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.DynamicUniformStorage;

public class LuminDynamicUniforms<T extends DynamicUniformStorage.DynamicUniform> implements AutoCloseable {

    private final DynamicUniformStorage<T> storage;

    public LuminDynamicUniforms(String label, int uniformSize, int initialCapacity) {
        this.storage = new DynamicUniformStorage<>(label, uniformSize, initialCapacity);
    }

    public GpuBufferSlice write(T uniform) {
        return storage.writeUniform(uniform);
    }

    public void endFrame() {
        storage.endFrame();
    }

    @Override
    public void close() {
        storage.close();
    }

}
