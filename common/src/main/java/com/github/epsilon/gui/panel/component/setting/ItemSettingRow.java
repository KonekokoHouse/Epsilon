package com.github.epsilon.gui.panel.component.setting;

import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.gui.dsl.PanelUiTree;
import com.github.epsilon.gui.panel.MD3Theme;
import com.github.epsilon.gui.panel.PanelLayout;
import com.github.epsilon.gui.panel.component.SettingRow;
import com.github.epsilon.settings.impl.ItemSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

public class ItemSettingRow extends SettingRow<ItemSetting> {

    public ItemSettingRow(ItemSetting setting) { super(setting); }

    @Override
    public void buildUi(PanelUiTree.Scope scope, GuiGraphicsExtractor guiGraphics, TextRenderer textRenderer,
        PanelLayout.Rect bounds, float hoverProgress, int mouseX, int mouseY, float partialTick) {
        float labelScale = 0.68f;
        float labelY = (bounds.height() - textRenderer.getHeight(labelScale)) / 2.0f;
        Identifier id = BuiltInRegistries.ITEM.getKey(setting.getValue());
        String summary = id != null ? id.toString() : "Air";
        float valueScale = 0.56f;
        scope.roundRect(0.0f, 0.0f, bounds.width(), bounds.height(), MD3Theme.CARD_RADIUS, MD3Theme.rowSurface(hoverProgress));
        scope.text(setting.getDisplayName(), MD3Theme.ROW_CONTENT_INSET, labelY, labelScale, MD3Theme.TEXT_PRIMARY);
        scope.text(summary, bounds.width() - MD3Theme.ROW_TRAILING_INSET - textRenderer.getWidth(summary, valueScale), labelY, valueScale, MD3Theme.TEXT_SECONDARY);
    }

    @Override
    public boolean mouseClicked(PanelLayout.Rect bounds, MouseButtonEvent event, boolean isDoubleClick) {
        return bounds.contains(event.x(), event.y()) && event.button() == 0;
    }
}
