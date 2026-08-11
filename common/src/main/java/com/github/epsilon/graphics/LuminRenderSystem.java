package com.github.epsilon.graphics;

import com.github.epsilon.gui.input.MouseButtonEvent;
import com.github.epsilon.gui.utils.UiCoordinateMapper;
import com.github.epsilon.modules.impl.ClientSetting;
import com.github.epsilon.utils.render.ScissorUtils;

public final class LuminRenderSystem {
    private static long renderFrameId;

    private LuminRenderSystem() {
    }

    public static void destroyAll() {
    }

    public static void endDynamicUniformFrame() {
    }

    public static void beginRenderFrame() {
        renderFrameId++;
    }

    public static long getRenderFrameId() {
        return renderFrameId;
    }

    public static double getGuiScale() {
        return ClientSetting.INSTANCE.getScale();
    }

    public static float getScaledWidth() {
        return UiCoordinateMapper.getProjectionWidth();
    }

    public static float getScaledHeight() {
        return UiCoordinateMapper.getProjectionHeight();
    }

    public static int getScaledWidthInt() {
        return (int) Math.ceil(getScaledWidth());
    }

    public static int getScaledHeightInt() {
        return (int) Math.ceil(getScaledHeight());
    }

    public static double toEpsilonMouseX(double mouseX) {
        return UiCoordinateMapper.toProjectionX(mouseX);
    }

    public static double toEpsilonMouseY(double mouseY) {
        return UiCoordinateMapper.toProjectionY(mouseY);
    }

    public static double toMinecraftGuiX(double epsilonX) {
        return UiCoordinateMapper.toMinecraftX(epsilonX);
    }

    public static double toMinecraftGuiY(double epsilonY) {
        return UiCoordinateMapper.toMinecraftY(epsilonY);
    }

    public static int toEpsilonMouseX(int mouseX) {
        return (int) Math.round(toEpsilonMouseX((double) mouseX));
    }

    public static int toEpsilonMouseY(int mouseY) {
        return (int) Math.round(toEpsilonMouseY((double) mouseY));
    }

    public static MouseButtonEvent toEpsilonMouseEvent(MouseButtonEvent event) {
        return UiCoordinateMapper.toProjectionEvent(event);
    }

    public static ScissorRect toFramebufferScissor(float x, float y, float width, float height) {
        return ScissorUtils.toFramebufferScissor(x, y, width, height);
    }

    public static ScissorRect toFramebufferScissor(float x, float y, float width, float height, float guiHeight) {
        return ScissorUtils.toFramebufferScissor(x, y, width, height, guiHeight);
    }

    public static ScissorRect toFramebufferScissor(float x, float y, float width, float height, int guiHeight) {
        return toFramebufferScissor(x, y, width, height, (float) guiHeight);
    }

    public static ScissorRect toFramebufferScissor(float x, float y, float width, float height, double guiHeight) {
        return toFramebufferScissor(x, y, width, height, (float) guiHeight);
    }

    public record ScissorRect(int x, int y, int width, int height) {
    }
}
