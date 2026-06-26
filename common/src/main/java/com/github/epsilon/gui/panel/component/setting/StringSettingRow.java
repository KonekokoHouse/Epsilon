package com.github.epsilon.gui.panel.component.setting;

import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.gui.dsl.PanelUiTree;
import com.github.epsilon.gui.panel.MD3Theme;
import com.github.epsilon.gui.panel.PanelLayout;
import com.github.epsilon.gui.panel.component.SettingRow;
import com.github.epsilon.gui.panel.utils.IMEFocusHelper;
import com.github.epsilon.settings.impl.StringSetting;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

import java.awt.*;

import static com.github.epsilon.Constants.mc;

public class StringSettingRow extends SettingRow<StringSetting> {

    private static final float FIELD_SCALE = 0.60f;
    private static final float FIELD_WIDTH = 120.0f;
    private static final int MAX_LENGTH = 256;

    private final TextRenderer measureTextRenderer = TextRenderer.create();
    private boolean focused;
    private String inputBuffer;
    private int cursorIndex;
    private int selectionAnchor = -1;

    public StringSettingRow(StringSetting setting) {
        super(setting);
    }

    @Override
    public void buildUi(PanelUiTree.Scope scope, GuiGraphicsExtractor guiGraphics, TextRenderer textRenderer, PanelLayout.Rect bounds, float hoverProgress, int mouseX, int mouseY, float partialTick) {
        float labelScale = 0.68f;
        float labelY = bounds.y() + (bounds.height() - textRenderer.getHeight(labelScale)) / 2.0f;
        scope.roundRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), MD3Theme.CARD_RADIUS, MD3Theme.rowSurface(hoverProgress));
        scope.text(setting.getDisplayName(), bounds.x() + MD3Theme.ROW_CONTENT_INSET, labelY, labelScale, MD3Theme.TEXT_PRIMARY);

        PanelLayout.Rect fieldBounds = getFieldBounds(bounds);
        boolean hovered = fieldBounds.contains(mouseX, mouseY);
        float fieldHover = hovered ? 1.0f : hoverProgress * 0.55f;

        String displaySource = focused ? getDisplayBuffer() : normalize(setting.getValue());
        DisplaySlice slice = buildDisplaySlice(displaySource, fieldBounds, focused);
        PanelUiTree.SelectionRange selection = null;
        if (focused && hasSelection()) {
            int selectionStart = Math.max(slice.start(), getSelectionStart());
            int selectionEnd = Math.min(slice.end(), getSelectionEnd());
            if (selectionEnd > selectionStart) {
                selection = new PanelUiTree.SelectionRange(selectionStart - slice.start(), selectionEnd - slice.start());
            }
        }
        scope.input(fieldBounds, focused, fieldHover,
                0.0f, new Color(0, 0, 0, 0), 0.0f,
                slice.textX() - fieldBounds.x(), slice.text(), FIELD_SCALE, MD3Theme.filledFieldContent(focused),
                selection, selection == null ? null : MD3Theme.withAlpha(MD3Theme.filledFieldIndicator(focused, fieldHover), 90),
                focused ? slice.caretIndex() : null, focused ? MD3Theme.filledFieldCaret(focused) : null,
                null, 0.0f, null);
        if (focused) {
            float caretX = slice.textX() + textRenderer.getWidth(slice.text().substring(0, Math.min(slice.caretIndex(), slice.text().length())), FIELD_SCALE);
            caretX = Math.min(caretX, fieldBounds.right() - 5.0f);
            float textY = fieldBounds.y() + (fieldBounds.height() - textRenderer.getHeight(FIELD_SCALE)) / 2.0f;
            IMEFocusHelper.updateCursorPos(caretX, textY);
        }
    }

    @Override
    public boolean mouseClicked(PanelLayout.Rect bounds, MouseButtonEvent event, boolean isDoubleClick) {
        if (event.button() != 0) {
            return false;
        }
        PanelLayout.Rect fieldBounds = getFieldBounds(bounds);
        if (!fieldBounds.contains(event.x(), event.y())) {
            return false;
        }
        focused = true;
        IMEFocusHelper.activate();
        inputBuffer = normalize(setting.getValue());
        cursorIndex = getCursorIndex(event.x(), fieldBounds);
        clearSelection();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!focused) {
            return false;
        }
        if (isControlDown()) {
            return handleControlShortcut(event.key());
        }
        return switch (event.key()) {
            case 257, 335 -> {
                commitInput();
                focused = false;
                IMEFocusHelper.deactivate();
                clearSelection();
                yield true;
            }
            case 256 -> {
                focused = false;
                IMEFocusHelper.deactivate();
                inputBuffer = null;
                clearSelection();
                yield true;
            }
            case 259 -> {
                deleteBackward();
                yield true;
            }
            case 261 -> {
                deleteForward();
                yield true;
            }
            case 263 -> {
                cursorIndex = Math.max(0, cursorIndex - 1);
                clearSelection();
                yield true;
            }
            case 262 -> {
                cursorIndex = Math.min(getDisplayBuffer().length(), cursorIndex + 1);
                clearSelection();
                yield true;
            }
            case 268 -> {
                cursorIndex = 0;
                clearSelection();
                yield true;
            }
            case 269 -> {
                cursorIndex = getDisplayBuffer().length();
                clearSelection();
                yield true;
            }
            default -> false;
        };
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!focused || !event.isAllowedChatCharacter()) {
            return false;
        }
        String value = event.codepointAsString();
        if (value.isEmpty()) {
            return false;
        }
        String current = getDisplayBuffer();
        int selectionLength = hasSelection() ? getSelectionEnd() - getSelectionStart() : 0;
        if (current.length() - selectionLength >= MAX_LENGTH) {
            return true;
        }
        replaceSelection(value);
        return true;
    }

    @Override
    public void setFocused(boolean focused) {
        if (!focused && this.focused) {
            commitInput();
            inputBuffer = null;
            IMEFocusHelper.deactivate();
        }
        this.focused = focused;
        if (focused && inputBuffer == null) {
            inputBuffer = normalize(setting.getValue());
            cursorIndex = inputBuffer.length();
            IMEFocusHelper.activate();
        }
        if (!focused) {
            clearSelection();
        }
    }

    @Override
    public boolean isFocused() {
        return focused;
    }

    private PanelLayout.Rect getFieldBounds(PanelLayout.Rect bounds) {
        return new PanelLayout.Rect(bounds.right() - MD3Theme.ROW_TRAILING_INSET - FIELD_WIDTH, bounds.y() + 4.0f, FIELD_WIDTH, 18.0f);
    }

    private String getDisplayBuffer() {
        return inputBuffer == null ? normalize(setting.getValue()) : inputBuffer;
    }

    private void commitInput() {
        String value = inputBuffer == null ? normalize(setting.getValue()) : inputBuffer;
        value = value.length() > MAX_LENGTH ? value.substring(0, MAX_LENGTH) : value;
        setting.setValue(value);
        inputBuffer = value;
        cursorIndex = inputBuffer.length();
        clearSelection();
    }

    private int getCursorIndex(double mouseX, PanelLayout.Rect fieldBounds) {
        String text = getDisplayBuffer();
        DisplaySlice slice = buildDisplaySlice(text, fieldBounds, true);
        for (int i = 0; i <= slice.text().length(); i++) {
            float width = measureTextRenderer.getWidth(slice.text().substring(0, i), FIELD_SCALE);
            if (mouseX <= slice.textX() + width) {
                return slice.start() + i;
            }
        }
        return slice.start() + slice.text().length();
    }

    private DisplaySlice buildDisplaySlice(String value, PanelLayout.Rect fieldBounds, boolean editing) {
        String safeValue = value == null ? "" : value;
        float horizontalInset = 6.0f;
        float availableWidth = Math.max(8.0f, fieldBounds.width() - horizontalInset * 2.0f);
        if (!editing) {
            String shown = fitWithEllipsis(safeValue, availableWidth);
            return new DisplaySlice(shown, fieldBounds.x() + horizontalInset, 0, 0, shown.length());
        }

        int safeCursor = Math.clamp(cursorIndex, 0, safeValue.length());
        int start = 0;
        int end = safeValue.length();
        while (start < safeCursor) {
            String candidate = safeValue.substring(start, end);
            float width = measureTextRenderer.getWidth(candidate, FIELD_SCALE);
            float caretWidth = measureTextRenderer.getWidth(safeValue.substring(start, safeCursor), FIELD_SCALE);
            if (width <= availableWidth && caretWidth <= availableWidth - 2.0f) {
                break;
            }
            start++;
        }
        while (end > safeCursor && measureTextRenderer.getWidth(safeValue.substring(start, end), FIELD_SCALE) > availableWidth) {
            end--;
        }
        String shown = safeValue.substring(start, end);
        return new DisplaySlice(shown, fieldBounds.x() + horizontalInset, safeCursor - start, start, end);
    }

    private String fitWithEllipsis(String value, float availableWidth) {
        if (measureTextRenderer.getWidth(value, FIELD_SCALE) <= availableWidth) {
            return value;
        }
        String ellipsis = "...";
        float ellipsisWidth = measureTextRenderer.getWidth(ellipsis, FIELD_SCALE);
        if (ellipsisWidth >= availableWidth) {
            return "";
        }
        int length = value.length();
        while (length > 0) {
            String candidate = value.substring(0, length) + ellipsis;
            if (measureTextRenderer.getWidth(candidate, FIELD_SCALE) <= availableWidth) {
                return candidate;
            }
            length--;
        }
        return ellipsis;
    }

    private String normalize(String value) {
        return value == null ? "" : value;
    }

    private boolean handleControlShortcut(int key) {
        return switch (key) {
            case 65 -> {
                selectAll();
                yield true;
            }
            case 67 -> {
                copySelection();
                yield true;
            }
            case 86 -> {
                pasteClipboard();
                yield true;
            }
            default -> false;
        };
    }

    private void selectAll() {
        String current = getDisplayBuffer();
        cursorIndex = current.length();
        selectionAnchor = 0;
    }

    private void copySelection() {
        if (!hasSelection()) {
            return;
        }
        String current = getDisplayBuffer();
        mc.keyboardHandler.setClipboard(current.substring(getSelectionStart(), getSelectionEnd()));
    }

    private void pasteClipboard() {
        String clipboard = mc.keyboardHandler.getClipboard();
        if (clipboard.isEmpty()) {
            return;
        }
        String sanitized = sanitizeClipboard(clipboard);
        if (sanitized.isEmpty()) {
            return;
        }
        String current = getDisplayBuffer();
        int selectionLength = hasSelection() ? getSelectionEnd() - getSelectionStart() : 0;
        int available = MAX_LENGTH - (current.length() - selectionLength);
        if (available <= 0) {
            return;
        }
        if (sanitized.length() > available) {
            sanitized = sanitized.substring(0, available);
        }
        replaceSelection(sanitized);
    }

    private String sanitizeClipboard(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        value.codePoints().forEach(codePoint -> {
            if (codePoint >= 32 && codePoint != 127) {
                builder.appendCodePoint(codePoint);
            }
        });
        return builder.toString();
    }

    private void replaceSelection(String value) {
        String current = getDisplayBuffer();
        int start = hasSelection() ? getSelectionStart() : cursorIndex;
        int end = hasSelection() ? getSelectionEnd() : cursorIndex;
        inputBuffer = current.substring(0, start) + value + current.substring(end);
        cursorIndex = start + value.length();
        clearSelection();
    }

    private void deleteBackward() {
        if (hasSelection()) {
            replaceSelection("");
            return;
        }
        if (inputBuffer != null && cursorIndex > 0) {
            inputBuffer = inputBuffer.substring(0, cursorIndex - 1) + inputBuffer.substring(cursorIndex);
            cursorIndex--;
        }
    }

    private void deleteForward() {
        if (hasSelection()) {
            replaceSelection("");
            return;
        }
        if (inputBuffer != null && cursorIndex < inputBuffer.length()) {
            inputBuffer = inputBuffer.substring(0, cursorIndex) + inputBuffer.substring(cursorIndex + 1);
        }
    }

    private boolean hasSelection() {
        return selectionAnchor >= 0 && selectionAnchor != cursorIndex;
    }

    private int getSelectionStart() {
        return Math.min(selectionAnchor, cursorIndex);
    }

    private int getSelectionEnd() {
        return Math.max(selectionAnchor, cursorIndex);
    }

    private void clearSelection() {
        selectionAnchor = -1;
    }

    private boolean isControlDown() {
        return InputConstants.isKeyDown(mc.getWindow(), 341) || InputConstants.isKeyDown(mc.getWindow(), 345);
    }

    private record DisplaySlice(String text, float textX, int caretIndex, int start, int end) {
    }

}
