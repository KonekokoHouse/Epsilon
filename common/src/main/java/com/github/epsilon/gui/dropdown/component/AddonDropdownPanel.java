package com.github.epsilon.gui.dropdown.component;

import com.github.epsilon.gui.addon.AddonPanelEntry;
import com.github.epsilon.gui.addon.AddonPanelEntryRegistry;
import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.epsilon.gui.dropdown.widget.ColorWidget;
import com.github.epsilon.gui.dropdown.widget.KeybindWidget;
import com.github.epsilon.gui.dropdown.widget.SettingWidget;
import com.github.epsilon.gui.dropdown.widget.StringWidget;
import com.github.slmpc.lumingraphics.ui.geometry.UiRect;
import com.github.slmpc.lumingraphics.ui.text.UiTextMetrics;
import com.github.slmpc.lumingraphics.ui.tree.UiTree;
import com.github.epsilon.gui.theme.MD3Theme;
import com.github.slmpc.lumingraphics.text.icon.IconChars;
import com.github.epsilon.settings.Setting;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class AddonDropdownPanel extends AbstractDropdownPanel {

    private static final float ADDON_ROW_HEIGHT = 28.0f;
    private static final float INFO_HEIGHT = 38.0f;
    private static final float LUA_INFO_HEIGHT = 60.0f;
    private static final float GAP = 4.0f;
    private static final float PADDING = 6.0f;

    private String selectedAddonId = "";
    private final List<SettingWidget<?>> widgets = new ArrayList<>();
    private AddonPanelEntry lastAddon;
    private int cachedWidgetsHeightFrameId = Integer.MIN_VALUE;
    private float cachedWidgetsHeight;

    public AddonDropdownPanel(int panelIndex) {
        super("addon", EpsilonTranslations.Gui.TAB_ADDON, "", panelIndex);
    }

    @Override
    protected float computeContentHeight() {
        AddonPanelEntry addon = resolveSelectedAddon();
        if (addon == null) {
            return PADDING * 2.0f + ADDON_ROW_HEIGHT;
        }
        ensureWidgets(addon);
        float height = PADDING + AddonPanelEntryRegistry.INSTANCE.entries().size() * (ADDON_ROW_HEIGHT + GAP)
                + getInfoHeight(addon) + GAP;
        if (widgets.isEmpty()) {
            height += ADDON_ROW_HEIGHT;
        } else {
            height += computeWidgetsHeight();
        }
        return height + PADDING;
    }

    @Override
    protected void drawPanelContent(UiTree.Scope scope, UiTextMetrics textMetrics, int mouseX, int mouseY, float visibleHeight) {
        float currentY = y + DropdownTheme.PANEL_HEADER_HEIGHT + PADDING - scroll;
        float contentX = x + PADDING;
        float contentW = width - PADDING * 2.0f;
        List<AddonPanelEntry> addons = AddonPanelEntryRegistry.INSTANCE.entries();
        AddonPanelEntry selected = resolveSelectedAddon();
        if (addons.isEmpty()) {
            scope.text(EpsilonTranslations.Gui.ADDON_EMPTY.getTranslatedName(), contentX, currentY + 4.0f, 0.55f, MD3Theme.TEXT_MUTED);
            return;
        }

        for (AddonPanelEntry addon : addons) {
            boolean active = selected != null && Objects.equals(addon.getAddonId(), selected.getAddonId());
            boolean hovered = isHovered(mouseX, mouseY, contentX, currentY, contentW, ADDON_ROW_HEIGHT);
            scope.roundRect(contentX, currentY, contentW, ADDON_ROW_HEIGHT, DropdownTheme.BUTTON_RADIUS,
                    active ? MD3Theme.PRIMARY_CONTAINER : (hovered ? MD3Theme.SURFACE_CONTAINER_HIGH : MD3Theme.SURFACE_CONTAINER_LOW));
            float textX = contentX + 6.0f;
            if (addon.isLua()) {
                scope.text(IconChars.CODE, textX, currentY + 7.0f, 0.62f,
                        active ? MD3Theme.ON_PRIMARY_CONTAINER : MD3Theme.TEXT_SECONDARY, "epsilon-icons");
                textX += 13.0f;
            }
            scope.text(trimToWidth(addon.getDisplayName(), 0.56f, contentW - (textX - contentX) - 4.0f, textMetrics),
                    textX, currentY + 5.0f, 0.56f, active ? MD3Theme.ON_PRIMARY_CONTAINER : MD3Theme.TEXT_PRIMARY);
            scope.text(trimToWidth(addon.getDisplayId(), 0.44f, contentW - (textX - contentX) - 4.0f, textMetrics),
                    textX, currentY + 16.0f, 0.44f, active ? MD3Theme.withAlpha(MD3Theme.ON_PRIMARY_CONTAINER, 180) : MD3Theme.TEXT_MUTED);
            currentY += ADDON_ROW_HEIGHT + GAP;
        }

        if (selected == null) return;
        ensureWidgets(selected);
        float infoY = currentY;
        float infoHeight = getInfoHeight(selected);
        scope.roundRect(contentX, infoY, contentW, infoHeight, DropdownTheme.BUTTON_RADIUS, MD3Theme.SURFACE_CONTAINER_HIGH);
        scope.text(trimToWidth(selected.getDisplayName(), 0.58f, contentW - 10.0f, textMetrics),
                contentX + 6.0f, infoY + 5.0f, 0.58f, MD3Theme.TEXT_PRIMARY);
        String meta = EpsilonTranslations.Gui.ADDON_INFO_MODULES.getTranslatedName() + " " + selected.getModuleCount();
        if (!selected.getVersion().isBlank())
            meta += "  " + EpsilonTranslations.Gui.ADDON_INFO_VERSION.getTranslatedName() + " " + selected.getVersion();
        scope.text(trimToWidth(meta, 0.45f, contentW - 10.0f, textMetrics),
                contentX + 6.0f, infoY + 18.0f, 0.45f, MD3Theme.TEXT_MUTED);
        drawLuaActions(scope, selected, contentX, infoY);
        currentY += infoHeight + GAP;

        if (widgets.isEmpty()) {
            scope.text(EpsilonTranslations.Gui.ADDON_NO_SETTINGS.getTranslatedName(), contentX, currentY + 4.0f, 0.55f, MD3Theme.TEXT_MUTED);
            return;
        }
        var stack = scope.stack(new UiRect(contentX, currentY, contentW, computeWidgetsHeight()));
        for (SettingWidget<?> widget : widgets) {
            if (!widget.isVisible()) continue;
            stack.item(widget.getHeight(), DropdownTheme.SETTING_GAP,
                    (bounds, itemScope) -> widget.drawInScope(itemScope, textMetrics, mouseX, mouseY, bounds));
        }
    }

    @Override
    protected boolean mouseClickedContent(double mouseX, double mouseY, int button) {
        if (button != 0 && button != 1 && button != 2) return false;
        float currentY = y + DropdownTheme.PANEL_HEADER_HEIGHT + PADDING - scroll;
        float contentX = x + PADDING;
        float contentW = width - PADDING * 2.0f;
        for (AddonPanelEntry addon : AddonPanelEntryRegistry.INSTANCE.entries()) {
            if (isHovered(mouseX, mouseY, contentX, currentY, contentW, ADDON_ROW_HEIGHT)) {
                selectedAddonId = addon.getAddonId();
                setScrollImmediate(Math.min(scroll, Math.max(0.0f, currentY - y)));
                ensureWidgets(addon);
                return true;
            }
            currentY += ADDON_ROW_HEIGHT + GAP;
        }
        AddonPanelEntry selected = resolveSelectedAddon();
        if (selected == null) return false;
        float infoY = currentY;
        if (button == 0 && selected.isLua()) {
            UiRect toggleBounds = getToggleBounds(selected, contentX, infoY);
            if (toggleBounds != null && toggleBounds.contains((float) mouseX, (float) mouseY) && selected.canToggle()) {
                selected.toggle();
                invalidateWidgets();
                return true;
            }
            UiRect reloadBounds = getReloadBounds(selected, contentX, infoY);
            if (reloadBounds.contains((float) mouseX, (float) mouseY) && selected.canReload()) {
                selected.reload();
                invalidateWidgets();
                return true;
            }
        }
        currentY += getInfoHeight(selected) + GAP;
        for (SettingWidget<?> widget : widgets) {
            if (!widget.isVisible()) continue;
            if (widget.mouseClicked(mouseX, mouseY, button)) {
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

    private AddonPanelEntry resolveSelectedAddon() {
        List<AddonPanelEntry> addons = AddonPanelEntryRegistry.INSTANCE.entries();
        if (addons.isEmpty()) {
            selectedAddonId = "";
            lastAddon = null;
            widgets.clear();
            return null;
        }
        for (AddonPanelEntry addon : addons) {
            if (Objects.equals(addon.getAddonId(), selectedAddonId)) {
                return addon;
            }
        }
        selectedAddonId = addons.getFirst().getAddonId();
        return addons.getFirst();
    }

    private void ensureWidgets(AddonPanelEntry addon) {
        if (addon.getAddonId().equals(lastAddon == null ? "" : lastAddon.getAddonId())
                && addon.getSettings().equals(lastAddon.getSettings())) return;
        widgets.clear();
        for (Setting<?> setting : addon.getSettings()) {
            SettingWidget<?> widget = SettingsContent.createWidget(setting);
            if (widget != null) widgets.add(widget);
        }
        lastAddon = addon;
        cachedWidgetsHeightFrameId = Integer.MIN_VALUE;
    }

    private void invalidateWidgets() {
        widgets.clear();
        lastAddon = null;
        cachedWidgetsHeightFrameId = Integer.MIN_VALUE;
    }

    private float getInfoHeight(AddonPanelEntry addon) {
        return addon != null && addon.isLua() ? LUA_INFO_HEIGHT : INFO_HEIGHT;
    }

    private void drawLuaActions(UiTree.Scope scope, AddonPanelEntry addon, float contentX, float infoY) {
        if (!addon.isLua()) return;

        UiRect toggleBounds = getToggleBounds(addon, contentX, infoY);
        if (toggleBounds != null) {
            scope.toggle(new UiRect(toggleBounds.x() + 3.0f, toggleBounds.y() + 4.0f,
                            MD3Theme.SWITCH_WIDTH, MD3Theme.SWITCH_HEIGHT),
                    addon.isEnabled() ? 1.0f : 0.0f, 0.0f);
        }

        UiRect reloadBounds = getReloadBounds(addon, contentX, infoY);
        scope.roundRect(reloadBounds.x(), reloadBounds.y(), reloadBounds.width(), reloadBounds.height(),
                DropdownTheme.BUTTON_RADIUS, MD3Theme.SURFACE_CONTAINER_HIGHEST);
        scope.text(IconChars.REFRESH, reloadBounds.x() + 4.0f, reloadBounds.y() + 4.0f, 0.68f,
                addon.canReload() ? MD3Theme.TEXT_PRIMARY : MD3Theme.TEXT_MUTED, "epsilon-icons");
    }

    private UiRect getToggleBounds(AddonPanelEntry addon, float contentX, float infoY) {
        if (addon.getKind() != AddonPanelEntry.Kind.LUA_SCRIPT) return null;
        return new UiRect(contentX + 4.0f, infoY + 34.0f, 32.0f, 22.0f);
    }

    private UiRect getReloadBounds(AddonPanelEntry addon, float contentX, float infoY) {
        float reloadX = addon.getKind() == AddonPanelEntry.Kind.LUA_SCRIPT ? contentX + 42.0f : contentX + 6.0f;
        return new UiRect(reloadX, infoY + 35.0f, 20.0f, 20.0f);
    }

    private float computeWidgetsHeight() {
        int frameId = getRenderFrameId();
        if (cachedWidgetsHeightFrameId == frameId) {
            return cachedWidgetsHeight;
        }

        float height = 0.0f;
        for (SettingWidget<?> widget : widgets) {
            if (widget.isVisible()) {
                height += widget.getHeight() + DropdownTheme.SETTING_GAP;
            }
        }
        cachedWidgetsHeightFrameId = frameId;
        cachedWidgetsHeight = height;
        return cachedWidgetsHeight;
    }

}
