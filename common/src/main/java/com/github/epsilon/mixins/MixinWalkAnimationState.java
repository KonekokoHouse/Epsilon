package com.github.epsilon.mixins;

import com.github.epsilon.interfaces.WalkAnimationStateAccessor;
import net.minecraft.world.entity.WalkAnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(WalkAnimationState.class)
public class MixinWalkAnimationState implements WalkAnimationStateAccessor {

    @Shadow
    private float speedOld;

    @Shadow
    private float speed;

    @Shadow
    private float position;

    @Shadow
    private float positionScale;

    @Override
    public void epsilon$copyFrom(WalkAnimationStateAccessor other) {
        speedOld = other.epsilon$getSpeedOld();
        speed = other.epsilon$getSpeed();
        position = other.epsilon$getPosition();
        positionScale = other.epsilon$getPositionScale();
    }

    @Override
    public float epsilon$getSpeedOld() {
        return speedOld;
    }

    @Override
    public float epsilon$getSpeed() {
        return speed;
    }

    @Override
    public float epsilon$getPosition() {
        return position;
    }

    @Override
    public float epsilon$getPositionScale() {
        return positionScale;
    }

    @Override
    public void epsilon$freeze(float position, float speed, float partialTicks) {
        this.speedOld = speed;
        this.speed = speed;
        this.positionScale = 1.0F;
        this.position = position + speed * (1.0F - partialTicks);
    }

}
