package com.github.epsilon.mixins;

import com.github.epsilon.holders.ShaderHolder;
import com.github.epsilon.interfaces.SubmitNodeCollectionAccessor;
import com.github.epsilon.modules.impl.render.Shaders;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.client.renderer.blockentity.state.ChestRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ChestRenderer.class)
public class MixinChestRenderer {

    @Redirect(method = "submit*", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;IIILnet/minecraft/client/resources/model/sprite/SpriteId;Lnet/minecraft/client/resources/model/sprite/SpriteGetter;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"))
    private <S> void applyShadersChestOutline(SubmitNodeCollector submitNodeCollector, Model<S> model, S modelState, PoseStack poseStack, int lightCoords, int overlayCoords, int tintedColor, SpriteId sprite, SpriteGetter sprites, int outlineColor, ModelFeatureRenderer.CrumblingOverlay crumblingOverlay, ChestRenderState state) {
        Shaders shaders = Shaders.INSTANCE;
        if (shaders.isEnabled() && shaders.shouldRenderChest(state.blockPos)) {
            submitNodeCollector.submitModel(model, modelState, poseStack, lightCoords, overlayCoords, tintedColor, sprite, sprites, 0, crumblingOverlay);

            if (!(submitNodeCollector instanceof SubmitNodeCollectionAccessor accessor)) {
                return;
            }

            RenderType renderType = sprite.renderType(model.renderType());
            RenderType outlineRenderType = renderType.isOutline() ? renderType : renderType.outline().orElse(null);
            if (outlineRenderType == null) {
                return;
            }

            ModelFeatureRenderer.Submit<S> submit = new ModelFeatureRenderer.Submit<>(
                    outlineRenderType,
                    poseStack.last().copy(),
                    model,
                    modelState,
                    15728880,
                    OverlayTexture.NO_OVERLAY,
                    shaders.outlineColor.getValue().getRGB(),
                    sprites.get(sprite),
                    null
            );
            ShaderHolder.INSTANCE.markChestOutlineSubmit(submit);

            SubmitNodeCollection collection = accessor.epsilon$getSubmitNodeCollection();
            collection.outline.submit(submit);
            return;
        }

        submitNodeCollector.submitModel(model, modelState, poseStack, lightCoords, overlayCoords, tintedColor, sprite, sprites, outlineColor, crumblingOverlay);
    }

}
