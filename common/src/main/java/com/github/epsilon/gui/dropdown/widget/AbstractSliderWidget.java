package com.github.epsilon.gui.dropdown.widget;

import com.github.epsilon.gui.dropdown.DropdownRenderer;
import com.github.epsilon.gui.dropdown.DropdownScreen;
import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.epsilon.settings.Setting;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.function.Predicate;

public abstract class AbstractSliderWidget<S extends Setting<T>, T extends Number> extends SettingWidget<S> {

    protected static final float VALUE_TEXT_SCALE = 0.46f;
    protected static final float VALUE_TEXT_Y_OFFSET = 3.0f;
    protected static final float EDITOR_Y_OFFSET = 5.0f;

    protected final DropdownTextField inputField;
    protected boolean dragging;
    private int sessionId = -1;

    public AbstractSliderWidget(S setting, int maxInputLength, Predicate<String> inputFilter) {
        super(setting);
        this.inputField = new DropdownTextField(maxInputLength, inputFilter);
    }

    @Override
    public float getHeight() {
        return DropdownTheme.SETTING_HEIGHT + 15.0f;
    }

    @Override
    public void draw(DropdownRenderer renderer, int mouseX, int mouseY) {
        syncSessionState();

        float ratio = getRatio();
        float sliderRatio = Mth.clamp(ratio, 0.0f, 1.0f);

        renderer.text().addText(setting.getDisplayName(), x + DropdownTheme.SETTING_PADDING_X, y + 1.0f, DropdownTheme.SETTING_TEXT_SCALE, DropdownTheme.settingLabel());

        float trackX = getTrackX();
        float trackY = getTrackY();
        float trackW = getTrackWidth();
        float trackH = DropdownTheme.SLIDER_HEIGHT;

        boolean editing = inputField.isFocused();
        if (editing) {
            inputField.draw(renderer, getEditorX(), getEditorY(), getEditorWidth(), getEditorHeight(), mouseX, mouseY, formatPlainValue(), DropdownTheme.SETTING_TEXT_SCALE);
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

            if (dragging && mouseX >= 0) {
                float rawRatio = Mth.clamp((float) (mouseX - trackX) / trackW, 0.0f, 1.0f);
                updateValueFromRatio(rawRatio);
            }
        }

        if (!editing) {
            drawValueLabels(renderer, trackX, trackY, trackW);
        }
    }

    protected abstract float getRatio();

    protected abstract void updateValueFromRatio(float rawRatio);

    protected abstract String formatValue(T value);

    protected abstract String formatPlainValue();

    protected abstract void commitInput();

    protected abstract void syncInputValue();

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        syncSessionState();
        if (button == 1) {
            if (isEditorHitboxHovered(mouseX, mouseY)) {
                inputField.setText(formatPlainValue());
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

    protected void drawValueLabels(DropdownRenderer renderer, float trackX, float trackY, float trackW) {
        String minValue = formatValue(getMin());
        String currentValue = formatValue(setting.getValue());
        String maxValue = formatValue(getMax());
        float textY = trackY + DropdownTheme.SLIDER_HEIGHT + VALUE_TEXT_Y_OFFSET;

        renderer.text().addText(minValue, trackX, textY, VALUE_TEXT_SCALE, DropdownTheme.settingLabelMuted());

        float currentWidth = renderer.text().getWidth(currentValue, VALUE_TEXT_SCALE);
        renderer.text().addText(currentValue, trackX + (trackW - currentWidth) * 0.5f, textY, VALUE_TEXT_SCALE, DropdownTheme.settingLabel());

        float maxWidth = renderer.text().getWidth(maxValue, VALUE_TEXT_SCALE);
        renderer.text().addText(maxValue, trackX + trackW - maxWidth, textY, VALUE_TEXT_SCALE, DropdownTheme.settingLabelMuted());
    }

    protected abstract T getMin();

    protected abstract T getMax();

    protected float getTrackX() {
        return x + DropdownTheme.SETTING_PADDING_X;
    }

    protected float getTrackY() {
        return y + DropdownTheme.SETTING_HEIGHT;
    }

    protected float getTrackWidth() {
        return width - DropdownTheme.SETTING_PADDING_X * 2.0f;
    }

    protected float getEditorX() {
        return getTrackX();
    }

    protected float getEditorY() {
        return getTrackY() - EDITOR_Y_OFFSET;
    }

    protected float getEditorWidth() {
        return getTrackWidth();
    }

    protected float getEditorHeight() {
        return DropdownTheme.INPUT_HEIGHT;
    }

    protected boolean isEditorBoundsHovered(double mouseX, double mouseY) {
        return isHovered(mouseX, mouseY, getEditorX(), getEditorY(), getEditorWidth(), getEditorHeight());
    }

    protected boolean isEditorHitboxHovered(double mouseX, double mouseY) {
        return isHovered(mouseX, mouseY, getTrackX(), getEditorY(), getTrackWidth(), DropdownTheme.INPUT_HEIGHT);
    }

}
