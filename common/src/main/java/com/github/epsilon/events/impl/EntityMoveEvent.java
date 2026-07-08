package com.github.epsilon.events.impl;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class EntityMoveEvent {
    public final Entity entity;
    public Vec3 movement;

    public EntityMoveEvent(Entity entity, Vec3 movement) {
        this.entity = entity;
        this.movement = movement;
    }
}
