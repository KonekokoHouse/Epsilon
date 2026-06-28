package com.github.epsilon.gui.dropdown.widget;

import com.github.epsilon.gui.dropdown.DropdownDrawContext;
import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.epsilon.settings.impl.ButtonSetting;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;

public class ButtonWidget extends SettingWidget<ButtonSetting> {

    private final Animation hoverAnim = new Animation(Easing.EASE_OUT_CUBIC, DropdownTheme.ANIM_HOVER);

    public ButtonWidget(ButtonSetting setting) {
        super(setting);
    }

    @Override
    public float getHeight() {
        return DropdownTheme.SETTING_HEIGHT + 2.0f;
    }

    @Override
    public void draw(DropdownDrawContext renderer, int mouseX, int mouseY) {
        float btnX = x + DropdownTheme.SETTING_PADDING_X;
        float btnY = y + 1.0f;
        float btnW = width - DropdownTheme.SETTING_PADDING_X * 2.0f;
        float btnH = DropdownTheme.BUTTON_HEIGHT;

        boolean hovered = isHovered(mouseX, mouseY, btnX, btnY, btnW, btnH);
        hoverAnim.run(hovered ? 1.0f : 0.0f);

        renderer.roundRect().addRoundRect(btnX, btnY, btnW, btnH, DropdownTheme.BUTTON_RADIUS, DropdownTheme.buttonSurface(hoverAnim.getValue()));

        String label = setting.getDisplayName();
        float textW = renderer.text().getWidth(label, DropdownTheme.SETTING_TEXT_SCALE);
        float textY = btnY + (btnH - renderer.text().getHeight(DropdownTheme.SETTING_TEXT_SCALE)) * 0.5f;
        renderer.text().addText(label, btnX + (btnW - textW) * 0.5f, textY, DropdownTheme.SETTING_TEXT_SCALE, DropdownTheme.buttonText());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        float btnX = x + DropdownTheme.SETTING_PADDING_X;
        float btnY = y + 1.0f;
        float btnW = width - DropdownTheme.SETTING_PADDING_X * 2.0f;
        float btnH = DropdownTheme.BUTTON_HEIGHT;

        if (isHovered(mouseX, mouseY, btnX, btnY, btnW, btnH)) {
            Runnable action = setting.getValue();
            if (action != null) {
                action.run();
            }
            return true;
        }
        return false;
    }

}
