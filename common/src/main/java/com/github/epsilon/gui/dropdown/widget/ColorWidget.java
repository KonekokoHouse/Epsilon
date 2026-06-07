package com.github.epsilon.gui.dropdown.widget;

import com.github.epsilon.gui.dropdown.DropdownRenderer;
import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import net.minecraft.util.Mth;

import java.awt.*;

public class ColorWidget extends SettingWidget<ColorSetting> {

    private final Animation openAnim = new Animation(Easing.EASE_OUT_CUBIC, DropdownTheme.ANIM_EXPAND);
    private boolean opened;
    private boolean pickingSB;
    private boolean pickingHue;
    private boolean pickingAlpha;

    public ColorWidget(ColorSetting setting) {
        super(setting);
    }

    @Override
    public float getHeight() {
        openAnim.run(opened ? 1.0f : 0.0f);
        float expandedHeight = DropdownTheme.COLOR_PICKER_HEIGHT + DropdownTheme.COLOR_HUE_HEIGHT + (setting.isAllowAlpha() ? DropdownTheme.COLOR_ALPHA_HEIGHT + 4.0f : 0.0f) + 10.0f;
        return DropdownTheme.SETTING_HEIGHT + expandedHeight * openAnim.getValue();
    }

    @Override
    public void draw(DropdownRenderer renderer, int mouseX, int mouseY) {
        openAnim.run(opened ? 1.0f : 0.0f);
        float t = openAnim.getValue();

        renderer.text().addText(setting.getDisplayName(), x + DropdownTheme.SETTING_PADDING_X, y + (DropdownTheme.SETTING_HEIGHT - renderer.text().getHeight(DropdownTheme.SETTING_TEXT_SCALE)) * 0.5f, DropdownTheme.SETTING_TEXT_SCALE, DropdownTheme.settingLabel());

        float previewX = x + width - DropdownTheme.SETTING_PADDING_X - DropdownTheme.COLOR_PREVIEW_SIZE;
        float previewY = y + (DropdownTheme.SETTING_HEIGHT - DropdownTheme.COLOR_PREVIEW_SIZE) * 0.5f;
        renderer.roundRect().addRoundRect(previewX, previewY, DropdownTheme.COLOR_PREVIEW_SIZE, DropdownTheme.COLOR_PREVIEW_SIZE, 2.0f, setting.getValue());

        if (t < 0.01f) return;

        Color color = setting.getValue();
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);

        float padX = DropdownTheme.SETTING_PADDING_X;
        float gradX = x + padX;
        float gradY = y + DropdownTheme.SETTING_HEIGHT + 2.0f;
        float gradW = width - padX * 2.0f;
        float gradH = DropdownTheme.COLOR_PICKER_HEIGHT * t;

        Color hueColor = Color.getHSBColor(hsb[0], 1.0f, 1.0f);
        drawSaturationBrightnessPalette(renderer, gradX, gradY, gradW, gradH, hueColor);

        float hueY = gradY + gradH + 3.0f;
        float hueH = DropdownTheme.COLOR_HUE_HEIGHT * t;
        for (int i = 0; i < (int) gradW; i++) {
            Color c = Color.getHSBColor(i / gradW, 1.0f, 1.0f);
            renderer.rect().addRect(gradX + i, hueY, 1.0f, hueH, c);
        }
        drawSliderPicker(renderer, gradX + gradW * hsb[0], hueY, hueH);

