package com.github.epsilon.gui.panel.view.settings;

import com.github.epsilon.gui.addon.AddonPanelEntry;
import com.github.epsilon.gui.addon.AddonPanelEntryRegistry;
import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.slmpc.lumingraphics.ui.text.UiTextMetrics;
import com.github.slmpc.lumingraphics.ui.geometry.UiRect;
import com.github.slmpc.lumingraphics.ui.tree.UiTree;
import com.github.slmpc.lumingraphics.ui.render.UiContentBuffer;
import com.github.slmpc.lumingraphics.ui.render.UiRenderBatch;
import com.github.slmpc.lumingraphics.ui.state.UiInvalidationState;
import com.github.epsilon.gui.panel.PanelState;
import com.github.epsilon.gui.panel.adapter.SettingListController;
import com.github.epsilon.gui.panel.component.setting.KeybindSettingRow;
import com.github.epsilon.gui.panel.popup.PanelPopupHost;
import com.github.epsilon.gui.panel.utils.ScrollBarDragState;
import com.github.epsilon.gui.panel.utils.ScrollBarUtils;
import com.github.epsilon.gui.theme.EpsilonUiTheme;
import com.github.epsilon.gui.theme.MD3Theme;
import com.github.slmpc.lumingraphics.text.icon.IconChars;
import com.github.epsilon.holders.TranslateHolder;
import com.github.epsilon.settings.Setting;
import com.github.epsilon.settings.SettingLayoutPlanner;
import com.github.epsilon.settings.impl.KeybindSetting;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

import java.awt.*;
import java.util.*;
import java.util.List;

public class AddonClientSettingTab implements ClientSettingTabView {
    private static final float LIST_GAP = 10.0f;
    private static final float LIST_ROW_HEIGHT = 34.0f;
    private static final float DETAIL_GAP = 8.0f;
    private static final float DETAIL_INFO_MIN_HEIGHT = 54.0f;
    private static final float DETAIL_INFO_MAX_HEIGHT = 120.0f;
    private static final float DETAIL_SETTINGS_MIN_HEIGHT = 96.0f;

    private final PanelState state;
    private final UiTextMetrics textRenderer;
    private final SettingListController settingListController;
    private final UiInvalidationState contentState = new UiInvalidationState();
    private final Map<String, Animation> rowHoverAnimations = new HashMap<>();
    private final Map<String, Animation> rowSelectionAnimations = new HashMap<>();
    private final Map<Setting<?>, Animation> settingHoverAnimations = new HashMap<>();
    private final ScrollBarDragState listScrollBarDrag = new ScrollBarDragState();
    private final ScrollBarDragState detailScrollBarDrag = new ScrollBarDragState();
    private final List<AddonRowEntry> rowEntries = new ArrayList<>();
    private final List<ActionEntry> actionEntries = new ArrayList<>();

    private UiRect bounds;
    private float lastListScroll = Float.NaN;
    private float lastDetailScroll = Float.NaN;
    private String lastSelectedAddonId = "";
    private List<String> lastAddonKeys = List.of();
    private List<String> lastVisibleSettings = List.of();
    private String lastListeningKey = "";
    private long lastContentSignature = Long.MIN_VALUE;
    private float listScrollVelocity = 0;
    private float detailScrollVelocity = 0;

