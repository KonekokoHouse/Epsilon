package com.github.epsilon.gui.panel.popup;

import com.github.epsilon.graphics.text.IconChars;
import com.github.epsilon.graphics.text.StaticFontLoader;
import com.github.epsilon.gui.dsl.PanelRenderBatch;
import com.github.epsilon.gui.dsl.PanelUiTree;
import com.github.epsilon.gui.panel.MD3Theme;
import com.github.epsilon.gui.panel.PanelLayout;
import com.github.epsilon.gui.panel.utils.PanelContentBuffer;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

import java.awt.*;

public class EnumSelectPopup implements PanelPopupHost.Popup {

    public static final int MAX_VISIBLE_ITEMS = 5;
    private static final float ITEM_HEIGHT = 24.0f;
    private static final float ITEM_INNER_HEIGHT = 22.0f;
    private static final float CONTENT_PADDING = 6.0f;
    private static final String DROPDOWN_ICON = IconChars.KEYBOARD_ARROW_DOWN;

    private final PanelLayout.Rect bounds;
    private final EnumSetting<?> setting;
    private final PanelLayout.Rect anchorBounds;
    private final boolean scrollable;
    private final float maxScroll;
    private float scroll;

    private final PanelContentBuffer contentBuffer = new PanelContentBuffer();
    private final Animation openAnimation = new Animation(Easing.EASE_OUT_CUBIC, 140L);
    private int hoveredIndex = -1;

    public EnumSelectPopup(PanelLayout.Rect bounds, PanelLayout.Rect anchorBounds, EnumSetting<?> setting) {
        this.anchorBounds = anchorBounds;
        this.setting = setting;

        int optionCount = setting.getModes().length;
        this.scrollable = optionCount > MAX_VISIBLE_ITEMS;
        if (scrollable) {
            float cappedHeight = MAX_VISIBLE_ITEMS * ITEM_HEIGHT + CONTENT_PADDING * 2;
            this.bounds = new PanelLayout.Rect(bounds.x(), bounds.y(), bounds.width(), cappedHeight);
        } else {
            this.bounds = bounds;
        }

        float fullContentHeight = optionCount * ITEM_HEIGHT;
        float viewportHeight = this.bounds.height() - CONTENT_PADDING * 2;
        this.maxScroll = Math.max(0, fullContentHeight - viewportHeight);

        this.openAnimation.setStartValue(0.0f);
    }

    public EnumSetting<?> getSetting() {
        return setting;
    }

    @Override
    public PanelLayout.Rect getBounds() {
        return bounds;
    }

    @Override
    public void extractGui(GuiGraphicsExtractor guiGraphics, PanelRenderBatch renderBatch, int mouseX, int mouseY, float partialTick) {
        contentBuffer.clear();
        PanelUiTree popupTree = PanelUiTree.build(scope -> {
            float progress = scope.animate(openAnimation, 1.0f);
            float popupY = bounds.y() - (1.0f - progress) * 6.0f;
            float viewportHeight = bounds.height() - CONTENT_PADDING * 2;
            float fullContentHeight = setting.getModes().length * ITEM_HEIGHT;
            float itemAreaWidth = bounds.width() - CONTENT_PADDING * 2 - (scrollable ? 6.0f : 0.0f);
            PanelLayout.Rect viewportBounds = new PanelLayout.Rect(bounds.x() + CONTENT_PADDING, popupY + CONTENT_PADDING,
                    bounds.width() - CONTENT_PADDING * 2, viewportHeight);

            scope.popupCard(new PanelLayout.Rect(bounds.x(), popupY, bounds.width(), bounds.height()),
                    MD3Theme.CARD_RADIUS,
                    POPUP_SHADOW_RADIUS,
                    MD3Theme.withAlpha(MD3Theme.SHADOW, (int) (MD3Theme.POPUP_SHADOW_ALPHA * progress)),
                    MD3Theme.withAlpha(MD3Theme.SURFACE_CONTAINER_LOW, 255));

            hoveredIndex = -1;
            scope.viewport(contentBuffer, viewportBounds, guiGraphics.guiHeight(), scroll, maxScroll, fullContentHeight, content -> {
                Enum<?>[] modes = setting.getModes();
                float itemStartY = popupY + CONTENT_PADDING - scroll;
                for (int i = 0; i < modes.length; i++) {
                    float itemY = itemStartY + i * ITEM_HEIGHT;
                    PanelLayout.Rect itemBounds = new PanelLayout.Rect(bounds.x() + CONTENT_PADDING, itemY, itemAreaWidth, ITEM_INNER_HEIGHT);
                    boolean visible = itemY + ITEM_INNER_HEIGHT > viewportBounds.y() && itemY < viewportBounds.bottom();
                    boolean hovered = visible && itemBounds.contains(mouseX, mouseY)
                            && mouseY >= viewportBounds.y() && mouseY <= viewportBounds.bottom();
                    if (hovered) {
                        hoveredIndex = i;
                    }
                    boolean selected = i == setting.getModeIndex();
                    Color baseBackground = MD3Theme.withAlpha(MD3Theme.SURFACE_CONTAINER_HIGHEST, 0);
                    Color hoverBackground = MD3Theme.lerp(MD3Theme.SURFACE_CONTAINER_HIGH, MD3Theme.SURFACE_CONTAINER_HIGHEST, 0.55f);
                    Color selectedBackground = MD3Theme.SECONDARY_CONTAINER;
                    Color background = selected ? selectedBackground : (hovered ? hoverBackground : baseBackground);
                    Color textColor = selected ? MD3Theme.ON_SECONDARY_CONTAINER : (hovered ? MD3Theme.withAlpha(MD3Theme.TEXT_PRIMARY, 255) : MD3Theme.TEXT_SECONDARY);
                    content.roundRect(itemBounds.x(), itemY, itemBounds.width(), itemBounds.height(), 8.0f, background);
                    if (selected) {
                        content.text("V", itemBounds.x() + 8.0f, itemY + 6.5f, 0.72f, MD3Theme.ON_SECONDARY_CONTAINER, StaticFontLoader.ICONS);
                    }
                    content.text(setting.getTranslatedValueByIndex(i), itemBounds.x() + (selected ? 22.0f : 10.0f), itemY + 7.0f, 0.62f, textColor);
                }
            });
        });
        renderBatch.render(popupTree);
    }

    @Override
    public void flush(PanelRenderBatch renderBatch) {
        renderBatch.flushAndClear();
        contentBuffer.flushAndClear();
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        if (!bounds.contains(event.x(), event.y()) || event.button() != 0) {
            return false;
        }
        Enum[] modes = setting.getModes();
        if (hoveredIndex < 0 || hoveredIndex >= modes.length) {
            return false;
        }
        ((EnumSetting) setting).setMode(modes[hoveredIndex]);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!scrollable || maxScroll <= 0.0f) {
            return false;
        }
        float nextScroll = scroll - (float) scrollY * 20.0f;
        scroll = Math.clamp(nextScroll, 0.0f, maxScroll);
        return true;
    }

    @Override
    public boolean shouldCloseAfterClick() {
        return true;
    }

}
