package com.github.epsilon.gui.dropdown.component;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.gui.dropdown.DropdownDrawContext;
import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.epsilon.gui.dropdown.widget.*;
import com.github.epsilon.gui.panel.MD3Theme;
import com.github.epsilon.gui.panel.PanelLayout;
import com.github.epsilon.holders.ConfigHolder;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.managers.impl.sound.SoundKey;
import com.github.epsilon.settings.Setting;
import com.github.epsilon.settings.SettingLayoutPlanner;
import com.github.epsilon.settings.impl.*;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;

import java.util.*;

public class SettingsContent {

    private final List<SettingSection> sections = new ArrayList<>();
    private final Map<String, Animation> sectionHoverAnimations = new HashMap<>();
    private final Map<String, Animation> sectionExpandAnimations = new HashMap<>();

    public SettingsContent(List<Setting<?>> settings) {
        this("dropdown:" + System.identityHashCode(settings), settings);
    }

    public SettingsContent(String ownerKey, List<Setting<?>> settings) {
        List<SettingLayoutPlanner.Section> plannedSections = SettingLayoutPlanner.plan(ownerKey, settings);
        Map<Setting<?>, SettingWidget<?>> widgets = new HashMap<>();
        for (Setting<?> setting : settings) {
            SettingWidget<?> widget = createWidget(setting);
            if (widget != null) {
                widgets.put(setting, widget);
            }
        }

        for (SettingLayoutPlanner.Section plannedSection : plannedSections) {
            List<SettingWidget<?>> sectionWidgets = new ArrayList<>();
            for (Setting<?> setting : plannedSection.settings()) {
                SettingWidget<?> widget = widgets.get(setting);
                if (widget != null) {
                    sectionWidgets.add(widget);
                }
            }
            if (!sectionWidgets.isEmpty()) {
                sections.add(new SettingSection(plannedSection, sectionWidgets));
            }
        }
    }

