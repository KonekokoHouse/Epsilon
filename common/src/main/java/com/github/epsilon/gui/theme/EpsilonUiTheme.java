package com.github.epsilon.gui.theme;

import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.slmpc.lumingraphics.core.geometry.LuminColor;

import java.awt.*;

/**
 * 将 Epsilon 的动态 Material 主题适配到独立 GUI 库。
 */
public final class EpsilonUiTheme {

    private EpsilonUiTheme() { }

    /** 未迁移 Screen 使用 {@link #INSTANCE}；Lumin UI 消费方只使用此公共主题视图。 */
    public static com.github.slmpc.lumingraphics.ui.theme.UiTheme lumin() {
        return LuminTheme.INSTANCE;
    }

    public static LuminColor lumin(Color color) {
        return new LuminColor(color.getRed() / 255.0f, color.getGreen() / 255.0f,
                color.getBlue() / 255.0f, color.getAlpha() / 255.0f);
    }

    private enum LuminTheme implements com.github.slmpc.lumingraphics.ui.theme.UiTheme {
        INSTANCE;

        @Override public float controlRadius() { return MD3Theme.CONTROL_RADIUS; }
        @Override public LuminColor textPrimary() { return lumin(MD3Theme.TEXT_PRIMARY); }
        @Override public LuminColor textMuted() { return lumin(MD3Theme.TEXT_MUTED); }
        @Override public LuminColor outlineSoft() { return lumin(MD3Theme.OUTLINE_SOFT); }
        @Override public LuminColor surface() { return lumin(MD3Theme.SURFACE_CONTAINER_HIGH); }
        @Override public LuminColor accent() { return lumin(MD3Theme.PRIMARY); }
        @Override public long hoverAnimationDuration() { return DropdownTheme.ANIM_HOVER; }
        @Override public boolean light() { return MD3Theme.isLightTheme(); }
        @Override public LuminColor filledFieldSurface(boolean focused, float hoverProgress) {
            return lumin(MD3Theme.filledFieldSurface(focused, hoverProgress));
        }
        @Override public LuminColor segmentedControlSurface() {
            return lumin(MD3Theme.segmentedControlSurface());
        }
        @Override public LuminColor segmentedControlIndicator() {
            return lumin(MD3Theme.segmentedControlIndicator());
        }
        @Override public LuminColor segmentedControlActiveLabel() {
            return lumin(MD3Theme.segmentedControlActiveLabel());
        }
        @Override public LuminColor segmentedControlInactiveLabel() {
            return lumin(MD3Theme.segmentedControlInactiveLabel());
        }
        @Override public LuminColor switchTrack(float progress) {
            return lumin(MD3Theme.switchTrack(progress));
        }
        @Override public LuminColor switchKnob(float progress) {
            return lumin(MD3Theme.switchKnob(progress));
        }
        @Override public LuminColor switchTrackOutline(float progress, float hoverProgress) {
            return lumin(MD3Theme.switchTrackOutline(progress, hoverProgress));
        }
        @Override public float switchTrackOutlineWidth(float progress) {
            return MD3Theme.switchTrackOutlineWidth(progress);
        }
        @Override public float switchHandleSizeOff() { return MD3Theme.SWITCH_HANDLE_SIZE_OFF; }
        @Override public float switchHandleSizeOn() { return MD3Theme.SWITCH_HANDLE_SIZE_ON; }
        @Override public float switchHandleInsetOff() { return MD3Theme.SWITCH_HANDLE_INSET_OFF; }
        @Override public float switchHandleInsetOn() { return MD3Theme.SWITCH_HANDLE_INSET_ON; }
        @Override public float switchStateLayerSize() { return MD3Theme.SWITCH_STATE_LAYER_SIZE; }
        @Override public LuminColor scrollBar(float hoverProgress) {
            return lumin(DropdownTheme.scrollbar(hoverProgress));
        }

    }
}
