package com.github.epsilon.interfaces;

import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

public interface IVec3 {
    Vec3 epsilon$set(double x, double y, double z);

    default Vec3 epsilon$set(Vec3i vec) {
        return epsilon$set(vec.getX(), vec.getY(), vec.getZ());
    }

    default Vec3 epsilon$set(Vector3d vec) {
        return epsilon$set(vec.x, vec.y, vec.z);
    }

    default Vec3 epsilon$set(Vec3 pos) {
        return epsilon$set(pos.x, pos.y, pos.z);
    }

    Vec3 epsilon$setXZ(double x, double z);

    Vec3 epsilon$setY(double y);
}
