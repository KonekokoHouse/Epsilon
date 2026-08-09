package com.github.epsilon.gui.panel.popup;

import com.github.epsilon.gui.theme.EpsilonUiTheme;
import com.github.epsilon.gui.theme.MD3Theme;
import com.github.epsilon.settings.impl.ChoiceSetting;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import com.github.slmpc.lumingraphics.mc.v2612.runtime.MinecraftUiRuntime2612;
import com.github.slmpc.lumingraphics.text.icon.IconChars;
import com.github.slmpc.lumingraphics.ui.geometry.UiRect;
import com.github.slmpc.lumingraphics.ui.render.UiContentBuffer;
import com.github.slmpc.lumingraphics.ui.render.UiRenderBatch;
import com.github.slmpc.lumingraphics.ui.tree.UiTree;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

import java.awt.Color;

public final class ChoiceSelectPopup implements PanelPopupHost.Popup {
    public static final int MAX_VISIBLE_ITEMS = 5;
    private static final float ITEM_HEIGHT = 24.0f;
    private static final float ITEM_INNER_HEIGHT = 22.0f;
    private static final float CONTENT_PADDING = 6.0f;

    private final UiRect bounds;
    private final ChoiceSetting setting;
    private final boolean scrollable;
    private final float maxScroll;
    private final Animation openAnimation = new Animation(Easing.EASE_OUT_CUBIC, 140L);
    private float scroll;
    private int hoveredIndex = -1;

    public ChoiceSelectPopup(UiRect bounds, ChoiceSetting setting) {
        this.setting = setting;
        int optionCount = setting.getChoices().size();
        scrollable = optionCount > MAX_VISIBLE_ITEMS;
        this.bounds = scrollable
                ? new UiRect(bounds.x(), bounds.y(), bounds.width(), MAX_VISIBLE_ITEMS * ITEM_HEIGHT + CONTENT_PADDING * 2)
                : bounds;
        maxScroll = Math.max(0.0f, optionCount * ITEM_HEIGHT - (this.bounds.height() - CONTENT_PADDING * 2));
        openAnimation.setStartValue(0.0f);
    }

    @Override
    public UiRect getBounds() {
        return bounds;
    }

    @Override
    public void extractGui(GuiGraphicsExtractor guiGraphics, UiRenderBatch renderBatch,
                           int mouseX, int mouseY, float partialTick) {
        UiContentBuffer contentBuffer = new UiContentBuffer(renderBatch);
        var textMetrics = MinecraftUiRuntime2612.current().textMetrics();
        UiTree popupTree = UiTree.build(scope -> {
            float progress = scope.animate(openAnimation, 1.0f);
            float popupY = bounds.y() - (1.0f - progress) * 6.0f;
            float viewportHeight = bounds.height() - CONTENT_PADDING * 2;
            float fullContentHeight = setting.getChoices().size() * ITEM_HEIGHT;
            float itemAreaWidth = bounds.width() - CONTENT_PADDING * 2 - (scrollable ? 6.0f : 0.0f);
            UiRect viewportBounds = new UiRect(bounds.x() + CONTENT_PADDING, popupY + CONTENT_PADDING,
                    bounds.width() - CONTENT_PADDING * 2, viewportHeight);
            UiRect popupBounds = new UiRect(bounds.x(), popupY, bounds.width(), bounds.height());

            scope.pushAbsolute(popupBounds, popup -> {
                popup.popupCard(popupBounds.atOrigin(), MD3Theme.CARD_RADIUS, MD3Theme.POPUP_SHADOW_BLUR,
                        EpsilonUiTheme.lumin(MD3Theme.withAlpha(MD3Theme.SHADOW,
                                (int) (MD3Theme.POPUP_SHADOW_ALPHA * progress))),
                        EpsilonUiTheme.lumin(MD3Theme.withAlpha(MD3Theme.SURFACE_CONTAINER_LOW, 255)));
                hoveredIndex = -1;
                popup.viewport(contentBuffer, viewportBounds.relativeTo(popupBounds), scroll, maxScroll,
                        fullContentHeight, content -> {
                            float itemStartY = popupY + CONTENT_PADDING - scroll;
                            for (int index = 0; index < setting.getChoices().size(); index++) {
                                float itemY = itemStartY + index * ITEM_HEIGHT;
                                UiRect itemBounds = new UiRect(bounds.x() + CONTENT_PADDING, itemY,
                                        itemAreaWidth, ITEM_INNER_HEIGHT);
                                boolean visible = itemY + ITEM_INNER_HEIGHT > viewportBounds.y()
                                        && itemY < viewportBounds.bottom();
                                boolean hovered = visible && itemBounds.contains(mouseX, mouseY)
                                        && mouseY >= viewportBounds.y() && mouseY <= viewportBounds.bottom();
                                if (hovered) hoveredIndex = index;
                                boolean selected = index == setting.getChoiceIndex();
                                Color background = selected ? MD3Theme.SECONDARY_CONTAINER
                                        : hovered ? MD3Theme.lerp(MD3Theme.SURFACE_CONTAINER_HIGH,
                                        MD3Theme.SURFACE_CONTAINER_HIGHEST, 0.55f)
                                        : MD3Theme.withAlpha(MD3Theme.SURFACE_CONTAINER_HIGHEST, 0);
                                Color textColor = selected ? MD3Theme.ON_SECONDARY_CONTAINER
                                        : hovered ? MD3Theme.withAlpha(MD3Theme.TEXT_PRIMARY, 255) : MD3Theme.TEXT_SECONDARY;
                                UiRect local = new UiRect(0.0f, index * ITEM_HEIGHT, itemAreaWidth, ITEM_INNER_HEIGHT);
                                content.roundRect(local.x(), local.y(), local.width(), local.height(), 8.0f, background);
                                float textScale = 0.62f;
                                float textY = local.y() + (local.height() - textMetrics.textHeight(textScale, null)) / 2.0f;
                                if (selected) {
                                    float iconScale = 0.72f;
                                    float iconY = local.y() + (local.height()
                                            - textMetrics.textHeight(iconScale, "epsilon-icons")) / 2.0f;
                                    content.text(IconChars.KEYBOARD_ARROW_DOWN, local.x() + 8.0f, iconY,
                                            iconScale, MD3Theme.ON_SECONDARY_CONTAINER, "epsilon-icons");
                                }
                                String choice = setting.getChoices().get(index);
                                content.text(setting.getTranslatedChoice(choice), local.x() + (selected ? 22.0f : 10.0f),
                                        textY, textScale, textColor);
                            }
                        });
            });
        });
        renderBatch.render(popupTree);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        if (!bounds.contains(event.x(), event.y()) || event.button() != 0
                || hoveredIndex < 0 || hoveredIndex >= setting.getChoices().size()) return false;
        setting.setValue(setting.getChoices().get(hoveredIndex));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!scrollable || maxScroll <= 0.0f) return false;
        scroll = Math.clamp(scroll - (float) scrollY * 20.0f, 0.0f, maxScroll);
        return true;
    }

    @Override
    public boolean shouldCloseAfterClick() {
        return true;
    }

    @Override
    public void close() {
    }
}
