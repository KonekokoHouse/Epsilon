package com.github.epsilon.gui.dropdown.widget;

import com.github.epsilon.gui.dropdown.DropdownRenderer;
import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.epsilon.gui.panel.MD3Theme;
import com.github.epsilon.gui.panel.utils.IMEFocusHelper;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

import java.util.function.Predicate;

import static com.github.epsilon.Constants.mc;

public class DropdownTextField {

    private final int maxLength;
    private final Predicate<String> inputFilter;
    private boolean focused;
    private String text = "";
    private int cursor;
    private float[] cursorMidpoints = new float[0];

    public DropdownTextField(int maxLength) {
        this(maxLength, value -> true);
    }

    public DropdownTextField(int maxLength, Predicate<String> inputFilter) {
        this.maxLength = maxLength;
        this.inputFilter = inputFilter == null ? value -> true : inputFilter;
    }

    public void draw(DropdownRenderer renderer, float x, float y, float width, float height, int mouseX, int mouseY, String placeholder, float textScale) {
        renderer.roundRect().addRoundRect(x, y, width, height, DropdownTheme.INPUT_RADIUS, DropdownTheme.inputSurface(focused));
        renderer.outline().addOutline(x, y, width, height, DropdownTheme.INPUT_RADIUS, 0.7f, focused ? MD3Theme.PRIMARY : MD3Theme.withAlpha(MD3Theme.OUTLINE, 90));

        boolean showPlaceholder = text.isEmpty() && !focused;
        String display = showPlaceholder ? placeholder : text;
        float textY = y + (height - renderer.text().getHeight(textScale)) / 2.0f; // i07你还挺聪明
        float textX = x + 4.0f;
        updateCursorLayout(renderer, textX, textScale);
        renderer.text().addText(trimToWidth(display, textScale, width - 8.0f, renderer), textX, textY, textScale, showPlaceholder ? MD3Theme.TEXT_MUTED : MD3Theme.TEXT_PRIMARY);

        if (focused) {
            int safeCursor = Math.min(cursor, text.length());
            float caretX = textX + renderer.text().getWidth(text.substring(0, safeCursor), textScale);
            drawCaret(renderer, caretX, textY, textScale);
            IMEFocusHelper.updateCursorPos(caretX, textY);
        }
    }

    public void drawCentered(DropdownRenderer renderer, float x, float y, float width, float height, int mouseX, int mouseY, String placeholder, float textScale) {
        renderer.roundRect().addRoundRect(x, y, width, height, DropdownTheme.INPUT_RADIUS, DropdownTheme.inputSurface(focused));
        renderer.outline().addOutline(x, y, width, height, DropdownTheme.INPUT_RADIUS, 0.7f, focused ? MD3Theme.PRIMARY : MD3Theme.withAlpha(MD3Theme.OUTLINE, 90));

        boolean showPlaceholder = text.isEmpty() && !focused;
        String display = showPlaceholder ? placeholder : text;

        float textY = y + (height - renderer.text().getHeight(textScale)) / 2.0f;
        String visibleText = trimToWidth(display, textScale, width - 8.0f, renderer);
        float textX = x + (width - renderer.text().getWidth(visibleText, textScale)) * 0.5f;
        float caretBaseX = x + (width - renderer.text().getWidth(text, textScale)) * 0.5f;
        updateCursorLayout(renderer, caretBaseX, textScale);
        renderer.text().addText(visibleText, textX, textY, textScale, showPlaceholder ? MD3Theme.TEXT_MUTED : MD3Theme.TEXT_PRIMARY);

        if (focused) {
            int safeCursor = Math.min(cursor, text.length());
            String beforeCursor = text.substring(0, safeCursor);
            float caretX = caretBaseX + renderer.text().getWidth(beforeCursor, textScale);
            drawCaret(renderer, caretX, textY, textScale);
            IMEFocusHelper.updateCursorPos(caretX, textY);
        }
    }

