package com.github.epsilon.settings.impl;

import com.github.epsilon.settings.Setting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;

import java.util.*;

public class StatusEffectListSetting extends Setting<List<MobEffect>> {

    public StatusEffectListSetting(String name, Collection<MobEffect> defaultValue, Dependency dependency) {
        super(name, dependency, null);
        this.defaultValue = normalize(defaultValue);
        this.value = new ArrayList<>(this.defaultValue);
    }

    @Override
    public void setValue(List<MobEffect> value) {
        super.setValue(normalize(value));
    }

    @Override
    public void setValueSilently(List<MobEffect> value) {
        super.setValueSilently(normalize(value));
    }

    @Override
    public void reset() {
        setValue(new ArrayList<>(defaultValue));
    }

    public boolean contains(MobEffect effect) {
        return value.contains(effect);
    }

    public void add(MobEffect effect) {
        if (effect == null || value.contains(effect)) return;
        List<MobEffect> next = new ArrayList<>(value);
        next.add(effect);
        setValue(next);
    }

    public void remove(MobEffect effect) {
        if (!value.contains(effect)) return;
        List<MobEffect> next = new ArrayList<>(value);
        next.remove(effect);
        setValue(next);
    }

    public void toggle(MobEffect effect) {
        if (contains(effect)) {
            remove(effect);
        } else {
            add(effect);
        }
    }

    public List<String> getIds() {
        List<String> ids = new ArrayList<>();
        for (MobEffect effect : value) {
            Identifier id = BuiltInRegistries.MOB_EFFECT.getKey(effect);
            if (id != null) ids.add(id.toString());
        }
        return ids;
    }

    public void setIds(Collection<String> ids) {
        List<MobEffect> effects = new ArrayList<>();
        if (ids != null) {
            for (String id : ids) {
                Identifier loc = Identifier.tryParse(id);
                if (loc == null) continue;
                MobEffect effect = BuiltInRegistries.MOB_EFFECT.getOptional(loc).orElse(null);
                if (effect != null) effects.add(effect);
            }
        }
        setValue(effects);
    }

    public int size() {
        return value.size();
    }

    public boolean isEmpty() {
        return value.isEmpty();
    }

    private static List<MobEffect> normalize(Collection<MobEffect> source) {
        if (source == null) return new ArrayList<>();
        List<MobEffect> effects = new ArrayList<>();
        for (MobEffect e : source) {
            if (e != null && !effects.contains(e)) effects.add(e);
        }
        return effects;
    }
}
