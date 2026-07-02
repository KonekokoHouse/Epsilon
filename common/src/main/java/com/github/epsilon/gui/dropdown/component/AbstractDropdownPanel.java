package com.github.epsilon.gui.dropdown.component;

import com.github.epsilon.assets.i18n.TranslateComponent;
import com.github.epsilon.graphics.text.StaticFontLoader;
import com.github.epsilon.gui.dropdown.DropdownDrawContext;
import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.epsilon.gui.panel.MD3Theme;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import net.minecraft.util.Mth;

public abstract class AbstractDropdownPanel implements DropdownPanel {

    protected final String id;
    protected final String title;
    protected final TranslateComponent titleComponent;
    protected final TitleSupplier titleSupplier;
    protected final String icon;
    protected final Animation openAnim = new Animation(Easing.EASE_IN_OUT_CUBIC, DropdownTheme.ANIM_OPEN);
    protected final Animation introAnim;

    protected float x;
    protected float y;
    protected float width = DropdownTheme.PANEL_WIDTH;
    protected boolean opened;
    protected boolean visible;
    protected boolean dragging;
    protected float dragOffsetX;
    protected float dragOffsetY;
    protected float scroll;
    protected float targetScroll;
    protected float maxScroll;
    protected float maxPanelHeight = 300.0f;

    private static final float SCROLL_SMOOTHING = 0.16f;
    private static final float SCROLL_EPSILON = 0.05f;
    private int renderFrameId = Integer.MIN_VALUE;
    private int cachedMetricsFrameId = Integer.MIN_VALUE;
    private float cachedExpand;
    private float cachedContentHeight;
    private float cachedVisibleContentHeight;
    private float cachedPanelHeight;

    protected AbstractDropdownPanel(String id, String title, String icon, int panelIndex) {
        this(id, title, null, icon, panelIndex);
    }

    protected AbstractDropdownPanel(String id, TranslateComponent titleComponent, String icon, int panelIndex) {
        this(id, null, titleComponent, icon, panelIndex);
    }

    protected AbstractDropdownPanel(String id, TitleSupplier titleSupplier, String icon, int panelIndex) {
        this.id = id;
        this.title = null;
        this.titleComponent = null;
        this.titleSupplier = titleSupplier;
        this.icon = icon;
        this.introAnim = new Animation(Easing.EASE_OUT_SINE, 120L + panelIndex * 45L);
    }

