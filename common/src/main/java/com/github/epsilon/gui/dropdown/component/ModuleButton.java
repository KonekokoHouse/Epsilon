package com.github.epsilon.gui.dropdown.component;

import com.github.epsilon.assets.i18n.EpsilonTranslateComponent;
import com.github.epsilon.assets.i18n.TranslateComponent;
import com.github.epsilon.graphics.text.StaticFontLoader;
import com.github.epsilon.gui.dropdown.DropdownRenderer;
import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.epsilon.gui.dropdown.widget.*;
import com.github.epsilon.gui.panel.MD3Theme;
import com.github.epsilon.managers.ConfigManager;
import com.github.epsilon.managers.sound.SoundKey;
import com.github.epsilon.managers.sound.SoundManager;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.Setting;
import com.github.epsilon.settings.SettingGroup;
import com.github.epsilon.settings.impl.*;
import com.github.epsilon.utils.client.KeybindUtils;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import org.jspecify.annotations.Nullable;

import java.awt.*;
import java.util.*;
import java.util.List;

public class ModuleButton extends Component {

    private static final TranslateComponent visibleComponent = EpsilonTranslateComponent.create("module", "visible");
    private static final TranslateComponent hiddenComponent = EpsilonTranslateComponent.create("module", "hidden");
    private static final TranslateComponent fromComponent = EpsilonTranslateComponent.create("module", "from");

    private final Module module;
    private final List<SettingSection> sections = new ArrayList<>();
    private final Map<SettingGroup, Animation> groupHoverAnimations = new HashMap<>();
    private final Map<SettingGroup, Animation> groupExpandAnimations = new HashMap<>();
    private final Animation expandAnim = new Animation(Easing.EASE_IN_OUT_CUBIC, DropdownTheme.ANIM_EXPAND);
    private final Animation toggleAnim = new Animation(Easing.EASE_OUT_CUBIC, DropdownTheme.ANIM_TOGGLE);
    private final Animation hoverAnim = new Animation(Easing.EASE_OUT_CUBIC, DropdownTheme.ANIM_HOVER);
    private final Animation keybindHoverAnim = new Animation(Easing.EASE_OUT_CUBIC, DropdownTheme.ANIM_HOVER);
    private boolean expanded;
    private boolean listeningKeybind;

    public ModuleButton(Module module) {
        this.module = module;
        Map<SettingGroup, List<SettingWidget<?>>> groupedWidgets = new LinkedHashMap<>();
        List<SettingWidget<?>> ungroupedWidgets = new ArrayList<>();

        for (Setting<?> setting : module.getSettings()) {
            SettingWidget<?> widget = createWidget(setting);
            if (widget == null) continue;

            SettingGroup group = setting.getGroup();
            if (group != null) {
                groupedWidgets.computeIfAbsent(group, k -> new ArrayList<>()).add(widget);
            } else {
                ungroupedWidgets.add(widget);
            }
        }

        for (SettingWidget<?> widget : ungroupedWidgets) {
            sections.add(new SettingSection(null, List.of(widget)));
        }

        for (Map.Entry<SettingGroup, List<SettingWidget<?>>> entry : groupedWidgets.entrySet()) {
            sections.add(new SettingSection(entry.getKey(), entry.getValue()));
        }
    }

