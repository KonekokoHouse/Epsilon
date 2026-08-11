package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.FreeCamera;
import com.github.epsilon.modules.impl.render.NoRender;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {

    @Shadow
    private Minecraft minecraft;

    @Unique
    private boolean epsilon$freeCameraSet;

    @Shadow
    private ItemStack itemActivationItem;

    @Shadow
    public abstract void pick(float partialTick);

    @Inject(method = "pick", at = @At("HEAD"), cancellable = true)
    private void updateFreeCameraTarget(float partialTick, CallbackInfo ci) {
        FreeCamera freeCamera = FreeCamera.INSTANCE;
        Entity cameraEntity = minecraft.getCameraEntity();
        if (!freeCamera.isEnabled() || cameraEntity == null || epsilon$freeCameraSet) return;

        ci.cancel();
        double x = cameraEntity.getX();
        double y = cameraEntity.getY();
        double z = cameraEntity.getZ();
        double lastX = cameraEntity.xo;
        double lastY = cameraEntity.yo;
        double lastZ = cameraEntity.zo;
        float yaw = cameraEntity.getYRot();
        float pitch = cameraEntity.getXRot();
        float lastYaw = cameraEntity.yRotO;
        float lastPitch = cameraEntity.xRotO;

        try {
            cameraEntity.setPos(freeCamera.pos.x, freeCamera.pos.y - cameraEntity.getEyeHeight(cameraEntity.getPose()), freeCamera.pos.z);
            cameraEntity.xo = freeCamera.prevPos.x;
            cameraEntity.yo = freeCamera.prevPos.y - cameraEntity.getEyeHeight(cameraEntity.getPose());
            cameraEntity.zo = freeCamera.prevPos.z;
            cameraEntity.setYRot(freeCamera.yaw);
            cameraEntity.setXRot(freeCamera.pitch);
            cameraEntity.yRotO = freeCamera.lastYaw;
            cameraEntity.xRotO = freeCamera.lastPitch;
            epsilon$freeCameraSet = true;
            pick(partialTick);
        } finally {
            epsilon$freeCameraSet = false;
            cameraEntity.setPos(x, y, z);
            cameraEntity.xo = lastX;
            cameraEntity.yo = lastY;
            cameraEntity.zo = lastZ;
            cameraEntity.setYRot(yaw);
            cameraEntity.setXRot(pitch);
            cameraEntity.yRotO = lastYaw;
            cameraEntity.xRotO = lastPitch;
        }
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void renderItemInHand(Camera camera, float partialTick, Matrix4f modelViewMatrix, CallbackInfo ci) {
        if (!FreeCamera.INSTANCE.renderHands()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderItemActivationAnimation", at = @At("HEAD"), cancellable = true)
    private void onRenderItemActivationAnimation(GuiGraphics guiGraphics, float partialTick, CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled()
                && NoRender.INSTANCE.totemAnimation.getValue()
                && itemActivationItem != null
                && itemActivationItem.is(Items.TOTEM_OF_UNDYING)) {
            ci.cancel();
        }
    }
}
