package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.Xray;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public class MixinBlockStateBase {

    @Inject(method = "getLightEmission", at = @At("HEAD"), cancellable = true)
    private void onGetLightEmission(CallbackInfoReturnable<Integer> cir) {
        if (Xray.INSTANCE.isEnabled()) cir.setReturnValue(15);
    }

}
