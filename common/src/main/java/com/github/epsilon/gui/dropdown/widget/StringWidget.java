package com.github.epsilon.gui.dropdown.widget;

import com.github.epsilon.gui.dropdown.DropdownDrawContext;
import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.epsilon.settings.impl.StringSetting;

public class StringWidget extends SettingWidget<StringSetting> {

    private final DropdownTextField inputField = new DropdownTextField(100);

    public StringWidget(StringSetting setting) {
        super(setting);
    }

    @Override
    public float getHeight() {
        return DropdownTheme.SETTING_HEIGHT + DropdownTheme.INPUT_HEIGHT + 2.0f;
    }

    @Override
    public void draw(DropdownDrawContext renderer, int mouseX, int mouseY) {
        renderer.text(setting.getDisplayName(), DropdownTheme.SETTING_PADDING_X, 1.0f, DropdownTheme.SETTING_TEXT_SCALE, DropdownTheme.settingLabel());

        float fieldX = DropdownTheme.SETTING_PADDING_X;
        float fieldY = DropdownTheme.SETTING_HEIGHT;
        float fieldW = width - DropdownTheme.SETTING_PADDING_X * 2.0f;
        float fieldH = DropdownTheme.INPUT_HEIGHT;

        if (!inputField.isFocused() && !inputField.getText().equals(setting.getValue())) {
            inputField.setText(setting.getValue());
        }
        inputField.draw(renderer, fieldX, fieldY, fieldW, fieldH, mouseX, mouseY, "...", DropdownTheme.SETTING_TEXT_SCALE);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float fieldX = absoluteX(DropdownTheme.SETTING_PADDING_X);
        float fieldY = absoluteY(DropdownTheme.SETTING_HEIGHT);
        float fieldW = width - DropdownTheme.SETTING_PADDING_X * 2.0f;
        float fieldH = DropdownTheme.INPUT_HEIGHT;

        if (button == 0 && inputField.focusIfContains(mouseX, mouseY, fieldX, fieldY, fieldW, fieldH)) {
            inputField.setText(setting.getValue());
            return true;
        }
        if (inputField.isFocused()) {
            syncSetting();
            inputField.blur();
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!inputField.isFocused()) return false;

        if (keyCode == 257 || keyCode == 335) {
            syncSetting();
            inputField.blur();
            return true;
        }
        if (keyCode == 256) {
            inputField.setText(setting.getValue());
            inputField.blur();
            return true;
        }
        if (inputField.keyPressed(keyCode)) {
            syncSetting();
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(String typedText) {
        if (inputField.charTyped(typedText)) {
            syncSetting();
            return true;
        }
        return false;
    }

    public boolean isFocused() {
        return inputField.isFocused();
    }

    private void syncSetting() {
        setting.setValue(inputField.getText());
    }

}
