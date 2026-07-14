package com.github.epsilon.gui.dropdown.component;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.gui.dropdown.DropdownDrawContext;
import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.epsilon.gui.dropdown.widget.DropdownTextField;
import com.github.epsilon.gui.panel.MD3Theme;
import com.github.epsilon.managers.Managers;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class FriendDropdownPanel extends AbstractDropdownPanel {

    private static final float ROW_HEIGHT = 20.0f;
    private static final float FIELD_HEIGHT = 18.0f;
    private static final float GAP = 4.0f;
    private static final float PADDING = 6.0f;

    private final DropdownTextField inputField = new DropdownTextField(32);

    public FriendDropdownPanel(int panelIndex) {
        super("friend", EpsilonTranslations.Gui.TAB_FRIEND, "", panelIndex);
    }

    @Override
    protected float computeContentHeight() {
        int friendCount = Managers.FRIEND.getFriends().size();
        return PADDING * 2.0f + FIELD_HEIGHT + GAP + Math.max(ROW_HEIGHT, friendCount * (ROW_HEIGHT + GAP));
    }

    @Override
    protected void drawPanelContent(DropdownDrawContext renderer, int mouseX, int mouseY, float visibleHeight) {
        float fieldX = x + PADDING;
        float fieldY = y + DropdownTheme.PANEL_HEADER_HEIGHT + PADDING - scroll;
        float fieldW = width - PADDING * 2.0f - 24.0f;
        inputField.draw(renderer, fieldX, fieldY, fieldW, FIELD_HEIGHT, mouseX, mouseY, EpsilonTranslations.Gui.FRIEND_INPUT_PLACEHOLDER.getTranslatedName(), DropdownTheme.SETTING_TEXT_SCALE);

        float addX = fieldX + fieldW + GAP;
        renderer.roundRect(addX, fieldY, 20.0f, FIELD_HEIGHT, DropdownTheme.BUTTON_RADIUS,
                isHovered(mouseX, mouseY, addX, fieldY, 20.0f, FIELD_HEIGHT) ? MD3Theme.PRIMARY : MD3Theme.PRIMARY_CONTAINER);
        renderer.text("+", addX + 7.0f, fieldY + 2.0f, 0.62f, MD3Theme.ON_PRIMARY_CONTAINER);

        List<String> friends = Managers.FRIEND.getFriends().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        float rowY = fieldY + FIELD_HEIGHT + GAP;
        if (friends.isEmpty()) {
            renderer.text(EpsilonTranslations.Gui.FRIEND_EMPTY.getTranslatedName(), x + PADDING, rowY + 4.0f, 0.55f, MD3Theme.TEXT_MUTED);
            return;
        }
        for (String name : friends) {
            boolean hovered = isHovered(mouseX, mouseY, x + PADDING, rowY, width - PADDING * 2.0f, ROW_HEIGHT);
            renderer.roundRect(x + PADDING, rowY, width - PADDING * 2.0f, ROW_HEIGHT, DropdownTheme.BUTTON_RADIUS,
                    hovered ? MD3Theme.SURFACE_CONTAINER_HIGH : MD3Theme.SURFACE_CONTAINER_LOW);
            renderer.text(trimToWidth(name, DropdownTheme.SETTING_TEXT_SCALE, width - 38.0f, renderer),
                    x + PADDING + 6.0f, rowY + (ROW_HEIGHT - renderer.textHeight(DropdownTheme.SETTING_TEXT_SCALE)) * 0.5f,
                    DropdownTheme.SETTING_TEXT_SCALE, MD3Theme.TEXT_PRIMARY);
            float removeX = x + width - PADDING - 18.0f;
            renderer.text("x", removeX + 5.0f, rowY + 3.0f, 0.54f,
                    isHovered(mouseX, mouseY, removeX, rowY + 1.0f, 16.0f, 16.0f) ? MD3Theme.ERROR : MD3Theme.TEXT_MUTED);
            rowY += ROW_HEIGHT + GAP;
        }
    }

    @Override
    protected boolean mouseClickedContent(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        float fieldX = x + PADDING;
        float fieldY = y + DropdownTheme.PANEL_HEADER_HEIGHT + PADDING - scroll;
        float fieldW = width - PADDING * 2.0f - 24.0f;
        if (inputField.focusIfContains(mouseX, mouseY, fieldX, fieldY, fieldW, FIELD_HEIGHT)) {
            return true;
        }
        if (isHovered(mouseX, mouseY, fieldX + fieldW + GAP, fieldY, 20.0f, FIELD_HEIGHT)) {
            addFriend();
            return true;
        }
        inputField.blur();

        float rowY = fieldY + FIELD_HEIGHT + GAP;
        for (String name : Managers.FRIEND.getFriends().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList()) {
            float removeX = x + width - PADDING - 18.0f;
            if (isHovered(mouseX, mouseY, removeX, rowY + 1.0f, 16.0f, 16.0f)) {
                Managers.FRIEND.removeFriend(name);
                return true;
            }
            rowY += ROW_HEIGHT + GAP;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!inputField.isFocused()) return false;
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            addFriend();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            inputField.blur();
            return true;
        }
        return inputField.keyPressed(keyCode);
    }

    @Override
    public boolean charTyped(String typedText) {
        return inputField.charTyped(typedText);
    }

    @Override
    public boolean hasActiveInput() {
        return inputField.isFocused();
    }

    private void addFriend() {
        String name = inputField.getText().trim();
        if (!name.isEmpty() && !Managers.FRIEND.isFriend(name)) {
            Managers.FRIEND.addFriend(name);
        }
        inputField.clear();
    }

}
