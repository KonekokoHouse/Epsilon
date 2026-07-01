package com.github.epsilon.settings.impl;

import com.github.epsilon.settings.Setting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.*;
import java.util.function.Predicate;

public class ItemListSetting extends Setting<List<Item>> {

    private final Predicate<Item> filter;

    public ItemListSetting(String name, Collection<Item> defaultValue, Predicate<Item> filter, Dependency dependency) {
        super(name, dependency, null);
        this.filter = filter;
        this.defaultValue = normalize(defaultValue);
        this.value = new ArrayList<>(this.defaultValue);
    }

    @Override
    public void setValue(List<Item> value) {
        super.setValue(normalize(value));
    }

    @Override
    public void setValueSilently(List<Item> value) {
        super.setValueSilently(normalize(value));
    }

    @Override
    public void reset() {
        setValue(new ArrayList<>(defaultValue));
    }

    public boolean contains(Item item) {
        return value.contains(item);
    }

    public void add(Item item) {
        if (item == null || item == Items.AIR || value.contains(item)) return;
        if (filter != null && !filter.test(item)) return;
        List<Item> next = new ArrayList<>(value);
        next.add(item);
        setValue(next);
    }

    public void remove(Item item) {
        if (!value.contains(item)) return;
        List<Item> next = new ArrayList<>(value);
        next.remove(item);
        setValue(next);
    }

    public void toggle(Item item) {
        if (contains(item)) {
            remove(item);
        } else {
            add(item);
        }
    }

    public List<String> getIds() {
        List<String> ids = new ArrayList<>();
        for (Item item : value) {
            ids.add(BuiltInRegistries.ITEM.getKey(item).toString());
        }
        return ids;
    }

    public void setIds(Collection<String> ids) {
        List<Item> items = new ArrayList<>();
        if (ids != null) {
            for (String id : ids) {
                Identifier loc = Identifier.tryParse(id);
                if (loc == null) continue;
                Item item = BuiltInRegistries.ITEM.getOptional(loc).orElse(null);
                if (item != null && item != Items.AIR) {
                    if (filter == null || filter.test(item)) {
                        items.add(item);
                    }
                }
            }
        }
        setValue(items);
    }

    public int size() {
        return value.size();
    }

    public boolean isEmpty() {
        return value.isEmpty();
    }

    public Predicate<Item> getFilter() {
        return filter;
    }

    private static List<Item> normalize(Collection<Item> source) {
        List<Item> items = new ArrayList<>();
        if (source == null) return items;
        for (Item item : source) {
            if (item != null && item != Items.AIR && !items.contains(item)) {
                items.add(item);
            }
        }
        return items;
    }
}
