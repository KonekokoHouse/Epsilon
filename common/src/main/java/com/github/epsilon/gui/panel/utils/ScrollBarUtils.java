package com.github.epsilon.gui.panel.utils;

import com.github.epsilon.gui.dsl.PanelUiTree;
import com.github.epsilon.gui.panel.MD3Theme;
import com.github.epsilon.gui.panel.PanelLayout;

public class ScrollBarUtils {

    private static final float WIDTH = 3.0f;
    private static final float PADDING = 2.0f;
    private static final float MIN_THUMB_HEIGHT = 12.0f;
    private static final float HIT_EXTRA_PADDING = 4.0f;

    /**
     * Total horizontal space the scrollbar occupies (width + padding on each side).
     */
    public static final float TOTAL_WIDTH = WIDTH + PADDING * 2;

    private ScrollBarUtils() {
    }

    public static void draw(PanelUiTree.Scope scope, PanelLayout.Rect viewport, float scroll, float maxScroll, float contentHeight) {
        if (maxScroll <= 0 || contentHeight <= viewport.height()) {
            return;
        }
        float trackHeight = viewport.height() - PADDING * 2;
        float thumbHeight = Math.max(MIN_THUMB_HEIGHT, (viewport.height() / contentHeight) * trackHeight);
        float thumbTravel = trackHeight - thumbHeight;
        float scrollRatio = maxScroll > 0 ? scroll / maxScroll : 0;
        float thumbY = viewport.y() + PADDING + scrollRatio * thumbTravel;
        float thumbX = viewport.right() - WIDTH - PADDING;
        scope.roundRect(thumbX, thumbY, WIDTH, thumbHeight, WIDTH / 2.0f, MD3Theme.withAlpha(MD3Theme.OUTLINE, 80));
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
        if (maxScroll <= 0 || contentHeight <= viewport.height()) {
            return null;
        }
        float trackHeight = viewport.height() - PADDING * 2;
        float thumbHeight = Math.max(MIN_THUMB_HEIGHT, (viewport.height() / contentHeight) * trackHeight);
        float thumbTravel = trackHeight - thumbHeight;
        float scrollRatio = maxScroll > 0 ? scroll / maxScroll : 0;
        float thumbY = viewport.y() + PADDING + scrollRatio * thumbTravel;
        float thumbX = viewport.right() - WIDTH - PADDING;
        // Wider hit-test area for the track
        float trackX = viewport.right() - TOTAL_WIDTH - HIT_EXTRA_PADDING;
        float trackWidth = TOTAL_WIDTH + HIT_EXTRA_PADDING;
        return new ThumbGeometry(thumbX, thumbY, WIDTH, thumbHeight, trackX, viewport.y(), trackWidth, viewport.height());
    }

    /**
     * Convert a thumb-top Y coordinate back to an absolute scroll value.
     */
    public static float scrollFromMouseY(float thumbTopY, PanelLayout.Rect viewport, float maxScroll, float contentHeight) {
        if (maxScroll <= 0 || contentHeight <= viewport.height()) {
            return 0;
        }
        float trackHeight = viewport.height() - PADDING * 2;
        float thumbHeight = Math.max(MIN_THUMB_HEIGHT, (viewport.height() / contentHeight) * trackHeight);
        float thumbTravel = trackHeight - thumbHeight;
        if (thumbTravel <= 0) {
            return 0;
        }
        float ratio = (thumbTopY - viewport.y() - PADDING) / thumbTravel;
        ratio = Math.clamp(ratio, 0.0f, 1.0f);
        return ratio * maxScroll;
    }

}
