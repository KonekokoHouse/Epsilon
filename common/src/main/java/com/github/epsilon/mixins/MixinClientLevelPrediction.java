package com.github.epsilon.mixins;

import com.github.epsilon.interfaces.ClientLevelPredictionAccessor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ClientLevel.class)
public interface MixinClientLevelPrediction extends ClientLevelPredictionAccessor {

    @Override
    @Invoker("getBlockStatePredictionHandler")
    BlockStatePredictionHandler epsilon$getBlockStatePredictionHandler();
}
