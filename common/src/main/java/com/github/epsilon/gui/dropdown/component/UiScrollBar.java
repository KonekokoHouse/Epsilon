package com.github.epsilon.gui.dropdown.component;

import com.github.epsilon.gui.theme.MD3Theme;
import com.github.epsilon.gui.theme.EpsilonUiTheme;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import com.github.slmpc.lumingraphics.ui.geometry.UiRect;
import com.github.slmpc.lumingraphics.ui.tree.UiTree;

/** Dropdown 使用的轻量滚动条，只依赖公共 Lumin UI 几何与绘制树。 */
final class UiScrollBar {
    static final float WIDTH = 2.0f;
    static final float RIGHT_INSET = 2.5f;
    static final float MIN_THUMB_HEIGHT = 10.0f;
    static final float HIT_WIDTH = 10.0f;
    static final float HOVER_WIDTH = 2.5f;

    private final Animation hoverAnimation = new Animation(Easing.EASE_OUT_CUBIC, 160L);
    private boolean dragging;
    private float dragOffset;

    boolean isDragging() {
        return dragging;
    }

    void draw(UiTree.Scope scope, UiRect viewport, float scroll, float maxScroll,
              float contentHeight, double mouseX, double mouseY) {
        Geometry geometry = computeGeometry(viewport, scroll, maxScroll, contentHeight);
        if (geometry == null) {
            hoverAnimation.run(0.0f);
            return;
        }
        hoverAnimation.run(geometry.trackContains(mouseX, mouseY) || dragging ? 1.0f : 0.0f);
        float hover = hoverAnimation.getValue();
        float thumbWidth = geometry.thumbWidth + (HOVER_WIDTH - geometry.thumbWidth) * hover;
        float thumbX = geometry.thumbX - (thumbWidth - geometry.thumbWidth) * 0.5f;
        scope.roundRect(thumbX, geometry.thumbY, thumbWidth, geometry.thumbHeight,
                thumbWidth * 0.5f, EpsilonUiTheme.lumin().scrollBar(hover));
    }

    boolean mouseClicked(double mouseX, double mouseY, UiRect viewport, float scroll,
                         float maxScroll, float contentHeight) {
        Geometry geometry = computeGeometry(viewport, scroll, maxScroll, contentHeight);
        if (geometry == null || !geometry.trackContains(mouseX, mouseY)) return false;
        dragging = true;
        dragOffset = geometry.thumbContains(mouseX, mouseY)
                ? (float) mouseY - geometry.thumbY
                : geometry.thumbHeight / 2.0f;
        return true;
    }

    float mouseDragged(double mouseY, UiRect viewport, float maxScroll, float contentHeight) {
        if (!dragging || maxScroll <= 0.0f) return -1.0f;
        Geometry geometry = computeGeometry(viewport, 0.0f, maxScroll, contentHeight);
        if (geometry == null) return 0.0f;
        float travel = geometry.trackHeight - geometry.thumbHeight;
        if (travel <= 0.0f) return 0.0f;
        float ratio = ((float) mouseY - dragOffset - geometry.trackY) / travel;
        return Math.clamp(ratio, 0.0f, 1.0f) * maxScroll;
    }

    boolean mouseReleased() {
        if (!dragging) return false;
        dragging = false;
        return true;
    }

    void reset() {
        dragging = false;
        hoverAnimation.run(0.0f);
    }

    boolean isHovered(double mouseX, double mouseY, UiRect viewport, float scroll,
                      float maxScroll, float contentHeight) {
        Geometry geometry = computeGeometry(viewport, scroll, maxScroll, contentHeight);
        return geometry != null && geometry.trackContains(mouseX, mouseY);
    }

    private static Geometry computeGeometry(UiRect viewport, float scroll, float maxScroll, float contentHeight) {
        if (maxScroll <= 0.0f || contentHeight <= viewport.height() || viewport.height() <= 0.5f) return null;
        float trackHeight = viewport.height();
        float thumbHeight = Math.min(trackHeight,
                Math.max(MIN_THUMB_HEIGHT, viewport.height() / contentHeight * trackHeight));
        float travel = trackHeight - thumbHeight;
        float thumbY = viewport.y() + travel * Math.clamp(scroll / maxScroll, 0.0f, 1.0f);
        return new Geometry(viewport.right() - RIGHT_INSET, thumbY, WIDTH, thumbHeight,
                viewport.right() - HIT_WIDTH, viewport.y(), HIT_WIDTH, trackHeight);
    }

    private record Geometry(float thumbX, float thumbY, float thumbWidth, float thumbHeight,
                            float trackX, float trackY, float trackWidth, float trackHeight) {
        boolean thumbContains(double mouseX, double mouseY) {
            return mouseX >= trackX && mouseX <= trackX + trackWidth
                    && mouseY >= thumbY && mouseY <= thumbY + thumbHeight;
        }

        boolean trackContains(double mouseX, double mouseY) {
            return mouseX >= trackX && mouseX <= trackX + trackWidth
                    && mouseY >= trackY && mouseY <= trackY + trackHeight;
        }
    }
}
