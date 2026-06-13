package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.movement.elytrafly.ElytraFly;
import com.github.epsilon.modules.impl.render.HandsView;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.github.epsilon.Constants.mc;

@Mixin(AvatarRenderer.class)
public class MixinAvatarRenderer {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("RETURN"))
    private void applyThirdPersonBlockingPose(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
        HandsView handsView = HandsView.INSTANCE;
        if (handsView.shouldApplyThirdPersonBlockingAnim(entity, HumanoidArm.RIGHT)) {
            state.rightArmPose = HumanoidModel.ArmPose.BLOCK;
        }
        if (handsView.shouldApplyThirdPersonBlockingAnim(entity, HumanoidArm.LEFT)) {
            state.leftArmPose = HumanoidModel.ArmPose.BLOCK;
        }
    }

    @ModifyExpressionValue(method = "extractFlightData", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Avatar;getFallFlyingTicks()I"))
    private int spoofFallFlyingTicks(int original, Avatar entity, AvatarRenderState reusedState, float partialTick) {
        if (ElytraFly.INSTANCE.isArmorMode() && entity == mc.player) {
            return 0;
        }
        return original;
    }

}