    private AbstractDropdownPanel(String id, String title, TranslateComponent titleComponent, String icon, int panelIndex) {
        this.id = id;
        this.title = title;
        this.titleComponent = titleComponent;
        this.titleSupplier = null;
        this.icon = icon;
        this.introAnim = new Animation(Easing.EASE_OUT_SINE, 120L + panelIndex * 45L);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void startIntro() {
        introAnim.setStartValue(0.0f);
        introAnim.run(0.0f);
        introAnim.run(1.0f);
        openAnim.setStartValue(opened ? 1.0f : 0.0f);
    }

    @Override
    public void beginRenderFrame(int frameId) {
        renderFrameId = frameId;
        cachedMetricsFrameId = Integer.MIN_VALUE;
    }

    @Override
    public float getIntroValue() {
        introAnim.run(1.0f);
        return introAnim.getValue();
    }

    @Override
    public void drawBackground(DropdownDrawContext renderer) {
        ensureFrameMetrics();
        float expand = cachedExpand;
        float contentHeight = cachedContentHeight;
        float visibleHeight = cachedVisibleContentHeight;
        updateScroll(contentHeight, visibleHeight, true);
        float panelHeight = cachedPanelHeight;

        renderer.shadow(x, y, width, panelHeight, DropdownTheme.PANEL_RADIUS, DropdownTheme.PANEL_SHADOW_BLUR, DropdownTheme.panelShadow());
        renderer.roundRect(x, y, width, panelHeight, DropdownTheme.PANEL_RADIUS, DropdownTheme.panelBackground());

        float iconX = x + 7.5f;
        float textX = icon == null || icon.isBlank() ? x + 10.0f : iconX + 16.0f;
        float textY = y + (DropdownTheme.PANEL_HEADER_HEIGHT - renderer.textHeight(DropdownTheme.HEADER_TEXT_SCALE)) * 0.5f;
        if (icon != null && !icon.isBlank()) {
            float iconY = y + (DropdownTheme.PANEL_HEADER_HEIGHT - renderer.textHeight(DropdownTheme.HEADER_ICON_SCALE)) * 0.5f;
            renderer.text(icon, iconX, iconY, DropdownTheme.HEADER_ICON_SCALE, MD3Theme.PRIMARY, StaticFontLoader.ICONS);
        }
        String headerTitle = getTitle();
        renderer.text(headerTitle, textX, textY, DropdownTheme.HEADER_TEXT_SCALE, MD3Theme.TEXT_PRIMARY);
        renderer.triangle(x + width - 10.0f, y + DropdownTheme.PANEL_HEADER_HEIGHT * 0.5f, 3.0f, expand, DropdownTheme.groupChevron(0.0f));

        if (contentHeight > visibleHeight && opened && expand > 0.5f) {
            float scrollbarX = x + width - 2.5f;
            float scrollbarTrackY = y + DropdownTheme.PANEL_HEADER_HEIGHT;
            float scrollbarTrackH = visibleHeight * expand;
            float thumbRatio = visibleHeight / contentHeight;
            float thumbH = Math.max(10.0f, scrollbarTrackH * thumbRatio);
            float thumbY = scrollbarTrackY + (scrollbarTrackH - thumbH) * (maxScroll > 0 ? scroll / maxScroll : 0);
            renderer.roundRect(scrollbarX, thumbY, 2.0f, thumbH, 1.0f, DropdownTheme.scrollbar());
        }
    }

    @Override
    public void drawContent(DropdownDrawContext renderer, int mouseX, int mouseY) {
        ensureFrameMetrics();
        if (cachedExpand < 0.01f) return;

        float contentHeight = cachedContentHeight;
        float visibleHeight = cachedVisibleContentHeight;
        updateScroll(contentHeight, visibleHeight, false);
        drawPanelContent(renderer, mouseX, mouseY, visibleHeight);
    }

    @Override
    public float getContentClipY() {
        return y + DropdownTheme.PANEL_HEADER_HEIGHT;
    }

    @Override
    public float getContentClipHeight() {
        ensureFrameMetrics();
        return cachedVisibleContentHeight * cachedExpand;
    }

    @Override
    public float getPanelHeight() {
        ensureFrameMetrics();
        return cachedPanelHeight;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isHeaderHovered(mouseX, mouseY)) {
            if (button == 0) {
                dragging = true;
                dragOffsetX = (float) (x - mouseX);
                dragOffsetY = (float) (y - mouseY);
                return true;
            }
            if (button == 1) {
                opened = !opened;
                return true;
            }
        }

        if (opened && openAnim.getValue() > 0.5f && isContentHovered(mouseX, mouseY)) {
            return mouseClickedContent(mouseX, mouseY, button);
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragging) {
            dragging = false;
            return true;
        }
        return mouseReleasedContent(mouseX, mouseY, button);
    }