    private static SettingWidget<?> createWidget(Setting<?> setting) {
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

    @Override
    public float getHeight() {
        expandAnim.run(expanded ? 1.0f : 0.0f);
        float settingsHeight = computeSettingsHeight();
        return DropdownTheme.MODULE_HEIGHT + settingsHeight * expandAnim.getValue();
    }

    private float computeSettingsHeight() {
        float height = DropdownTheme.SETTING_GAP + DropdownTheme.MODULE_ADDON_INFO_HEIGHT + DropdownTheme.SETTING_GAP;
        for (SettingSection section : sections) {
            if (section.isGroup()) {
                height += DropdownTheme.GROUP_HEADER_HEIGHT;
                if (!section.group().isCollapsed()) {
                    height += DropdownTheme.GROUP_INSET;
                    for (SettingWidget<?> widget : section.widgets()) {
                        if (widget.isVisible()) {
                            height += widget.getHeight() + DropdownTheme.SETTING_GAP;
                        }
                    }
                }
                height += DropdownTheme.SETTING_GAP;
            } else {
                for (SettingWidget<?> widget : section.widgets()) {
                    if (widget.isVisible()) {
                        height += widget.getHeight() + DropdownTheme.SETTING_GAP;
                    }
                }
            }
        }
        return height;
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

    @Override
    public void draw(DropdownRenderer renderer, int mouseX, int mouseY) {
        expandAnim.run(expanded ? 1.0f : 0.0f);
        toggleAnim.run(module.isEnabled() ? 1.0f : 0.0f);
        boolean headerHovered = isHovered(mouseX, mouseY, x, y, width, DropdownTheme.MODULE_HEIGHT);
        hoverAnim.run(headerHovered ? 1.0f : 0.0f);

        float hover = hoverAnim.getValue();
        float toggle = toggleAnim.getValue();

        Color bg = MD3Theme.lerp(DropdownTheme.moduleDisabled(hover), DropdownTheme.moduleEnabled(hover), toggle);
        renderer.rect().addRect(x + 2.0f, y, width - 4.0f, DropdownTheme.MODULE_HEIGHT, bg);
        renderer.rect().addRect(x + 3.0f, y + DropdownTheme.MODULE_HEIGHT - 0.5f, width - 6.0f, 0.5f, DropdownTheme.moduleDivider());

        Color textColor = MD3Theme.lerp(DropdownTheme.moduleTextDisabled(hover), DropdownTheme.moduleTextEnabled(), toggle);
        float textY = y + (DropdownTheme.MODULE_HEIGHT - renderer.text().getHeight(DropdownTheme.MODULE_TEXT_SCALE)) * 0.5f;
        float leftX = x + DropdownTheme.MODULE_PADDING_X;
        renderer.text().addText(module.getTranslatedName(), leftX, textY, DropdownTheme.MODULE_TEXT_SCALE, textColor);

        drawKeybindButton(renderer, mouseX, mouseY, toggle);
        drawHiddenButton(renderer, mouseX, mouseY);

        float expand = expandAnim.getValue();

        for (SettingSection section : sections) {
            if (section.isGroup()) {
                runGroupAnimations(section);
            }
        }

        if (expand > 0.01f) {
            float settingY = y + DropdownTheme.MODULE_HEIGHT + DropdownTheme.SETTING_GAP;
            if (expand > 0.5f) {
                drawAddonInfo(renderer, settingY);
            }
            settingY += DropdownTheme.MODULE_ADDON_INFO_HEIGHT + DropdownTheme.SETTING_GAP;
            for (SettingSection section : sections) {
                float sectionH = getSectionHeight(section);
                if (section.isGroup()) {
                    if (expand > 0.5f) {
                        drawGroupSection(renderer, mouseX, mouseY, section, settingY);
                    }
                    settingY += sectionH;
                } else {
                    for (SettingWidget<?> widget : section.widgets()) {
                        if (!widget.isVisible()) continue;
                        widget.setPosition(x + DropdownTheme.SETTING_INDENT, settingY, width - DropdownTheme.SETTING_INDENT * 2.0f);
                        if (expand > 0.5f) {
                            widget.draw(renderer, mouseX, mouseY);
                        }
                        settingY += widget.getHeight() + DropdownTheme.SETTING_GAP;
                    }
                }
            }
        }
    }

    private void runGroupAnimations(SettingSection section) {
        SettingGroup group = section.group();
        Animation expandAnimG = groupExpandAnimations.computeIfAbsent(group, k -> createGroupAnimation(180L, group.isCollapsed() ? 0.0f : 1.0f));
        expandAnimG.run(group.isCollapsed() ? 0.0f : 1.0f);
    }

    private void drawAddonInfo(DropdownRenderer renderer, float infoY) {
        float infoX = x + DropdownTheme.SETTING_INDENT;
        float infoW = width - DropdownTheme.SETTING_INDENT * 2.0f;
        float infoH = DropdownTheme.MODULE_ADDON_INFO_HEIGHT;

        float scale = DropdownTheme.MODULE_ADDON_INFO_TEXT_SCALE;
        String addonLabel = fromComponent.getTranslatedName() + " " + getAddonLabel();
        float textY = infoY + (infoH - renderer.text().getHeight(scale)) * 0.5f - 0.5f;
        renderer.text().addText(addonLabel, infoX + DropdownTheme.SETTING_PADDING_X, textY, scale, DropdownTheme.moduleAddonInfoText());
    }

    private void drawGroupSection(DropdownRenderer renderer, int mouseX, int mouseY, SettingSection section, float sectionY) {
        SettingGroup group = section.group();

        float headerW = width - DropdownTheme.SETTING_INDENT * 2.0f;
        float headerX = x + DropdownTheme.SETTING_INDENT;
        float headerH = DropdownTheme.GROUP_HEADER_HEIGHT;

        Animation hoverAnim = groupHoverAnimations.computeIfAbsent(group, k -> createGroupAnimation(120L, 0.0f));
        hoverAnim.run(isHovered(mouseX, mouseY, headerX, sectionY, headerW, headerH) ? 1.0f : 0.0f);
        float hoverProgress = hoverAnim.getValue();

        Animation expandAnimG = groupExpandAnimations.get(group);
        float expandProgress = expandAnimG != null ? expandAnimG.getValue() : (group.isCollapsed() ? 0.0f : 1.0f);

        Color headerBg = MD3Theme.lerp(DropdownTheme.groupBackground(), DropdownTheme.groupBackgroundHover(), hoverProgress);
        float headerRadius = DropdownTheme.BUTTON_RADIUS;
        renderer.roundRect().addRoundRect(headerX, sectionY, headerW, headerH, headerRadius, headerBg);

        String label = group.getDisplayName();
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

        float chevronSize = 2.5f;
        float chevronCenterX = headerX + headerW - DropdownTheme.SETTING_PADDING_X - chevronSize;
        float chevronCenterY = sectionY + headerH * 0.5f;
        renderer.triangle().addChevronTriangle(chevronCenterX, chevronCenterY, chevronSize, expandProgress, DropdownTheme.groupChevron(hoverProgress));

        if (!group.isCollapsed()) {
            float childY = sectionY + headerH + DropdownTheme.SETTING_GAP + DropdownTheme.GROUP_INSET;
            float childX = x + DropdownTheme.SETTING_INDENT + DropdownTheme.GROUP_INSET;
            float childW = width - (DropdownTheme.SETTING_INDENT + DropdownTheme.GROUP_INSET) * 2.0f;
            for (SettingWidget<?> widget : section.widgets()) {
                if (!widget.isVisible()) continue;
                widget.setPosition(childX, childY, childW);
                widget.draw(renderer, mouseX, mouseY);
                childY += widget.getHeight() + DropdownTheme.SETTING_GAP;
            }
        }
    }

    private Animation createGroupAnimation(long duration, float startValue) {
        Animation anim = new Animation(Easing.EASE_OUT_CUBIC, duration);
        anim.setStartValue(startValue);
        return anim;
    }

    private String getAddonLabel() {
        String addonId = module.getAddonId();
        return addonId == null || addonId.isBlank() ? "unknown" : addonId;
    }

    private void drawKeybindButton(DropdownRenderer renderer, int mouseX, int mouseY, float toggle) {
        float btnW = DropdownTheme.KEYBIND_WIDTH;
        float btnH = DropdownTheme.KEYBIND_HEIGHT;
        float btnX = x + width - DropdownTheme.MODULE_PADDING_X - btnW;
        float btnY = y + (DropdownTheme.MODULE_HEIGHT - btnH) * 0.5f;
        float radius = DropdownTheme.KEYBIND_RADIUS;
        boolean btnHovered = isHovered(mouseX, mouseY, btnX, btnY, btnW, btnH);
        keybindHoverAnim.run(btnHovered ? 1.0f : 0.0f);
        float kbHover = keybindHoverAnim.getValue();

        String keyText = listeningKeybind ? "..." : formatCompactKeybind(module.getKeyBind());
        float textScale = keyText.length() >= 3 ? 0.46f : 0.52f;
        float textW = renderer.text().getWidth(keyText, textScale);
        float textH = renderer.text().getHeight(textScale);

        Color surface;
        Color outline;
        Color text = DropdownTheme.keybindText(true);
        if (listeningKeybind) {
            surface = DropdownTheme.keybindSurface(true);
            outline = MD3Theme.withAlpha(MD3Theme.PRIMARY, 200);
        } else {
            Color idleSurface = MD3Theme.SECONDARY_CONTAINER;
            Color activeSurface = MD3Theme.PRIMARY;
            surface = MD3Theme.lerp(idleSurface, activeSurface, toggle);
            surface = MD3Theme.lerp(surface, MD3Theme.TEXT_PRIMARY, kbHover * 0.08f);
            outline = MD3Theme.lerp(MD3Theme.withAlpha(MD3Theme.SECONDARY, 220), MD3Theme.withAlpha(MD3Theme.ON_PRIMARY_CONTAINER, 235), toggle);
            outline = MD3Theme.lerp(outline, MD3Theme.withAlpha(MD3Theme.TEXT_PRIMARY, 245), kbHover * 0.5f);
        }

        renderer.roundRect().addRoundRect(btnX, btnY, btnW, btnH, radius, surface);
        renderer.outline().addOutline(btnX, btnY, btnW, btnH, radius, 0.8f, outline);

        float textX = btnX + (btnW - textW) * 0.5f;
        float textY = btnY + (btnH - textH) * 0.5f - 0.5f;
        renderer.text().addText(keyText, textX, textY, textScale, text);
        if (module.getBindMode() == Module.BindMode.Hold && !listeningKeybind) {
            renderer.rect().addRect(textX, textY + textH + 0.5f, textW, 0.75f, text);
        }
    }

    private String formatCompactKeybind(int keyCode) {
        if (keyCode == KeybindUtils.NONE) return "NONE";
        if (KeybindUtils.isMouseButton(keyCode)) return "M" + (KeybindUtils.decodeMouseButton(keyCode) + 1);
        String label = KeybindUtils.format(keyCode).trim();
        if (label.isEmpty()) return "?";

        String[] parts = label.split("[^A-Za-z0-9]+");
        StringBuilder initials = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty() && Character.isLetterOrDigit(part.charAt(0))) {
                initials.append(Character.toUpperCase(part.charAt(0)));
            }
            if (initials.length() == 3) break;
        }
        if (initials.length() >= 2) return initials.toString();

        String compact = label.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        if (!compact.isEmpty()) return compact.length() > 3 ? compact.substring(0, 3) : compact;
        return label.length() > 3 ? label.substring(0, 3) : label;
    }

