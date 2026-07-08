package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.movement.EntityControl;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

@Mixin(value = Mob.class, priority = 1001)
public abstract class MixinMob {

    @ModifyReturnValue(method = "isSaddled", at = @At("RETURN"))
    private boolean overrideSaddleCheck(boolean original) {
        EntityControl ec = EntityControl.INSTANCE;
        if (ec.isEnabled() && ec.spoofSaddle()) {
            return true;
        }
        return original;
    }
}
