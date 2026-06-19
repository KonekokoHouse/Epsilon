package com.github.epsilon.gui.panel.adapter;

import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.gui.dsl.PanelUiTree;
import com.github.epsilon.gui.panel.MD3Theme;
import com.github.epsilon.gui.panel.PanelLayout;
import com.github.epsilon.gui.panel.component.SettingRow;
import com.github.epsilon.gui.panel.component.setting.*;
import com.github.epsilon.gui.panel.popup.BlockListSelectPopup;
import com.github.epsilon.gui.panel.popup.ColorPickerPopup;
import com.github.epsilon.gui.panel.popup.EnumSelectPopup;
import com.github.epsilon.gui.panel.popup.PanelPopupHost;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.managers.impl.sound.SoundKey;
import com.github.epsilon.settings.Setting;
import com.github.epsilon.settings.SettingGroup;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import org.jspecify.annotations.Nullable;

import java.util.*;

public class SettingListController {

    private static final float GROUP_HEADER_HEIGHT = 30.0f;
    private static final float GROUP_ROW_INSET = 4.0f;
    private static final float GROUP_OUTLINE_INSET = 1.0f;
    private static final float GROUP_COUNT_CHIP_HEIGHT = 14.0f;

    private final PanelPopupHost popupHost;
    private final TextRenderer measureTextRenderer = TextRenderer.create();
    private final Map<Setting<?>, SettingRow<?>> rowCache = new HashMap<>();
    private final Map<SettingGroup, Animation> groupHoverAnimations = new HashMap<>();
    private final Map<SettingGroup, Animation> groupExpandAnimations = new HashMap<>();
    private final List<SettingEntry> settingEntries = new ArrayList<>();
    private final List<GroupEntry> groupEntries = new ArrayList<>();

    private SettingEntry draggingSliderEntry;
    private EnumSettingRow activeEnumRow;

    public SettingListController(PanelPopupHost popupHost) {
        this.popupHost = popupHost;
    }

    public PanelPopupHost getPopupHost() {
        return popupHost;
    }

    public boolean isPopupHovered(int mouseX, int mouseY) {
        return popupHost.getActivePopup() != null && popupHost.getActivePopup().getBounds().contains(mouseX, mouseY);
    }

    public void prepareLayout(List<Setting<?>> settings) {
        rowCache.keySet().removeIf(setting -> settings == null || !settings.contains(setting));
        List<SettingGroup> visibleGroups = settings == null
                ? List.of()
                : settings.stream().map(Setting::getGroup).filter(Objects::nonNull).distinct().toList();
        groupHoverAnimations.keySet().removeIf(group -> !visibleGroups.contains(group));
        groupExpandAnimations.keySet().removeIf(group -> !visibleGroups.contains(group));
        settingEntries.clear();
        groupEntries.clear();
    }

    public float getContentHeight(List<Setting<?>> settings) {
        if (settings == null || settings.isEmpty()) {
            return 0.0f;
        }

        float height = 0.0f;
        for (SettingSection section : buildSections(settings)) {
            if (section.isGroup()) {
                height += getGroupHeight(section);
            } else {
                SettingRow<?> row = rowCache.computeIfAbsent(section.settings().getFirst(), SettingViewFactory::create);
                if (row != null) {
                    height += row.getHeight();
                }
            }
            height += MD3Theme.ROW_GAP;
        }
        return height;
    }

    public void layoutRows(List<Setting<?>> settings, PanelLayout.Rect viewport, float scroll, float rowWidth,
                           PanelUiTree.Scope scope, TextRenderer textRenderer, int mouseX, int mouseY,
                           RowRenderCallback callback) {
        prepareLayout(settings);

        if (activeEnumRow != null && popupHost.getActivePopup() == null) {
            activeEnumRow.setDropdownOpen(false);
            activeEnumRow = null;
        }

        appendRows(settings, viewport, scroll, rowWidth, scope, textRenderer, mouseX, mouseY, callback);
    }

