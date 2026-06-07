package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.Fullbright;
import com.github.epsilon.modules.impl.render.Filter;
import com.github.epsilon.modules.impl.render.Xray;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.*;

@Mixin(Lightmap.class)
public class MixinLightmap {

    @Final
    @Shadow
    private GpuTexture texture;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRender(LightmapRenderState renderState, CallbackInfo ci) {
        if (Xray.INSTANCE.isEnabled() || Fullbright.INSTANCE.isGammaMode() || Filter.INSTANCE.isLightMapMode()) {
            if (Filter.INSTANCE.isLightMapMode()) {
                RenderSystem.getDevice().createCommandEncoder().clearColorTexture(this.texture, Filter.INSTANCE.getLightMapColor().getRGB());
            } else {
                RenderSystem.getDevice().createCommandEncoder().clearColorTexture(this.texture, -1);
            }
            ci.cancel();
        }
    }

}
