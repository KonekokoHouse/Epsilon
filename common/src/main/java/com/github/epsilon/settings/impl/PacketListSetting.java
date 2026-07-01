package com.github.epsilon.settings.impl;

import com.github.epsilon.settings.Setting;
import net.minecraft.network.protocol.Packet;

import java.util.*;
import java.util.function.Predicate;

public class PacketListSetting extends Setting<Set<Class<? extends Packet<?>>>> {

    private final Predicate<Class<? extends Packet<?>>> filter;

    public PacketListSetting(String name, Set<Class<? extends Packet<?>>> defaultValue,
                             Predicate<Class<? extends Packet<?>>> filter, Dependency dependency) {
        super(name, dependency, null);
        this.filter = filter;
        this.defaultValue = normalize(defaultValue);
        this.value = new LinkedHashSet<>(this.defaultValue);
    }

    @Override
    public void setValue(Set<Class<? extends Packet<?>>> value) {
        super.setValue(normalize(value));
    }

    @Override
    public void setValueSilently(Set<Class<? extends Packet<?>>> value) {
        super.setValueSilently(normalize(value));
    }

    @Override
    public void reset() {
        setValue(new LinkedHashSet<>(defaultValue));
    }

    public boolean contains(Class<? extends Packet<?>> packetClass) {
        return value.contains(packetClass);
    }

    public void add(Class<? extends Packet<?>> packetClass) {
        if (packetClass == null || value.contains(packetClass)) return;
        if (filter != null && !filter.test(packetClass)) return;
        Set<Class<? extends Packet<?>>> next = new LinkedHashSet<>(value);
        next.add(packetClass);
        setValue(next);
    }

    public void remove(Class<? extends Packet<?>> packetClass) {
        if (!value.contains(packetClass)) return;
        Set<Class<? extends Packet<?>>> next = new LinkedHashSet<>(value);
        next.remove(packetClass);
        setValue(next);
    }

    public void toggle(Class<? extends Packet<?>> packetClass) {
        if (contains(packetClass)) {
            remove(packetClass);
        } else {
            add(packetClass);
        }
    }

    public Set<String> getClassNames() {
        Set<String> names = new LinkedHashSet<>();
        for (Class<? extends Packet<?>> clazz : value) {
            names.add(clazz.getName());
        }
        return names;
    }

    @SuppressWarnings("unchecked")
    public void setClassNames(Collection<String> names) {
        Set<Class<? extends Packet<?>>> classes = new LinkedHashSet<>();
        if (names != null) {
            for (String name : names) {
                try {
                    Class<?> clazz = Class.forName(name);
                    if (Packet.class.isAssignableFrom(clazz)) {
                        Class<? extends Packet<?>> packetClass = (Class<? extends Packet<?>>) clazz;
                        if (filter == null || filter.test(packetClass)) {
                            classes.add(packetClass);
                        }
                    }
                } catch (ClassNotFoundException ignored) {
                }
            }
        }
        setValue(classes);
    }

    public int size() {
        return value.size();
    }

    public boolean isEmpty() {
        return value.isEmpty();
    }

    public Predicate<Class<? extends Packet<?>>> getFilter() {
        return filter;
    }

    private static Set<Class<? extends Packet<?>>> normalize(Set<Class<? extends Packet<?>>> source) {
        if (source == null) return new LinkedHashSet<>();
        return new LinkedHashSet<>(source);
    }
}
