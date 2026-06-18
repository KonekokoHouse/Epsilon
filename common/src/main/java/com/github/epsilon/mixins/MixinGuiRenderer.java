package com.github.epsilon.mixins;

import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.impl.Render2DEvent;
import com.github.epsilon.utils.render.EpsilonGuiRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
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
    private FeatureRenderDispatcher featureRenderDispatcher;

    @Unique
    private GuiRenderState epsilon$levelRenderState;

    @Unique
    private EpsilonGuiRenderer epsilon$levelGuiRenderer;

    @Unique
    private GuiRenderState epsilon$renderState;

    @Unique
    private EpsilonGuiRenderer epsilon$guiRenderer;

    @Inject(method = "draw", at = @At("HEAD"))
    private void onDrawHead(CallbackInfo ci) {
        epsilon$ensureRenderers();

        int mouseX = (int) mc.mouseHandler.getScaledXPos(mc.getWindow());
        int mouseY = (int) mc.mouseHandler.getScaledYPos(mc.getWindow());

        GuiGraphicsExtractor levelGuiGraphics = new GuiGraphicsExtractor(mc, epsilon$levelRenderState, mouseX, mouseY);
        EventBus.INSTANCE.post(new Render2DEvent.Level(levelGuiGraphics));
        epsilon$levelGuiRenderer.render();
        epsilon$levelGuiRenderer.endFrame();

        GuiGraphicsExtractor guiGraphics = new GuiGraphicsExtractor(mc, epsilon$renderState, mouseX, mouseY);
        EventBus.INSTANCE.post(new Render2DEvent.HUD(guiGraphics));

        epsilon$guiRenderer.render();

        epsilon$guiRenderer.endFrame();
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void onClose(CallbackInfo ci) {
        if (epsilon$levelGuiRenderer != null) {
            epsilon$levelGuiRenderer.close();
            epsilon$levelGuiRenderer = null;
            epsilon$levelRenderState = null;
        }
        if (epsilon$guiRenderer != null) {
            epsilon$guiRenderer.close();
            epsilon$guiRenderer = null;
            epsilon$renderState = null;
        }
    }

    @Unique
    private void epsilon$ensureRenderers() {
        if (epsilon$levelRenderState == null || epsilon$levelGuiRenderer == null) {
            this.epsilon$levelRenderState = new GuiRenderState();
            this.epsilon$levelGuiRenderer = new EpsilonGuiRenderer(
                    this.epsilon$levelRenderState,
                    this.featureRenderDispatcher
            );
        }
        if (epsilon$renderState == null || epsilon$guiRenderer == null) {
            this.epsilon$renderState = new GuiRenderState();
            this.epsilon$guiRenderer = new EpsilonGuiRenderer(
                    this.epsilon$renderState,
                    this.featureRenderDispatcher
            );
        }
    }

}
