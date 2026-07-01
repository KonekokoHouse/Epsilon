package com.github.epsilon.settings.impl;

import com.github.epsilon.settings.Setting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.*;

public class StorageBlockListSetting extends Setting<List<BlockEntityType<?>>> {

    public static final BlockEntityType<?>[] STORAGE_BLOCKS = {
            BlockEntityType.BARREL,
            BlockEntityType.BLAST_FURNACE,
            BlockEntityType.BREWING_STAND,
            BlockEntityType.CAMPFIRE,
            BlockEntityType.CHEST,
            BlockEntityType.CHISELED_BOOKSHELF,
            BlockEntityType.CRAFTER,
            BlockEntityType.DISPENSER,
            BlockEntityType.DECORATED_POT,
            BlockEntityType.DROPPER,
            BlockEntityType.ENDER_CHEST,
            BlockEntityType.FURNACE,
            BlockEntityType.HOPPER,
            BlockEntityType.SHULKER_BOX,
            BlockEntityType.SMOKER,
            BlockEntityType.TRAPPED_CHEST
    };

    public StorageBlockListSetting(String name, Collection<BlockEntityType<?>> defaultValue, Dependency dependency) {
        super(name, dependency, null);
        this.defaultValue = normalize(defaultValue);
        this.value = new ArrayList<>(this.defaultValue);
    }

    @Override
    public void setValue(List<BlockEntityType<?>> value) {
        super.setValue(normalize(value));
    }

    @Override
    public void setValueSilently(List<BlockEntityType<?>> value) {
        super.setValueSilently(normalize(value));
    }

    @Override
    public void reset() {
        setValue(new ArrayList<>(defaultValue));
    }

    public boolean contains(BlockEntityType<?> type) {
        return value.contains(type);
    }

    public void add(BlockEntityType<?> type) {
        if (type == null || value.contains(type)) return;
        List<BlockEntityType<?>> next = new ArrayList<>(value);
        next.add(type);
        setValue(next);
    }

    public void remove(BlockEntityType<?> type) {
        if (!value.contains(type)) return;
        List<BlockEntityType<?>> next = new ArrayList<>(value);
        next.remove(type);
        setValue(next);
    }

    public void toggle(BlockEntityType<?> type) {
        if (contains(type)) {
            remove(type);
        } else {
            add(type);
        }
    }

    public List<String> getIds() {
        List<String> ids = new ArrayList<>();
        for (BlockEntityType<?> type : value) {
            Identifier id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type);
            if (id != null) ids.add(id.toString());
        }
        return ids;
    }

    public void setIds(Collection<String> ids) {
        List<BlockEntityType<?>> types = new ArrayList<>();
        if (ids != null) {
            for (String id : ids) {
                Identifier loc = Identifier.tryParse(id);
                if (loc == null) continue;
                BlockEntityType<?> type = BuiltInRegistries.BLOCK_ENTITY_TYPE.getOptional(loc).orElse(null);
                if (type != null) types.add(type);
            }
        }
        setValue(types);
    }

    public int size() {
        return value.size();
    }

    public boolean isEmpty() {
        return value.isEmpty();
    }

    private static List<BlockEntityType<?>> normalize(Collection<BlockEntityType<?>> source) {
        if (source == null) return new ArrayList<>();
        List<BlockEntityType<?>> types = new ArrayList<>();
        for (BlockEntityType<?> t : source) {
            if (t != null && !types.contains(t)) types.add(t);
        }
        return types;
    }
}