        if (setting.isAllowAlpha()) {
            float alphaY = hueY + hueH + 4.0f;
            float alphaH = DropdownTheme.COLOR_ALPHA_HEIGHT * t;
            for (int i = 0; i < (int) gradW; i++) {
                float a = i / gradW;
                Color c = new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) (a * 255));
                renderer.rect().addRect(gradX + i, alphaY, 1.0f, alphaH, c);
            }
            drawSliderPicker(renderer, gradX + gradW * (color.getAlpha() / 255.0f), alphaY, alphaH);

            if (pickingAlpha) {
                float newAlpha = Mth.clamp((mouseX - gradX) / gradW, 0.0f, 1.0f);
                Color current = setting.getValue();
                setting.setValue(new Color(current.getRed(), current.getGreen(), current.getBlue(), (int) (newAlpha * 255)));
            }
        }

        if (pickingSB) {
            float newSat = Mth.clamp((mouseX - gradX) / gradW, 0.0f, 1.0f);
            float newBri = 1.0f - Mth.clamp((mouseY - gradY) / (DropdownTheme.COLOR_PICKER_HEIGHT * t), 0.0f, 1.0f);
            Color newColor = Color.getHSBColor(hsb[0], newSat, newBri);
            if (setting.isAllowAlpha()) {
                newColor = new Color(newColor.getRed(), newColor.getGreen(), newColor.getBlue(), color.getAlpha());
            }
            setting.setValue(newColor);
        }

        if (pickingHue) {
            float newHue = Mth.clamp((mouseX - gradX) / gradW, 0.0f, 1.0f);
            Color newColor = Color.getHSBColor(newHue, hsb[1], hsb[2]);
            if (setting.isAllowAlpha()) {
                newColor = new Color(newColor.getRed(), newColor.getGreen(), newColor.getBlue(), color.getAlpha());
            }
            setting.setValue(newColor);
        }

        float pickerCx = gradX + gradW * hsb[1];
        float pickerCy = gradY + gradH * (1.0f - hsb[2]);
        renderer.rect().addRect(pickerCx - 3.0f, pickerCy - 3.0f, 6.0f, 6.0f, new Color(0, 0, 0, 135));
        renderer.rect().addRect(pickerCx - 2.0f, pickerCy - 2.0f, 4.0f, 4.0f, Color.WHITE);
    }

    private void drawSaturationBrightnessPalette(DropdownRenderer renderer, float x, float y, float width, float height, Color hueColor) {
        renderer.rect().addRect(x - 1.0f, y - 1.0f, width + 2.0f, height + 2.0f, new Color(255, 255, 255, 45));
        renderer.rect().addRectGradient(x, y, width, height, Color.WHITE, Color.WHITE, hueColor, hueColor);
        renderer.rect().addRectGradient(x, y, width, height, new Color(0, 0, 0, 0), Color.BLACK, Color.BLACK, new Color(0, 0, 0, 0));
    }

    private void drawSliderPicker(DropdownRenderer renderer, float centerX, float y, float height) {
        float pickerW = 3.0f;
        renderer.rect().addRect(centerX - pickerW * 0.5f - 1.0f, y - 2.0f, pickerW + 2.0f, height + 4.0f, new Color(0, 0, 0, 145));
        renderer.rect().addRect(centerX - pickerW * 0.5f, y - 1.0f, pickerW, height + 2.0f, Color.WHITE);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float previewX = x + width - DropdownTheme.SETTING_PADDING_X - DropdownTheme.COLOR_PREVIEW_SIZE;
        float previewY = y + (DropdownTheme.SETTING_HEIGHT - DropdownTheme.COLOR_PREVIEW_SIZE) * 0.5f;
        if ((button == 0 || button == 1) && isHovered(mouseX, mouseY, previewX - 2, previewY - 2, DropdownTheme.COLOR_PREVIEW_SIZE + 4, DropdownTheme.COLOR_PREVIEW_SIZE + 4)) {
            opened = !opened;
            return true;
        }

        if (button != 0) return false;

        if (!opened || openAnim.getValue() < 0.5f) return false;

        float padX = DropdownTheme.SETTING_PADDING_X;
        float gradX = x + padX;
        float gradY = y + DropdownTheme.SETTING_HEIGHT + 2.0f;
        float gradW = width - padX * 2.0f;
        float gradH = DropdownTheme.COLOR_PICKER_HEIGHT * openAnim.getValue();
        float hueY = gradY + gradH + 3.0f;
        float hueH = DropdownTheme.COLOR_HUE_HEIGHT * openAnim.getValue();

        if (isHovered(mouseX, mouseY, gradX, gradY, gradW, gradH)) {
            pickingSB = true;
            return true;
        }
        if (isHovered(mouseX, mouseY, gradX, hueY, gradW, hueH)) {
            pickingHue = true;
            return true;
        }
        if (setting.isAllowAlpha()) {
            float alphaY = hueY + hueH + 4.0f;
            float alphaH = DropdownTheme.COLOR_ALPHA_HEIGHT * openAnim.getValue();
            if (isHovered(mouseX, mouseY, gradX, alphaY, gradW, alphaH)) {
                pickingAlpha = true;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && (pickingSB || pickingHue || pickingAlpha)) {
            pickingSB = false;
            pickingHue = false;
            pickingAlpha = false;
            return true;
        }
        return false;
    }

}
