package com.github.epsilon.mixins;

import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.impl.AfterRender3DEvent;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.utils.render.WorldToScreen;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void onPostRenderLevel(DeltaTracker deltaTracker, boolean renderOutline, Camera camera,
                                   GameRenderer gameRenderer, LightTexture lightTexture,
                                   Matrix4f modelViewMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        WorldToScreen.update(camera, modelViewMatrix, projectionMatrix);
        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(modelViewMatrix);
        EventBus.INSTANCE.post(new Render3DEvent(poseStack));
        EventBus.INSTANCE.post(new AfterRender3DEvent());
    }
}
