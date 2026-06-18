package com.github.epsilon.mixins;

import com.github.epsilon.holders.ShaderHolder;
import com.github.epsilon.modules.impl.render.Shaders;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {

    @Final
    @Shadow
    private Minecraft minecraft;

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;doEntityOutline()V", shift = At.Shift.BEFORE))
    private void processShadersOutline(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        Shaders shaders = Shaders.INSTANCE;
        if (shaders.isEnabled()) {
            RenderTarget target = minecraft.levelRenderer.entityOutlineTarget;
            ShaderHolder.INSTANCE.processEntityOutlineTarget(target, shaders.mode.getValue());
            ShaderHolder.INSTANCE.processChestOutlineTarget(minecraft.gameRenderer.mainRenderTarget());
            ShaderHolder.INSTANCE.processHandOutlineTarget(minecraft.gameRenderer.mainRenderTarget());
        }
    }

    @WrapOperation(method = "renderItemInHand", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;renderAllFeatures(Lnet/minecraft/client/renderer/SubmitNodeStorage;)V"))
    private void renderHandShaderOutline(FeatureRenderDispatcher dispatcher, SubmitNodeStorage submitNodeStorage, Operation<Void> original) {
        if (!ShaderHolder.INSTANCE.isRenderingHands()) {
            original.call(dispatcher, submitNodeStorage);
            return;
        }

        try (FeatureRenderDispatcher.PreparedFrame frame = dispatcher.prepareFrame(submitNodeStorage)) {
            frame.executeSolid();
            frame.executeTranslucent();
            frame.executeOutline();
            frame.executeTranslucentAfterTerrain();
            frame.executeAlwaysOnTop();
        }

        ShaderHolder.INSTANCE.endHandOutlineCapture();
    }

}
