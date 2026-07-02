package com.github.epsilon.gui.dropdown.widget;

import com.github.epsilon.gui.dropdown.DropdownDrawContext;
import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.epsilon.gui.panel.MD3Theme;
import com.github.epsilon.settings.Setting;

import java.util.function.Function;

/**
 * A read-only widget that displays a setting's name and its formatted value.
 */
public class SimpleValueWidget extends SettingWidget<Setting<?>> {

    private final Function<Setting<?>, String> valueFormatter;

    public SimpleValueWidget(Setting<?> setting, Function<Setting<?>, String> valueFormatter) {
        super(setting);
        this.valueFormatter = valueFormatter;
    }

    @Override
    public float getHeight() {
        return DropdownTheme.SETTING_HEIGHT;
    }

    @Override
    public void draw(DropdownDrawContext renderer, int mouseX, int mouseY) {
        float labelY = (getHeight() - renderer.textHeight(DropdownTheme.SETTING_TEXT_SCALE)) * 0.5f;
        renderer.text(setting.getDisplayName(), DropdownTheme.SETTING_PADDING_X, labelY,
                DropdownTheme.SETTING_TEXT_SCALE, DropdownTheme.settingLabel());

        String valueText = valueFormatter.apply(setting);
        float valueScale = 0.50f;
        float valueW = renderer.textWidth(valueText, valueScale);
        float valueX = x + width - DropdownTheme.SETTING_PADDING_X - valueW;
        float valueY = labelY;
        renderer.text(valueText, valueX, valueY, valueScale, MD3Theme.TEXT_SECONDARY);
    }

}