    private boolean isKeybindButtonHovered(double mouseX, double mouseY) {
        float btnX = x + width - DropdownTheme.MODULE_PADDING_X - DropdownTheme.KEYBIND_WIDTH;
        float btnY = y + (DropdownTheme.MODULE_HEIGHT - DropdownTheme.KEYBIND_HEIGHT) * 0.5f;
        return isHovered(mouseX, mouseY, btnX, btnY, DropdownTheme.KEYBIND_WIDTH, DropdownTheme.KEYBIND_HEIGHT);
    }

    private void drawHiddenButton(DropdownRenderer renderer, int mouseX, int mouseY) {
        float btnW = 18.0f;
        float btnH = DropdownTheme.KEYBIND_HEIGHT;
        float btnX = x + width - DropdownTheme.MODULE_PADDING_X - DropdownTheme.KEYBIND_WIDTH - 4.0f - btnW;
        float btnY = y + (DropdownTheme.MODULE_HEIGHT - btnH) * 0.5f;
        boolean hovered = isHovered(mouseX, mouseY, btnX, btnY, btnW, btnH);
        if (!module.isHidden()) {
            renderer.roundRect().addRoundRect(btnX, btnY, btnW, btnH, DropdownTheme.KEYBIND_RADIUS,
                    MD3Theme.lerp(MD3Theme.SECONDARY_CONTAINER, MD3Theme.SECONDARY, hovered ? 0.12f : 0.0f));
            String icon = Category.HUD.icon;
            float scale = 0.58f;
            float iconW = renderer.text().getWidth(icon, scale, StaticFontLoader.ICONS);
            float iconH = renderer.text().getHeight(scale, StaticFontLoader.ICONS);
            renderer.text().addText(icon, btnX + (btnW - iconW) * 0.5f, btnY + (btnH - iconH) * 0.5f - 1.0f,
                    scale, MD3Theme.ON_SECONDARY_CONTAINER, StaticFontLoader.ICONS);
        }
        if (hovered) {
            String hint = module.isHidden() ? hiddenComponent.getTranslatedName() : visibleComponent.getTranslatedName();
            float hintScale = 0.42f;
            float hintW = renderer.text().getWidth(hint, hintScale);
            float hintX = Math.max(x + 2.0f, Math.min(btnX + (btnW - hintW) * 0.5f, x + width - hintW - 2.0f));
            renderer.text().addText(hint, hintX, y + DropdownTheme.MODULE_HEIGHT + 1.0f, hintScale, MD3Theme.TEXT_MUTED);
        }
    }