    public void appendRows(List<Setting<?>> settings, PanelLayout.Rect viewport, float scroll, float rowWidth,
                           PanelUiTree.Scope scope, TextRenderer textRenderer, int mouseX, int mouseY,
                           RowRenderCallback callback) {
        float rowY = viewport.y() - scroll;
        for (SettingSection section : buildSections(settings)) {
            if (section.isGroup()) {
                PanelLayout.Rect groupBounds = new PanelLayout.Rect(viewport.x(), rowY, rowWidth, getGroupHeight(section));
                PanelLayout.Rect headerBounds = new PanelLayout.Rect(groupBounds.x(), groupBounds.y(), groupBounds.width(), GROUP_HEADER_HEIGHT);
                groupEntries.add(new GroupEntry(section.group(), headerBounds));
                buildGroupCard(scope, textRenderer, section, groupBounds, headerBounds, mouseX, mouseY);

                if (!section.group().isCollapsed()) {
                    float childY = groupBounds.y() + GROUP_HEADER_HEIGHT + GROUP_ROW_INSET;
                    float childWidth = Math.max(0.0f, groupBounds.width() - GROUP_ROW_INSET * 2.0f);
                    for (Setting<?> setting : section.settings()) {
                        SettingRow<?> row = rowCache.computeIfAbsent(setting, SettingViewFactory::create);
                        if (row == null) {
                            continue;
                        }

                        PanelLayout.Rect rowBounds = new PanelLayout.Rect(groupBounds.x() + GROUP_ROW_INSET, childY, childWidth, row.getHeight());
                        settingEntries.add(new SettingEntry(row, rowBounds));
                        callback.render(setting, row, rowBounds);
                        childY += row.getHeight() + MD3Theme.ROW_GAP;
                    }
                }

                rowY += groupBounds.height() + MD3Theme.ROW_GAP;
                continue;
            }

            Setting<?> setting = section.settings().getFirst();
            SettingRow<?> row = rowCache.computeIfAbsent(setting, SettingViewFactory::create);
            if (row == null) {
                continue;
            }
            PanelLayout.Rect rowBounds = new PanelLayout.Rect(viewport.x(), rowY, rowWidth, row.getHeight());
            settingEntries.add(new SettingEntry(row, rowBounds));
            callback.render(setting, row, rowBounds);
            rowY += row.getHeight() + MD3Theme.ROW_GAP;
        }
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick, PanelLayout.Rect popupBounds) {
        return mouseClicked(event, isDoubleClick, popupBounds, null);
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick, PanelLayout.Rect popupBounds, RowClickInterceptor interceptor) {
        if (event.button() != 0) {
            return false;
        }

        clearFocus();
        for (GroupEntry entry : groupEntries) {
            if (entry.bounds().contains(event.x(), event.y())) {
                entry.group().toggleCollapsed();
                draggingSliderEntry = null;
                Managers.SOUND.playInUi(entry.group().isCollapsed() ? SoundKey.SETTINGS_CLOSE : SoundKey.SETTINGS_OPEN);
                return true;
            }
        }

        for (SettingEntry entry : settingEntries) {
            if (interceptor != null && interceptor.handle(entry.row, entry.bounds, event, isDoubleClick)) {
                draggingSliderEntry = null;
                return true;
            }
            if (entry.row instanceof IntSettingRow intRow && intRow.mouseClicked(entry.bounds, event, isDoubleClick)) {
                draggingSliderEntry = intRow.isDragging() ? entry : null;
                return true;
            }
            if (entry.row instanceof DoubleSettingRow doubleRow && doubleRow.mouseClicked(entry.bounds, event, isDoubleClick)) {
                draggingSliderEntry = doubleRow.isDragging() ? entry : null;
                return true;
            }
            if (entry.row instanceof EnumSettingRow enumRow && entry.row.mouseClicked(entry.bounds, event, isDoubleClick)) {
                popupHost.open(createEnumPopup(enumRow, entry.bounds, popupBounds));
                enumRow.setDropdownOpen(true);
                if (activeEnumRow != null && activeEnumRow != enumRow) {
                    activeEnumRow.setDropdownOpen(false);
                }
                activeEnumRow = enumRow;
                draggingSliderEntry = null;
                return true;
            }
            if (entry.row instanceof ColorSettingRow colorRow && entry.row.mouseClicked(entry.bounds, event, isDoubleClick)) {
                popupHost.open(createColorPopup(colorRow, entry.bounds, popupBounds));
                draggingSliderEntry = null;
                return true;
            }
            if (entry.row instanceof BlockListSettingRow blockListRow && entry.row.mouseClicked(entry.bounds, event, isDoubleClick)) {
                popupHost.open(createBlockListPopup(blockListRow, popupBounds));
                draggingSliderEntry = null;
                return true;
            }
            if (entry.row.mouseClicked(entry.bounds, event, isDoubleClick)) {
                draggingSliderEntry = null;
                return true;
            }
        }

        draggingSliderEntry = null;
        return false;
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingSliderEntry == null) {
            return false;
        }

