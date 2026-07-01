package com.github.epsilon.settings.impl;

import com.github.epsilon.settings.Setting;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ColorListSetting extends Setting<List<Color>> {

    public ColorListSetting(String name, Collection<Color> defaultValue, Dependency dependency) {
        super(name, dependency, null);
        this.defaultValue = normalize(defaultValue);
        this.value = new ArrayList<>(this.defaultValue);
    }

    @Override
    public void setValue(List<Color> value) {
        super.setValue(normalize(value));
    }

    @Override
    public void setValueSilently(List<Color> value) {
        super.setValueSilently(normalize(value));
    }

    @Override
    public void reset() {
        List<Color> copy = new ArrayList<>();
        for (Color c : defaultValue) {
            copy.add(new Color(c.getRGB(), true));
        }
        setValue(copy);
    }

    public boolean contains(Color color) {
        for (Color c : value) {
            if (c.getRGB() == color.getRGB()) return true;
        }
        return false;
    }

    public void add(Color color) {
        if (color == null) return;
        List<Color> next = new ArrayList<>(value);
        next.add(color);
        setValue(next);
    }

    public void remove(int index) {
        if (index < 0 || index >= value.size()) return;
        List<Color> next = new ArrayList<>(value);
        next.remove(index);
        setValue(next);
    }

    public void set(int index, Color color) {
        if (index < 0 || index >= value.size() || color == null) return;
        List<Color> next = new ArrayList<>(value);
        next.set(index, color);
        setValue(next);
    }

    public int size() {
        return value.size();
    }

    public boolean isEmpty() {
        return value.isEmpty();
    }

    private static List<Color> normalize(Collection<Color> source) {
        if (source == null) return new ArrayList<>();
        List<Color> result = new ArrayList<>();
        for (Color c : source) {
            if (c != null) result.add(c);
        }
        return result;
    }
}
