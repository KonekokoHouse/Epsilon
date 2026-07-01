package com.github.epsilon.settings.impl;

import com.github.epsilon.settings.Setting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.function.Predicate;

public class BlockSetting extends Setting<Block> {

    private final Predicate<Block> filter;

    public BlockSetting(String name, Block defaultValue, Predicate<Block> filter, Dependency dependency) {
        super(name, dependency, null);
        this.filter = filter;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    @Override
    public void setValue(Block value) {
        if (value != null && (filter == null || filter.test(value))) {
            super.setValue(value);
        }
    }

    public Predicate<Block> getFilter() {
        return filter;
    }

    public String getId() {
        return BuiltInRegistries.BLOCK.getKey(getValue()).toString();
    }

    public void setId(String id) {
        Identifier loc = Identifier.tryParse(id);
        if (loc == null) return;
        Block block = BuiltInRegistries.BLOCK.getOptional(loc).orElse(Blocks.AIR);
        if (block != Blocks.AIR || id.equals("minecraft:air")) {
            setValue(block);
        }
    }
}
