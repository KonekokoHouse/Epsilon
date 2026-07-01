package com.github.epsilon.settings.impl;

import com.github.epsilon.settings.Setting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.MenuType;

import java.util.*;

public class ScreenHandlerListSetting extends Setting<List<MenuType<?>>> {

    public ScreenHandlerListSetting(String name, Collection<MenuType<?>> defaultValue, Dependency dependency) {
        super(name, dependency, null);
        this.defaultValue = normalize(defaultValue);
        this.value = new ArrayList<>(this.defaultValue);
    }

    @Override
    public void setValue(List<MenuType<?>> value) {
        super.setValue(normalize(value));
    }

    @Override
    public void setValueSilently(List<MenuType<?>> value) {
        super.setValueSilently(normalize(value));
    }

    @Override
    public void reset() {
        setValue(new ArrayList<>(defaultValue));
    }

    public boolean contains(MenuType<?> type) {
        return value.contains(type);
    }

    public void add(MenuType<?> type) {
        if (type == null || value.contains(type)) return;
        List<MenuType<?>> next = new ArrayList<>(value);
        next.add(type);
        setValue(next);
    }

    public void remove(MenuType<?> type) {
        if (!value.contains(type)) return;
        List<MenuType<?>> next = new ArrayList<>(value);
        next.remove(type);
        setValue(next);
    }

    public void toggle(MenuType<?> type) {
        if (contains(type)) {
            remove(type);
        } else {
            add(type);
        }
    }

    public List<String> getIds() {
        List<String> ids = new ArrayList<>();
        for (MenuType<?> type : value) {
            Identifier id = BuiltInRegistries.MENU.getKey(type);
            if (id != null) ids.add(id.toString());
        }
        return ids;
    }

    public void setIds(Collection<String> ids) {
        List<MenuType<?>> types = new ArrayList<>();
        if (ids != null) {
            for (String id : ids) {
                Identifier loc = Identifier.tryParse(id);
                if (loc == null) continue;
                MenuType<?> type = BuiltInRegistries.MENU.getOptional(loc).orElse(null);
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

    private static List<MenuType<?>> normalize(Collection<MenuType<?>> source) {
        if (source == null) return new ArrayList<>();
        List<MenuType<?>> types = new ArrayList<>();
        for (MenuType<?> t : source) {
            if (t != null && !types.contains(t)) types.add(t);
        }
        return types;
    }
}
