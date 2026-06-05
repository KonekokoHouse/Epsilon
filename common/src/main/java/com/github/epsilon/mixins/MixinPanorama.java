package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.ClientSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.Panorama;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.github.epsilon.Constants.mc;

@Mixin(Panorama.class)
public class MixinPanorama {

    @Unique
    private static final int EPSILON_PARALLAX_PADDING = 24;

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void onExtractRenderState(GuiGraphicsExtractor graphics, int width, int height, boolean shouldSpin, CallbackInfo ci) {
        ClientSetting.BackgroundTexture texture = ClientSetting.INSTANCE.getTexture();
        if (texture != null) {
            int padding = EPSILON_PARALLAX_PADDING * 2;
            float scale = Math.max((float) (width + padding) / texture.width(), (float) (height + padding) / texture.height());
            int drawWidth = Math.round(texture.width() * scale);
            int drawHeight = Math.round(texture.height() * scale);
            int drawX = -Math.round((float) ((drawWidth - width) * (1.0 - mc.mouseHandler.getScaledXPos(mc.getWindow()) / width)));
            int drawY = -Math.round((float) ((drawHeight - height) * (1.0 - mc.mouseHandler.getScaledYPos(mc.getWindow()) / height)));
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture.id(), drawX, drawY, 0.0F, 0.0F, drawWidth, drawHeight, texture.width(), texture.height(), texture.width(), texture.height());
            ci.cancel();
        }
    }

}
