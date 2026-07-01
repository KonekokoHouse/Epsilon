package com.github.epsilon.settings.impl;

import com.github.epsilon.settings.Setting;

import java.util.*;

public class EnchantmentListSetting extends Setting<Set<String>> {

    public EnchantmentListSetting(String name, Set<String> defaultValue, Dependency dependency) {
        super(name, dependency, null);
        this.defaultValue = normalize(defaultValue);
        this.value = new LinkedHashSet<>(this.defaultValue);
    }

    @Override
    public void setValue(Set<String> value) {
        super.setValue(normalize(value));
    }

    @Override
    public void setValueSilently(Set<String> value) {
        super.setValueSilently(normalize(value));
    }

    @Override
    public void reset() {
        setValue(new LinkedHashSet<>(defaultValue));
    }

    public boolean contains(String enchantmentId) {
        return value.contains(enchantmentId);
    }

    public void add(String enchantmentId) {
        if (enchantmentId == null || value.contains(enchantmentId)) return;
        Set<String> next = new LinkedHashSet<>(value);
        next.add(enchantmentId);
        setValue(next);
    }

    public void remove(String enchantmentId) {
        if (!value.contains(enchantmentId)) return;
        Set<String> next = new LinkedHashSet<>(value);
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

    public Set<String> getIds() {
        return new LinkedHashSet<>(value);
    }

    public void setIds(Collection<String> ids) {
        setValue(new LinkedHashSet<>(ids));
    }

    public int size() {
        return value.size();
    }

    public boolean isEmpty() {
        return value.isEmpty();
    }

    private static Set<String> normalize(Set<String> source) {
        if (source == null) return new LinkedHashSet<>();
        return new LinkedHashSet<>(source);
    }
}
