package com.github.epsilon.settings.impl;

import com.github.epsilon.settings.Setting;

import java.util.*;

public class EnchantmentListSetting extends Setting<List<String>> {

    public EnchantmentListSetting(String name, Collection<String> defaultValue, Dependency dependency) {
        super(name, dependency, null);
        this.defaultValue = normalize(defaultValue);
        this.value = new ArrayList<>(this.defaultValue);
    }

    @Override
    public void setValue(List<String> value) {
        super.setValue(normalize(value));
    }

    @Override
    public void setValueSilently(List<String> value) {
        super.setValueSilently(normalize(value));
    }

    @Override
    public void reset() {
        setValue(new ArrayList<>(defaultValue));
    }

    public boolean contains(String enchantmentId) {
        return value.contains(enchantmentId);
    }

    public void add(String enchantmentId) {
        if (enchantmentId == null || value.contains(enchantmentId)) return;
        List<String> next = new ArrayList<>(value);
        next.add(enchantmentId);
        setValue(next);
    }

    public void remove(String enchantmentId) {
        if (!value.contains(enchantmentId)) return;
        List<String> next = new ArrayList<>(value);
        next.remove(enchantmentId);
        setValue(next);
    }

    public void toggle(String enchantmentId) {
        if (contains(enchantmentId)) {
            remove(enchantmentId);
        } else {
            add(enchantmentId);
        }
    }

    public List<String> getIds() {
        return new ArrayList<>(value);
    }

    public void setIds(Collection<String> ids) {
        setValue(new ArrayList<>(ids));
    }

    public int size() {
        return value.size();
    }

    public boolean isEmpty() {
        return value.isEmpty();
    }

    private static List<String> normalize(Collection<String> source) {
        if (source == null) return new ArrayList<>();
        List<String> result = new ArrayList<>();
        for (String s : source) {
            if (s != null && !result.contains(s)) result.add(s);
        }
        return result;
    }
}
