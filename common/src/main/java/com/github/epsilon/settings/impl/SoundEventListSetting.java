package com.github.epsilon.settings.impl;

import net.minecraft.sounds.SoundEvent;

import java.util.Collection;

public class SoundEventListSetting extends RegistryListSetting<SoundEvent> {

    public SoundEventListSetting(String name, Collection<SoundEvent> defaultValue, Dependency dependency) {
        super(name, defaultValue, Type.SOUND_EVENT, null, dependency);
    }
}
