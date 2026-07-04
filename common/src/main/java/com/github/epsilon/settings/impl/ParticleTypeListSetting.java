package com.github.epsilon.settings.impl;

import net.minecraft.core.particles.ParticleType;

import java.util.Collection;

public class ParticleTypeListSetting extends RegistryListSetting<ParticleType<?>> {

    public ParticleTypeListSetting(String name, Collection<ParticleType<?>> defaultValue, Dependency dependency) {
        super(name, defaultValue, Type.PARTICLE_TYPE, null, dependency);
    }
}
