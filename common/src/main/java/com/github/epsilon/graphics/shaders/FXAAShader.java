package com.github.epsilon.graphics.shaders;

import com.mojang.blaze3d.pipeline.RenderTarget;

/** 1.21.1 不支持的 FXAA 后处理兼容入口。 */
public final class FXAAShader {

    public static final FXAAShader INSTANCE = new FXAAShader();

    private FXAAShader() {
    }

    public void renderMainTarget() {
    }

    public void render(RenderTarget framebuffer) {
    }
}
