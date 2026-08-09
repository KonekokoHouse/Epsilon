package com.github.epsilon.gui.utils;

import com.github.epsilon.modules.impl.ClientSetting;
import net.minecraft.client.input.MouseButtonEvent;

import static com.github.epsilon.Constants.mc;

/** 在 Minecraft GUI 坐标和 Epsilon UI projection 坐标之间转换输入与原版 overlay 位置。 */
public final class UiCoordinateMapper {

    private UiCoordinateMapper() {
    }

    public static double toProjectionX(double guiX) {
        return toProjectionCoordinate(guiX, mc.getWindow().getWidth(), mc.getWindow().getGuiScaledWidth(),
                ClientSetting.INSTANCE.getScale());
    }

    public static double toProjectionY(double guiY) {
        return toProjectionCoordinate(guiY, mc.getWindow().getHeight(), mc.getWindow().getGuiScaledHeight(),
                ClientSetting.INSTANCE.getScale());
    }

    public static int toProjectionX(int guiX) {
        return (int) Math.round(toProjectionX((double) guiX));
    }

    public static int toProjectionY(int guiY) {
        return (int) Math.round(toProjectionY((double) guiY));
    }

    public static MouseButtonEvent toProjectionEvent(MouseButtonEvent event) {
        return new MouseButtonEvent(toProjectionX(event.x()), toProjectionY(event.y()), event.buttonInfo());
    }

    public static double toMinecraftX(double projectionX) {
        return toMinecraftCoordinate(projectionX, mc.getWindow().getWidth(), mc.getWindow().getGuiScaledWidth(),
                ClientSetting.INSTANCE.getScale());
    }

    public static double toMinecraftY(double projectionY) {
        return toMinecraftCoordinate(projectionY, mc.getWindow().getHeight(), mc.getWindow().getGuiScaledHeight(),
                ClientSetting.INSTANCE.getScale());
    }

    public static double toMinecraftLength(double projectionLength) {
        return toMinecraftX(projectionLength) - toMinecraftX(0.0);
    }

    public static float getProjectionWidth() {
        return (float) projectionExtent(mc.getWindow().getWidth(), ClientSetting.INSTANCE.getScale());
    }

    public static float getProjectionHeight() {
        return (float) projectionExtent(mc.getWindow().getHeight(), ClientSetting.INSTANCE.getScale());
    }

    public static int getProjectionWidthInt() {
        return (int) Math.ceil(getProjectionWidth());
    }

    public static int getProjectionHeightInt() {
        return (int) Math.ceil(getProjectionHeight());
    }

    static double toProjectionCoordinate(double guiCoordinate, double framebufferExtent,
                                         double minecraftGuiExtent, double projectionScale) {
        requirePositive(framebufferExtent, "framebufferExtent");
        requirePositive(minecraftGuiExtent, "minecraftGuiExtent");
        requirePositive(projectionScale, "projectionScale");
        return guiCoordinate * framebufferExtent / minecraftGuiExtent / projectionScale;
    }

    static double toMinecraftCoordinate(double projectionCoordinate, double framebufferExtent,
                                        double minecraftGuiExtent, double projectionScale) {
        requirePositive(framebufferExtent, "framebufferExtent");
        requirePositive(minecraftGuiExtent, "minecraftGuiExtent");
        requirePositive(projectionScale, "projectionScale");
        return projectionCoordinate * projectionScale * minecraftGuiExtent / framebufferExtent;
    }

    static double projectionExtent(double framebufferExtent, double projectionScale) {
        requirePositive(framebufferExtent, "framebufferExtent");
        requirePositive(projectionScale, "projectionScale");
        return framebufferExtent / projectionScale;
    }

    private static void requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be positive and finite");
        }
    }
}
