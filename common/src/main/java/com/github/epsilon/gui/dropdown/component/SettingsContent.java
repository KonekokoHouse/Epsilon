package com.github.epsilon.gui.dropdown.component;

import com.github.epsilon.assets.i18n.EpsilonTranslateComponent;
import com.github.epsilon.assets.i18n.TranslateComponent;
import com.github.epsilon.gui.dropdown.DropdownRenderer;
import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.epsilon.gui.dropdown.widget.*;
import com.github.epsilon.gui.panel.MD3Theme;
import com.github.epsilon.holders.ConfigHolder;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.managers.impl.sound.SoundKey;
import com.github.epsilon.settings.Setting;
import com.github.epsilon.settings.SettingGroup;
import com.github.epsilon.settings.impl.*;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import org.jspecify.annotations.Nullable;

import java.util.*;

public class SettingsContent {

    private static final TranslateComponent noSettingsComponent = EpsilonTranslateComponent.create("gui", "addon.no_settings");

    private final List<SettingSection> sections = new ArrayList<>();
    private final Map<SettingGroup, Animation> groupHoverAnimations = new HashMap<>();
    private final Map<SettingGroup, Animation> groupExpandAnimations = new HashMap<>();

    public SettingsContent(List<Setting<?>> settings, List<SettingGroup> orderedGroups) {
        Map<SettingGroup, SettingSection> groupedSections = new LinkedHashMap<>();

        for (Setting<?> setting : settings) {
            SettingWidget<?> widget = createWidget(setting);
            if (widget == null) continue;
            SettingGroup group = setting.getGroup();
            if (group != null) {
                SettingSection section = groupedSections.get(group);
                if (section == null) {
                    section = new SettingSection(group, new ArrayList<>());
                    groupedSections.put(group, section);
                    sections.add(section);
                }
                section.widgets().add(widget);
            } else {
                sections.add(new SettingSection(null, new ArrayList<>(List.of(widget))));
            }
        }
    }

    public static SettingWidget<?> createWidget(Setting<?> setting) {
        if (setting instanceof BoolSetting s) return new BoolWidget(s);
        if (setting instanceof IntSetting s) return new IntSliderWidget(s);
        if (setting instanceof DoubleSetting s) return new DoubleSliderWidget(s);
        if (setting instanceof EnumSetting<?> s) return new EnumWidget(s);
        if (setting instanceof ColorSetting s) return new ColorWidget(s);
        if (setting instanceof KeybindSetting s) return new KeybindWidget(s);
        if (setting instanceof StringSetting s) return new StringWidget(s);
        if (setting instanceof ButtonSetting s) return new ButtonWidget(s);
        return null;
    }

    public float computeContentHeight() {
        if (sections.isEmpty()) return DropdownTheme.MODULE_HEIGHT;
        float height = DropdownTheme.SETTING_GAP;
        for (SettingSection section : sections) {
            height += getSectionHeight(section);
        }
        return height;
    }