    @Override
    public void mouseDragged(double mouseX, double mouseY) {
        if (dragging) {
            x = (float) (mouseX + dragOffsetX);
            y = (float) (mouseY + dragOffsetY);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!opened) return false;
        if (isPanelHovered(mouseX, mouseY)) {
            targetScroll = Mth.clamp(targetScroll - (float) amount * DropdownTheme.SCROLL_SPEED, 0.0f, maxScroll);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    @Override
    public boolean charTyped(String typedText) {
        return false;
    }

    @Override
    public boolean hasActiveInput() {
        return false;
    }

    @Override
    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public void setMaxPanelHeight(float maxPanelHeight) {
        this.maxPanelHeight = maxPanelHeight;
    }

    @Override
    public float getX() {
        return x;
    }

    @Override
    public float getY() {
        return y;
    }

    @Override
    public float getWidth() {
        return width;
    }

    @Override
    public boolean isOpened() {
        return opened;
    }

    @Override
    public void setOpened(boolean opened) {
        this.opened = opened;
        if (!opened) setScrollImmediate(0.0f);
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    protected abstract float computeContentHeight();

    protected abstract void drawPanelContent(DropdownDrawContext renderer, int mouseX, int mouseY, float visibleHeight);

    protected boolean mouseClickedContent(double mouseX, double mouseY, int button) {
        return false;
    }

    protected boolean mouseReleasedContent(double mouseX, double mouseY, int button) {
        return false;
    }

    protected String getTitle() {
        if (titleSupplier != null) return titleSupplier.get();
        return titleComponent != null ? titleComponent.getTranslatedName() : title;
    }

    @FunctionalInterface
    public interface TitleSupplier {
        String get();
    }

    protected float computeVisibleContentHeight(float contentHeight) {
        float maxContentHeight = Math.max(0.0f, maxPanelHeight - DropdownTheme.PANEL_HEADER_HEIGHT - DropdownTheme.PANEL_BOTTOM_PADDING);
        return Math.min(contentHeight, maxContentHeight);
    }

    protected int getRenderFrameId() {
        return renderFrameId;
    }

    private void ensureFrameMetrics() {
        if (cachedMetricsFrameId == renderFrameId) {
            return;
        }
        openAnim.run(opened ? 1.0f : 0.0f);
        cachedExpand = openAnim.getValue();
        cachedContentHeight = computeContentHeight();
        cachedVisibleContentHeight = computeVisibleContentHeight(cachedContentHeight);
        cachedPanelHeight = DropdownTheme.PANEL_HEADER_HEIGHT + (cachedVisibleContentHeight + DropdownTheme.PANEL_BOTTOM_PADDING) * cachedExpand;
        cachedMetricsFrameId = renderFrameId;
    }

    protected boolean isHeaderHovered(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + DropdownTheme.PANEL_HEADER_HEIGHT;
    }

    protected boolean isContentHovered(double mouseX, double mouseY) {
        float clipY = getContentClipY();
        float clipH = getContentClipHeight();
        return mouseX >= x && mouseX <= x + width && mouseY >= clipY && mouseY <= clipY + clipH;
    }

    protected boolean isPanelHovered(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + getPanelHeight();
    }

    protected boolean isHovered(double mouseX, double mouseY, float x, float y, float w, float h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    protected void setScrollImmediate(float value) {
        scroll = Mth.clamp(value, 0.0f, maxScroll);
        targetScroll = scroll;
    }

    private void updateScroll(float contentHeight, float visibleHeight, boolean animate) {
        maxScroll = Math.max(0.0f, contentHeight - visibleHeight);
        targetScroll = Mth.clamp(targetScroll, 0.0f, maxScroll);
        scroll = Mth.clamp(scroll, 0.0f, maxScroll);

        if (!animate) {
            return;
        }

        if (Math.abs(scroll - targetScroll) <= SCROLL_EPSILON) {
            scroll = targetScroll;
        } else {
            scroll = Mth.lerp(SCROLL_SMOOTHING, scroll, targetScroll);
        }
    }

    protected String trimToWidth(String value, float scale, float maxWidth, DropdownDrawContext renderer) {
        if (value == null || value.isEmpty()) return "";
        if (renderer.textWidth(value, scale) <= maxWidth) return value;
        String ellipsis = "...";
        float ellipsisWidth = renderer.textWidth(ellipsis, scale);
        if (ellipsisWidth >= maxWidth) return ellipsis;
        for (int len = value.length() - 1; len >= 0; len--) {
            String candidate = value.substring(0, len) + ellipsis;
            if (renderer.textWidth(candidate, scale) <= maxWidth) return candidate;
        }
        return ellipsis;
    }

}
