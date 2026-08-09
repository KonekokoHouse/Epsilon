package com.github.epsilon.gui.dropdown.widget;

import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.epsilon.gui.theme.MD3Theme;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.managers.impl.sound.SoundKey;
import com.github.epsilon.settings.impl.ChoiceSetting;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import com.github.slmpc.lumingraphics.ui.text.UiTextMetrics;
import com.github.slmpc.lumingraphics.ui.tree.UiTree;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.List;

public final class ChoiceWidget extends SettingWidget<ChoiceSetting> {
    private static final float FIELD_HEIGHT = 14.0f;
    private static final float FIELD_RADIUS = 5.0f;
    private static final float FIELD_TEXT_SCALE = 0.50f;
    private static final float FIELD_TEXT_PADDING_X = 6.0f;
    private static final float LIST_GAP_Y = 3.0f;
    private static final float LIST_PADDING_Y = 2.0f;
    private static final float OPTION_HEIGHT = 12.0f;
    private static final float OPTION_GAP = 1.0f;
    private static final float OPTION_TEXT_SCALE = 0.48f;

    private final Animation expandAnim = new Animation(Easing.DECELERATE, DropdownTheme.ANIM_EXPAND);
    private final Animation hoverAnim = new Animation(Easing.EASE_OUT_CUBIC, DropdownTheme.ANIM_HOVER);
    private boolean expanded;

    public ChoiceWidget(ChoiceSetting setting) {
        super(setting);
    }

    @Override
    public float getHeight() {
        expandAnim.run(shouldExpand() ? 1.0f : 0.0f);
        return getCollapsedHeight() + getExpandedTotalHeight() * expandAnim.getValue();
    }

    @Override
    public void draw(UiTree.Scope scope, UiTextMetrics textMetrics, int mouseX, int mouseY) {
        float expand = updateExpandProgress();
        float hover = updateHoverProgress(isFieldHovered(mouseX, mouseY));
        float fieldX = getLocalFieldX();
        float fieldY = getLocalFieldY();
        float fieldW = getFieldWidth();
        scope.text(setting.getDisplayName(), DropdownTheme.SETTING_PADDING_X, 1.0f,
                DropdownTheme.SETTING_TEXT_SCALE, DropdownTheme.settingLabel());
        drawCurrentValueField(scope, textMetrics, fieldX, fieldY, fieldW, hover, expand);
        if (expand > 0.001f && getHiddenChoiceCount() > 0) {
            drawExpandedOptions(scope, textMetrics, mouseX, mouseY, fieldX, fieldW, expand);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isFieldHovered(mouseX, mouseY)) return handleFieldClick(button);
        if (expanded) return handleExpandedClick(mouseX, mouseY);
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (expanded && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            expanded = false;
            Managers.SOUND.playInUi(SoundKey.SETTINGS_CLOSE);
            return true;
        }
        return false;
    }

    private void drawCurrentValueField(UiTree.Scope scope, UiTextMetrics textMetrics, float fieldX, float fieldY,
                                       float fieldW, float hover, float expand) {
        Color background = MD3Theme.filledFieldSurface(expanded, hover);
        float textY = fieldY + (FIELD_HEIGHT - textMetrics.textHeight(FIELD_TEXT_SCALE, null)) * 0.5f;
        scope.roundRect(fieldX, fieldY, fieldW, FIELD_HEIGHT, FIELD_RADIUS, background);
        scope.outline(fieldX, fieldY, fieldW, FIELD_HEIGHT, FIELD_RADIUS, 0.7f,
                MD3Theme.filledFieldIndicator(expanded, hover));
        scope.text(setting.getTranslatedValue(), fieldX + FIELD_TEXT_PADDING_X, textY,
                FIELD_TEXT_SCALE, MD3Theme.filledFieldContent(expanded));
        scope.triangle(fieldX + fieldW - 10.0f, fieldY + FIELD_HEIGHT * 0.5f, 3.0f,
                expand, DropdownTheme.expandArrow(expand));
    }