    public void draw(DropdownRenderer renderer, int mouseX, int mouseY, float panelX, float contentY, float panelWidth) {
        if (sections.isEmpty()) {
            String label = noSettingsComponent.getTranslatedName();
            float labelScale = 0.58f;
            float textW = renderer.text().getWidth(label, labelScale);
            renderer.text().addText(label, panelX + (panelWidth - textW) * 0.5f, contentY + 8.0f, labelScale, MD3Theme.TEXT_MUTED);
            return;
        }

        float currentY = contentY + DropdownTheme.SETTING_GAP;
        for (SettingSection section : sections) {
            if (section.isGroup()) {
                drawGroupSection(renderer, mouseX, mouseY, section, panelX, currentY, panelWidth);
            } else {
                float widgetY = currentY;
                for (SettingWidget<?> widget : section.widgets()) {
                    if (!widget.isVisible()) continue;
                    widget.setPosition(panelX + DropdownTheme.SETTING_INDENT, widgetY, panelWidth - DropdownTheme.SETTING_INDENT * 2.0f);
                    widget.draw(renderer, mouseX, mouseY);
                    widgetY += widget.getHeight() + DropdownTheme.SETTING_GAP;
                }
            }
            currentY += getSectionHeight(section);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, float panelX, float contentY, float panelWidth) {
        float currentY = contentY + DropdownTheme.SETTING_GAP;
        for (SettingSection section : sections) {
            if (section.isGroup()) {
                float headerX = panelX + DropdownTheme.SETTING_INDENT;
                float headerW = panelWidth - DropdownTheme.SETTING_INDENT * 2.0f;
                if (isHovered(mouseX, mouseY, headerX, currentY, headerW, DropdownTheme.GROUP_HEADER_HEIGHT)) {
                    section.group().toggleCollapsed();
                    Managers.SOUND.playInUi(section.group().isCollapsed() ? SoundKey.SETTINGS_CLOSE : SoundKey.SETTINGS_OPEN);
                    ConfigHolder.INSTANCE.saveNow();
                    return true;
                }
                if (!section.group().isCollapsed()) {
                    for (SettingWidget<?> widget : section.widgets()) {
                        if (!widget.isVisible()) continue;
                        if (widget.mouseClicked(mouseX, mouseY, button)) {
                            ConfigHolder.INSTANCE.saveNow();
                            return true;
                        }
                    }
                }
            } else {
                for (SettingWidget<?> widget : section.widgets()) {
                    if (!widget.isVisible()) continue;
                    if (widget.mouseClicked(mouseX, mouseY, button)) {
                        ConfigHolder.INSTANCE.saveNow();
                        return true;
                    }
                }
            }
            currentY += getSectionHeight(section);
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button, float panelX, float contentY, float panelWidth) {
        for (SettingSection section : sections) {
            for (SettingWidget<?> widget : section.widgets()) {
                if (!widget.isVisible()) continue;
                if (widget.mouseReleased(mouseX, mouseY, button)) {
                    ConfigHolder.INSTANCE.saveNow();
                    return true;
                }
            }
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (SettingSection section : sections) {
            for (SettingWidget<?> widget : section.widgets()) {
                if (!widget.isVisible()) continue;
                if (widget.keyPressed(keyCode, scanCode, modifiers)) {
                    ConfigHolder.INSTANCE.saveNow();
                    return true;
                }
            }
        }
        return false;
    }

    public boolean charTyped(String typedText) {
        for (SettingSection section : sections) {
            for (SettingWidget<?> widget : section.widgets()) {
                if (!widget.isVisible()) continue;
                if (widget.charTyped(typedText)) {
                    ConfigHolder.INSTANCE.saveNow();
                    return true;
                }
            }
        }
        return false;
    }

    public boolean hasActiveInput() {
        for (SettingSection section : sections) {
            for (SettingWidget<?> widget : section.widgets()) {
                if (widget instanceof KeybindWidget kw && kw.isListening()) return true;
                if (widget instanceof StringWidget sw && sw.isFocused()) return true;
                if (widget instanceof IntSliderWidget iw && iw.isFocused()) return true;
                if (widget instanceof DoubleSliderWidget dw && dw.isFocused()) return true;
                if (widget instanceof ColorWidget cw && cw.hasFocusedInput()) return true;
            }
        }
        return false;
    }

    private float getSectionHeight(SettingSection section) {
        if (!section.isGroup()) {
            float h = 0.0f;
            for (SettingWidget<?> widget : section.widgets()) {
                if (widget.isVisible()) {
                    h += widget.getHeight() + DropdownTheme.SETTING_GAP;
                }
            }
            return h;
        }

        if (section.group().isCollapsed()) {
            return DropdownTheme.GROUP_HEADER_HEIGHT + DropdownTheme.SETTING_GAP;
        }

        float h = DropdownTheme.GROUP_HEADER_HEIGHT + DropdownTheme.SETTING_GAP + DropdownTheme.GROUP_INSET;
        for (SettingWidget<?> widget : section.widgets()) {
            if (widget.isVisible()) {
                h += widget.getHeight() + DropdownTheme.SETTING_GAP;
            }
        }
        return h;
    }

    private void drawGroupSection(DropdownRenderer renderer, int mouseX, int mouseY, SettingSection section, float panelX, float sectionY, float panelWidth) {
        SettingGroup group = section.group();
        Animation expandAnimG = groupExpandAnimations.computeIfAbsent(group, ignored -> createGroupAnimation(group.isCollapsed() ? 0.0f : 1.0f));
        Animation hoverAnim = groupHoverAnimations.computeIfAbsent(group, ignored -> createGroupAnimation(0.0f));
        float headerW = panelWidth - DropdownTheme.SETTING_INDENT * 2.0f;
        float headerX = panelX + DropdownTheme.SETTING_INDENT;
        float headerH = DropdownTheme.GROUP_HEADER_HEIGHT;
        hoverAnim.run(isHovered(mouseX, mouseY, headerX, sectionY, headerW, headerH) ? 1.0f : 0.0f);
        expandAnimG.run(group.isCollapsed() ? 0.0f : 1.0f);

        float hoverProgress = hoverAnim.getValue();
        float expandProgress = expandAnimG.getValue();
        renderer.roundRect().addRoundRect(headerX, sectionY, headerW, headerH, DropdownTheme.BUTTON_RADIUS,
                MD3Theme.lerp(DropdownTheme.groupBackground(), DropdownTheme.groupBackgroundHover(), hoverProgress));

        String label = trimToWidth(group.getDisplayName(), DropdownTheme.GROUP_HEADER_TEXT_SCALE, headerW - 74.0f, renderer);
        float labelY = sectionY + (headerH - renderer.text().getHeight(DropdownTheme.GROUP_HEADER_TEXT_SCALE)) * 0.5f;
        renderer.text().addText(label, headerX + DropdownTheme.SETTING_PADDING_X, labelY, DropdownTheme.GROUP_HEADER_TEXT_SCALE, DropdownTheme.groupText());

        String countLabel = Integer.toString(section.widgets().size());
        float countWidth = renderer.text().getWidth(countLabel, DropdownTheme.GROUP_COUNT_TEXT_SCALE) + DropdownTheme.GROUP_COUNT_CHIP_PADDING * 2.0f;
        float countX = headerX + headerW - DropdownTheme.SETTING_PADDING_X - countWidth - 12.0f;
        float chipH = DropdownTheme.GROUP_COUNT_CHIP_HEIGHT;
        float countY = sectionY + (headerH - chipH) * 0.5f;
        renderer.roundRect().addRoundRect(countX, countY, countWidth, chipH, chipH / 2.0f, DropdownTheme.groupCountChip());
        float countTextY = countY + (chipH - renderer.text().getHeight(DropdownTheme.GROUP_COUNT_TEXT_SCALE)) * 0.5f;
        renderer.text().addText(countLabel, countX + DropdownTheme.GROUP_COUNT_CHIP_PADDING, countTextY, DropdownTheme.GROUP_COUNT_TEXT_SCALE, DropdownTheme.groupCountText());

        renderer.triangle().addChevronTriangle(headerX + headerW - DropdownTheme.SETTING_PADDING_X - 2.5f,
                sectionY + headerH * 0.5f, 2.5f, expandProgress, DropdownTheme.groupChevron(hoverProgress));

        if (!group.isCollapsed()) {
            float childY = sectionY + headerH + DropdownTheme.SETTING_GAP + DropdownTheme.GROUP_INSET;
            float childX = panelX + DropdownTheme.SETTING_INDENT + DropdownTheme.GROUP_INSET;
            float childW = panelWidth - (DropdownTheme.SETTING_INDENT + DropdownTheme.GROUP_INSET) * 2.0f;
            for (SettingWidget<?> widget : section.widgets()) {
                if (!widget.isVisible()) continue;
                widget.setPosition(childX, childY, childW);
                widget.draw(renderer, mouseX, mouseY);
                childY += widget.getHeight() + DropdownTheme.SETTING_GAP;
            }
        }
    }

    private Animation createGroupAnimation(float startValue) {
        Animation anim = new Animation(Easing.EASE_OUT_CUBIC, DropdownTheme.ANIM_GROUP);
        anim.setStartValue(startValue);
        return anim;
    }

    private boolean isHovered(double mouseX, double mouseY, float x, float y, float w, float h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private String trimToWidth(String value, float scale, float maxWidth, DropdownRenderer renderer) {
        if (value == null || value.isEmpty()) return "";
        if (renderer.text().getWidth(value, scale) <= maxWidth) return value;
        String ellipsis = "...";
        float ellipsisWidth = renderer.text().getWidth(ellipsis, scale);
        if (ellipsisWidth >= maxWidth) return ellipsis;
        for (int len = value.length() - 1; len >= 0; len--) {
            String candidate = value.substring(0, len) + ellipsis;
            if (renderer.text().getWidth(candidate, scale) <= maxWidth) return candidate;
        }
        return ellipsis;
    }

    private record SettingSection(@Nullable SettingGroup group, List<SettingWidget<?>> widgets) {
        private boolean isGroup() {
            return group != null;
        }
    }

}
