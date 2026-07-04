package com.github.epsilon.settings.impl;

import com.github.epsilon.settings.Setting;
import com.github.epsilon.utils.world.BlockRegistryUtils;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.*;
import java.util.function.Predicate;

/**
 * 统一的注册表列表 Setting，通过 {@link Type} 枚举区分不同注册表类型。
 * 替代了原先分散的 BlockListSetting、ItemListSetting、EntityTypeListSetting 等。
 */
public class RegistryListSetting<T> extends Setting<List<T>> {

    public enum Type {
        BLOCK,
        ITEM,
        ENTITY_TYPE,
        SOUND_EVENT,
        PARTICLE_TYPE,
        MENU,
        MOB_EFFECT,
        BLOCK_ENTITY_TYPE,
        STRING_LIST,
        PACKET;

        @SuppressWarnings("unchecked")
        <T> Registry<T> registry() {
            if (this == STRING_LIST) return null;
            if (this == PACKET) return (Registry<T>) PacketListSetting.PACKET_REGISTRY;
            return (Registry<T>) switch (this) {
                case BLOCK -> BuiltInRegistries.BLOCK;
                case ITEM -> BuiltInRegistries.ITEM;
                case ENTITY_TYPE -> BuiltInRegistries.ENTITY_TYPE;
                case SOUND_EVENT -> BuiltInRegistries.SOUND_EVENT;
                case PARTICLE_TYPE -> BuiltInRegistries.PARTICLE_TYPE;
                case MENU -> BuiltInRegistries.MENU;
                case MOB_EFFECT -> BuiltInRegistries.MOB_EFFECT;
                case BLOCK_ENTITY_TYPE -> BuiltInRegistries.BLOCK_ENTITY_TYPE;
                default -> null;
            };
        }

        String toId(Object entry) {
            if (this == STRING_LIST) return (String) entry;
            if (this == PACKET) return ((Class<?>) entry).getName();
            Identifier key = registry().getKey(entry);
            return key != null ? key.toString() : "";
        }

        @SuppressWarnings("unchecked")
        <T> T fromId(String id) {
            if (this == STRING_LIST) return (T) id;
            if (this == PACKET) { try { return (T) Class.forName(id); } catch (ClassNotFoundException _) { return null; } }
            Identifier loc = Identifier.tryParse(id);
            if (loc == null) return null;
            return (T) registry().getOptional(loc).orElse(null);
        }

        @SuppressWarnings("unchecked")
        <T> Predicate<T> defaultFilter() {
            return (Predicate<T>) switch (this) {
                case BLOCK -> (Predicate<Block>) BlockRegistryUtils::isSelectable;
                case ITEM -> (Predicate<Item>) item -> item != null && item != Items.AIR;
                case ENTITY_TYPE -> (Predicate<EntityType<?>>) e -> e != null;
                case STRING_LIST -> (Predicate<String>) s -> s != null && !s.isBlank();
                default -> null;
            };
        }
    }

    private final Type registryType;
    private final Predicate<T> filter;

    public RegistryListSetting(String name, Collection<T> defaultValue, Type registryType,
                               Predicate<T> filter, Dependency dependency) {
        super(name, dependency, null);
        this.registryType = registryType;
        this.filter = filter;
        this.defaultValue = normalize(defaultValue);
        this.value = new ArrayList<>(this.defaultValue);
    }

    @Override
    public void setValue(List<T> value) {
        super.setValue(normalize(value));
    }

    @Override
    public void setValueSilently(List<T> value) {
        super.setValueSilently(normalize(value));
    }

    @Override
    public void reset() {
        setValue(new ArrayList<>(defaultValue));
    }

    public boolean contains(T entry) {
        return value.contains(entry);
    }

    public void add(T entry) {
        if (entry == null || value.contains(entry)) return;
        if (filter != null && !filter.test(entry)) return;
        List<T> next = new ArrayList<>(value);
        next.add(entry);
        setValue(next);
    }

    public void remove(T entry) {
        if (!value.contains(entry)) return;
        List<T> next = new ArrayList<>(value);
        next.remove(entry);
        setValue(next);
    }

    public void toggle(T entry) {
        if (contains(entry)) {
            remove(entry);
        } else {
            add(entry);
        }
    }

    public List<String> getIds() {
        List<String> ids = new ArrayList<>();
        for (T entry : value) {
            ids.add(registryType.toId(entry));
        }
        return ids;
    }

    public void setIds(Collection<String> ids) {
        List<T> entries = new ArrayList<>();
        if (ids != null) {
            for (String id : ids) {
                T entry = registryType.fromId(id);
                if (entry != null && (filter == null || filter.test(entry))) {
                    if (!entries.contains(entry)) entries.add(entry);
                }
            }
        }
        setValue(entries);
    }

    public int size() {
        return value.size();
    }

    public boolean isEmpty() {
        return value.isEmpty();
    }

    public Type getRegistryType() {
        return registryType;
    }

    public Predicate<T> getFilter() {
        return filter;
    }

    @SuppressWarnings("unchecked")
    private List<T> normalize(Collection<T> source) {
        if (source == null) return new ArrayList<>();
        // 为 BLOCK 类型使用 BlockRegistryUtils 的筛选逻辑
        if (registryType == Type.BLOCK) {
            List<Block> blocks = new ArrayList<>();
            for (Object o : source) {
                Block block = (Block) o;
                if (BlockRegistryUtils.isSelectable(block) && !blocks.contains(block)) {
                    blocks.add(block);
                }
            }
            return (List<T>) blocks;
        }
        List<T> result = new ArrayList<>();
        for (T e : source) {
            if (e != null && !result.contains(e)) result.add(e);
        }
        return result;
    }
}
