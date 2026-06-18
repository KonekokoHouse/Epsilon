package com.github.epsilon.mixins;

import com.github.epsilon.holders.ShaderHolder;
import com.github.epsilon.modules.impl.render.Shaders;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ModelFeatureRenderer.class)
public class MixinModelFeatureRenderer {

    @Unique
    private boolean epsilon$renderingChestOutline;

    @WrapOperation(method = "buildGroup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer;prepareModel(Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$Submit;)V"))
    private void redirectChestOutlineSubmit(ModelFeatureRenderer instance, ModelFeatureRenderer.Submit<?> submit, Operation<Void> original) {
        epsilon$renderingChestOutline = submit.tintedColor() == ShaderHolder.EPSILON_CHEST_OUTLINE_MARKER;
        if (!epsilon$renderingChestOutline) {
            original.call(instance, submit);
            return;
        }

        ShaderHolder.INSTANCE.beginChestOutlineCapture();
        try {
            original.call(instance, epsilon$withOutlineColor(submit, Shaders.INSTANCE.outlineColor.getValue().getRGB()));
        } finally {
            ShaderHolder.INSTANCE.endChestOutlineCapture();
            epsilon$renderingChestOutline = false;
        }
    }

    @Unique
    private static <S> ModelFeatureRenderer.Submit<S> epsilon$withOutlineColor(ModelFeatureRenderer.Submit<S> submit, int color) {
        return new ModelFeatureRenderer.Submit<>(
                submit.renderType(),
                submit.pose(),
                submit.model(),
                submit.state(),
                submit.lightCoords(),
                submit.overlayCoords(),
                color,
                submit.sprite(),
                submit.sheetedDecalPose()
        );
    }

}
