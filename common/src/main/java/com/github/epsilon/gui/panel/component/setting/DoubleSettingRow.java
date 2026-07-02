package com.github.epsilon.gui.panel.component.setting;

import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.gui.dsl.PanelUiTree;
import com.github.epsilon.gui.panel.MD3Theme;
import com.github.epsilon.gui.panel.PanelLayout;
import com.github.epsilon.gui.panel.component.SettingRow;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

public class DoubleSettingRow extends SettingRow<DoubleSetting> {

    private static final float FIELD_TEXT_SCALE = 0.60f;
    private static final float FIELD_TEXT_PADDING = 5.0f;

    private final Animation hoverAnimation = new Animation(Easing.EASE_OUT_QUART, 150L);
    private final Animation pressAnimation = new Animation(Easing.EASE_OUT_CUBIC, 120L);
    private final Animation indicatorAnimation = new Animation(Easing.EASE_OUT_QUART, 150L);
    private final TextRenderer measureTextRenderer = TextRenderer.create();
    private boolean dragging;
    private boolean focused;
    private String inputBuffer;
    private int cursorIndex;

    public DoubleSettingRow(DoubleSetting setting) {
        super(setting);
        hoverAnimation.setStartValue(0.0f);
        pressAnimation.setStartValue(0.0f);
        indicatorAnimation.setStartValue(0.0f);
    }

    @Override
    public void buildUi(PanelUiTree.Scope scope, GuiGraphicsExtractor guiGraphics, TextRenderer textRenderer, PanelLayout.Rect bounds, float hoverProgress, int mouseX, int mouseY, float partialTick) {
        float labelScale = 0.68f;
        float labelY = (bounds.height() - textRenderer.getHeight(labelScale)) / 2.0f;
        hoverAnimation.run(dragging ? 1.0f : hoverProgress);
        pressAnimation.run(dragging ? 1.0f : 0.0f);
        indicatorAnimation.run((dragging || hoverProgress > 0.01f) ? 1.0f : 0.0f);

        float animatedHover = hoverAnimation.getValue();
        float animatedPress = pressAnimation.getValue();
        float indicatorProgress = indicatorAnimation.getValue();

        scope.roundRect(0.0f, 0.0f, bounds.width(), bounds.height(), MD3Theme.CARD_RADIUS, MD3Theme.rowSurface(animatedHover));
        scope.text(setting.getDisplayName(), MD3Theme.ROW_CONTENT_INSET, labelY, labelScale, MD3Theme.TEXT_PRIMARY);

        PanelLayout.Rect trackBounds = getTrackBounds(bounds);
        PanelLayout.Rect fieldBounds = getFieldBounds(bounds);
        float progress = getProgress();
        float visualProgress = Math.clamp(progress, 0.0f, 1.0f);
        float handleWidth = 2.0f - animatedPress * 2.0f;
        float handleHeight = 14.0f;
        float handleX = trackBounds.x() + trackBounds.width() * visualProgress - handleWidth / 2.0f;
        float handleGap = 2.5f;

        scope.slider(trackBounds.relativeTo(bounds), visualProgress, 3.0f,
                MD3Theme.SECONDARY_CONTAINER,
                handleGap, 2.0f, MD3Theme.PRIMARY,
                handleWidth, handleHeight, 1.0f, MD3Theme.PRIMARY);

        if (indicatorProgress > 0.01f) {
            String label = formatValue();
            float textScale = 0.62f;
            float bubbleWidth = textRenderer.getWidth(label, textScale) + 16.0f;
            float bubbleHeight = 18.0f;
            float bubbleX = handleX + handleWidth / 2.0f - bubbleWidth / 2.0f;
            float bubbleY = bounds.y() - 22.0f;
            int bubbleAlpha = (int) (255 * indicatorProgress);
            scope.pushAbsolute(new PanelLayout.Rect(bubbleX, bubbleY, bubbleWidth, bubbleHeight), bubble -> {
                bubble.roundRect(0.0f, 0.0f, bubbleWidth, bubbleHeight, 9.0f, MD3Theme.withAlpha(MD3Theme.INVERSE_SURFACE, bubbleAlpha));
                float textWidth = textRenderer.getWidth(label, textScale);
                float textHeight = textRenderer.getHeight(textScale);
                float textX = (bubbleWidth - textWidth) / 2.0f;
                float textY = (bubbleHeight - textHeight) / 2.0f;
                bubble.text(label, textX, textY, textScale, MD3Theme.withAlpha(MD3Theme.INVERSE_ON_SURFACE, bubbleAlpha));
            });
        }

        float fieldHover = animatedHover * 0.85f;
        String display = focused ? getDisplayBuffer() : formatValue();
        float displayScale = getFieldTextScale(textRenderer, display, fieldBounds);
        float displayWidth = textRenderer.getWidth(display, displayScale);
        float displayX = fieldBounds.x() + (fieldBounds.width() - displayWidth) / 2.0f;
        scope.input(fieldBounds.relativeTo(bounds), focused, fieldHover,
                displayX - fieldBounds.x(), display, displayScale, MD3Theme.filledFieldContent(focused),
                focused ? Math.min(cursorIndex, display.length()) : null, focused ? MD3Theme.filledFieldCaret(focused) : null,
                null, 0.0f, null);
    }

