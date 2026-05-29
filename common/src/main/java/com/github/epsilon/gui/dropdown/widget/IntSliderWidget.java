package com.github.epsilon.gui.dropdown.widget;

import com.github.epsilon.gui.dropdown.DropdownRenderer;
import com.github.epsilon.gui.dropdown.DropdownScreen;
import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.epsilon.settings.impl.IntSetting;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

public class IntSliderWidget extends SettingWidget<IntSetting> {

    private static final float VALUE_TEXT_SCALE = 0.46f;
    private static final float VALUE_TEXT_Y_OFFSET = 3.0f;
    private static final float EDITOR_Y_OFFSET = 5.0f;

    private final DropdownTextField inputField = new DropdownTextField(12, value -> value.matches("[0-9-]"));
    private boolean dragging;
    private int sessionId = -1;

    public IntSliderWidget(IntSetting setting) {
        super(setting);
    }

    @Override
    public float getHeight() {
        return DropdownTheme.SETTING_HEIGHT + 15.0f;
    }

    @Override
    public void draw(DropdownRenderer renderer, int mouseX, int mouseY) {
        syncSessionState();
        float ratio = (float) (setting.getValue() - setting.getMin()) / (float) (setting.getMax() - setting.getMin());
        float sliderRatio = Mth.clamp(ratio, 0.0f, 1.0f);

        renderer.text().addText(setting.getDisplayName(), x + DropdownTheme.SETTING_PADDING_X, y + 1.0f, DropdownTheme.SETTING_TEXT_SCALE, DropdownTheme.settingLabel());

        float trackX = getTrackX();
        float trackY = getTrackY();
        float trackW = getTrackWidth();
        float trackH = DropdownTheme.SLIDER_HEIGHT;

        boolean editing = inputField.isFocused();
        if (editing) {
            inputField.draw(renderer, getEditorX(), getEditorY(), getEditorWidth(), getEditorHeight(), mouseX, mouseY, Integer.toString(setting.getValue()), DropdownTheme.SETTING_TEXT_SCALE);
        } else {
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

        if (!editing) {
            drawValueLabels(renderer, trackX, trackY, trackW);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        syncSessionState();
        if (button == 1) {
            String plainValue = Integer.toString(setting.getValue());
            if (isEditorHitboxHovered(mouseX, mouseY)) {
                inputField.setText(plainValue);
                inputField.focusIfContains(mouseX, mouseY, getEditorX(), getEditorY(), getEditorWidth(), getEditorHeight());
                inputField.setCursorToEnd();
                dragging = false;
                return true;
            }
        }
        if (button == 0) {
            if (inputField.isFocused()) {
                if (isEditorBoundsHovered(mouseX, mouseY)) {
                    return true;
                }
                commitInput();
                inputField.blur();
            }
            if (isEditorHitboxHovered(mouseX, mouseY)) {
                dragging = true;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        syncSessionState();
        if (button == 0 && dragging) {
            dragging = false;
            return true;
        }
        if (inputField.isFocused()) {
            if (isEditorBoundsHovered(mouseX, mouseY)) {
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
        syncSessionState();
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
        syncSessionState();
        if (inputField.charTyped(typedText)) {
            syncInputValue();
            return true;
        }
        return false;
    }

    public boolean isFocused() {
        return inputField.isFocused();
    }

    private void syncSessionState() {
        int currentSessionId = DropdownScreen.INSTANCE.getSessionId();
        if (sessionId == currentSessionId) {
            return;
        }
        sessionId = currentSessionId;
        dragging = false;
        inputField.blur();
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

    private void drawValueLabels(DropdownRenderer renderer, float trackX, float trackY, float trackW) {
        String minValue = formatValue(setting.getMin());
        String currentValue = formatValue(setting.getValue());
        String maxValue = formatValue(setting.getMax());
        float textY = trackY + DropdownTheme.SLIDER_HEIGHT + VALUE_TEXT_Y_OFFSET;

        renderer.text().addText(minValue, trackX, textY, VALUE_TEXT_SCALE, DropdownTheme.settingLabelMuted());

        float currentWidth = renderer.text().getWidth(currentValue, VALUE_TEXT_SCALE);
        renderer.text().addText(currentValue, trackX + (trackW - currentWidth) * 0.5f, textY, VALUE_TEXT_SCALE, DropdownTheme.settingLabel());

        float maxWidth = renderer.text().getWidth(maxValue, VALUE_TEXT_SCALE);
        renderer.text().addText(maxValue, trackX + trackW - maxWidth, textY, VALUE_TEXT_SCALE, DropdownTheme.settingLabelMuted());
    }

    private String formatValue(int value) {
        return setting.isPercentageMode() ? value + "%" : Integer.toString(value);
    }

    private float getTrackX() {
        return x + DropdownTheme.SETTING_PADDING_X;
    }

    private float getTrackY() {
        return y + DropdownTheme.SETTING_HEIGHT;
    }

    private float getTrackWidth() {
        return width - DropdownTheme.SETTING_PADDING_X * 2.0f;
    }

    private float getEditorX() {
        return getTrackX();
    }

    private float getEditorY() {
        return getTrackY() - EDITOR_Y_OFFSET;
    }

    private float getEditorWidth() {
        return getTrackWidth();
    }

    private float getEditorHeight() {
        return DropdownTheme.INPUT_HEIGHT;
    }

    private boolean isEditorBoundsHovered(double mouseX, double mouseY) {
        return isHovered(mouseX, mouseY, getEditorX(), getEditorY(), getEditorWidth(), getEditorHeight());
    }

    private boolean isEditorHitboxHovered(double mouseX, double mouseY) {
        return isHovered(mouseX, mouseY, getTrackX(), getEditorY(), getTrackWidth(), DropdownTheme.INPUT_HEIGHT);
    }

}
