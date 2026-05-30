package com.github.epsilon.gui.panel.utils;

import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.renderers.*;
import com.github.epsilon.gui.panel.PanelLayout;


public class PanelScissor {

    private PanelScissor() {
    }

    public static void apply(PanelLayout.Rect rect, RectRenderer rectRenderer, RoundRectRenderer roundRectRenderer, RoundRectOutlineRenderer roundRectOutlineRenderer, ShadowRenderer shadowRenderer, TriangleRenderer triangleRenderer, TextRenderer textRenderer, int guiHeight) {
        LuminRenderSystem.ScissorRect scissor = LuminRenderSystem.toFramebufferScissor(rect.x(), rect.y(), rect.width(), rect.height());
        int x = scissor.x();
        int y = scissor.y();
        int width = scissor.width();
        int height = scissor.height();
        rectRenderer.setScissor(x, y, width, height);
        roundRectRenderer.setScissor(x, y, width, height);
        roundRectOutlineRenderer.setScissor(x, y, width, height);
        shadowRenderer.setScissor(x, y, width, height);
        triangleRenderer.setScissor(x, y, width, height);
        textRenderer.setScissor(x, y, width, height);
    }

    public static void clear(RectRenderer rectRenderer, RoundRectRenderer roundRectRenderer, RoundRectOutlineRenderer roundRectOutlineRenderer, ShadowRenderer shadowRenderer, TriangleRenderer triangleRenderer, TextRenderer textRenderer) {
        rectRenderer.clearScissor();
        roundRectRenderer.clearScissor();
        roundRectOutlineRenderer.clearScissor();
        shadowRenderer.clearScissor();
        triangleRenderer.clearScissor();
        textRenderer.clearScissor();
    }

}
