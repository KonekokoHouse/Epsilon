package com.github.epsilon.gui.dropdown.widget;

import com.github.epsilon.gui.dropdown.DropdownRenderer;
import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.epsilon.settings.impl.DoubleSetting;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.text.DecimalFormat;

public class DoubleSliderWidget extends SettingWidget<DoubleSetting> {

    private static final DecimalFormat FORMAT = new DecimalFormat("#0.00");
    private static final float VALUE_FIELD_WIDTH = 40.0f;
    private static final float VALUE_FIELD_HEIGHT = 12.0f;

    private final DropdownTextField inputField = new DropdownTextField(16, value -> value.matches("[0-9.\\-]"));
    private boolean dragging;

    public DoubleSliderWidget(DoubleSetting setting) {
        super(setting);
    }

    @Override
    public float getHeight() {
        return DropdownTheme.SETTING_HEIGHT + 8.0f;
    }

    @Override
    public void draw(DropdownRenderer renderer, int mouseX, int mouseY) {
        float ratio = (float) ((setting.getValue() - setting.getMin()) / (setting.getMax() - setting.getMin()));
        float sliderRatio = Mth.clamp(ratio, 0.0f, 1.0f);

        renderer.text().addText(setting.getDisplayName(), x + DropdownTheme.SETTING_PADDING_X, y + 1.0f, DropdownTheme.SETTING_TEXT_SCALE, DropdownTheme.settingLabel());

        String valueStr = inputField.isFocused() ? inputField.getText() : formatPlainValue();
        if (!inputField.isFocused() && setting.isPercentageMode()) valueStr += "%";
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
            double range = setting.getMax() - setting.getMin();
            double step = setting.getStep();
            double value = setting.getMin() + Math.round(rawRatio * range / step) * step;
            setting.setValue(Mth.clamp(value, setting.getMin(), setting.getMax()));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            String plainValue = formatPlainValue();
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
            inputField.setText(formatPlainValue());
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

    private String formatPlainValue() {
        return FORMAT.format(setting.getValue());
    }

    private void commitInput() {
        String text = inputField.getText();
        if (text == null || text.isBlank() || "-".equals(text) || ".".equals(text)) {
            inputField.setText(formatPlainValue());
            return;
        }
        try {
            double value = Double.parseDouble(text);
            setting.setValue(Mth.clamp(value, setting.getMin(), setting.getMax()));
        } catch (NumberFormatException ignored) {
        }
        inputField.setText(formatPlainValue());
        inputField.setCursorToEnd();
    }

    private void syncInputValue() {
        String text = inputField.getText();
        if (text == null || text.isBlank() || "-".equals(text) || ".".equals(text)) return;
        try {
            double value = Double.parseDouble(text);
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
