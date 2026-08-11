package com.github.epsilon.graphics.shaders;

import com.mojang.blaze3d.pipeline.RenderTarget;

import java.awt.Color;

/** 1.21.1 不支持的全屏颜色后处理兼容入口。 */
public final class FilterShader {

    public static final FilterShader INSTANCE = new FilterShader();

    private FilterShader() {
    }

    public void renderToMainTarget(Color color) {
    }

    public void render(RenderTarget framebuffer, Color color) {
    }
}
