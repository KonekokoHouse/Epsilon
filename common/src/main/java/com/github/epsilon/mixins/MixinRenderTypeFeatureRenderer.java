package com.github.epsilon.mixins;

import com.github.epsilon.holders.ShaderHolder;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mixin(RenderTypeFeatureRenderer.class)
public class MixinRenderTypeFeatureRenderer {

    @Shadow
    @Final
    private List<?> groups;

    @Unique
    private final Set<Integer> epsilon$chestOutlineGroups = new HashSet<>();

    @Inject(method = "prepareGroup", at = @At("HEAD"))
    private void markChestOutlineGroup(FeatureFrameContext context, List<? extends SubmitNode> submits, boolean strictlyOrdered, CallbackInfo ci) {
        for (SubmitNode submitNode : submits) {
            if (ShaderHolder.INSTANCE.isChestOutlineSubmit(submitNode)) {
                epsilon$chestOutlineGroups.add(groups.size());
                break;
            }
        }
    }

    @WrapOperation(method = "executeGroup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/rendertype/PreparedRenderType;drawFromBuffer(Lnet/minecraft/client/renderer/StagedVertexBuffer$ExecuteInfo;)V"))
    private void redirectChestOutlineDraw(PreparedRenderType renderType, StagedVertexBuffer.ExecuteInfo info, Operation<Void> original, FeatureFrameContext context, int groupIndex, List<? extends SubmitNode> submits, boolean strictlyOrdered) {
        if (epsilon$chestOutlineGroups.contains(groupIndex)) {
            ShaderHolder.INSTANCE.beginChestOutlineCapture();
            original.call(renderType, info);
            ShaderHolder.INSTANCE.endChestOutlineCapture();
            return;
        }

        original.call(renderType, info);
    }

    @Inject(method = "finishExecute", at = @At("RETURN"))
    private void clearChestOutlineGroups(FeatureFrameContext context, CallbackInfo ci) {
        epsilon$chestOutlineGroups.clear();
    }

}
