package com.github.epsilon.graphics.renderers;

import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.holders.RendererHolder;
import com.github.epsilon.utils.render.ScissorUtils;

import java.awt.*;

public class ShadowRenderer implements IRenderer {

    private boolean scissorEnabled = false;
    private int scissorX, scissorY, scissorW, scissorH;

    private ShadowRenderer() {
    }

    public static ShadowRenderer create() {
        return RendererHolder.INSTANCE.register(new ShadowRenderer());
    }

    public void addShadow(float x, float y, float width, float height, float radius, float blurRadius, Color color) {
        addShadow(x, y, width, height, radius, radius, radius, radius, blurRadius, color);
    }

    public void addShadow(float x, float y, float width, float height, float rTL, float rTR, float rBR, float rBL, float blurRadius, Color color) {

    }

    public void setScissor(int x, int y, int width, int height) {
        LuminRenderSystem.ScissorRect scissor = ScissorUtils.clampFramebufferScissor(x, y, width, height);
        scissorEnabled = true;
        scissorX = scissor.x();
        scissorY = scissor.y();
        scissorW = scissor.width();
        scissorH = scissor.height();
    }

    public void clearScissor() {
        scissorEnabled = false;
    }

}
