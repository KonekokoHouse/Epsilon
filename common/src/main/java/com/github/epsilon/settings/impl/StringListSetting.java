package com.github.epsilon.settings.impl;

import com.github.epsilon.settings.Setting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class StringListSetting extends Setting<List<String>> {

    public StringListSetting(String name, Collection<String> defaultValue, Dependency dependency) {
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

    public boolean contains(String str) {
        return value.contains(str);
    }

    public void add(String str) {
        if (str == null || value.contains(str)) return;
        List<String> next = new ArrayList<>(value);
        next.add(str);
        setValue(next);
    }

    public void remove(String str) {
        if (!value.contains(str)) return;
        List<String> next = new ArrayList<>(value);
        next.remove(str);
        setValue(next);
    }

    public String get(int index) {
        return value.get(index);
    }

    public int size() {
        return value.size();
    }

    public boolean isEmpty() {
        return value.isEmpty();
    }

    private static List<String> normalize(Collection<String> source) {
        if (source == null) return new ArrayList<>();
        return new ArrayList<>(source);
    }
}
