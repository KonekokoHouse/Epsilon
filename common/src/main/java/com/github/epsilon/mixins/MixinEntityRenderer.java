package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.NameTags;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public class MixinEntityRenderer<T extends LivingEntity, S extends LivingEntityRenderState> {

    @Inject(method = "shouldShowName", at = @At("HEAD"), cancellable = true)
    private void onShouldShowName(T entity, double distance, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof Player && (!NameTags.INSTANCE.vanillaNameTags.getValue()) && NameTags.INSTANCE.isEnabled()) {
            cir.setReturnValue(false);
        }
    }

}