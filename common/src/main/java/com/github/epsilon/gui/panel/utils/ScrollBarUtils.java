package com.github.epsilon.gui.panel.utils;

import com.github.slmpc.lumingraphics.ui.geometry.UiRect;
import com.github.slmpc.lumingraphics.ui.tree.UiTree;
import com.github.slmpc.lumingraphics.ui.control.UiScrollBar;
import com.github.epsilon.gui.theme.EpsilonUiTheme;

public class ScrollBarUtils {

    /**
     * Total horizontal space the scrollbar occupies (width + padding on each side).
     */
    public static final float TOTAL_WIDTH = UiScrollBar.TOTAL_WIDTH;

    private ScrollBarUtils() {
    }

    public static void draw(UiTree.Scope scope, UiRect viewport, float scroll, float maxScroll, float contentHeight) {
        UiScrollBar.Geometry geometry = UiScrollBar.computeGeometry(viewport, scroll, maxScroll, contentHeight);
        if (geometry != null) {
            scope.roundRect(geometry.thumbX(), geometry.thumbY(), geometry.thumbWidth(), geometry.thumbHeight(),
                    geometry.thumbWidth() / 2.0f, EpsilonUiTheme.lumin().scrollBar(0.0f));
        }
    }

    /**
     * Geometry of the scrollbar thumb and its hit-test track area.
     */
    public record ThumbGeometry(float thumbX, float thumbY, float thumbWidth, float thumbHeight,
                                float trackX, float trackY, float trackWidth, float trackHeight) {
        public boolean thumbContains(double px, double py) {
            return px >= thumbX && px <= thumbX + thumbWidth && py >= thumbY && py <= thumbY + thumbHeight;
        }

        public boolean trackContains(double px, double py) {
            return px >= trackX && px <= trackX + trackWidth && py >= trackY && py <= trackY + trackHeight;
        }
    }

    /**
     * Compute the thumb geometry for hit-testing.
     * Returns null if there is no scrollbar (maxScroll &lt;= 0).
     */
    public static ThumbGeometry computeThumb(UiRect viewport, float scroll, float maxScroll, float contentHeight) {
        UiScrollBar.Geometry geometry = UiScrollBar.computeGeometry(viewport, scroll, maxScroll, contentHeight);
        if (geometry == null) {
            return null;
        }
        return new ThumbGeometry(geometry.thumbX(), geometry.thumbY(), geometry.thumbWidth(), geometry.thumbHeight(),
                geometry.trackX(), geometry.trackY(), geometry.trackWidth(), geometry.trackHeight());
    }

    /**
     * Convert a thumb-top Y coordinate back to an absolute scroll value.
     */
    public static float scrollFromMouseY(float thumbTopY, UiRect viewport, float maxScroll, float contentHeight) {
        return UiScrollBar.scrollFromThumbTopY(thumbTopY, viewport, maxScroll, contentHeight);
    }

}
