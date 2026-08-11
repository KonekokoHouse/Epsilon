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

    @Override
    public void epsilon$freeze(float position, float speed, float partialTicks) {
        this.speedOld = speed;
        this.speed = speed;
        this.position = position + speed * (1.0f - partialTicks);
    }

}
