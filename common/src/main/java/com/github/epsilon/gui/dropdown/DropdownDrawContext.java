package com.github.epsilon.gui.dropdown;

import com.github.epsilon.graphics.LuminTexture;
import com.github.epsilon.graphics.schedulers.render2d.Render2DTexture;
import com.github.epsilon.graphics.text.ttf.TtfFontLoader;
import com.github.epsilon.gui.dsl.PanelUiTree;
import net.minecraft.resources.Identifier;

import java.awt.*;

/**
 * Dropdown 的单帧声明式绘制上下文。
 * <p>
 * 组件只向 {@link PanelUiTree.Scope} 提交节点；分层、合批和 RenderPass 提交由 PanelUiTree/Render2DScheduler 统一处理。
 */
public final class DropdownDrawContext {

    private final PanelUiTree.Scope scope;
    private final TextMetrics textMetrics;

    public DropdownDrawContext(PanelUiTree.Scope scope, TextMetrics textMetrics) {
        this.scope = scope;
        this.textMetrics = textMetrics;
    }

    public PanelUiTree.Scope scope() {
        return scope;
    }

    public DropdownDrawContext withScope(PanelUiTree.Scope scope) {
        return new DropdownDrawContext(scope, textMetrics);
    }

    public void shadow(float x, float y, float width, float height, float radius, float blurRadius, Color color) {
        scope.shadow(x, y, width, height, radius, blurRadius, color);
    }

    public void shadow(float x, float y, float width, float height, float topLeft, float topRight,
                       float bottomRight, float bottomLeft, float blurRadius, Color color) {
        scope.shadow(x, y, width, height, topLeft, topRight, bottomRight, bottomLeft, blurRadius, color);
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
        scope.outline(x, y, width, height, radius, outlineWidth, color);
    }

    public void outline(float x, float y, float width, float height, float topLeft, float topRight,
                        float bottomRight, float bottomLeft, float outlineWidth, Color color) {
        scope.outline(x, y, width, height, topLeft, topRight, bottomRight, bottomLeft, outlineWidth, color);
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

    public void texture(Identifier texture, float x, float y, float width, float height,
                        float u0, float v0, float u1, float v1, Color color) {
        texture(texture, x, y, width, height, u0, v0, u1, v1, color, false);
    }

    public void texture(Identifier texture, float x, float y, float width, float height,
                        float u0, float v0, float u1, float v1, Color color, boolean linearFilter) {
        scope.texture(new Render2DTexture.IdentifierRef(texture, linearFilter), x, y, width, height, u0, v0, u1, v1, color);
    }

    public void texture(LuminTexture texture, float x, float y, float width, float height,
                        float u0, float v0, float u1, float v1, Color color) {
        scope.texture(new Render2DTexture.LuminRef(texture), x, y, width, height, u0, v0, u1, v1, color);
    }

    public void roundedTexture(Identifier texture, float x, float y, float width, float height, float radius,
                               float u0, float v0, float u1, float v1, Color color) {
        roundedTexture(texture, x, y, width, height, radius, u0, v0, u1, v1, color, false);
    }

    public void roundedTexture(Identifier texture, float x, float y, float width, float height, float radius,
                               float u0, float v0, float u1, float v1, Color color, boolean linearFilter) {
        scope.roundedTexture(new Render2DTexture.IdentifierRef(texture, linearFilter), x, y, width, height, radius, u0, v0, u1, v1, color);
    }

    public void roundedTexture(LuminTexture texture, float x, float y, float width, float height, float radius,
                               float u0, float v0, float u1, float v1, Color color) {
        scope.roundedTexture(new Render2DTexture.LuminRef(texture), x, y, width, height, radius, u0, v0, u1, v1, color);
    }

    public void playerHead(LuminTexture texture, float x, float y, float size, float radius, Color color) {
        scope.playerHead(texture, x, y, size, radius, color);
    }

    public void text(String text, float x, float y, float scale, Color color) {
        scope.text(text, x, y, scale, color);
    }

    public void text(String text, float x, float y, float scale, Color color, TtfFontLoader fontLoader) {
        scope.text(text, x, y, scale, color, fontLoader);
    }

    public float textHeight(float scale) {
        return textMetrics.getHeight(scale);
    }

    public float textHeight(float scale, TtfFontLoader fontLoader) {
        return textMetrics.getHeight(scale, fontLoader);
    }

    public float textWidth(String text, float scale) {
        return textMetrics.getWidth(text, scale);
    }

    public float textWidth(String text, float scale, TtfFontLoader fontLoader) {
        return textMetrics.getWidth(text, scale, fontLoader);
    }

    public interface TextMetrics {
        float getHeight(float scale);

        float getHeight(float scale, TtfFontLoader fontLoader);

        float getWidth(String text, float scale);

        float getWidth(String text, float scale, TtfFontLoader fontLoader);
    }
}
