package com.github.epsilon.gui.dropdown;

import com.github.epsilon.graphics.text.ttf.TtfFontLoader;
import com.github.epsilon.graphics.LuminTexture;
import com.github.epsilon.graphics.schedulers.Render2DTexture;
import com.github.epsilon.gui.dsl.PanelUiTree;
import com.github.epsilon.gui.panel.PanelLayout;
import net.minecraft.resources.Identifier;

import java.awt.*;

/**
 * Dropdown 的单帧绘制上下文。
 * <p>
 * 组件只向 {@link PanelUiTree.Scope} 提交声明式节点；具体分层、合批和 RenderPass 提交由
 * PanelUiTree/Render2DScheduler 统一处理。
 */
public final class DropdownDrawContext {

    private final PanelUiTree.Scope scope;
    private final TextMetrics textMetrics;
    private final ShadowFacade shadowFacade = new ShadowFacade();
    private final RoundRectFacade roundRectFacade = new RoundRectFacade();
    private final OutlineFacade outlineFacade = new OutlineFacade();
    private final RectFacade rectFacade = new RectFacade();
    private final TriangleFacade triangleFacade = new TriangleFacade();
    private final TextureFacade textureFacade = new TextureFacade();
    private final TextFacade textFacade = new TextFacade();

    public DropdownDrawContext(PanelUiTree.Scope scope, TextMetrics textMetrics) {
        this.scope = scope;
        this.textMetrics = textMetrics;
    }

    public PanelUiTree.Scope scope() {
        return scope;
    }

    public TextFacade text() {
        return textFacade;
    }

    public ShadowFacade shadow() {
        return shadowFacade;
    }

    public RoundRectFacade roundRect() {
        return roundRectFacade;
    }

    public OutlineFacade outline() {
        return outlineFacade;
    }

    public RectFacade rect() {
        return rectFacade;
    }

    public TriangleFacade triangle() {
        return triangleFacade;
    }

    public TextureFacade texture() {
        return textureFacade;
    }

    public Stack stack(PanelLayout.Rect bounds) {
        return new Stack(bounds);
    }

    public final class Stack {
        private final PanelLayout.Rect bounds;
        private float cursor;

        private Stack(PanelLayout.Rect bounds) {
            this.bounds = bounds;
            this.cursor = bounds.y();
        }

        public PanelLayout.Rect item(float height) {
            PanelLayout.Rect rect = new PanelLayout.Rect(bounds.x(), cursor, bounds.width(), Math.max(0.0f, height));
            cursor += Math.max(0.0f, height);
            return rect;
        }

        public PanelLayout.Rect item(float height, float gapAfter) {
            PanelLayout.Rect rect = item(height);
            gap(gapAfter);
            return rect;
        }

        public void gap(float gap) {
            cursor += Math.max(0.0f, gap);
        }

        public PanelLayout.Rect offset(float xOffset, float yOffset, float widthOffset, float heightOffset) {
            return new PanelLayout.Rect(
                    bounds.x() + xOffset,
                    cursor + yOffset,
                    Math.max(0.0f, bounds.width() + widthOffset),
                    Math.max(0.0f, heightOffset)
            );
        }

        public float cursor() {
            return cursor;
        }
    }

    public void shadow(float x, float y, float width, float height, float radius, float blurRadius, Color color) {
        scope.shadow(x, y, width, height, radius, blurRadius, color);
    }

    public void roundRect(float x, float y, float width, float height, float radius, Color color) {
        scope.roundRect(x, y, width, height, radius, color);
    }

    public void roundRect(float x, float y, float width, float height, float topLeft, float topRight,
                          float bottomRight, float bottomLeft, Color color) {
        scope.roundRect(x, y, width, height, topLeft, topRight, bottomRight, bottomLeft, color);
    }

    public void roundRectHorizontalGradient(float x, float y, float width, float height, float radius, Color left, Color right) {
        scope.roundRectHorizontalGradient(x, y, width, height, radius, left, right);
    }

    public void outline(float x, float y, float width, float height, float radius, float outlineWidth, Color color) {
        scope.outline(x, y, width, height, radius, radius, radius, radius, outlineWidth, color);
    }

    public void rect(float x, float y, float width, float height, Color color) {
        scope.rect(x, y, width, height, color);
    }

    public void rectOutline(float x, float y, float width, float height, float outlineWidth, Color color) {
        scope.rectOutline(x, y, width, height, outlineWidth, color);
    }

    public void rectGradient(float x, float y, float width, float height,
                             Color topLeft, Color bottomLeft, Color bottomRight, Color topRight) {
        scope.rectGradient(x, y, width, height, topLeft, bottomLeft, bottomRight, topRight);
    }

    public void triangle(float centerX, float centerY, float size, float progress, Color color) {
        scope.triangle(centerX, centerY, size, progress, color);
    }

    public void text(String text, float x, float y, float scale, Color color) {
        scope.text(text, x, y, scale, color);
    }

    public void text(String text, float x, float y, float scale, Color color, TtfFontLoader fontLoader) {
        scope.text(text, x, y, scale, color, fontLoader);
    }

    public interface TextMetrics {
        float getHeight(float scale);

        float getHeight(float scale, TtfFontLoader fontLoader);

        float getWidth(String text, float scale);

        float getWidth(String text, float scale, TtfFontLoader fontLoader);
    }

