package com.github.epsilon.mixins;

import com.github.epsilon.interfaces.LevelRendererAccessor;
import com.github.epsilon.managers.ShaderManager;
import com.github.epsilon.modules.impl.render.Shaders;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
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
            RenderTarget target = ((LevelRendererAccessor) minecraft.levelRenderer).epsilon$getEntityOutlineTarget();
            ShaderManager.INSTANCE.processEntityOutlineTarget(target, shaders.mode.getValue());
            ShaderManager.INSTANCE.processChestOutlineTarget(minecraft.getMainRenderTarget());
            ShaderManager.INSTANCE.processHandOutlineTarget(minecraft.getMainRenderTarget());
        }
    }

}
