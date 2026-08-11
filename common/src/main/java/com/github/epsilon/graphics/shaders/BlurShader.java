package com.github.epsilon.graphics.shaders;

import net.minecraft.world.phys.AABB;

/** 1.21.1 不支持的后处理兼容入口。 */
public final class BlurShader {

    public static final BlurShader INSTANCE = new BlurShader();

    private BlurShader() {
    }

    public void render(float x, float y, float width, float height,
                       float rTL, float rTR, float rBR, float rBL, float blurStrength) {
    }

    public void render(float x, float y, float width, float height, float radius, float blurStrength) {
    }

    public void render3DBox(AABB box, double blurStrength) {
    }
}
