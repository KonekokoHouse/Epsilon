package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.FreeCamera;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.debug.ChunkBorderRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChunkBorderRenderer.class)
public class MixinChunkBorderRenderer {

    @Final
    @Shadow
    private Minecraft minecraft;

    @ModifyExpressionValue(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;chunkPosition()Lnet/minecraft/world/level/ChunkPos;"))
    private ChunkPos render$getChunkPos(ChunkPos original) {
        FreeCamera freeCamera = FreeCamera.INSTANCE;
        if (freeCamera.isEnabled()) {
            float tickDelta = com.github.epsilon.Constants.getDeltaTracker().getGameTimeDeltaPartialTick(true);
            return new ChunkPos(Mth.floor(freeCamera.getX(tickDelta)) >> 4,
                    Mth.floor(freeCamera.getZ(tickDelta)) >> 4);
        }
        return original;
    }

}
