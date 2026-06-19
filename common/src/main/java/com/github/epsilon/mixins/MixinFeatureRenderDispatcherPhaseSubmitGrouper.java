package com.github.epsilon.mixins;

import com.github.epsilon.holders.ShaderHolder;
import net.minecraft.client.renderer.feature.FeatureRendererType;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.List;

@Mixin(targets = "net.minecraft.client.renderer.feature.FeatureRenderDispatcher$PhaseSubmitGrouper")
public abstract class MixinFeatureRenderDispatcherPhaseSubmitGrouper {

    @Shadow
    @Final
    private List<SubmitNode> allSubmits;

    @Invoker("addOrExtendGroup")
    protected abstract <Submit extends SubmitNode> void invokeAddOrExtendGroup(FeatureRendererType<Submit> featureType, boolean strictlyOrdered, int fromInclusive, int toInclusive);

    @Inject(method = "acceptFeatureGroup", at = @At("HEAD"), cancellable = true)
    private <Submit extends SubmitNode> void splitChestOutlineGroup(FeatureRendererType<Submit> featureType, Collection<Submit> submits, boolean strictlyOrdered, CallbackInfo ci) {
        if (submits.stream().noneMatch(ShaderHolder.INSTANCE::isChestOutlineSubmit)) {
            return;
        }

        for (Submit submit : submits) {
            if (submit.featureType() != featureType) {
                throw new IllegalArgumentException(submit + " was not of feature type " + featureType);
            }

            int index = this.allSubmits.size();
            this.allSubmits.add(submit);
            boolean chestOutlineSubmit = ShaderHolder.INSTANCE.isChestOutlineSubmit(submit);
            this.invokeAddOrExtendGroup(featureType, chestOutlineSubmit != strictlyOrdered, index, index);
        }

        ci.cancel();
    }

}
