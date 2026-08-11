package com.github.epsilon.utils.render.esp;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.LivingEntity;

import java.awt.Color;

/** 1.21.1 不支持的 RenderPipeline 圆环效果兼容入口。 */
public final class CircleESP {

    private CircleESP() {
    }

    public static void render(PoseStack poseStack, LivingEntity target, float radius,
                              Color sideColor, Color lineColor, float alphaFactor) {
    }
}