    public boolean focusIfContains(double mouseX, double mouseY, float x, float y, float width, float height) {
        if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) {
            return false;
        }
        focused = true;
        cursor = resolveCursor(mouseX);
        IMEFocusHelper.activate();
        return true;
    }

    public boolean focusIfContainsCentered(double mouseX, double mouseY, float x, float y, float width, float height) {
        if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) {
            return false;
        }
        focused = true;
        cursor = resolveCursor(mouseX);
        IMEFocusHelper.activate();
        return true;
    }

    public void focus() {
        focused = true;
        cursor = text.length();
        IMEFocusHelper.activate();
    }

    public void blur() {
        if (focused) {
            focused = false;
            IMEFocusHelper.deactivate();
        }
    }

    public boolean keyPressed(KeyEvent event) {
        return keyPressed(event.key());
    }

    public boolean keyPressed(int keyCode) {
        if (!focused) return false;
        if (isControlDown()) {
            return handleControlShortcut(keyCode);
        }
        return switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (cursor > 0 && !text.isEmpty()) {
                    text = text.substring(0, cursor - 1) + text.substring(cursor);
                    cursor--;
                }
                yield true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (cursor < text.length()) {
                    text = text.substring(0, cursor) + text.substring(cursor + 1);
                }
                yield true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                cursor = Math.max(0, cursor - 1);
                yield true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                cursor = Math.min(text.length(), cursor + 1);
                yield true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                cursor = 0;
                yield true;
            }
            case GLFW.GLFW_KEY_END -> {
                cursor = text.length();
                yield true;
            }
            default -> false;
        };
    }

    public boolean charTyped(CharacterEvent event) {
        if (!focused) return false;
        if (!event.isAllowedChatCharacter()) return true;
        insertText(event.codepointAsString());
        return true;
    }

    public boolean charTyped(String typedText) {
        if (!focused) return false;
        insertText(typedText);
        return true;
    }

    public boolean isFocused() {
        return focused;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = clamp(text == null ? "" : text);
        cursor = Math.min(cursor, this.text.length());
    }

    public void clear() {
        text = "";
        cursor = 0;
    }

    public void setCursorToEnd() {
        cursor = text.length();
    }

    private void insertText(String inserted) {
        if (inserted == null || inserted.isEmpty()) return;
        StringBuilder accepted = new StringBuilder();
        inserted.codePoints().forEach(codePoint -> {
            String candidate = new String(Character.toChars(codePoint));
            if (inputFilter.test(candidate)) accepted.append(candidate);
        });
        if (accepted.isEmpty()) return;
        int available = maxLength - text.length();
        if (available <= 0) return;
        String safe = accepted.length() > available ? accepted.substring(0, available) : accepted.toString();
        text = text.substring(0, cursor) + safe + text.substring(cursor);
        cursor += safe.length();
    }

    private boolean handleControlShortcut(int keyCode) {
        return switch (keyCode) {
            case GLFW.GLFW_KEY_A -> {
                cursor = text.length();
                yield true;
            }
            case GLFW.GLFW_KEY_V -> {
                insertText(mc.keyboardHandler.getClipboard());
                yield true;
            }
            default -> false;
        };
    }

    private boolean isControlDown() {
        var window = mc.getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    private String clamp(String value) {
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    private void updateCursorLayout(DropdownRenderer renderer, float textX, float textScale) {
        if (text.isEmpty()) {
            cursorMidpoints = new float[0];
            return;
        }
        if (cursorMidpoints.length != text.length()) {
            cursorMidpoints = new float[text.length()];
        }
        for (int i = 0; i < text.length(); i++) {
            float left = renderer.text().getWidth(text.substring(0, i), textScale);
            float right = renderer.text().getWidth(text.substring(0, i + 1), textScale);
            cursorMidpoints[i] = textX + (left + right) * 0.5f;
        }
    }

    private int resolveCursor(double mouseX) {
        if (text.isEmpty()) return 0;
        if (cursorMidpoints.length != text.length()) return text.length();
        for (int i = 0; i < cursorMidpoints.length; i++) {
            if (mouseX < cursorMidpoints[i]) return i;
        }
        return text.length();
    }

    private void drawCaret(DropdownRenderer renderer, float x, float y, float textScale) {
        if (System.currentTimeMillis() % 1000 > 500) {
            renderer.rect().addRect(x, y, 0.8f, renderer.text().getHeight(textScale), MD3Theme.TEXT_PRIMARY);
        }
    }

    private String trimToWidth(String value, float scale, float maxWidth, DropdownRenderer renderer) {
        if (value == null || value.isEmpty()) return "";
        if (renderer.text().getWidth(value, scale) <= maxWidth) return value;
        String ellipsis = "...";
        float ellipsisWidth = renderer.text().getWidth(ellipsis, scale);
        if (ellipsisWidth >= maxWidth) return ellipsis;
        for (int len = value.length() - 1; len >= 0; len--) {
            String candidate = value.substring(0, len) + ellipsis;
            if (renderer.text().getWidth(candidate, scale) <= maxWidth) return candidate;
        }
        return ellipsis;
    }

}
