package com.github.epsilon.mixins;

import com.github.epsilon.graphics.LuminRenderSystem;
<<<<<<< HEAD
import net.minecraft.client.renderer.DynamicUniforms;
=======
import com.github.epsilon.graphics.text.ttf.TtfFontLoader;
import com.mojang.blaze3d.TracyFrameCapture;
import com.mojang.blaze3d.systems.RenderSystem;
import org.jspecify.annotations.Nullable;
>>>>>>> 39383183 (优化 GUI 打开动画和字体渲染器性能 (#333))
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DynamicUniforms.class)
public class MixinRenderSystem {

    @Inject(method = "reset", at = @At("RETURN"))
    private void onReset(CallbackInfo ci) {
        LuminRenderSystem.endDynamicUniformFrame();
        TtfFontLoader.beginRenderFrame();
    }

}