    private void drawExpandedOptions(UiTree.Scope scope, UiTextMetrics textMetrics, int mouseX, int mouseY,
                                     float fieldX, float fieldW, float expand) {
        float listY = getLocalListY();
        float clipH = getListHeight() * expand;
        float visibleBottom = listY + clipH;
        scope.roundRect(fieldX, listY, fieldW, clipH, FIELD_RADIUS, DropdownTheme.settingSurface());
        scope.outline(fieldX, listY, fieldW, clipH, FIELD_RADIUS, 0.7f,
                MD3Theme.withAlpha(MD3Theme.OUTLINE, 96));

        List<String> choices = getHiddenChoices();
        for (int index = 0; index < choices.size(); index++) {
            float optionY = getLocalOptionY(index);
            if (optionY >= visibleBottom) continue;
            float visibleHeight = Math.min(OPTION_HEIGHT, visibleBottom - optionY);
            if (visibleHeight <= 0.0f) continue;
            boolean hovered = isOptionHovered(mouseX, mouseY, index);
            if (hovered) {
                scope.roundRect(fieldX + 1.5f, optionY, fieldW - 3.0f, visibleHeight,
                        FIELD_RADIUS - 1.0f, MD3Theme.rowSurface(1.0f));
            }
            float lineHeight = textMetrics.textHeight(OPTION_TEXT_SCALE, null);
            float textY = optionY + (OPTION_HEIGHT - lineHeight) * 0.5f;
            if (textY + lineHeight > visibleBottom) continue;
            float alpha = Mth.clamp((visibleBottom - optionY) / OPTION_HEIGHT, 0.0f, 1.0f);
            Color color = hovered ? MD3Theme.TEXT_PRIMARY : DropdownTheme.settingLabelMuted();
            color = MD3Theme.withAlpha(color, Mth.clamp((int) (color.getAlpha() * alpha), 0, 255));
            scope.text(setting.getTranslatedChoice(choices.get(index)), fieldX + FIELD_TEXT_PADDING_X,
                    textY, OPTION_TEXT_SCALE, color);
        }
    }

    private boolean handleFieldClick(int button) {
        if ((button == GLFW.GLFW_MOUSE_BUTTON_RIGHT || button == GLFW.GLFW_MOUSE_BUTTON_LEFT)
                && getHiddenChoiceCount() > 0) {
            expanded = !expanded;
            Managers.SOUND.playInUi(expanded ? SoundKey.SETTINGS_OPEN : SoundKey.SETTINGS_CLOSE);
            return true;
        }
        return expanded;
    }

    private boolean handleExpandedClick(double mouseX, double mouseY) {
        List<String> choices = getHiddenChoices();
        for (int index = 0; index < choices.size(); index++) {
            if (isOptionHovered(mouseX, mouseY, index)) {
                setting.setValue(choices.get(index));
                expanded = false;
                Managers.SOUND.playInUi(SoundKey.SETTINGS_CLOSE);
                return true;
            }
        }
        expanded = false;
        Managers.SOUND.playInUi(SoundKey.SETTINGS_CLOSE);
        return false;
    }

    private float updateExpandProgress() {
        expandAnim.run(shouldExpand() ? 1.0f : 0.0f);
        return expandAnim.getValue();
    }

    private float updateHoverProgress(boolean hovered) {
        hoverAnim.run(hovered || expanded ? 1.0f : 0.0f);
        return hoverAnim.getValue();
    }

    private int getHiddenChoiceCount() {
        return Math.max(0, setting.getChoices().size() - 1);
    }

    private List<String> getHiddenChoices() {
        return setting.getChoices().stream().filter(choice -> !choice.equals(setting.getValue())).toList();
    }

    private boolean shouldExpand() {
        return expanded && getHiddenChoiceCount() > 0;
    }

    private float getCollapsedHeight() {
        return DropdownTheme.SETTING_HEIGHT - 1.0f + FIELD_HEIGHT;
    }

    private float getExpandedTotalHeight() {
        return getHiddenChoiceCount() <= 0 ? 0.0f : LIST_GAP_Y + getListHeight();
    }

    private float getListHeight() {
        int count = getHiddenChoiceCount();
        return count <= 0 ? 0.0f : LIST_PADDING_Y * 2.0f + count * OPTION_HEIGHT
                + Math.max(0, count - 1) * OPTION_GAP;
    }

    private boolean isFieldHovered(double mouseX, double mouseY) {
        return isHovered(mouseX, mouseY, absoluteX(getLocalFieldX()), absoluteY(getLocalFieldY()),
                getFieldWidth(), FIELD_HEIGHT);
    }

    private boolean isOptionHovered(double mouseX, double mouseY, int index) {
        return isHovered(mouseX, mouseY, absoluteX(getLocalFieldX()), absoluteY(getLocalOptionY(index)),
                getFieldWidth(), OPTION_HEIGHT);
    }

    private float getLocalFieldX() {
        return DropdownTheme.SETTING_PADDING_X;
    }

    private float getLocalFieldY() {
        return DropdownTheme.SETTING_HEIGHT - 1.0f;
    }

    private float getFieldWidth() {
        return width - DropdownTheme.SETTING_PADDING_X * 2.0f;
    }

    private float getLocalListY() {
        return getLocalFieldY() + FIELD_HEIGHT + LIST_GAP_Y;
    }

    private float getLocalOptionY(int index) {
        return getLocalListY() + LIST_PADDING_Y + index * (OPTION_HEIGHT + OPTION_GAP);
    }
}
