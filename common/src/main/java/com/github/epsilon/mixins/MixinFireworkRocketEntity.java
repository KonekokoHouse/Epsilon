package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.movement.ElytraFly;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FireworkRocketEntity.class)
public class MixinFireworkRocketEntity {

    @Shadow
    private int life;

    @Shadow
    private int lifetime;

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        ElytraFly module = ElytraFly.INSTANCE;
        if (module.isEnabled()) {
            FireworkRocketEntity firework = (FireworkRocketEntity) (Object) this;
            if (module.isMyFirework(firework) && this.life > this.lifetime) {
                firework.discard();
            }
        }
    }

    @Inject(method = "onHitEntity", at = @At("HEAD"), cancellable = true)
    private void hookOnHitEntity(EntityHitResult hitResult, CallbackInfo ci) {
        ElytraFly module = ElytraFly.INSTANCE;
        if (module.isEnabled()) {
            FireworkRocketEntity firework = (FireworkRocketEntity) (Object) this;
            if (module.isMyFirework(firework)) {
                firework.discard();
                ci.cancel();
            }
        }
    }

    @Inject(method = "onHitBlock", at = @At("HEAD"), cancellable = true)
    private void hookOnHitBlock(BlockHitResult hitResult, CallbackInfo ci) {
        ElytraFly module = ElytraFly.INSTANCE;
        if (module.isEnabled()) {
            FireworkRocketEntity firework = (FireworkRocketEntity) (Object) this;
            if (module.isMyFirework(firework)) {
                firework.discard();
                ci.cancel();
            }
        }
    }

}
