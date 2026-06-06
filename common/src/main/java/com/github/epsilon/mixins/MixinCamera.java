package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.CameraClip;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import static com.github.epsilon.Constants.mc;

@Mixin(Camera.class)
public class MixinCamera {

    @Inject(method = "getMaxZoom", at = @At("HEAD"), cancellable = true)
    private void hookGetMaxZoom(float cameraDist, CallbackInfoReturnable<Float> cir) {
        CameraClip cameraClip = CameraClip.INSTANCE;
        if (cameraClip.isEnabled()) {
            cir.setReturnValue(cameraClip.distance.getValue().floatValue());
        }
    }

    @ModifyArgs(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setPosition(DDD)V"))
    private void hookActionCameraPosition(Args args) {
        CameraClip cameraClip = CameraClip.INSTANCE;
        if (cameraClip.isEnabled() && cameraClip.action.getValue()) {
            if (mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK) {
                Vec3 targetPos = new Vec3(args.get(0), args.get(1), args.get(2));
                cameraClip.updateActionCamera(targetPos);
                Vec3 cameraPos = cameraClip.getCameraPos();
                if (cameraPos != null) {
                    args.set(0, cameraPos.x);
                    args.set(1, cameraPos.y);
                    args.set(2, cameraPos.z);
                }
            } else {
                cameraClip.resetCameraPos();
            }
        }
    }

}
