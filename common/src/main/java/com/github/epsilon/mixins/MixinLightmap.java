package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.Filter;
import com.github.epsilon.modules.impl.render.Fullbright;
import com.github.epsilon.modules.impl.render.Xray;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.Color;

@Mixin(LightTexture.class)
public class MixinLightmap {

    @Final
    @Shadow
    private NativeImage lightPixels;

    @Inject(
            method = "updateLightTexture",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/DynamicTexture;upload()V")
    )
    private void overrideLightPixels(float partialTick, CallbackInfo ci) {
        if (!Xray.INSTANCE.isEnabled() && !Fullbright.INSTANCE.isGammaMode() && !Filter.INSTANCE.isLightMapMode()) {
            return;
        }

        int color = Filter.INSTANCE.isLightMapMode() ? nativeColor(Filter.INSTANCE.getLightMapColor()) : -1;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                lightPixels.setPixelRGBA(x, y, color);
            }
        }
    }

    private static int nativeColor(Color color) {
        return color.getAlpha() << 24 | color.getBlue() << 16 | color.getGreen() << 8 | color.getRed();
    }
}
