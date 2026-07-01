package com.github.epsilon.settings.impl;

import com.github.epsilon.settings.Setting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.function.Predicate;

public class ItemSetting extends Setting<Item> {

    private final Predicate<Item> filter;

    public ItemSetting(String name, Item defaultValue, Predicate<Item> filter, Dependency dependency) {
        super(name, dependency, null);
        this.filter = filter;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    @Override
    public void setValue(Item value) {
        if (value != null && (filter == null || filter.test(value))) {
            super.setValue(value);
        }
    }

    public Predicate<Item> getFilter() {
        return filter;
    }

    public String getId() {
        return BuiltInRegistries.ITEM.getKey(getValue()).toString();
    }

    public void setId(String id) {
        Identifier loc = Identifier.tryParse(id);
        if (loc == null) return;
        Item item = BuiltInRegistries.ITEM.getOptional(loc).orElse(Items.AIR);
        if (item != Items.AIR || id.equals("minecraft:air")) {
            setValue(item);
        }
    }
}
