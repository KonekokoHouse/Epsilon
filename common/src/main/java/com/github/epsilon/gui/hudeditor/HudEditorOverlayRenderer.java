package com.github.epsilon.gui.hudeditor;

import com.github.epsilon.gui.dsl.PanelRenderBatch;
import com.github.epsilon.gui.dsl.PanelUiTree;
import com.github.epsilon.modules.HudModule;
import net.minecraft.util.Mth;

import java.awt.*;

public class HudEditorOverlayRenderer {

    private static final Color GUIDE_LINE_COLOR = new Color(255, 255, 255, 26);
    private static final Color GUIDE_BAND_COLOR = new Color(255, 255, 255, 10);
    private static final Color DRAG_GUIDE_LINE_COLOR = new Color(145, 205, 255, 52);
    private static final Color DRAG_GUIDE_BAND_COLOR = new Color(120, 190, 255, 20);
    private static final Color SNAP_PREVIEW_COLOR = new Color(170, 220, 255, 145);
    private static final Color ANCHOR_LABEL_BG = new Color(8, 12, 18, 150);
    private static final Color ANCHOR_LABEL_TEXT = new Color(236, 241, 247, 220);
    private static final Color ANCHOR_MARKER_OUTLINE = new Color(5, 8, 12, 190);
    private static final Color ANCHOR_MARKER_COLOR = new Color(236, 241, 247, 230);
    private static final Color DRAG_ANCHOR_MARKER_COLOR = new Color(170, 220, 255, 235);

    private static final float GUIDE_LABEL_SCALE = 0.72f;
    private static final float GUIDE_THICKNESS = 1.0f;
    private static final float ANCHOR_MARKER_OUTER_SIZE = 7.0f;
    private static final float ANCHOR_MARKER_INNER_SIZE = 4.0f;
    private static final float LABEL_PADDING_X = 6.0f;
    private static final float LABEL_PADDING_Y = 4.0f;
    private static final float LABEL_MARGIN = 6.0f;

    private final PanelRenderBatch renderBatch = new PanelRenderBatch();
    private final PanelUiTree.Scope scope = new PanelUiTree.Scope();

    public void addThirdGuides(HudModule focus, boolean draggingFocus, int screenWidth, int screenHeight) {
        float splitX1 = screenWidth / 3.0f;
        float splitX2 = splitX1 * 2.0f;
        float splitY1 = screenHeight / 3.0f;
        float splitY2 = splitY1 * 2.0f;
        Color bandColor = draggingFocus ? DRAG_GUIDE_BAND_COLOR : GUIDE_BAND_COLOR;
        Color lineColor = draggingFocus ? DRAG_GUIDE_LINE_COLOR : GUIDE_LINE_COLOR;

        switch (focus.getHorizontalAnchor()) {
            case Left -> scope.rect(0.0f, 0.0f, splitX1, screenHeight, bandColor);
            case Center -> scope.rect(splitX1, 0.0f, splitX1, screenHeight, bandColor);
            case Right -> scope.rect(splitX2, 0.0f, screenWidth - splitX2, screenHeight, bandColor);
        }

        switch (focus.getVerticalAnchor()) {
            case Top -> scope.rect(0.0f, 0.0f, screenWidth, splitY1, bandColor);
            case Center -> scope.rect(0.0f, splitY1, screenWidth, splitY1, bandColor);
            case Bottom -> scope.rect(0.0f, splitY2, screenWidth, screenHeight - splitY2, bandColor);
        }

        scope.rect(splitX1, 0.0f, GUIDE_THICKNESS, screenHeight, lineColor);
        scope.rect(splitX2, 0.0f, GUIDE_THICKNESS, screenHeight, lineColor);
        scope.rect(0.0f, splitY1, screenWidth, GUIDE_THICKNESS, lineColor);
        scope.rect(0.0f, splitY2, screenWidth, GUIDE_THICKNESS, lineColor);
    }

    public void addAnchorOverlay(HudModule focus, boolean draggingFocus, int screenWidth, int screenHeight) {
        float anchorX = HudLayoutHelper.getAnchorPointX(focus.getHorizontalAnchor(), focus.x, focus.width);
        float anchorY = HudLayoutHelper.getAnchorPointY(focus.getVerticalAnchor(), focus.y, focus.height);

        float outerHalf = ANCHOR_MARKER_OUTER_SIZE / 2.0f;
        float innerHalf = ANCHOR_MARKER_INNER_SIZE / 2.0f;
        Color markerColor = draggingFocus ? DRAG_ANCHOR_MARKER_COLOR : ANCHOR_MARKER_COLOR;
        scope.rect(anchorX - outerHalf, anchorY - outerHalf, ANCHOR_MARKER_OUTER_SIZE, ANCHOR_MARKER_OUTER_SIZE, ANCHOR_MARKER_OUTLINE);
        scope.rect(anchorX - innerHalf, anchorY - innerHalf, ANCHOR_MARKER_INNER_SIZE, ANCHOR_MARKER_INNER_SIZE, markerColor);

        String label = focus.getHorizontalAnchor().name() + " / " + focus.getVerticalAnchor().name();
        float textWidth = renderBatch.textRenderer().getWidth(label, GUIDE_LABEL_SCALE);
        float textHeight = renderBatch.textRenderer().getHeight(GUIDE_LABEL_SCALE);
        float labelWidth = textWidth + LABEL_PADDING_X * 2.0f;
        float labelHeight = textHeight + LABEL_PADDING_Y * 2.0f;
        float labelX = Mth.clamp(focus.x + focus.width / 2.0f - labelWidth / 2.0f, LABEL_MARGIN, screenWidth - labelWidth - LABEL_MARGIN);
        float preferredLabelY = focus.y - labelHeight - LABEL_MARGIN;
        float labelY = preferredLabelY >= LABEL_MARGIN
                ? preferredLabelY
                : Mth.clamp(focus.y + focus.height + LABEL_MARGIN, LABEL_MARGIN, screenHeight - labelHeight - LABEL_MARGIN);

        scope.layer(0, layer -> layer.rect(labelX, labelY, labelWidth, labelHeight, ANCHOR_LABEL_BG));
        scope.layer(10, layer -> layer.text(label, labelX + LABEL_PADDING_X, labelY + LABEL_PADDING_Y - 1.0f, GUIDE_LABEL_SCALE, ANCHOR_LABEL_TEXT));
    }

    public void addSnapPreview(Float snapPreviewX, Float snapPreviewY, int screenWidth, int screenHeight) {
        if (snapPreviewX != null) {
            float x = Mth.clamp(snapPreviewX, 0.0f, screenWidth - GUIDE_THICKNESS);
            scope.rect(x, 0.0f, GUIDE_THICKNESS, screenHeight, SNAP_PREVIEW_COLOR);
        }

        if (snapPreviewY != null) {
            float y = Mth.clamp(snapPreviewY, 0.0f, screenHeight - GUIDE_THICKNESS);
            scope.rect(0.0f, y, screenWidth, GUIDE_THICKNESS, SNAP_PREVIEW_COLOR);
        }
    }

    public void flushRenderer() {
        renderBatch.render(scope.snapshot());
        renderBatch.flushAndClear();
        scope.clear();
    }

}
