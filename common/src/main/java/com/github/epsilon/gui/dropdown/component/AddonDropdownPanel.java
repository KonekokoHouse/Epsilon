package com.github.epsilon.gui.dropdown.component;

import com.github.epsilon.addon.EpsilonAddon;
import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.gui.dropdown.DropdownDrawContext;
import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.epsilon.gui.dropdown.widget.ColorWidget;
import com.github.epsilon.gui.dropdown.widget.KeybindWidget;
import com.github.epsilon.gui.dropdown.widget.SettingWidget;
import com.github.epsilon.gui.dropdown.widget.StringWidget;
import com.github.epsilon.gui.panel.MD3Theme;
import com.github.epsilon.gui.panel.PanelLayout;
import com.github.epsilon.holders.AddonHolder;
import com.github.epsilon.holders.ConfigHolder;
import com.github.epsilon.settings.Setting;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class AddonDropdownPanel extends AbstractDropdownPanel {

    private static final float ADDON_ROW_HEIGHT = 28.0f;
    private static final float INFO_HEIGHT = 38.0f;
    private static final float GAP = 4.0f;
    private static final float PADDING = 6.0f;

    private String selectedAddonId = "";
    private final List<SettingWidget<?>> widgets = new ArrayList<>();
    private EpsilonAddon lastAddon;

    public AddonDropdownPanel(int panelIndex) {
        super("addon", EpsilonTranslations.Gui.TAB_ADDON, "", panelIndex);
    }

    @Override
    protected float computeContentHeight() {
        EpsilonAddon addon = resolveSelectedAddon();
        if (addon == null) {
            return PADDING * 2.0f + ADDON_ROW_HEIGHT;
        }
        ensureWidgets(addon);
        float height = PADDING + AddonHolder.INSTANCE.getAddons().size() * (ADDON_ROW_HEIGHT + GAP) + INFO_HEIGHT + GAP;
        if (widgets.isEmpty()) {
            height += ADDON_ROW_HEIGHT;
        } else {
            for (SettingWidget<?> widget : widgets) {
                if (widget.isVisible()) height += widget.getHeight() + DropdownTheme.SETTING_GAP;
            }
        }
        return height + PADDING;
    }

    @Override
    protected void drawPanelContent(DropdownDrawContext renderer, int mouseX, int mouseY, float visibleHeight) {
        float currentY = y + DropdownTheme.PANEL_HEADER_HEIGHT + PADDING - scroll;
        float contentX = x + PADDING;
        float contentW = width - PADDING * 2.0f;
        List<EpsilonAddon> addons = AddonHolder.INSTANCE.getAddons();
        EpsilonAddon selected = resolveSelectedAddon();
        if (addons.isEmpty()) {
            renderer.text().addText(EpsilonTranslations.Gui.ADDON_EMPTY.getTranslatedName(), contentX, currentY + 4.0f, 0.55f, MD3Theme.TEXT_MUTED);
            return;
        }

        for (EpsilonAddon addon : addons) {
            boolean active = selected != null && Objects.equals(addon.getAddonId(), selected.getAddonId());
            boolean hovered = isHovered(mouseX, mouseY, contentX, currentY, contentW, ADDON_ROW_HEIGHT);
            renderer.roundRect().addRoundRect(contentX, currentY, contentW, ADDON_ROW_HEIGHT, DropdownTheme.BUTTON_RADIUS,
                    active ? MD3Theme.PRIMARY_CONTAINER : (hovered ? MD3Theme.SURFACE_CONTAINER_HIGH : MD3Theme.SURFACE_CONTAINER_LOW));
            renderer.text().addText(trimToWidth(addon.getDisplayName(), 0.56f, contentW - 10.0f, renderer),
                    contentX + 6.0f, currentY + 5.0f, 0.56f, active ? MD3Theme.ON_PRIMARY_CONTAINER : MD3Theme.TEXT_PRIMARY);
            renderer.text().addText(trimToWidth(addon.getAddonId(), 0.44f, contentW - 10.0f, renderer),
                    contentX + 6.0f, currentY + 16.0f, 0.44f, active ? MD3Theme.withAlpha(MD3Theme.ON_PRIMARY_CONTAINER, 180) : MD3Theme.TEXT_MUTED);
            currentY += ADDON_ROW_HEIGHT + GAP;
        }

        if (selected == null) return;
        ensureWidgets(selected);
        renderer.roundRect().addRoundRect(contentX, currentY, contentW, INFO_HEIGHT, DropdownTheme.BUTTON_RADIUS, MD3Theme.SURFACE_CONTAINER_HIGH);
        renderer.text().addText(trimToWidth(selected.getDisplayName(), 0.58f, contentW - 10.0f, renderer),
                contentX + 6.0f, currentY + 5.0f, 0.58f, MD3Theme.TEXT_PRIMARY);
        String meta = EpsilonTranslations.Gui.ADDON_INFO_MODULES.getTranslatedName() + " " + selected.getRegisteredModules().size();
        if (!selected.getVersion().isBlank())
            meta += "  " + EpsilonTranslations.Gui.ADDON_INFO_VERSION.getTranslatedName() + " " + selected.getVersion();
        renderer.text().addText(trimToWidth(meta, 0.45f, contentW - 10.0f, renderer),
                contentX + 6.0f, currentY + 18.0f, 0.45f, MD3Theme.TEXT_MUTED);
        currentY += INFO_HEIGHT + GAP;

        if (widgets.isEmpty()) {
            renderer.text().addText(EpsilonTranslations.Gui.ADDON_NO_SETTINGS.getTranslatedName(), contentX, currentY + 4.0f, 0.55f, MD3Theme.TEXT_MUTED);
            return;
        }
        DropdownDrawContext.Stack stack = renderer.stack(new PanelLayout.Rect(contentX, currentY, contentW, computeWidgetsHeight()));
        for (SettingWidget<?> widget : widgets) {
            if (!widget.isVisible()) continue;
            widget.draw(renderer, mouseX, mouseY, stack.item(widget.getHeight(), DropdownTheme.SETTING_GAP));
        }
    }

    @Override
    protected boolean mouseClickedContent(double mouseX, double mouseY, int button) {
        if (button != 0 && button != 1 && button != 2) return false;
        float currentY = y + DropdownTheme.PANEL_HEADER_HEIGHT + PADDING - scroll;
        float contentX = x + PADDING;
        float contentW = width - PADDING * 2.0f;
        for (EpsilonAddon addon : AddonHolder.INSTANCE.getAddons()) {
            if (isHovered(mouseX, mouseY, contentX, currentY, contentW, ADDON_ROW_HEIGHT)) {
                selectedAddonId = addon.getAddonId();
                setScrollImmediate(Math.min(scroll, Math.max(0.0f, currentY - y)));
                ensureWidgets(addon);
                return true;
            }
            currentY += ADDON_ROW_HEIGHT + GAP;
        }
        currentY += INFO_HEIGHT + GAP;
        for (SettingWidget<?> widget : widgets) {
            if (!widget.isVisible()) continue;
            if (widget.mouseClicked(mouseX, mouseY, button)) {
                ConfigHolder.INSTANCE.saveNow();
                return true;
            }
            currentY += widget.getHeight() + DropdownTheme.SETTING_GAP;
        }
        return false;
    }

    @Override
    protected boolean mouseReleasedContent(double mouseX, double mouseY, int button) {
        for (SettingWidget<?> widget : widgets) {
            if (!widget.isVisible()) continue;
            if (widget.mouseReleased(mouseX, mouseY, button)) {
                ConfigHolder.INSTANCE.saveNow();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (SettingWidget<?> widget : widgets) {
            if (!widget.isVisible()) continue;
            if (widget.keyPressed(keyCode, scanCode, modifiers)) {
                ConfigHolder.INSTANCE.saveNow();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(String typedText) {
        for (SettingWidget<?> widget : widgets) {
            if (!widget.isVisible()) continue;
            if (widget.charTyped(typedText)) {
                ConfigHolder.INSTANCE.saveNow();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasActiveInput() {
        for (SettingWidget<?> widget : widgets) {
            if (widget instanceof KeybindWidget kw && kw.isListening()) return true;
            if (widget instanceof StringWidget sw && sw.isFocused()) return true;
            if (widget instanceof ColorWidget cw && cw.hasFocusedInput()) return true;
        }
        return false;
    }

    private EpsilonAddon resolveSelectedAddon() {
        List<EpsilonAddon> addons = AddonHolder.INSTANCE.getAddons();
        if (addons.isEmpty()) {
            selectedAddonId = "";
            lastAddon = null;
            widgets.clear();
            return null;
        }
        for (EpsilonAddon addon : addons) {
            if (Objects.equals(addon.getAddonId(), selectedAddonId)) {
                return addon;
            }
        }
        selectedAddonId = addons.getFirst().getAddonId();
        return addons.getFirst();
    }

    private void ensureWidgets(EpsilonAddon addon) {
        if (addon == lastAddon) return;
        widgets.clear();
        for (Setting<?> setting : addon.getSettings()) {
            SettingWidget<?> widget = SettingsContent.createWidget(setting);
            if (widget != null) widgets.add(widget);
        }
        lastAddon = addon;
    }

    private float computeWidgetsHeight() {
        float height = 0.0f;
        for (SettingWidget<?> widget : widgets) {
            if (widget.isVisible()) {
                height += widget.getHeight() + DropdownTheme.SETTING_GAP;
            }
        }
        return height;
    }

}
