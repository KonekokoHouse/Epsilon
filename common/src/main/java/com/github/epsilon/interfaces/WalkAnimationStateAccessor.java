package com.github.epsilon.interfaces;

public interface WalkAnimationStateAccessor {

    void epsilon$copyFrom(WalkAnimationStateAccessor other);

    float epsilon$getSpeedOld();

    float epsilon$getSpeed();

    float epsilon$getPosition();

    float epsilon$getPositionScale();

    void epsilon$freeze(float position, float speed, float partialTicks);

}
