package com.github.epsilon.utils.render.esp;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.LivingEntity;

import java.awt.Color;

/** 1.21.1 不支持的捕获标记 RenderPipeline 兼容入口。 */
public final class CaptureMarkESP {

    private CaptureMarkESP() {
    }

    public static void render(PoseStack poseStack, LivingEntity target, double espSize,
                              double rotSpeed, double waveSpeed, Color color1, Color color2) {
    }
}
