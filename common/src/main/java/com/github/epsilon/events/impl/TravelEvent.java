package com.github.epsilon.events.impl;

import com.github.epsilon.events.bus.Cancellable;
import net.minecraft.world.phys.Vec3;

public class TravelEvent extends Cancellable {
    private static final TravelEvent INSTANCE = new TravelEvent();

    public Vec3 move;
    public boolean isCancel;

    public static TravelEvent get(Vec3 move) {
        INSTANCE.move = move;
        INSTANCE.isCancel = false;
        return INSTANCE;
    }
}
