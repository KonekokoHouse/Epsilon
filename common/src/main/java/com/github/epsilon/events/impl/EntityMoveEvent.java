package com.github.epsilon.events.impl;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class EntityMoveEvent {

    private static final EntityMoveEvent INSTANCE = new EntityMoveEvent();

    public Entity entity;
    public Vec3 movement;

    public static EntityMoveEvent get(Entity entity, Vec3 movement) {
        INSTANCE.entity = entity;
        INSTANCE.movement = movement;
        return INSTANCE;
    }
}
