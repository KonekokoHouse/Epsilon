package com.github.epsilon.mixins;

import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.impl.AttackYawEvent;
import com.github.epsilon.events.impl.TravelEvent;
import com.github.epsilon.modules.impl.movement.AutoSprint;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.github.epsilon.Constants.mc;

@Mixin(Player.class)
public class MixinPlayer {

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void onTravelPre(Vec3 input, CallbackInfo ci) {
        if ((Player) (Object) this == mc.player) {
            TravelEvent event = EventBus.INSTANCE.post(new TravelEvent());
            if (event.isCancelled()) {
                ci.cancel();
            }
        }
    }

    @ModifyExpressionValue(method = {"causeExtraKnockback", "doSweepAttack"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getYRot()F"))
    private float modifyAttackYaw(float original) {
        AttackYawEvent event = EventBus.INSTANCE.post(new AttackYawEvent(original));
        return event.getYaw();
    }

    @Inject(method = "causeExtraKnockback", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setSprinting(Z)V", shift = At.Shift.AFTER))
    private void hookCauseExtraKnockback(CallbackInfo callbackInfo) {
        if (AutoSprint.INSTANCE.isEnabled() && AutoSprint.INSTANCE.keepSprint.getValue()) {
            float multiplier = 0.6f + 0.4f * AutoSprint.INSTANCE.motion.getValue().floatValue();
            mc.player.setDeltaMovement(mc.player.getDeltaMovement().x / 0.6 * multiplier, mc.player.getDeltaMovement().y, mc.player.getDeltaMovement().z / 0.6 * multiplier);
            mc.player.setSprinting(true);
        }
    }

}