    public final class ShadowFacade {
        public void addShadow(float x, float y, float width, float height, float radius, float blurRadius, Color color) {
            shadow(x, y, width, height, radius, blurRadius, color);
        }

        public void addShadow(float x, float y, float width, float height, float topLeft, float topRight,
                              float bottomRight, float bottomLeft, float blurRadius, Color color) {
            scope.shadow(x, y, width, height, topLeft, topRight, bottomRight, bottomLeft, blurRadius, color);
        }
    }

    public final class RoundRectFacade {
        public void addRoundRect(float x, float y, float width, float height, float radius, Color color) {
            roundRect(x, y, width, height, radius, color);
        }

        public void addRoundRect(float x, float y, float width, float height, float topLeft, float topRight,
                                 float bottomRight, float bottomLeft, Color color) {
            roundRect(x, y, width, height, topLeft, topRight, bottomRight, bottomLeft, color);
        }

        public void addHorizontalGradient(float x, float y, float width, float height, float radius, Color left, Color right) {
            roundRectHorizontalGradient(x, y, width, height, radius, left, right);
        }
    }

    public final class OutlineFacade {
        public void addOutline(float x, float y, float width, float height, float radius, float outlineWidth, Color color) {
            outline(x, y, width, height, radius, outlineWidth, color);
        }

        public void addOutline(float x, float y, float width, float height, float topLeft, float topRight,
                               float bottomRight, float bottomLeft, float outlineWidth, Color color) {
            scope.outline(x, y, width, height, topLeft, topRight, bottomRight, bottomLeft, outlineWidth, color);
        }
    }

    public final class RectFacade {
        public void addRect(float x, float y, float width, float height, Color color) {
            rect(x, y, width, height, color);
        }

        public void addOutline(float x, float y, float width, float height, float outlineWidth, Color color) {
            rectOutline(x, y, width, height, outlineWidth, color);
        }

        public void addRectGradient(float x, float y, float width, float height,
                                    Color topLeft, Color bottomLeft, Color bottomRight, Color topRight) {
            rectGradient(x, y, width, height, topLeft, bottomLeft, bottomRight, topRight);
        }
    }

    public final class TriangleFacade {
        public void addChevronTriangle(float centerX, float centerY, float size, float progress, Color color) {
            triangle(centerX, centerY, size, progress, color);
        }
    }

    public final class TextureFacade {
        public void addQuadTexture(Identifier texture, float x, float y, float width, float height,
                                   float u0, float v0, float u1, float v1, Color color) {
            addQuadTexture(texture, x, y, width, height, u0, v0, u1, v1, color, false);
        }

        public void addQuadTexture(Identifier texture, float x, float y, float width, float height,
                                   float u0, float v0, float u1, float v1, Color color, boolean linearFilter) {
            scope.texture(new Render2DTexture.IdentifierRef(texture, linearFilter), x, y, width, height, u0, v0, u1, v1, color);
        }

        public void addQuadTexture(LuminTexture texture, float x, float y, float width, float height,
                                   float u0, float v0, float u1, float v1, Color color) {
            scope.texture(new Render2DTexture.LuminRef(texture), x, y, width, height, u0, v0, u1, v1, color);
        }

        public void addRoundedTexture(Identifier texture, float x, float y, float width, float height, float radius,
                                      float u0, float v0, float u1, float v1, Color color) {
            addRoundedTexture(texture, x, y, width, height, radius, u0, v0, u1, v1, color, false);
        }

        public void addRoundedTexture(Identifier texture, float x, float y, float width, float height, float radius,
                                      float u0, float v0, float u1, float v1, Color color, boolean linearFilter) {
            scope.roundedTexture(new Render2DTexture.IdentifierRef(texture, linearFilter), x, y, width, height, radius, u0, v0, u1, v1, color);
        }

        public void addRoundedTexture(LuminTexture texture, float x, float y, float width, float height, float radius,
                                      float u0, float v0, float u1, float v1, Color color) {
            scope.roundedTexture(new Render2DTexture.LuminRef(texture), x, y, width, height, radius, u0, v0, u1, v1, color);
        }

        public void addPlayerHead(LuminTexture texture, float x, float y, float size, float radius, Color color) {
            addRoundedTexture(texture, x, y, size, size, radius, 8.0f / 64.0f, 8.0f / 64.0f, 16.0f / 64.0f, 16.0f / 64.0f, color);
            addRoundedTexture(texture, x, y, size, size, radius, 40.0f / 64.0f, 8.0f / 64.0f, 48.0f / 64.0f, 16.0f / 64.0f, color);
        }
    }

    public final class TextFacade implements TextMetrics {
        public void addText(String text, float x, float y, float scale, Color color) {
            DropdownDrawContext.this.text(text, x, y, scale, color);
        }

        public void addText(String text, float x, float y, float scale, Color color, TtfFontLoader fontLoader) {
            DropdownDrawContext.this.text(text, x, y, scale, color, fontLoader);
        }

        @Override
        public float getHeight(float scale) {
            return textMetrics.getHeight(scale);
        }

        @Override
        public float getHeight(float scale, TtfFontLoader fontLoader) {
            return textMetrics.getHeight(scale, fontLoader);
        }

        @Override
        public float getWidth(String text, float scale) {
            return textMetrics.getWidth(text, scale);
        }

        @Override
        public float getWidth(String text, float scale, TtfFontLoader fontLoader) {
            return textMetrics.getWidth(text, scale, fontLoader);
        }

    }
}
