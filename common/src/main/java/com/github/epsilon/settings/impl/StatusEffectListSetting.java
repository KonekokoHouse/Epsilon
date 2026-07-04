package com.github.epsilon.settings.impl;

import net.minecraft.world.effect.MobEffect;

import java.util.Collection;

public class StatusEffectListSetting extends RegistryListSetting<MobEffect> {

    public StatusEffectListSetting(String name, Collection<MobEffect> defaultValue, Dependency dependency) {
        super(name, defaultValue, Type.MOB_EFFECT, null, dependency);
    }
}
