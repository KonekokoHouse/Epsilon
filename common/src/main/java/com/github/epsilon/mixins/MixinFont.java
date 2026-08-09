package com.github.epsilon.mixins;

import com.github.epsilon.graphics.LuminRenderPipelines;
import com.github.epsilon.modules.impl.ClientSetting;
import com.github.epsilon.modules.impl.render.NoRender;
import com.github.slmpc.lumingraphics.mc.v2612.runtime.MinecraftUiRuntime2612;
import com.github.slmpc.lumingraphics.mc.v2612.text.MinecraftFontAdapter2612;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Font.class)
public class MixinFont {
    private static final MinecraftFontAdapter2612.RenderOptions EPSILON_RENDER_OPTIONS =
            new MinecraftFontAdapter2612.RenderOptions(
                    LuminRenderPipelines.TTF_FONT_AA,
                    LuminRenderPipelines.TTF_FONT_NO_AA,
                    () -> ClientSetting.INSTANCE.fontAntiAliasing.getValue());

    @Inject(method = "getGlyph", at = @At("HEAD"), cancellable = true)
    private void onGetGlyph(int codepoint, Style style, CallbackInfoReturnable<BakedGlyph> cir) {
        if (ClientSetting.INSTANCE.replaceMinecraftFont.getValue()) {
            MinecraftFontAdapter2612 adapter = minecraftFontOrNull();
            BakedGlyph glyph = adapter == null ? null : adapter.glyph(codepoint, EPSILON_RENDER_OPTIONS);
            if (glyph != null) {
                cir.setReturnValue(glyph);
            }
        }
    }

    @Inject(method = "width(Ljava/lang/String;)I", at = @At("HEAD"), cancellable = true)
    private void onWidthString(String text, CallbackInfoReturnable<Integer> cir) {
        if (ClientSetting.INSTANCE.replaceMinecraftFont.getValue()) {
            MinecraftFontAdapter2612 adapter = minecraftFontOrNull();
            if (adapter != null) cir.setReturnValue(Mth.ceil(adapter.width(text)));
        }
    }

    @Inject(method = "width(Lnet/minecraft/util/FormattedCharSequence;)I", at = @At("HEAD"), cancellable = true)
    private void onWidthFormattedCharSequence(FormattedCharSequence text, CallbackInfoReturnable<Integer> cir) {
        if (ClientSetting.INSTANCE.replaceMinecraftFont.getValue()) {
            MinecraftFontAdapter2612 adapter = minecraftFontOrNull();
            if (adapter != null) cir.setReturnValue(Mth.ceil(adapter.width(text)));
        }
    }

    @Inject(method = "width(Lnet/minecraft/network/chat/FormattedText;)I", at = @At("HEAD"), cancellable = true)
    private void onWidthFormattedText(FormattedText text, CallbackInfoReturnable<Integer> cir) {
        if (ClientSetting.INSTANCE.replaceMinecraftFont.getValue()) {
            MinecraftFontAdapter2612 adapter = minecraftFontOrNull();
            if (adapter != null) cir.setReturnValue(Mth.ceil(adapter.width(text)));
        }
    }

    private static MinecraftFontAdapter2612 minecraftFontOrNull() {
        MinecraftUiRuntime2612 runtime = MinecraftUiRuntime2612.currentOrNull();
        if (runtime == null || !runtime.graphicsRuntime().frameActive()) return null;
        ClientSetting.INSTANCE.configureMinecraftFonts(runtime);
        return runtime.minecraftFont();
    }

}
