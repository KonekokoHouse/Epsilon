package com.github.epsilon.settings.impl;

import com.github.epsilon.settings.Setting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;

import java.util.HashMap;
import java.util.Map;

public class StatusEffectAmplifierMapSetting extends Setting<Map<MobEffect, Integer>> {

    public StatusEffectAmplifierMapSetting(String name, Map<MobEffect, Integer> defaultValue, Dependency dependency) {
        super(name, dependency, null);
        this.defaultValue = new HashMap<>(defaultValue);
        this.value = new HashMap<>(defaultValue);
    }

    @Override
    public void setValue(Map<MobEffect, Integer> value) {
        super.setValue(value != null ? new HashMap<>(value) : new HashMap<>());
    }

    @Override
    public void setValueSilently(Map<MobEffect, Integer> value) {
        super.setValueSilently(value != null ? new HashMap<>(value) : new HashMap<>());
    }

    @Override
    public void reset() {
        setValue(new HashMap<>(defaultValue));
    }

    public int getAmplifier(MobEffect effect) {
        return value.getOrDefault(effect, 0);
    }

    public void setAmplifier(MobEffect effect, int amplifier) {
        if (effect == null) return;
        Map<MobEffect, Integer> next = new HashMap<>(value);
        next.put(effect, amplifier);
        setValue(next);
    }

    public void remove(MobEffect effect) {
        Map<MobEffect, Integer> next = new HashMap<>(value);
        next.remove(effect);
        setValue(next);
    }

    public boolean contains(MobEffect effect) {
        return value.containsKey(effect);
    }

    public Map<String, Integer> getSerializableMap() {
        Map<String, Integer> result = new HashMap<>();
        for (Map.Entry<MobEffect, Integer> entry : value.entrySet()) {
            Identifier id = BuiltInRegistries.MOB_EFFECT.getKey(entry.getKey());
            if (id != null) result.put(id.toString(), entry.getValue());
        }
        return result;
    }

    public void setFromSerializableMap(Map<String, Integer> map) {
        Map<MobEffect, Integer> effects = new HashMap<>();
        if (map != null) {
            for (Map.Entry<String, Integer> entry : map.entrySet()) {
                Identifier loc = Identifier.tryParse(entry.getKey());
                if (loc == null) continue;
                MobEffect effect = BuiltInRegistries.MOB_EFFECT.getOptional(loc).orElse(null);
                if (effect != null) effects.put(effect, entry.getValue());
            }
        }
        setValue(effects);
    }
}
