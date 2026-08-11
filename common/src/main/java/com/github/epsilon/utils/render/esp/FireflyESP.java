package com.github.epsilon.utils.render.esp;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.LivingEntity;

import java.awt.Color;

/** 1.21.1 不支持的 RenderPipeline 萤火轨迹兼容入口。 */
public final class FireflyESP {

    private FireflyESP() {
    }

    public enum ColorMode {
        Solid,
        Blend,
        Rainbow
    }

    public static void render(PoseStack stack, LivingEntity target, int espLength, int factor,
                              double shaking, double amplitude, Color color, ColorMode colorMode,
                              Color secondColor, double colorMix, double colorSpeed,
                              double rainbowSpeed, double rainbowSaturation, double rainbowBrightness) {
    }
}
