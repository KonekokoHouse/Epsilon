package com.github.epsilon.utils.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.Entity;

import java.awt.Color;

/** 1.21.1 不支持的实体 render-state 线框效果兼容入口。 */
public final class WireframeEntityRenderer {

    private WireframeEntityRenderer() {
    }

    public static void beginBatch(PoseStack renderStack) {
    }

    public static void endBatch() {
    }

    public static void render(PoseStack renderStack, Entity entity, double scale,
                              Color sideColor, Color lineColor, float lineWidth) {
    }
}
