package com.github.epsilon.settings.impl;

import com.github.epsilon.settings.Setting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import java.util.*;

public class SoundEventListSetting extends Setting<List<SoundEvent>> {

    public SoundEventListSetting(String name, Collection<SoundEvent> defaultValue, Dependency dependency) {
        super(name, dependency, null);
        this.defaultValue = normalize(defaultValue);
        this.value = new ArrayList<>(this.defaultValue);
    }

    @Override
    public void setValue(List<SoundEvent> value) {
        super.setValue(normalize(value));
    }

    @Override
    public void setValueSilently(List<SoundEvent> value) {
        super.setValueSilently(normalize(value));
    }

    @Override
    public void reset() {
        setValue(new ArrayList<>(defaultValue));
    }

    public boolean contains(SoundEvent sound) {
        return value.contains(sound);
    }

    public void add(SoundEvent sound) {
        if (sound == null || value.contains(sound)) return;
        List<SoundEvent> next = new ArrayList<>(value);
        next.add(sound);
        setValue(next);
    }

    public void remove(SoundEvent sound) {
        if (!value.contains(sound)) return;
        List<SoundEvent> next = new ArrayList<>(value);
        next.remove(sound);
        setValue(next);
    }

    public void toggle(SoundEvent sound) {
        if (contains(sound)) {
            remove(sound);
        } else {
            add(sound);
        }
    }

    public List<String> getIds() {
        List<String> ids = new ArrayList<>();
        for (SoundEvent sound : value) {
            Identifier id = BuiltInRegistries.SOUND_EVENT.getKey(sound);
            if (id != null) ids.add(id.toString());
        }
        return ids;
    }

    public void setIds(Collection<String> ids) {
        List<SoundEvent> sounds = new ArrayList<>();
        if (ids != null) {
            for (String id : ids) {
                Identifier loc = Identifier.tryParse(id);
                if (loc == null) continue;
                SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getOptional(loc).orElse(null);
                if (sound != null) sounds.add(sound);
            }
        }
        setValue(sounds);
    }

    public int size() {
        return value.size();
    }

    public boolean isEmpty() {
        return value.isEmpty();
    }

    private static List<SoundEvent> normalize(Collection<SoundEvent> source) {
        if (source == null) return new ArrayList<>();
        List<SoundEvent> sounds = new ArrayList<>();
        for (SoundEvent s : source) {
            if (s != null && !sounds.contains(s)) sounds.add(s);
        }
        return sounds;
    }
}
