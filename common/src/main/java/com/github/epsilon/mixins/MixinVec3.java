package com.github.epsilon.mixins;

import com.github.epsilon.interfaces.IVec3;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;

@Mixin(Vec3.class)
public class MixinVec3 implements IVec3 {

    @Shadow @Final @Mutable
    public double x;

    @Shadow @Final @Mutable
    public double y;

    @Shadow @Final @Mutable
    public double z;

    @Override
    public Vec3 epsilon$set(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return (Vec3) (Object) this;
    }

    @Override
    public Vec3 epsilon$setXZ(double x, double z) {
        this.x = x;
        this.z = z;
        return (Vec3) (Object) this;
    }

    @Override
    public Vec3 epsilon$setY(double y) {
        this.y = y;
        return (Vec3) (Object) this;
    }
}
