package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.movement.EntityControl;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.animal.pig.Pig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Pig.class)
public abstract class MixinPig {
    @Inject(method = "getControllingPassenger", at = @At("HEAD"), cancellable = true)
    private void overrideGetControllingPassenger(CallbackInfoReturnable<LivingEntity> cir) {
        EntityControl ec = EntityControl.INSTANCE;
        if (ec.isEnabled() && ec.spoofSaddle()) {
            if (((LivingEntity) (Object) this).getFirstPassenger() instanceof Player player) {
                cir.setReturnValue(player);
            }
        }
    }
}
