package com.github.epsilon.gui.panel.utils;

import com.github.epsilon.gui.dropdown.component.DropdownScrollBar;
import com.github.epsilon.gui.dsl.PanelUiTree;
import com.github.epsilon.gui.panel.PanelLayout;

public class ScrollBarUtils {

    /**
     * Total horizontal space the scrollbar occupies (width + padding on each side).
     */
    public static final float TOTAL_WIDTH = DropdownScrollBar.TOTAL_WIDTH;

    private ScrollBarUtils() {
    }

    public static void draw(PanelUiTree.Scope scope, PanelLayout.Rect viewport, float scroll, float maxScroll, float contentHeight) {
        DropdownScrollBar.draw(scope, viewport, scroll, maxScroll, contentHeight);
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
    public static ThumbGeometry computeThumb(PanelLayout.Rect viewport, float scroll, float maxScroll, float contentHeight) {
        DropdownScrollBar.Geometry geometry = DropdownScrollBar.computeGeometry(viewport, scroll, maxScroll, contentHeight);
        if (geometry == null) {
            return null;
        }
        return new ThumbGeometry(geometry.thumbX(), geometry.thumbY(), geometry.thumbWidth(), geometry.thumbHeight(),
                geometry.trackX(), geometry.trackY(), geometry.trackWidth(), geometry.trackHeight());
    }

    /**
     * Convert a thumb-top Y coordinate back to an absolute scroll value.
     */
    public static float scrollFromMouseY(float thumbTopY, PanelLayout.Rect viewport, float maxScroll, float contentHeight) {
        return DropdownScrollBar.scrollFromThumbTopY(thumbTopY, viewport, maxScroll, contentHeight);
    }

}
