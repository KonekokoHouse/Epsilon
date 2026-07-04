package com.github.epsilon.settings.impl;

import net.minecraft.world.level.block.Block;

import java.util.Collection;
import java.util.function.Predicate;

public class BlockListSetting extends RegistryListSetting<Block> {

    public BlockListSetting(String name, Collection<Block> defaultValue, Dependency dependency) {
        this(name, defaultValue, null, dependency);
    }

    public BlockListSetting(String name, Collection<Block> defaultValue,
                            Predicate<Block> filter, Dependency dependency) {
        super(name, defaultValue, Type.BLOCK, filter, dependency);
    }
}
