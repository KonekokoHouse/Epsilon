package com.github.epsilon.settings.impl;

import com.github.epsilon.settings.Setting;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.*;

public class ParticleTypeListSetting extends Setting<List<ParticleType<?>>> {

    public ParticleTypeListSetting(String name, Collection<ParticleType<?>> defaultValue, Dependency dependency) {
        super(name, dependency, null);
        this.defaultValue = normalize(defaultValue);
        this.value = new ArrayList<>(this.defaultValue);
    }

    @Override
    public void setValue(List<ParticleType<?>> value) {
        super.setValue(normalize(value));
    }

    @Override
    public void setValueSilently(List<ParticleType<?>> value) {
        super.setValueSilently(normalize(value));
    }

    @Override
    public void reset() {
        setValue(new ArrayList<>(defaultValue));
    }

    public boolean contains(ParticleType<?> particleType) {
        return value.contains(particleType);
    }

    public void add(ParticleType<?> particleType) {
        if (particleType == null || value.contains(particleType)) return;
        List<ParticleType<?>> next = new ArrayList<>(value);
        next.add(particleType);
        setValue(next);
    }

    public void remove(ParticleType<?> particleType) {
        if (!value.contains(particleType)) return;
        List<ParticleType<?>> next = new ArrayList<>(value);
        next.remove(particleType);
        setValue(next);
    }

    public void toggle(ParticleType<?> particleType) {
        if (contains(particleType)) {
            remove(particleType);
        } else {
            add(particleType);
        }
    }

    public List<String> getIds() {
        List<String> ids = new ArrayList<>();
        for (ParticleType<?> particleType : value) {
            Identifier id = BuiltInRegistries.PARTICLE_TYPE.getKey(particleType);
            if (id != null) ids.add(id.toString());
        }
        return ids;
    }

    public void setIds(Collection<String> ids) {
        List<ParticleType<?>> types = new ArrayList<>();
        if (ids != null) {
            for (String id : ids) {
                Identifier loc = Identifier.tryParse(id);
                if (loc == null) continue;
                ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.getOptional(loc).orElse(null);
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

    private static List<ParticleType<?>> normalize(Collection<ParticleType<?>> source) {
        if (source == null) return new ArrayList<>();
        List<ParticleType<?>> types = new ArrayList<>();
        for (ParticleType<?> t : source) {
            if (t != null && !types.contains(t)) types.add(t);
        }
        return types;
    }
}
