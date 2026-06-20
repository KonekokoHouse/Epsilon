package com.github.epsilon.gui.panel.component.setting;

import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.graphics.text.StaticFontLoader;
import com.github.epsilon.gui.dsl.PanelUiTree;
import com.github.epsilon.gui.panel.MD3Theme;
import com.github.epsilon.gui.panel.PanelLayout;
import com.github.epsilon.gui.panel.component.PanelElements;
import com.github.epsilon.gui.panel.component.SettingRow;
import com.github.epsilon.settings.impl.BlockListSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

public class BlockListSettingRow extends SettingRow<BlockListSetting> {

    public BlockListSettingRow(BlockListSetting setting) {
        super(setting);
    }

    @Override
    public void buildUi(PanelUiTree.Scope scope, GuiGraphicsExtractor guiGraphics, TextRenderer textRenderer,
                        PanelLayout.Rect bounds, float hoverProgress, int mouseX, int mouseY, float partialTick) {
        float labelScale = 0.68f;
        float labelY = bounds.y() + (bounds.height() - textRenderer.getHeight(labelScale)) / 2.0f - 1.0f;
        String summary = setting.size() + " blocks";
        float chipTextScale = 0.58f;

        scope.roundRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), MD3Theme.CARD_RADIUS, MD3Theme.rowSurface(hoverProgress));
        scope.text(setting.getDisplayName(), bounds.x() + MD3Theme.ROW_CONTENT_INSET, labelY, labelScale, MD3Theme.TEXT_PRIMARY);

        PanelLayout.Rect chipBounds = PanelElements.measureAssistChipBounds(textRenderer, bounds, summary, chipTextScale, 8.0f, 12.0f, 94.0f);
        scope.chip(chipBounds, summary, chipTextScale, MD3Theme.SECONDARY_CONTAINER, MD3Theme.ON_SECONDARY_CONTAINER,
                "+", 0.58f, StaticFontLoader.ICONS);
    }

    @Override
    public boolean mouseClicked(PanelLayout.Rect bounds, MouseButtonEvent event, boolean isDoubleClick) {
        return bounds.contains(event.x(), event.y()) && event.button() == 0;
    }

}