    private boolean isHiddenButtonHovered(double mouseX, double mouseY) {
        float btnW = 18.0f;
        float btnH = DropdownTheme.KEYBIND_HEIGHT;
        float btnX = x + width - DropdownTheme.MODULE_PADDING_X - DropdownTheme.KEYBIND_WIDTH - 4.0f - btnW;
        float btnY = y + (DropdownTheme.MODULE_HEIGHT - btnH) * 0.5f;
        return isHovered(mouseX, mouseY, btnX, btnY, btnW, btnH);
    }

    private boolean isGroupHeaderHovered(double mouseX, double mouseY, float headerX, float headerY) {
        float headerW = width - DropdownTheme.SETTING_INDENT * 2.0f;
        return isHovered(mouseX, mouseY, headerX, headerY, headerW, DropdownTheme.GROUP_HEADER_HEIGHT);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (listeningKeybind) {
            module.setKeyBind(KeybindUtils.encodeMouseButton(button));
            listeningKeybind = false;
            return true;
        }

        if (isHovered(mouseX, mouseY, x, y, width, DropdownTheme.MODULE_HEIGHT)) {
            if (isHiddenButtonHovered(mouseX, mouseY)) {
                module.setHidden(!module.isHidden());
                ConfigManager.INSTANCE.saveNow();
                return true;
            }
            if (isKeybindButtonHovered(mouseX, mouseY)) {
                if (button == 0) {
                    listeningKeybind = true;
                    return true;
                }
                if (button == 2) {
                    module.setBindMode(module.getBindMode() == Module.BindMode.Toggle ? Module.BindMode.Hold : Module.BindMode.Toggle);
                    return true;
                }
            }
            if (button == 0) {
                module.toggle();
                return true;
            }
            if (button == 1) {
                expanded = !expanded;
                return true;
            }
        }

        if (expanded && expandAnim.getValue() > 0.5f) {
            float settingY = y + DropdownTheme.MODULE_HEIGHT + DropdownTheme.SETTING_GAP;
            settingY += DropdownTheme.MODULE_ADDON_INFO_HEIGHT + DropdownTheme.SETTING_GAP;
            for (SettingSection section : sections) {
                if (section.isGroup()) {
                    float headerX = x + DropdownTheme.SETTING_INDENT;
                    if (isGroupHeaderHovered(mouseX, mouseY, headerX, settingY)) {
                        section.group().toggleCollapsed();
                        SoundManager.INSTANCE.playInUi(section.group().isCollapsed() ? SoundKey.SETTINGS_CLOSE : SoundKey.SETTINGS_OPEN);
                        return true;
                    }
                    if (!section.group().isCollapsed()) {
                        for (SettingWidget<?> widget : section.widgets()) {
                            if (!widget.isVisible()) continue;
                            if (widget.mouseClicked(mouseX, mouseY, button)) {
                                return true;
                            }
                        }
                    }
                } else {
                    for (SettingWidget<?> widget : section.widgets()) {
                        if (!widget.isVisible()) continue;
                        if (widget.mouseClicked(mouseX, mouseY, button)) {
                            return true;
                        }
                    }
                }
                settingY += getSectionHeight(section);
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (expanded) {
            for (SettingSection section : sections) {
                for (SettingWidget<?> widget : section.widgets()) {
                    if (!widget.isVisible()) continue;
                    if (widget.mouseReleased(mouseX, mouseY, button)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (listeningKeybind) {
            module.setKeyBind(keyCode == 256 || keyCode == 259 ? KeybindUtils.NONE : keyCode);
            listeningKeybind = false;
            return true;
        }

        if (expanded) {
            for (SettingSection section : sections) {
                for (SettingWidget<?> widget : section.widgets()) {
                    if (!widget.isVisible()) continue;
                    if (widget.keyPressed(keyCode, scanCode, modifiers)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(String typedText) {
        if (expanded) {
            for (SettingSection section : sections) {
                for (SettingWidget<?> widget : section.widgets()) {
                    if (!widget.isVisible()) continue;
                    if (widget.charTyped(typedText)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public Module getModule() {
        return module;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public boolean hasListeningKeybind() {
        if (listeningKeybind) return true;
        for (SettingSection section : sections) {
            for (SettingWidget<?> widget : section.widgets()) {
                if (widget instanceof KeybindWidget kw && kw.isListening()) return true;
            }
        }
        return false;
    }

    public boolean hasFocusedInput() {
        for (SettingSection section : sections) {
            for (SettingWidget<?> widget : section.widgets()) {
                if (widget instanceof StringWidget sw && sw.isFocused()) return true;
                if (widget instanceof IntSliderWidget iw && iw.isFocused()) return true;
                if (widget instanceof DoubleSliderWidget dw && dw.isFocused()) return true;
                if (widget instanceof ColorWidget cw && cw.hasFocusedInput()) return true;
            }
        }
        return false;
    }

    private record SettingSection(@Nullable SettingGroup group, List<SettingWidget<?>> widgets) {
        private boolean isGroup() {
            return group != null;
        }
    }

}