    public static SettingWidget<?> createWidget(Setting<?> setting) {
        if (setting instanceof BoolSetting s) return new BoolWidget(s);
        if (setting instanceof IntSetting s) return new IntSliderWidget(s);
        if (setting instanceof DoubleSetting s) return new DoubleSliderWidget(s);
        if (setting instanceof EnumSetting<?> s) return new EnumWidget(s);
        if (setting instanceof ColorSetting s) return new ColorWidget(s);
        if (setting instanceof BlockListSetting s) return new BlockListWidget(s);
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

    public void draw(DropdownDrawContext renderer, int mouseX, int mouseY, float panelX, float contentY, float panelWidth) {
        if (sections.isEmpty()) {
            String label = EpsilonTranslations.Gui.ADDON_NO_SETTINGS.getTranslatedName();
            float labelScale = 0.58f;
            float textW = renderer.text().getWidth(label, labelScale);
            renderer.text().addText(label, panelX + (panelWidth - textW) * 0.5f, contentY + 8.0f, labelScale, MD3Theme.TEXT_MUTED);
            return;
        }

        float currentY = contentY + DropdownTheme.SETTING_GAP;
        for (SettingSection section : sections) {
            if (section.hasHeader()) {
                drawSection(renderer, mouseX, mouseY, section, panelX, currentY, panelWidth);
            } else {
                DropdownDrawContext.Stack stack = renderer.stack(new PanelLayout.Rect(
                        panelX + DropdownTheme.SETTING_INDENT,
                        currentY,
                        panelWidth - DropdownTheme.SETTING_INDENT * 2.0f,
                        getSectionHeight(section)
                ));
                for (SettingWidget<?> widget : section.widgets()) {
                    if (!widget.isVisible()) continue;
                    widget.draw(renderer, mouseX, mouseY, stack.item(widget.getHeight(), DropdownTheme.SETTING_GAP));
                }
            }
            currentY += getSectionHeight(section);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, float panelX, float contentY, float panelWidth) {
        float currentY = contentY + DropdownTheme.SETTING_GAP;
        for (SettingSection section : sections) {
            if (section.hasHeader()) {
                float headerX = panelX + DropdownTheme.SETTING_INDENT;
                float headerW = panelWidth - DropdownTheme.SETTING_INDENT * 2.0f;
                if (isHovered(mouseX, mouseY, headerX, currentY, headerW, DropdownTheme.GROUP_HEADER_HEIGHT)) {
                    section.toggleCollapsed();
                    Managers.SOUND.playInUi(section.isCollapsed() ? SoundKey.SETTINGS_CLOSE : SoundKey.SETTINGS_OPEN);
                    ConfigHolder.INSTANCE.saveNow();
                    return true;
                }
                if (!section.isCollapsed()) {
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
        if (!section.hasHeader()) {
            float h = 0.0f;
            for (SettingWidget<?> widget : section.widgets()) {
                if (widget.isVisible()) {
                    h += widget.getHeight() + DropdownTheme.SETTING_GAP;
                }
            }
            return h;
        }

        if (section.isCollapsed()) {
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

    private void drawSection(DropdownDrawContext renderer, int mouseX, int mouseY, SettingSection section, float panelX, float sectionY, float panelWidth) {
        Animation expandAnimG = sectionExpandAnimations.computeIfAbsent(section.key(), ignored -> createGroupAnimation(section.isCollapsed() ? 0.0f : 1.0f));
        Animation hoverAnim = sectionHoverAnimations.computeIfAbsent(section.key(), ignored -> createGroupAnimation(0.0f));
        float headerW = panelWidth - DropdownTheme.SETTING_INDENT * 2.0f;
        float headerX = panelX + DropdownTheme.SETTING_INDENT;
        float headerH = DropdownTheme.GROUP_HEADER_HEIGHT;
        hoverAnim.run(isHovered(mouseX, mouseY, headerX, sectionY, headerW, headerH) ? 1.0f : 0.0f);
        expandAnimG.run(section.isCollapsed() ? 0.0f : 1.0f);

        float hoverProgress = hoverAnim.getValue();
        float expandProgress = expandAnimG.getValue();
        renderer.roundRect().addRoundRect(headerX, sectionY, headerW, headerH, DropdownTheme.BUTTON_RADIUS,
                MD3Theme.lerp(DropdownTheme.groupBackground(), DropdownTheme.groupBackgroundHover(), hoverProgress));

        String label = trimToWidth(section.title(), DropdownTheme.GROUP_HEADER_TEXT_SCALE, headerW - 74.0f, renderer);
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

        if (!section.isCollapsed()) {
            float childY = sectionY + headerH + DropdownTheme.SETTING_GAP + DropdownTheme.GROUP_INSET;
            float childX = panelX + DropdownTheme.SETTING_INDENT + DropdownTheme.GROUP_INSET;
            float childW = panelWidth - (DropdownTheme.SETTING_INDENT + DropdownTheme.GROUP_INSET) * 2.0f;
            DropdownDrawContext.Stack stack = renderer.stack(new PanelLayout.Rect(childX, childY, childW, getSectionHeight(section)));
            for (SettingWidget<?> widget : section.widgets()) {
                if (!widget.isVisible()) continue;
                widget.draw(renderer, mouseX, mouseY, stack.item(widget.getHeight(), DropdownTheme.SETTING_GAP));
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

    private String trimToWidth(String value, float scale, float maxWidth, DropdownDrawContext renderer) {
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

    private record SettingSection(SettingLayoutPlanner.Section model, List<SettingWidget<?>> widgets) {
        private String key() {
            return model.key();
        }

        private String title() {
            return model.title();
        }

        private boolean hasHeader() {
            return model.hasHeader();
        }

        private boolean isCollapsed() {
            return model.isCollapsed();
        }

        private void toggleCollapsed() {
            model.toggleCollapsed();
        }
    }

}