    public AddonClientSettingTab(PanelState state, UiTextMetrics textRenderer, PanelPopupHost popupHost) {
        this.state = state;
        this.textRenderer = textRenderer;
        this.settingListController = new SettingListController(popupHost);
    }

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, UiRenderBatch renderBatch, UiRect bounds, int mouseX, int mouseY, float partialTick) {
        UiContentBuffer listBuffer = new UiContentBuffer(renderBatch);
        UiContentBuffer detailBuffer = new UiContentBuffer(renderBatch);
        this.bounds = bounds;

        if (Math.abs(listScrollVelocity) > 0.01f) {
            state.scrollAddonList(listScrollVelocity * partialTick);
            listScrollVelocity *= 0.86f;
            if (Math.abs(listScrollVelocity) < 0.3f) {
                listScrollVelocity = 0;
            }
            markDirty();
        }
        if (Math.abs(detailScrollVelocity) > 0.01f) {
            state.scrollAddonDetail(detailScrollVelocity * partialTick);
            detailScrollVelocity *= 0.86f;
            if (Math.abs(detailScrollVelocity) < 0.3f) {
                detailScrollVelocity = 0;
            }
            markDirty();
        }

        List<AddonPanelEntry> addons = AddonPanelEntryRegistry.INSTANCE.entries();
        AddonPanelEntry selectedAddon = resolveSelectedAddon(addons);
        List<Setting<?>> selectedSettings = selectedAddon == null
                ? List.of()
                : selectedAddon.getSettings().stream().filter(Setting::isAvailable).toList();

        UiRect listPanelBounds = getListPanelBounds(bounds);
        UiRect listViewport = getListViewport(listPanelBounds);
        UiRect detailPanelBounds = getDetailPanelBounds(bounds, listPanelBounds);
        UiRect infoBounds = getDetailInfoBounds(detailPanelBounds, selectedAddon);
        UiRect settingsViewport = getDetailSettingsViewport(detailPanelBounds, selectedAddon);

        float listContentHeight = addons.size() * (LIST_ROW_HEIGHT + MD3Theme.ROW_GAP);
        state.setMaxAddonListScroll(listContentHeight - listViewport.height());
        float maxListScroll = Math.max(0.0f, listContentHeight - listViewport.height());
        boolean listHasScrollBar = maxListScroll > 0.0f;
        float listRowWidth = listHasScrollBar ? listViewport.width() - ScrollBarUtils.TOTAL_WIDTH : listViewport.width();

        String settingOwnerKey = selectedAddon == null ? "addon-settings:none" : "addon-settings:" + selectedAddon.getAddonId();
        float settingsContentHeight = settingListController.getContentHeight(settingOwnerKey, selectedSettings);
        state.setMaxAddonDetailScroll(settingsContentHeight - settingsViewport.height());
        float maxDetailScroll = Math.max(0.0f, settingsContentHeight - settingsViewport.height());
        boolean detailHasScrollBar = maxDetailScroll > 0.0f;
        float settingsRowWidth = detailHasScrollBar ? settingsViewport.width() - ScrollBarUtils.TOTAL_WIDTH : settingsViewport.width();

        long contentSignature = buildContentSignature(addons, selectedAddon, selectedSettings, settingOwnerKey);
        boolean popupConsumesHover = settingListController.isPopupHovered(mouseX, mouseY);
        int effectiveMouseX = popupConsumesHover ? Integer.MIN_VALUE : mouseX;
        int effectiveMouseY = popupConsumesHover ? Integer.MIN_VALUE : mouseY;
        boolean rebuildContent = true;

        if (rebuildContent) {
            contentState.beginRebuild();
            settingListController.prepareLayout(settingOwnerKey, selectedSettings);
            rowEntries.clear();
            actionEntries.clear();
            List<String> addonIds = addons.stream().map(AddonPanelEntry::getAddonId).toList();
            rowHoverAnimations.keySet().removeIf(id -> !addonIds.contains(id));
            rowSelectionAnimations.keySet().removeIf(id -> !addonIds.contains(id));
        }

        UiTree tree = UiTree.build(scope -> {
            buildAddonShell(scope, listPanelBounds, detailPanelBounds);

            if (addons.isEmpty()) {
                float hintScale = 0.60f;
                String hint = EpsilonTranslations.Gui.ADDON_EMPTY.getTranslatedName();
                float hintWidth = textRenderer.textWidth(hint, hintScale, null);
                float hintX = bounds.x() + (bounds.width() - hintWidth) / 2.0f;
                float hintY = bounds.y() + bounds.height() / 2.0f - textRenderer.textHeight(hintScale, null) / 2.0f;
                scope.text(hint, hintX, hintY, hintScale, MD3Theme.TEXT_MUTED);
                return;
            }

            scope.viewport(listBuffer, listViewport, state.getAddonListScroll(), maxListScroll, listContentHeight, mouseX, mouseY, content -> {
                if (!rebuildContent) {
                    return;
                }
                float rowY = listViewport.y() - state.getAddonListScroll();
                for (AddonPanelEntry addon : addons) {
                    UiRect rowBounds = new UiRect(listViewport.x(), rowY, listRowWidth, LIST_ROW_HEIGHT);
                    rowEntries.add(new AddonRowEntry(addon.getAddonId(), rowBounds));

                    Animation hoverAnimation = rowHoverAnimations.computeIfAbsent(addon.getAddonId(), ignored -> createAnimation());
                    Animation selectionAnimation = rowSelectionAnimations.computeIfAbsent(addon.getAddonId(), ignored -> createAnimation());
                    hoverAnimation.run(rowBounds.contains(mouseX, mouseY) ? 1.0f : 0.0f);
                    selectionAnimation.run(selectedAddon != null && Objects.equals(selectedAddon.getAddonId(), addon.getAddonId()) ? 1.0f : 0.0f);
                    contentState.noteAnimation(!hoverAnimation.isFinished() || !selectionAnimation.isFinished());

                    content.pushAbsolute(rowBounds, rowScope ->
                            buildAddonListRow(rowScope, addon, rowBounds, hoverAnimation.getValue(), selectionAnimation.getValue()));
                    rowY += LIST_ROW_HEIGHT + MD3Theme.ROW_GAP;
                }
            });

            if (selectedAddon != null) {
                buildAddonInfo(scope, selectedAddon, infoBounds);
                if (selectedSettings.isEmpty()) {
                    float hintScale = 0.58f;
                    String hint = EpsilonTranslations.Gui.ADDON_NO_SETTINGS.getTranslatedName();
                    scope.text(hint, settingsViewport.x() + 2.0f, settingsViewport.y() + 2.0f, hintScale, MD3Theme.TEXT_MUTED);
                } else {
                    scope.viewport(detailBuffer, settingsViewport, state.getAddonDetailScroll(), maxDetailScroll, settingsContentHeight, effectiveMouseX, effectiveMouseY, content -> {
                        if (!rebuildContent) {
                            return;
                        }
                        settingListController.layoutRows(settingOwnerKey, selectedSettings, settingsViewport, state.getAddonDetailScroll(), settingsRowWidth,
                                content, textRenderer, effectiveMouseX, effectiveMouseY, (setting, row, rowBounds) -> {
                                    if (row instanceof KeybindSettingRow keybindRow) {
                                        keybindRow.setListening(state.getListeningKeybindSetting() == keybindRow.getSetting());
                                    }
                                    Animation hoverAnimation = settingHoverAnimations.computeIfAbsent(setting, ignored -> {
                                        Animation animation = createAnimation();
                                        animation.setStartValue(0.0f);
                                        return animation;
                                    });
                                    hoverAnimation.run(rowBounds.contains(effectiveMouseX, effectiveMouseY) ? 1.0f : 0.0f);
                                    content.pushAbsolute(rowBounds, rowScope ->
                                            row.buildUi(rowScope, guiGraphics, textRenderer, rowBounds,
                                                    hoverAnimation.getValue(), effectiveMouseX, effectiveMouseY, partialTick));
                                    contentState.noteAnimation(!hoverAnimation.isFinished() || row.hasActiveAnimation());
                                });
                        contentState.noteAnimation(settingListController.hasActiveAnimations());
                    });
                }
            }
        });
        renderBatch.render(tree);

        if (rebuildContent) {
            rememberSnapshot(bounds, mouseX, mouseY, addons, selectedAddon, selectedSettings, guiGraphics.guiHeight(), contentSignature);
        }
    }

    @Override
    public void markDirty() {
        contentState.markDirty();
    }

    @Override
    public boolean hasActiveAnimations() {
        return contentState.hasActiveAnimations()
                || rowHoverAnimations.values().stream().anyMatch(animation -> !animation.isFinished())
                || rowSelectionAnimations.values().stream().anyMatch(animation -> !animation.isFinished())
                || settingHoverAnimations.values().stream().anyMatch(animation -> !animation.isFinished())
                || settingListController.hasActiveAnimations();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        if (bounds == null || event.button() != 0) {
            return false;
        }

        listScrollVelocity = 0;
        detailScrollVelocity = 0;

        if (state.getListeningKeybindSetting() != null) {
            state.setListeningKeybindSetting(null);
            markDirty();
        }

        UiRect listViewport = getListViewport(getListPanelBounds(bounds));
        UiRect settingsViewport = getDetailSettingsViewport(getDetailPanelBounds(bounds, getListPanelBounds(bounds)), resolveSelectedAddon(AddonPanelEntryRegistry.INSTANCE.entries()));

        if (listScrollBarDrag.mouseClicked(event.x(), event.y(), listViewport, state.getAddonListScroll(), state.getMaxAddonListScroll())) {
            float newScroll = listScrollBarDrag.mouseDragged(event.y(), listViewport, state.getMaxAddonListScroll());
            if (newScroll >= 0.0f) {
                state.setAddonListScroll(newScroll);
            }
            markDirty();
            return true;
        }

        if (detailScrollBarDrag.mouseClicked(event.x(), event.y(), settingsViewport, state.getAddonDetailScroll(), state.getMaxAddonDetailScroll())) {
            float newScroll = detailScrollBarDrag.mouseDragged(event.y(), settingsViewport, state.getMaxAddonDetailScroll());
            if (newScroll >= 0.0f) {
                state.setAddonDetailScroll(newScroll);
            }
            markDirty();
            return true;
        }

        for (AddonRowEntry entry : rowEntries) {
            if (entry.bounds().contains(event.x(), event.y())) {
                if (!Objects.equals(state.getSelectedAddonId(), entry.addonId())) {
                    state.setSelectedAddonId(entry.addonId());
                    state.setAddonDetailScroll(0.0f);
                    settingListController.clearFocus();
                }
                markDirty();
                return true;
            }
        }

        for (ActionEntry entry : actionEntries) {
            if (!entry.bounds().contains(event.x(), event.y()) || !entry.enabled()) continue;
            if (entry.type() == ActionType.TOGGLE) entry.addon().toggle();
            else entry.addon().reload();
            markDirty();
            return true;
        }

        if (settingListController.mouseClicked(event, isDoubleClick, settingsViewport, (row, rowBounds, clickEvent, doubleClick) -> {
            if (row instanceof KeybindSettingRow keybindRow && row.mouseClicked(rowBounds, clickEvent, doubleClick)) {
                state.setListeningKeybindSetting(keybindRow.getSetting());
                return true;
            }
            return false;
        })) {
            markDirty();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        boolean consumed = false;
        consumed |= listScrollBarDrag.mouseReleased();
        consumed |= detailScrollBarDrag.mouseReleased();
        consumed |= settingListController.mouseReleased(event);
        if (consumed) {
            markDirty();
        }
        return consumed;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double mouseX, double mouseY) {
        if (listScrollBarDrag.isDragging()) {
            UiRect listViewport = getListViewport(getListPanelBounds(bounds));
            float newScroll = listScrollBarDrag.mouseDragged(event.y(), listViewport, state.getMaxAddonListScroll());
            if (newScroll >= 0.0f) {
                state.setAddonListScroll(newScroll);
            }
            markDirty();
            return true;
        }
        if (detailScrollBarDrag.isDragging()) {
            UiRect settingsViewport = getDetailSettingsViewport(getDetailPanelBounds(bounds, getListPanelBounds(bounds)), resolveSelectedAddon(AddonPanelEntryRegistry.INSTANCE.entries()));
            float newScroll = detailScrollBarDrag.mouseDragged(event.y(), settingsViewport, state.getMaxAddonDetailScroll());
            if (newScroll >= 0.0f) {
                state.setAddonDetailScroll(newScroll);
            }
            markDirty();
            return true;
        }
        if (settingListController.mouseDragged(event, mouseX, mouseY)) {
            markDirty();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (bounds == null) {
            return false;
        }
        UiRect listViewport = getListViewport(getListPanelBounds(bounds));
        if (listViewport.contains(mouseX, mouseY)) {
            listScrollVelocity -= (float) scrollY * 24.0f;
            markDirty();
            return true;
        }
        UiRect settingsViewport = getDetailSettingsViewport(getDetailPanelBounds(bounds, getListPanelBounds(bounds)), resolveSelectedAddon(AddonPanelEntryRegistry.INSTANCE.entries()));
        if (settingsViewport.contains(mouseX, mouseY)) {
            detailScrollVelocity -= (float) scrollY * 24.0f;
            markDirty();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        KeybindSetting listening = state.getListeningKeybindSetting();
        if (listening != null) {
            if (event.key() == 256) {
                state.setListeningKeybindSetting(null);
                markDirty();
                return true;
            }
            if (event.key() == 259 || event.key() == 261) {
                listening.setValue(-1);
                state.setListeningKeybindSetting(null);
                markDirty();
                return true;
            }
            listening.setValue(event.key());
            state.setListeningKeybindSetting(null);
            markDirty();
            return true;
        }
        if (settingListController.keyPressed(event)) {
            markDirty();
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (settingListController.charTyped(event)) {
            markDirty();
            return true;
        }
        return false;
    }

    @Override
    public boolean consumesHover(int mouseX, int mouseY) {
        return settingListController.isPopupHovered(mouseX, mouseY);
    }

    @Override
    public void onActivated() {
        resolveSelectedAddon(AddonPanelEntryRegistry.INSTANCE.entries());
        markDirty();
    }

    @Override
    public void onDeactivated() {
        listScrollBarDrag.reset();
        detailScrollBarDrag.reset();
        listScrollVelocity = 0;
        detailScrollVelocity = 0;
        settingListController.clearFocus();
        if (state.getListeningKeybindSetting() != null) {
            state.setListeningKeybindSetting(null);
        }
        markDirty();
    }

    private AddonPanelEntry resolveSelectedAddon(List<AddonPanelEntry> addons) {
        if (addons.isEmpty()) {
            if (!state.getSelectedAddonId().isEmpty()) {
                state.setSelectedAddonId("");
            }
            return null;
        }

        for (AddonPanelEntry addon : addons) {
            if (Objects.equals(addon.getAddonId(), state.getSelectedAddonId())) {
                return addon;
            }
        }

        AddonPanelEntry fallback = addons.getFirst();
        state.setSelectedAddonId(fallback.getAddonId());
        return fallback;
    }

    private void buildAddonShell(UiTree.Scope scope, UiRect listPanelBounds, UiRect detailPanelBounds) {
        scope.pushAbsolute(listPanelBounds, listPanel ->
                listPanel.roundRect(0.0f, 0.0f, listPanelBounds.width(), listPanelBounds.height(), MD3Theme.CARD_RADIUS, MD3Theme.SURFACE_CONTAINER));
        scope.pushAbsolute(detailPanelBounds, detailPanel ->
                detailPanel.roundRect(0.0f, 0.0f, detailPanelBounds.width(), detailPanelBounds.height(), MD3Theme.CARD_RADIUS, MD3Theme.SURFACE_CONTAINER));
    }

    private void buildAddonListRow(UiTree.Scope scope, AddonPanelEntry addon, UiRect rowBounds, float hoverProgress, float selectedProgress) {
        Color baseColor = MD3Theme.rowSurface(hoverProgress);
        Color rowColor = selectedProgress > 0.01f
                ? MD3Theme.lerp(baseColor, MD3Theme.PRIMARY_CONTAINER, selectedProgress * 0.45f)
                : baseColor;
        scope.roundRect(0.0f, 0.0f, rowBounds.width(), rowBounds.height(), MD3Theme.CARD_RADIUS, rowColor);

        float titleScale = 0.64f;
        float subScale = 0.50f;
        float textX = MD3Theme.ROW_CONTENT_INSET;
        if (addon.isLua()) {
            scope.text(IconChars.CODE, textX, 8.0f, 0.70f,
                    selectedProgress > 0.2f ? MD3Theme.ON_PRIMARY_CONTAINER : MD3Theme.TEXT_SECONDARY, "epsilon-icons");
            textX += 15.0f;
        }
        float titleY = 7.0f;
        scope.text(trimToWidth(addon.getDisplayName(), titleScale, rowBounds.width() - 14.0f), textX, titleY, titleScale, selectedProgress > 0.2f ? MD3Theme.ON_PRIMARY_CONTAINER : MD3Theme.TEXT_PRIMARY);
        scope.text(trimToWidth(addon.getDisplayId(), subScale, rowBounds.width() - 14.0f), textX, titleY + 12.0f, subScale, selectedProgress > 0.2f ? MD3Theme.withAlpha(MD3Theme.ON_PRIMARY_CONTAINER, 180) : MD3Theme.TEXT_MUTED);
    }

    private void buildAddonInfo(UiTree.Scope scope, AddonPanelEntry addon, UiRect infoBounds) {
        scope.pushAbsolute(infoBounds, info -> buildAddonInfoContent(info, addon, infoBounds));
    }

    private void buildAddonInfoContent(UiTree.Scope scope, AddonPanelEntry addon, UiRect infoBounds) {
        scope.roundRect(0.0f, 0.0f, infoBounds.width(), infoBounds.height(), MD3Theme.CARD_RADIUS, MD3Theme.SURFACE_CONTAINER_HIGH);

        float titleScale = 0.72f;
        float labelScale = 0.52f;
        float descScale = 0.56f;
        float titleHeight = textRenderer.textHeight(titleScale, null);
        float labelHeight = textRenderer.textHeight(labelScale, null);
        float textX = MD3Theme.ROW_CONTENT_INSET;
        float titleY = 8.0f;
        scope.text(trimToWidth(addon.getDisplayName(), titleScale, infoBounds.width() - 96.0f), textX, titleY, titleScale, MD3Theme.TEXT_PRIMARY);

        String version = addon.getVersion().isBlank() ? "-" : addon.getVersion();
        String metaLine = EpsilonTranslations.Gui.ADDON_INFO_ID.getTranslatedName() + ": " + addon.getDisplayId()
                + "  •  " + EpsilonTranslations.Gui.ADDON_INFO_VERSION.getTranslatedName() + ": " + version;
        float metaY = titleY + titleHeight + 3.0f;
        scope.text(trimToWidth(metaLine, labelScale, infoBounds.width() - 18.0f), textX, metaY, labelScale, MD3Theme.TEXT_SECONDARY);

        String authors = addon.getAuthors().isEmpty() ? "-" : String.join(", ", addon.getAuthors());
        float authorsY = metaY + labelHeight + 3.0f;
        scope.text(trimToWidth(EpsilonTranslations.Gui.ADDON_INFO_AUTHORS.getTranslatedName() + ": " + authors, labelScale, infoBounds.width() - 18.0f),
                textX, authorsY, labelScale, MD3Theme.TEXT_MUTED);

        if (!addon.getDescription().isBlank()) {
            float detailY = authorsY + labelHeight + 5.0f;
            scope.text(trimToWidth(addon.getDescription(), descScale, infoBounds.width() - 18.0f), textX, detailY, descScale, MD3Theme.TEXT_PRIMARY);
        }

        String chipText = addon.getModuleCount() + " " + EpsilonTranslations.Gui.ADDON_INFO_MODULES.getTranslatedName();
        float chipScale = 0.48f;
        float chipWidth = textRenderer.textWidth(chipText, chipScale, null) + 10.0f;
        float chipHeight = 14.0f;
        float chipX = infoBounds.width() - MD3Theme.ROW_TRAILING_INSET - chipWidth;
        float chipY = 8.0f;
        scope.roundRect(chipX, chipY, chipWidth, chipHeight, chipHeight / 2.0f, MD3Theme.PRIMARY_CONTAINER);
        scope.text(chipText,
                chipX + (chipWidth - textRenderer.textWidth(chipText, chipScale, null)) / 2.0f,
                chipY + (chipHeight - textRenderer.textHeight(chipScale, null)) / 2.0f,
                chipScale,
                MD3Theme.ON_PRIMARY_CONTAINER);

        if (addon.isLua()) {
            float actionY = infoBounds.height() - 25.0f;
            if (!addon.getError().isBlank()) {
                scope.text(trimToWidth(addon.getError(), 0.46f, infoBounds.width() - 18.0f),
                        MD3Theme.ROW_CONTENT_INSET, actionY - 13.0f, 0.46f, MD3Theme.ERROR);
            }
            if (addon.getKind() == AddonPanelEntry.Kind.LUA_SCRIPT) {
                UiRect toggleHitBounds = new UiRect(infoBounds.x() + textX, infoBounds.y() + actionY, 32.0f, 20.0f);
                actionEntries.add(new ActionEntry(addon, ActionType.TOGGLE, toggleHitBounds, addon.canToggle()));
                scope.toggle(new UiRect(textX + 3.0f, actionY + 2.0f, MD3Theme.SWITCH_WIDTH, MD3Theme.SWITCH_HEIGHT),
                        addon.isEnabled() ? 1.0f : 0.0f, 0.0f);
                textX += 37.0f;
            }
            if (addon.getKind() == AddonPanelEntry.Kind.LUA_SCRIPT || addon.getKind() == AddonPanelEntry.Kind.LUA_ERROR) {
                UiRect reloadBounds = new UiRect(infoBounds.x() + textX, infoBounds.y() + actionY, 20.0f, 20.0f);
                actionEntries.add(new ActionEntry(addon, ActionType.RELOAD, reloadBounds, addon.canReload()));
                scope.roundRect(textX, actionY, 20.0f, 20.0f, 5.0f, MD3Theme.SURFACE_CONTAINER_HIGHEST);
                scope.text(IconChars.REFRESH, textX + 4.0f, actionY + 4.0f, 0.68f,
                        addon.canReload() ? MD3Theme.TEXT_PRIMARY : MD3Theme.TEXT_MUTED, "epsilon-icons");
            }

            String type = EpsilonTranslations.Gui.ADDON_LUA_SCRIPT.getTranslatedName();
            float typeScale = 0.46f;
            float typeWidth = textRenderer.textWidth(type, typeScale, null) + 22.0f;
            float typeX = infoBounds.width() - MD3Theme.ROW_TRAILING_INSET - typeWidth;
            scope.roundRect(typeX, actionY, typeWidth, 20.0f, 5.0f, MD3Theme.SECONDARY_CONTAINER);
            scope.text(IconChars.CODE, typeX + 5.0f, actionY + 5.0f, 0.58f,
                    MD3Theme.ON_SECONDARY_CONTAINER, "epsilon-icons");
            scope.text(type, typeX + 17.0f, actionY + 5.0f, typeScale, MD3Theme.ON_SECONDARY_CONTAINER);
        }
    }

    private UiRect getListPanelBounds(UiRect bounds) {
        float width = Math.clamp(bounds.width() * 0.32f, 126.0f, 156.0f);
        return new UiRect(bounds.x(), bounds.y(), width, bounds.height());
    }

    private UiRect getListViewport(UiRect listPanelBounds) {
        return new UiRect(
                listPanelBounds.x() + 4.0f,
                listPanelBounds.y() + 4.0f,
                listPanelBounds.width() - 8.0f,
                listPanelBounds.height() - 8.0f
        );
    }

    private UiRect getDetailPanelBounds(UiRect bounds, UiRect listPanelBounds) {
        float x = listPanelBounds.right() + LIST_GAP;
        return new UiRect(x, bounds.y(), bounds.right() - x, bounds.height());
    }

    private UiRect getDetailInfoBounds(UiRect detailPanelBounds, AddonPanelEntry addon) {
        return new UiRect(
                detailPanelBounds.x() + 4.0f,
                detailPanelBounds.y() + 4.0f,
                detailPanelBounds.width() - 8.0f,
                getDetailInfoHeight(detailPanelBounds, addon)
        );
    }

    private UiRect getDetailSettingsViewport(UiRect detailPanelBounds, AddonPanelEntry addon) {
        UiRect infoBounds = getDetailInfoBounds(detailPanelBounds, addon);
        float y = infoBounds.bottom() + DETAIL_GAP;
        return new UiRect(
                detailPanelBounds.x() + 4.0f,
                y,
                detailPanelBounds.width() - 8.0f,
                Math.max(0.0f, detailPanelBounds.bottom() - y - 4.0f)
        );
    }

    private float getDetailInfoHeight(UiRect detailPanelBounds, AddonPanelEntry addon) {
        float titleHeight = textRenderer.textHeight(0.72f, null);
        float labelHeight = textRenderer.textHeight(0.52f, null);
        float descHeight = textRenderer.textHeight(0.56f, null);

        float naturalHeight = 8.0f + titleHeight + 3.0f + labelHeight + 3.0f + labelHeight + 8.0f;
        if (addon != null && !addon.getDescription().isBlank()) {
            naturalHeight += 5.0f + descHeight;
        }
        if (addon != null && addon.isLua()) naturalHeight += 25.0f;
        if (addon != null && !addon.getError().isBlank()) naturalHeight += 14.0f;

        float availableForInfo = detailPanelBounds.height() - DETAIL_GAP - DETAIL_SETTINGS_MIN_HEIGHT - 8.0f;
        float maxHeight = Math.max(DETAIL_INFO_MIN_HEIGHT, Math.clamp(availableForInfo, DETAIL_INFO_MIN_HEIGHT, DETAIL_INFO_MAX_HEIGHT));
        return Math.clamp(naturalHeight, DETAIL_INFO_MIN_HEIGHT, maxHeight);
    }

    private boolean shouldRebuild(UiRect bounds, int mouseX, int mouseY, List<AddonPanelEntry> addons, AddonPanelEntry selectedAddon, List<Setting<?>> selectedSettings, int guiHeight, long contentSignature) {
        if (contentState.needsRebuild(bounds, mouseX, mouseY, guiHeight, contentSignature)) {
            return true;
        }
        if (Float.compare(lastListScroll, state.getAddonListScroll()) != 0) {
            return true;
        }
        if (Float.compare(lastDetailScroll, state.getAddonDetailScroll()) != 0) {
            return true;
        }
        if (!Objects.equals(lastSelectedAddonId, selectedAddon == null ? "" : selectedAddon.getAddonId())) {
            return true;
        }
        String listeningKey = state.getListeningKeybindSetting() == null ? "" : state.getListeningKeybindSetting().getName();
        if (!Objects.equals(lastListeningKey, listeningKey)) {
            return true;
        }
        List<String> addonKeys = addons.stream().map(AddonPanelEntry::getAddonId).toList();
        if (!Objects.equals(lastAddonKeys, addonKeys)) {
            return true;
        }
        List<String> visibleSettings = selectedSettings.stream().map(Setting::getName).toList();
        if (!Objects.equals(lastVisibleSettings, visibleSettings)) {
            return true;
        }
        return lastContentSignature != contentSignature;
    }

    private void rememberSnapshot(UiRect bounds, int mouseX, int mouseY, List<AddonPanelEntry> addons, AddonPanelEntry selectedAddon, List<Setting<?>> selectedSettings, int guiHeight, long contentSignature) {
        contentState.rememberSnapshot(bounds, mouseX, mouseY, guiHeight, contentSignature);
        lastListScroll = state.getAddonListScroll();
        lastDetailScroll = state.getAddonDetailScroll();
        lastSelectedAddonId = selectedAddon == null ? "" : selectedAddon.getAddonId();
        lastListeningKey = state.getListeningKeybindSetting() == null ? "" : state.getListeningKeybindSetting().getName();
        lastAddonKeys = addons.stream().map(AddonPanelEntry::getAddonId).toList();
        lastVisibleSettings = selectedSettings.stream().map(Setting::getName).toList();
        lastContentSignature = contentSignature;
    }

    private long buildContentSignature(List<AddonPanelEntry> addons, AddonPanelEntry selectedAddon, List<Setting<?>> selectedSettings, String settingOwnerKey) {
        long signature = 17L;
        signature = signature * 31L + TranslateHolder.INSTANCE.getRevision();
        signature = signature * 31L + Float.floatToIntBits(state.getAddonListScroll());
        signature = signature * 31L + Float.floatToIntBits(state.getAddonDetailScroll());
        signature = signature * 31L + state.getSelectedAddonId().hashCode();
        signature = signature * 31L + (state.getListeningKeybindSetting() == null ? 0 : state.getListeningKeybindSetting().getName().hashCode());
        for (AddonPanelEntry addon : addons) {
            signature = signature * 31L + addon.getAddonId().hashCode();
            signature = signature * 31L + addon.getDisplayName().hashCode();
            signature = signature * 31L + addon.getDescription().hashCode();
            signature = signature * 31L + addon.getVersion().hashCode();
            signature = signature * 31L + addon.getModuleCount();
            signature = signature * 31L + (addon.isEnabled() ? 1 : 0);
            signature = signature * 31L + addon.getError().hashCode();
            for (String author : addon.getAuthors()) {
                signature = signature * 31L + author.hashCode();
            }
        }
        if (selectedAddon != null) {
            signature = signature * 31L + selectedAddon.getAddonId().hashCode();
        }
        for (Setting<?> setting : selectedSettings) {
            signature = signature * 31L + setting.getName().hashCode();
            signature = signature * 31L + (setting.isAvailable() ? 1 : 0);
        }
        signature = signature * 31L + SettingLayoutPlanner.signature(settingOwnerKey, selectedSettings);
        return signature;
    }

    private String trimToWidth(String value, float scale, float width) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (textRenderer.textWidth(value, scale, null) <= width) {
            return value;
        }
        String ellipsis = "...";
        float ellipsisWidth = textRenderer.textWidth(ellipsis, scale, null);
        if (ellipsisWidth >= width) {
            return ellipsis;
        }
        for (int length = value.length() - 1; length >= 0; length--) {
            String candidate = value.substring(0, length) + ellipsis;
            if (textRenderer.textWidth(candidate, scale, null) <= width) {
                return candidate;
            }
        }
        return ellipsis;
    }

    private Animation createAnimation() {
        Animation animation = new Animation(Easing.EASE_OUT_CUBIC, 120L);
        animation.setStartValue(0.0f);
        return animation;
    }

    @Override
    public void close() {
        settingListController.close();
        markDirty();
    }

    private record AddonRowEntry(String addonId, UiRect bounds) {
    }

    private enum ActionType { TOGGLE, RELOAD }

    private record ActionEntry(AddonPanelEntry addon, ActionType type, UiRect bounds, boolean enabled) {
    }

}