    public PanelLayout.Rect getTrackBounds(PanelLayout.Rect bounds) {
        return new PanelLayout.Rect(bounds.right() - MD3Theme.ROW_TRAILING_INSET - 116.0f, bounds.y() + 12.0f, 72.0f, 6.0f);
    }

    public PanelLayout.Rect getFieldBounds(PanelLayout.Rect bounds) {
        return new PanelLayout.Rect(bounds.right() - MD3Theme.ROW_TRAILING_INSET - 40.0f, bounds.y() + 4.0f, 40.0f, 18.0f);
    }

    @Override
    public boolean mouseClicked(PanelLayout.Rect bounds, MouseButtonEvent event, boolean isDoubleClick) {
        PanelLayout.Rect fieldBounds = getFieldBounds(bounds);
        if (event.button() == 0 && fieldBounds.contains(event.x(), event.y())) {
            dragging = false;
            focused = true;
            inputBuffer = formatPlainValue();
            cursorIndex = getCursorIndex(event.x(), fieldBounds);
            return true;
        }
        if (event.button() != 0 || !getInteractiveBounds(bounds).contains(event.x(), event.y())) {
            return false;
        }
        focused = false;
        dragging = true;
        updateFromMouse(bounds, event.x());
        return true;
    }

    @Override
    public boolean mouseReleased(PanelLayout.Rect bounds, MouseButtonEvent event) {
        dragging = false;
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!focused) {
            return false;
        }
        return switch (event.key()) {
            case 257, 335 -> {
                commitInput();
                focused = false;
                yield true;
            }
            case 256 -> {
                focused = false;
                inputBuffer = null;
                yield true;
            }
            case 259 -> {
                if (inputBuffer != null && cursorIndex > 0) {
                    inputBuffer = inputBuffer.substring(0, cursorIndex - 1) + inputBuffer.substring(cursorIndex);
                    cursorIndex--;
                }
                yield true;
            }
            case 261 -> {
                if (inputBuffer != null && cursorIndex < inputBuffer.length()) {
                    inputBuffer = inputBuffer.substring(0, cursorIndex) + inputBuffer.substring(cursorIndex + 1);
                }
                yield true;
            }
            case 263 -> {
                cursorIndex = Math.max(0, cursorIndex - 1);
                yield true;
            }
            case 262 -> {
                cursorIndex = Math.min(getDisplayBuffer().length(), cursorIndex + 1);
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
        if (!value.matches("[0-9.-]")) {
            return false;
        }
        String current = getDisplayBuffer();
        inputBuffer = current.substring(0, cursorIndex) + value + current.substring(cursorIndex);
        cursorIndex++;
        return true;
    }

    @Override
    public void setFocused(boolean focused) {
        if (!focused && this.focused) {
            commitInput();
            inputBuffer = null;
        }
        this.focused = focused;
        if (focused && inputBuffer == null) {
            inputBuffer = formatPlainValue();
            cursorIndex = inputBuffer.length();
        }
    }

    @Override
    public boolean isFocused() {
        return focused;
    }

    @Override
    public boolean hasActiveAnimation() {
        return !hoverAnimation.isFinished() || !pressAnimation.isFinished() || !indicatorAnimation.isFinished();
    }

    public void updateFromMouse(PanelLayout.Rect bounds, double mouseX) {
        PanelLayout.Rect trackBounds = getTrackBounds(bounds);
        double progress = (mouseX - trackBounds.x()) / trackBounds.width();
        progress = Math.clamp(progress, 0.0, 1.0);
        double rawValue = setting.getMin() + (setting.getMax() - setting.getMin()) * progress;
        double step = setting.getStep() <= 0.0 ? 0.01 : setting.getStep();
        double snapped = setting.getMin() + Math.round((rawValue - setting.getMin()) / step) * step;
        setting.setValue(snapped);
    }

    public boolean isDragging() {
        return dragging;
    }

    private PanelLayout.Rect getInteractiveBounds(PanelLayout.Rect bounds) {
        PanelLayout.Rect track = getTrackBounds(bounds);
        return new PanelLayout.Rect(track.x(), track.y() - 6.0f, track.width(), track.height() + 12.0f);
    }

    private float getProgress() {
        if (setting.getMax() <= setting.getMin()) {
            return 0.0f;
        }
        return (float) ((setting.getValue() - setting.getMin()) / (setting.getMax() - setting.getMin()));
    }

    private String formatValue() {
        return Math.abs(setting.getValue() - Math.round(setting.getValue())) < 0.0001 ? Integer.toString((int) Math.round(setting.getValue())) : String.format("%.2f", setting.getValue());
    }

    private String formatPlainValue() {
        return Math.abs(setting.getValue() - Math.round(setting.getValue())) < 0.0001 ? Integer.toString((int) Math.round(setting.getValue())) : String.format("%.2f", setting.getValue());
    }

    private String getDisplayBuffer() {
        return inputBuffer == null ? formatPlainValue() : inputBuffer;
    }

    private void commitInput() {
        if (inputBuffer == null || inputBuffer.isBlank() || "-".equals(inputBuffer) || ".".equals(inputBuffer)) {
            inputBuffer = formatPlainValue();
            cursorIndex = inputBuffer.length();
            return;
        }
        try {
            setting.setUnboundedValue(Double.parseDouble(inputBuffer));
        } catch (NumberFormatException ignored) {
        }
        inputBuffer = formatPlainValue();
        cursorIndex = inputBuffer.length();
    }

    private int getCursorIndex(double mouseX, PanelLayout.Rect fieldBounds) {
        String text = getDisplayBuffer();
        float scale = getFieldTextScale(measureTextRenderer, text, fieldBounds);
        float textWidth = measureTextRenderer.getWidth(text, scale);
        float textStart = fieldBounds.x() + (fieldBounds.width() - textWidth) / 2.0f;
        for (int i = 0; i <= text.length(); i++) {
            float width = measureTextRenderer.getWidth(text.substring(0, i), scale);
            if (mouseX <= textStart + width) {
                return i;
            }
        }
        return text.length();
    }

    private float getFieldTextScale(TextRenderer textRenderer, String text, PanelLayout.Rect fieldBounds) {
        float textWidth = textRenderer.getWidth(text, FIELD_TEXT_SCALE);
        float maxTextWidth = Math.max(1.0f, fieldBounds.width() - FIELD_TEXT_PADDING * 2.0f);
        if (textWidth <= maxTextWidth || textWidth <= 0.0f) {
            return FIELD_TEXT_SCALE;
        }
        return FIELD_TEXT_SCALE * maxTextWidth / textWidth;
    }

}
