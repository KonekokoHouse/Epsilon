package com.github.epsilon.settings.impl;

import net.minecraft.world.item.Item;

import java.util.Collection;
import java.util.function.Predicate;

public class ItemListSetting extends RegistryListSetting<Item> {

    public ItemListSetting(String name, Collection<Item> defaultValue, Dependency dependency) {
        this(name, defaultValue, null, dependency);
    }

    public ItemListSetting(String name, Collection<Item> defaultValue,
                           Predicate<Item> filter, Dependency dependency) {
        super(name, defaultValue, Type.ITEM, filter, dependency);
    }
}
