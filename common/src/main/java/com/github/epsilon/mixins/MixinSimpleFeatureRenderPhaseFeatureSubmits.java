package com.github.epsilon.mixins;

import com.github.epsilon.holders.ShaderHolder;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.client.renderer.feature.phase.SimpleFeatureRenderPhase$FeatureSubmits")
public class MixinSimpleFeatureRenderPhaseFeatureSubmits {

    @Unique
    private static final Object EPSILON_CHEST_OUTLINE_BATCH_KEY = new Object();

    @ModifyReturnValue(method = "batchKey", at = @At("RETURN"))
    private static Object splitChestOutlineBatch(Object original, SubmitNode submit) {
        return ShaderHolder.INSTANCE.isChestOutlineSubmit(submit) ? EPSILON_CHEST_OUTLINE_BATCH_KEY : original;
    }

}
