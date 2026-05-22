package com.github.epsilon.gui.dropdown.widget;

import com.github.epsilon.gui.dropdown.DropdownRenderer;
import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.epsilon.settings.impl.IntSetting;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

public class IntSliderWidget extends SettingWidget<IntSetting> {

    private static final float VALUE_FIELD_WIDTH = 40.0f;
    private static final float VALUE_FIELD_HEIGHT = 12.0f;

    private final DropdownTextField inputField = new DropdownTextField(12, value -> value.matches("[0-9-]"));
    private boolean dragging;

    public IntSliderWidget(IntSetting setting) {
        super(setting);
    }

    @Override
    public float getHeight() {
        return DropdownTheme.SETTING_HEIGHT + 8.0f;
    }

    @Override
    public void draw(DropdownRenderer renderer, int mouseX, int mouseY) {
        float ratio = (float) (setting.getValue() - setting.getMin()) / (float) (setting.getMax() - setting.getMin());
        float sliderRatio = Mth.clamp(ratio, 0.0f, 1.0f);

        renderer.text().addText(setting.getDisplayName(), x + DropdownTheme.SETTING_PADDING_X, y + 1.0f, DropdownTheme.SETTING_TEXT_SCALE, DropdownTheme.settingLabel());

        String valueStr = inputField.isFocused()
                ? inputField.getText()
                : (setting.isPercentageMode() ? setting.getValue() + "%" : String.valueOf(setting.getValue()));
        if (!inputField.isFocused() && !inputField.getText().equals(valueStr)) {
            inputField.setText(valueStr);
        }
        inputField.draw(renderer, getFieldX(), getFieldY(), VALUE_FIELD_WIDTH, VALUE_FIELD_HEIGHT, mouseX, mouseY, valueStr, DropdownTheme.SETTING_TEXT_SCALE);

        float trackX = x + DropdownTheme.SETTING_PADDING_X;
        float trackY = y + DropdownTheme.SETTING_HEIGHT;
        float trackW = width - DropdownTheme.SETTING_PADDING_X * 2.0f;
        float trackH = DropdownTheme.SLIDER_HEIGHT;

        renderer.roundRect().addRoundRect(trackX, trackY, trackW, trackH, DropdownTheme.SLIDER_RADIUS, DropdownTheme.sliderTrack());

        float activeW = trackW * sliderRatio;
        if (activeW > 0.5f) {
            renderer.roundRect().addRoundRect(trackX, trackY, activeW, trackH, DropdownTheme.SLIDER_RADIUS, DropdownTheme.sliderActive());
        }

        float knobX = trackX + trackW * sliderRatio;
        float knobY = trackY + trackH * 0.5f;
        float kr = DropdownTheme.SLIDER_KNOB_RADIUS;
        renderer.roundRect().addRoundRect(knobX - kr, knobY - kr, kr * 2.0f, kr * 2.0f, kr, DropdownTheme.sliderKnob());

        if (dragging) {
            float rawRatio = Mth.clamp((float) (mouseX - trackX) / trackW, 0.0f, 1.0f);
            int range = setting.getMax() - setting.getMin();
            int step = setting.getStep();
            int value = setting.getMin() + Math.round(rawRatio * range / step) * step;
            setting.setValue(Mth.clamp(value, setting.getMin(), setting.getMax()));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            String plainValue = Integer.toString(setting.getValue());
            if (inputField.focusIfContains(mouseX, mouseY, getFieldX(), getFieldY(), VALUE_FIELD_WIDTH, VALUE_FIELD_HEIGHT)) {
                inputField.setText(plainValue);
                inputField.setCursorToEnd();
                dragging = false;
                return true;
            }
            if (inputField.isFocused()) {
                commitInput();
                inputField.blur();
            }
            float trackX = x + DropdownTheme.SETTING_PADDING_X;
            float trackY = y + DropdownTheme.SETTING_HEIGHT - 3.0f;
            float trackW = width - DropdownTheme.SETTING_PADDING_X * 2.0f;
            if (isHovered(mouseX, mouseY, trackX, trackY, trackW, DropdownTheme.SLIDER_HEIGHT + 6.0f)) {
                dragging = true;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragging) {
            dragging = false;
            return true;
        }
        if (button == 0 && inputField.isFocused()) {
            if (isHovered(mouseX, mouseY, getFieldX(), getFieldY(), VALUE_FIELD_WIDTH, VALUE_FIELD_HEIGHT)) {
                return true;
            }
            commitInput();
            inputField.blur();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!inputField.isFocused()) return false;
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            commitInput();
            inputField.blur();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            inputField.setText(Integer.toString(setting.getValue()));
            inputField.blur();
            return true;
        }
        if (inputField.keyPressed(keyCode)) {
            syncInputValue();
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(String typedText) {
        if (inputField.charTyped(typedText)) {
            syncInputValue();
            return true;
        }
        return false;
    }

    public boolean isFocused() {
        return inputField.isFocused();
    }

    private void commitInput() {
        String text = inputField.getText();
        if (text == null || text.isBlank() || "-".equals(text)) {
            inputField.setText(Integer.toString(setting.getValue()));
            return;
        }
        try {
            int value = Integer.parseInt(text);
            setting.setValue(Mth.clamp(value, setting.getMin(), setting.getMax()));
        } catch (NumberFormatException ignored) {
        }
        inputField.setText(Integer.toString(setting.getValue()));
        inputField.setCursorToEnd();
    }

    private void syncInputValue() {
        String text = inputField.getText();
        if (text == null || text.isBlank() || "-".equals(text)) return;
        try {
            int value = Integer.parseInt(text);
            setting.setValue(Mth.clamp(value, setting.getMin(), setting.getMax()));
        } catch (NumberFormatException ignored) {
        }
    }

    private float getFieldX() {
        return x + width - DropdownTheme.SETTING_PADDING_X - VALUE_FIELD_WIDTH;
    }

    private float getFieldY() {
        return y + 2.0f;
    }

}
