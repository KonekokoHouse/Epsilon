package com.github.epsilon.gui.dropdown.widget;

import com.github.epsilon.gui.dropdown.DropdownRenderer;
import com.github.epsilon.gui.dropdown.DropdownScreen;
import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.epsilon.gui.panel.MD3Theme;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.managers.impl.sound.SoundKey;
import com.github.epsilon.settings.impl.BlockListSetting;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;

import java.awt.*;

public class BlockListWidget extends SettingWidget<BlockListSetting> {

    private static final float FIELD_HEIGHT = 14.0f;
    private final Animation hoverAnim = new Animation(Easing.EASE_OUT_CUBIC, DropdownTheme.ANIM_HOVER);

    public BlockListWidget(BlockListSetting setting) {
        super(setting);
    }

    @Override
    public float getHeight() {
        return DropdownTheme.SETTING_HEIGHT - 1.0f + FIELD_HEIGHT;
    }

    @Override
    public void draw(DropdownRenderer renderer, int mouseX, int mouseY) {
        boolean hovered = isFieldHovered(mouseX, mouseY);
        hoverAnim.run(hovered ? 1.0f : 0.0f);

        renderer.text().addText(setting.getDisplayName(), x + DropdownTheme.SETTING_PADDING_X, y + 1.0f, DropdownTheme.SETTING_TEXT_SCALE, DropdownTheme.settingLabel());

        float fieldX = getFieldX();
        float fieldY = getFieldY();
        float fieldW = getFieldWidth();
        float hover = hoverAnim.getValue();
        Color background = MD3Theme.lerp(MD3Theme.SECONDARY_CONTAINER, MD3Theme.PRIMARY_CONTAINER, hover * 0.6f);
        Color outline = MD3Theme.lerp(MD3Theme.withAlpha(MD3Theme.OUTLINE, 90), MD3Theme.PRIMARY, hover);
        String label = setting.size() + " blocks";
        float labelScale = 0.50f;
        float iconScale = 0.54f;
        float labelY = centeredTextY(renderer, fieldY, FIELD_HEIGHT, labelScale);
        float iconY = centeredTextY(renderer, fieldY, FIELD_HEIGHT, iconScale);

        renderer.roundRect().addRoundRect(fieldX, fieldY, fieldW, FIELD_HEIGHT, DropdownTheme.INPUT_RADIUS, background);
        renderer.outline().addOutline(fieldX, fieldY, fieldW, FIELD_HEIGHT, DropdownTheme.INPUT_RADIUS, 0.7f, outline);
        renderer.text().addText(label, fieldX + 6.0f, labelY, labelScale, MD3Theme.ON_SECONDARY_CONTAINER);
        renderer.text().addText("+", fieldX + fieldW - 12.0f, iconY, iconScale, MD3Theme.ON_SECONDARY_CONTAINER);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !isFieldHovered(mouseX, mouseY)) {
            return false;
        }
        DropdownScreen.INSTANCE.openBlockListPopup(setting);
        Managers.SOUND.playInUi(SoundKey.SETTINGS_OPEN);
        return true;
    }

    private boolean isFieldHovered(double mouseX, double mouseY) {
        return isHovered(mouseX, mouseY, getFieldX(), getFieldY(), getFieldWidth(), FIELD_HEIGHT);
    }

    private float getFieldX() {
        return x + DropdownTheme.SETTING_PADDING_X;
    }

    private float getFieldY() {
        return y + DropdownTheme.SETTING_HEIGHT - 1.0f;
    }

    private float getFieldWidth() {
        return width - DropdownTheme.SETTING_PADDING_X * 2.0f;
    }

    private float centeredTextY(DropdownRenderer renderer, float boxY, float boxHeight, float scale) {
        return boxY + (boxHeight - renderer.text().getHeight(scale)) * 0.5f;
    }

}
