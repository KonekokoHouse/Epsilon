package com.github.epsilon.settings.impl;

import com.github.epsilon.settings.Setting;
import net.minecraft.core.BlockPos;

public class BlockPosSetting extends Setting<BlockPos> {

    public BlockPosSetting(String name, BlockPos defaultValue, Dependency dependency) {
        super(name, dependency, null);
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    @Override
    public void reset() {
        if (value == null) {
            value = defaultValue;
        } else {
            value = new BlockPos(defaultValue.getX(), defaultValue.getY(), defaultValue.getZ());
        }
    }

    public void set(int x, int y, int z) {
        setValue(new BlockPos(x, y, z));
    }

    public int getX() {
        return value.getX();
    }

    public int getY() {
        return value.getY();
    }

    public int getZ() {
        return value.getZ();
    }
}
