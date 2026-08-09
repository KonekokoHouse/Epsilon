package com.github.epsilon.mixins;

import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.impl.Render2DEvent;
import com.github.epsilon.gui.hudeditor.HudEditorScreen;
import com.github.epsilon.gui.screen.MainMenuScreen;
import com.github.slmpc.lumingraphics.mc.v2612.runtime.MinecraftGuiExtractionBridge2612;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.github.epsilon.Constants.mc;

@Mixin(GuiRenderer.class)
public class MixinGuiRenderer {

    @Shadow
    @Final
    private MultiBufferSource.BufferSource bufferSource;

    @Shadow
    @Final
    private SubmitNodeCollector submitNodeCollector;

    @Shadow
    @Final
    private FeatureRenderDispatcher featureRenderDispatcher;

    @Unique
    private MinecraftGuiExtractionBridge2612 epsilon$levelGuiBridge;

    @Inject(method = "draw", at = @At("HEAD"))
    private void onDrawHead(GpuBufferSlice fogBuffer, CallbackInfo ci) {
        // 只在原版主 GuiRenderer 上运行，避免被 MeteorClient 继承的自定义 GuiRenderer 重复触发
        if (((GuiRenderer) (Object) this).getClass() != GuiRenderer.class
                || MinecraftGuiExtractionBridge2612.isNativeSubmissionActive()) {
            return;
        }

        if (epsilon$levelGuiBridge == null) {
            var resources = new MinecraftGuiExtractionBridge2612.NativeResources(
                    this.bufferSource, this.submitNodeCollector, this.featureRenderDispatcher);
            this.epsilon$levelGuiBridge = new MinecraftGuiExtractionBridge2612(resources);
        }

        int mouseX = (int) mc.mouseHandler.getScaledXPos(mc.getWindow());
        int mouseY = (int) mc.mouseHandler.getScaledYPos(mc.getWindow());

        HudEditorScreen.INSTANCE.renderPendingHudElements();

        GuiGraphicsExtractor levelGuiGraphics = epsilon$levelGuiBridge.extractor(mc, mouseX, mouseY);
        EventBus.INSTANCE.post(new Render2DEvent.Level(levelGuiGraphics));
        epsilon$levelGuiBridge.submit(fogBuffer);
    }

    @Inject(method = "draw", at = @At("RETURN"))
    private void onDrawReturn(GpuBufferSlice fogBuffer, CallbackInfo ci) {
        // 只在原版主 GuiRenderer 上运行，避免被 MeteorClient 继承的自定义 GuiRenderer 重复触发
        if (((GuiRenderer) (Object) this).getClass() != GuiRenderer.class) {
            return;
        }

        MainMenuScreen.INSTANCE.renderPendingOverlay();
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void onClose(CallbackInfo ci) {
        if (epsilon$levelGuiBridge != null) {
            epsilon$levelGuiBridge.close();
            epsilon$levelGuiBridge = null;
        }
    }

}
