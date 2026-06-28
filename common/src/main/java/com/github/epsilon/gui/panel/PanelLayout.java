package com.github.epsilon.gui.panel;

public class PanelLayout {

    private PanelLayout() {
    }

    public static Layout compute(int screenWidth, int screenHeight, float railWidth) {
        float panelWidth = Math.min(screenWidth * 0.56f, 584.0f);
        float panelHeight = Math.min(screenHeight * 0.56f, 324.0f);
        panelWidth = Math.max(panelWidth, 528.0f);
        panelHeight = Math.max(panelHeight, 300.0f);

        float x = (screenWidth - panelWidth) / 2.0f;
        float y = (screenHeight - panelHeight) / 2.0f;

        float gap = MD3Theme.SECTION_GAP;
        float columnHeight = panelHeight - MD3Theme.OUTER_PADDING * 2.0f;
        float railX = x + MD3Theme.OUTER_PADDING;
        float modulesX = railX + railWidth + gap;
        float maxContentRight = x + panelWidth - MD3Theme.OUTER_PADDING;
        float moduleWidth = Math.min(164.0f, panelWidth * 0.292f);
        float detailX = modulesX + moduleWidth + gap;
        float detailWidth = maxContentRight - detailX;

        Rect panel = new Rect(x, y, panelWidth, panelHeight);
        Rect rail = new Rect(railX, y + MD3Theme.OUTER_PADDING, railWidth, columnHeight);
        Rect modules = new Rect(modulesX, y + MD3Theme.OUTER_PADDING, moduleWidth, columnHeight);
        Rect detail = new Rect(detailX, y + MD3Theme.OUTER_PADDING, detailWidth, columnHeight);

        return new Layout(panel, rail, modules, detail);
    }

    public record Layout(Rect panel, Rect rail, Rect modules, Rect detail) {
    }

    public record Rect(float x, float y, float width, float height) {
        public float right() {
            return x + width;
        }

        public float bottom() {
            return y + height;
        }

        public float centerX() {
            return x + width / 2.0f;
        }

        public float centerY() {
            return y + height / 2.0f;
        }

        public boolean contains(double px, double py) {
            return px >= x && px <= right() && py >= y && py <= bottom();
        }

        public Rect inset(float amount) {
            return new Rect(x + amount, y + amount, width - amount * 2.0f, height - amount * 2.0f);
        }

        public Rect atOrigin() {
            return new Rect(0.0f, 0.0f, width, height);
        }

        public Rect relativeTo(Rect origin) {
            return new Rect(x - origin.x(), y - origin.y(), width, height);
        }
    }

}
