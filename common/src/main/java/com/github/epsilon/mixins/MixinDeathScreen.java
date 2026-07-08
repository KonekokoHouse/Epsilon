package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.BetterDeathScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DeathScreen.class)
public class MixinDeathScreen {

    /**
     * freecam 激活时，完全跳过死亡界面的渲染。
     */
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void onExtractRenderState(CallbackInfo ci) {
        if (BetterDeathScreen.freecamActive) {
            ci.cancel();
        }
    }

    /**
     * freecam 激活时，禁用鼠标点击（防止误触按钮）。
     */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(CallbackInfoReturnable<Boolean> cir) {
        if (BetterDeathScreen.freecamActive) {
            cir.setReturnValue(false);
        }
    }
}