        draggingSliderEntry.row.mouseReleased(draggingSliderEntry.bounds, event);
        draggingSliderEntry = null;
        return true;
    }

    public boolean mouseDragged(MouseButtonEvent event, double mouseX, double mouseY) {
        if (draggingSliderEntry == null || event.button() != 0) {
            return false;
        }
        if (draggingSliderEntry.row instanceof IntSettingRow intRow) {
            intRow.updateFromMouse(draggingSliderEntry.bounds, event.x());
            return true;
        }
        if (draggingSliderEntry.row instanceof DoubleSettingRow doubleRow) {
            doubleRow.updateFromMouse(draggingSliderEntry.bounds, event.x());
            return true;
        }
        return false;
    }

    public boolean keyPressed(KeyEvent event) {
        for (SettingEntry entry : settingEntries) {
            if (entry.row.keyPressed(event)) {
                return true;
            }
        }
        return false;
    }

    public boolean charTyped(CharacterEvent event) {
        for (SettingEntry entry : settingEntries) {
            if (entry.row.charTyped(event)) {
                return true;
            }
        }
        return false;
    }

    public boolean preeditUpdated(@Nullable PreeditEvent event) {
        for (SettingEntry entry : settingEntries) {
            if (entry.row.preeditUpdated(event)) {
                return true;
            }
        }
        return false;
    }

    public void clearFocus() {
        draggingSliderEntry = null;
        for (SettingRow<?> row : rowCache.values()) {
            row.setFocused(false);
        }
    }

    public void clearAll() {
        clearFocus();
        settingEntries.clear();
        rowCache.clear();
        groupHoverAnimations.clear();
        groupExpandAnimations.clear();
        if (activeEnumRow != null) {
            activeEnumRow.setDropdownOpen(false);
            activeEnumRow = null;
        }
    }

    public boolean hasActiveAnimations() {
        return groupHoverAnimations.values().stream().anyMatch(animation -> !animation.isFinished())
                || groupExpandAnimations.values().stream().anyMatch(animation -> !animation.isFinished());
    }

    private void buildGroupCard(PanelUiTree.Scope scope, TextRenderer textRenderer, SettingSection section,
                                PanelLayout.Rect groupBounds, PanelLayout.Rect headerBounds, int mouseX, int mouseY) {
        SettingGroup group = section.group();
        Animation hoverAnimation = groupHoverAnimations.computeIfAbsent(group, ignored -> createAnimation(120L, 0.0f));
        Animation expandAnimation = groupExpandAnimations.computeIfAbsent(group, ignored -> createAnimation(180L, group.isCollapsed() ? 0.0f : 1.0f));
        float hoverProgress = scope.animate(hoverAnimation, headerBounds.contains(mouseX, mouseY));
        float expandProgress = scope.animate(expandAnimation, !group.isCollapsed());

        scope.roundRect(groupBounds.x(), groupBounds.y(), groupBounds.width(), groupBounds.height(), MD3Theme.CARD_RADIUS, MD3Theme.OUTLINE_SOFT);
        scope.roundRect(
                groupBounds.x() + GROUP_OUTLINE_INSET,
                groupBounds.y() + GROUP_OUTLINE_INSET,
                groupBounds.width() - GROUP_OUTLINE_INSET * 2.0f,
                groupBounds.height() - GROUP_OUTLINE_INSET * 2.0f,
                Math.max(1.0f, MD3Theme.CARD_RADIUS - GROUP_OUTLINE_INSET),
                MD3Theme.lerp(MD3Theme.SURFACE_CONTAINER_LOW, MD3Theme.SURFACE_CONTAINER, expandProgress)
        );
        if (hoverProgress > 0.01f) {
            scope.roundRect(headerBounds.x(), headerBounds.y(), headerBounds.width(), headerBounds.height(), MD3Theme.CARD_RADIUS,
                    MD3Theme.stateLayer(MD3Theme.TEXT_PRIMARY, hoverProgress, MD3Theme.isLightTheme() ? 10 : 14));
        }

        float labelScale = 0.66f;
        String label = trimToWidth(group.getDisplayName(), labelScale, headerBounds.width() - 74.0f, textRenderer);
        float labelY = headerBounds.y() + (GROUP_HEADER_HEIGHT - textRenderer.getHeight(labelScale)) / 2.0f - 1.0f;
        scope.text(label, headerBounds.x() + MD3Theme.ROW_CONTENT_INSET + 2.0f, labelY, labelScale, MD3Theme.TEXT_PRIMARY);

        String countLabel = Integer.toString(section.settings().size());
        float countScale = 0.46f;
        float countWidth = textRenderer.getWidth(countLabel, countScale) + 10.0f;
        float countX = headerBounds.right() - MD3Theme.ROW_TRAILING_INSET - 20.0f - countWidth;
        float countY = headerBounds.y() + (GROUP_HEADER_HEIGHT - GROUP_COUNT_CHIP_HEIGHT) / 2.0f;
        scope.roundRect(countX, countY, countWidth, GROUP_COUNT_CHIP_HEIGHT, GROUP_COUNT_CHIP_HEIGHT / 2.0f,
                MD3Theme.withAlpha(MD3Theme.SECONDARY_CONTAINER, 210));
        scope.text(countLabel,
                countX + (countWidth - textRenderer.getWidth(countLabel, countScale)) / 2.0f,
                countY + (GROUP_COUNT_CHIP_HEIGHT - textRenderer.getHeight(countScale)) / 2.0f - 1.0f,
                countScale,
                MD3Theme.ON_SECONDARY_CONTAINER);

        float chevronSize = 3.0f;
        float chevronCenterX = headerBounds.right() - MD3Theme.ROW_TRAILING_INSET - chevronSize - 3.0f;
        float chevronCenterY = headerBounds.y() + GROUP_HEADER_HEIGHT / 2.0f;
        scope.triangle(chevronCenterX, chevronCenterY, chevronSize, expandProgress, MD3Theme.lerp(MD3Theme.TEXT_MUTED, MD3Theme.PRIMARY, hoverProgress));
    }

    private List<SettingSection> buildSections(List<Setting<?>> settings) {
        if (settings == null || settings.isEmpty()) {
            return List.of();
        }

        List<SettingSection> sections = new ArrayList<>();
        Map<SettingGroup, SettingSection> groupedSections = new HashMap<>();
        for (Setting<?> setting : settings) {
            SettingGroup group = setting.getGroup();
            if (group == null) {
                sections.add(new SettingSection(null, new ArrayList<>(List.of(setting))));
                continue;
            }

            SettingSection section = groupedSections.get(group);
            if (section == null) {
                section = new SettingSection(group, new ArrayList<>());
                groupedSections.put(group, section);
                sections.add(section);
            }
            section.settings().add(setting);
        }
        return sections;
    }

    private float getGroupHeight(SettingSection section) {
        if (!section.isGroup()) {
            return 0.0f;
        }
        if (section.group().isCollapsed()) {
            return GROUP_HEADER_HEIGHT;
        }

        float height = GROUP_HEADER_HEIGHT + GROUP_ROW_INSET * 2.0f;
        for (Setting<?> setting : section.settings()) {
            SettingRow<?> row = rowCache.computeIfAbsent(setting, SettingViewFactory::create);
            if (row != null) {
                height += row.getHeight() + MD3Theme.ROW_GAP;
            }
        }
        return height;
    }

    private Animation createAnimation(long duration, float startValue) {
        Animation animation = new Animation(Easing.EASE_OUT_CUBIC, duration);
        animation.setStartValue(startValue);
        return animation;
    }

    private String trimToWidth(String value, float scale, float width, TextRenderer textRenderer) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (textRenderer.getWidth(value, scale) <= width) {
            return value;
        }
        String ellipsis = "...";
        float ellipsisWidth = textRenderer.getWidth(ellipsis, scale);
        if (ellipsisWidth >= width) {
            return ellipsis;
        }
        for (int length = value.length() - 1; length >= 0; length--) {
            String candidate = value.substring(0, length) + ellipsis;
            if (textRenderer.getWidth(candidate, scale) <= width) {
                return candidate;
            }
        }
        return ellipsis;
    }

    private EnumSelectPopup createEnumPopup(EnumSettingRow enumRow, PanelLayout.Rect rowBounds, PanelLayout.Rect popupBounds) {
        PanelLayout.Rect chipBounds = enumRow.getChipBounds(measureTextRenderer, rowBounds);
        int optionCount = enumRow.getSetting().getModes().length;
        int visibleCount = Math.min(optionCount, EnumSelectPopup.MAX_VISIBLE_ITEMS);
        float popupHeight = visibleCount * 24.0f + 12.0f;
        float popupWidth = Math.max(108.0f, chipBounds.width() + 24.0f);
        float popupX = Math.max(popupBounds.x() + MD3Theme.PANEL_VIEWPORT_INSET, chipBounds.right() - popupWidth);
        float popupY = chipBounds.bottom() + 4.0f;
        float maxBottom = popupBounds.bottom() - MD3Theme.PANEL_VIEWPORT_INSET;
        if (popupY + popupHeight > maxBottom) {
            popupY = chipBounds.y() - popupHeight - 4.0f;
        }
        return new EnumSelectPopup(new PanelLayout.Rect(popupX, popupY, popupWidth, popupHeight), chipBounds, enumRow.getSetting());
    }

    private ColorPickerPopup createColorPopup(ColorSettingRow colorRow, PanelLayout.Rect rowBounds, PanelLayout.Rect popupBounds) {
        PanelLayout.Rect swatchBounds = colorRow.getSwatchBounds(rowBounds);
        int channelCount = colorRow.getSetting().isAllowAlpha() ? 4 : 3;
        float popupWidth = 156.0f;
        float popupHeight = 58.0f + channelCount * 24.0f;
        float popupX = Math.max(popupBounds.x() + MD3Theme.PANEL_VIEWPORT_INSET, swatchBounds.right() - popupWidth);
        float popupY = swatchBounds.bottom() + 4.0f;
        float maxBottom = popupBounds.bottom() - MD3Theme.PANEL_VIEWPORT_INSET;
        if (popupY + popupHeight > maxBottom) {
            popupY = swatchBounds.y() - popupHeight - 4.0f;
        }
        return new ColorPickerPopup(new PanelLayout.Rect(popupX, popupY, popupWidth, popupHeight), swatchBounds, colorRow.getSetting());
    }

    private BlockListSelectPopup createBlockListPopup(BlockListSettingRow blockListRow, PanelLayout.Rect popupBounds) {
        PanelLayout.Rect bounds = popupHost.getCenteredBounds(Math.min(360.0f, popupBounds.width() - 24.0f), Math.min(246.0f, popupBounds.height() - 24.0f));
        return new BlockListSelectPopup(bounds, blockListRow.getSetting());
    }

    @FunctionalInterface
    public interface RowRenderCallback {
        void render(Setting<?> setting, SettingRow<?> row, PanelLayout.Rect rowBounds);
    }

    @FunctionalInterface
    public interface RowClickInterceptor {
        boolean handle(SettingRow<?> row, PanelLayout.Rect rowBounds, MouseButtonEvent event, boolean isDoubleClick);
    }

    private record SettingEntry(SettingRow<?> row, PanelLayout.Rect bounds) {
    }

    private record GroupEntry(SettingGroup group, PanelLayout.Rect bounds) {
    }

    private record SettingSection(SettingGroup group, List<Setting<?>> settings) {
        private boolean isGroup() {
            return group != null;
        }
    }

}
