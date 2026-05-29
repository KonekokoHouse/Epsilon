package com.github.epsilon.gui.dropdown.widget;

import com.github.epsilon.settings.impl.IntSetting;
import net.minecraft.util.Mth;

public class IntSliderWidget extends AbstractSliderWidget<IntSetting, Integer> {

    public IntSliderWidget(IntSetting setting) {
        super(setting, 12, value -> value.matches("[0-9-]"));
    }

    @Override
    protected float getRatio() {
        return (float) (setting.getValue() - setting.getMin()) / (float) (setting.getMax() - setting.getMin());
    }

    @Override
    protected void updateValueFromRatio(float rawRatio) {
        int range = setting.getMax() - setting.getMin();
        int step = setting.getStep();
        int value = setting.getMin() + Math.round(rawRatio * range / step) * step;
        setting.setValue(Mth.clamp(value, setting.getMin(), setting.getMax()));
    }

    @Override
    protected String formatValue(Integer value) {
        return setting.isPercentageMode() ? value + "%" : Integer.toString(value);
    }

    @Override
    protected String formatPlainValue() {
        return Integer.toString(setting.getValue());
    }

    @Override
    protected void commitInput() {
        String text = inputField.getText();
        if (text == null || text.isBlank() || "-".equals(text)) {
            inputField.setText(formatPlainValue());
            return;
        }
        try {
            int value = Integer.parseInt(text);
            setting.setValue(Mth.clamp(value, setting.getMin(), setting.getMax()));
        } catch (NumberFormatException ignored) {
        }
        inputField.setText(formatPlainValue());
        inputField.setCursorToEnd();
    }

    @Override
    protected void syncInputValue() {
        String text = inputField.getText();
        if (text == null || text.isBlank() || "-".equals(text)) return;
        try {
            int value = Integer.parseInt(text);
            setting.setValue(Mth.clamp(value, setting.getMin(), setting.getMax()));
        } catch (NumberFormatException ignored) {
        }
    }

    @Override
    protected Integer getMin() {
        return setting.getMin();
    }

    @Override
    protected Integer getMax() {
        return setting.getMax();
    }

}
