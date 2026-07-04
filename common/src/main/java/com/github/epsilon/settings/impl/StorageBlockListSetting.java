package com.github.epsilon.settings.impl;

import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.Collection;

public class StorageBlockListSetting extends RegistryListSetting<BlockEntityType<?>> {

    public static final BlockEntityType<?>[] STORAGE_BLOCKS = {
            BlockEntityType.BARREL,
            BlockEntityType.BLAST_FURNACE,
            BlockEntityType.BREWING_STAND,
            BlockEntityType.CAMPFIRE,
            BlockEntityType.CHEST,
            BlockEntityType.CHISELED_BOOKSHELF,
            BlockEntityType.CRAFTER,
            BlockEntityType.DECORATED_POT,
            BlockEntityType.DISPENSER,
            BlockEntityType.DROPPER,
            BlockEntityType.ENDER_CHEST,
            BlockEntityType.FURNACE,
            BlockEntityType.HOPPER,
            BlockEntityType.SHULKER_BOX,
            BlockEntityType.SMOKER,
            BlockEntityType.TRAPPED_CHEST
    };

    public StorageBlockListSetting(String name, Collection<BlockEntityType<?>> defaultValue, Dependency dependency) {
        super(name, defaultValue, Type.BLOCK_ENTITY_TYPE, null, dependency);
    }
}
