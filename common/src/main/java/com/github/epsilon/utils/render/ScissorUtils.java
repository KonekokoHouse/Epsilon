package com.github.epsilon.utils.render;

import com.github.epsilon.graphics.LuminRenderSystem;

import static com.github.epsilon.Constants.mc;

public class ScissorUtils {

    private ScissorUtils() {
    }

    /**
     * 将 GUI 坐标矩形转换为帧缓冲裁剪矩形。
     *
     * @param x X 坐标
     * @param y Y 坐标
     * @param width 宽度
     * @param height 高度
     * @return 操作结果
     */
    public static LuminRenderSystem.ScissorRect toFramebufferScissor(float x, float y, float width, float height) {
        double scale = LuminRenderSystem.getGuiScale();
        int framebufferHeight = getFramebufferHeight();
        int sx = (int) Math.round(x * scale);
        int sy = (int) Math.round(framebufferHeight - (y + height) * scale);
        int sw = Math.max(0, (int) Math.round(width * scale));
        int sh = Math.max(0, (int) Math.round(height * scale));
        return clampFramebufferScissor(sx, sy, sw, sh);
    }

    /**
     * 将 GUI 坐标矩形转换为帧缓冲裁剪矩形。
     *
     * @param x X 坐标
     * @param y Y 坐标
     * @param width 宽度
     * @param height 高度
     * @param guiHeight GUI 坐标系高度
     * @return 操作结果
     */
    public static LuminRenderSystem.ScissorRect toFramebufferScissor(float x, float y, float width, float height, float guiHeight) {
        double scale = LuminRenderSystem.getGuiScale();
        int sx = (int) Math.round(x * scale);
        int sy = (int) Math.round((guiHeight - y - height) * scale);
        int sw = Math.max(0, (int) Math.round(width * scale));
        int sh = Math.max(0, (int) Math.round(height * scale));
        return clampFramebufferScissor(sx, sy, sw, sh);
    }

    /**
     * 将 GUI 坐标矩形转换为帧缓冲裁剪矩形。
     *
     * @param x X 坐标
     * @param y Y 坐标
     * @param width 宽度
     * @param height 高度
     * @param scale 缩放值或 GUI 到帧缓冲的比例
     * @param framebufferHeight 帧缓冲高度
     * @return 操作结果
     */
    public static LuminRenderSystem.ScissorRect toFramebufferScissor(float x, float y, float width, float height, double scale, int framebufferHeight) {
        int sx = (int) Math.round(x * scale);
        int sy = (int) Math.round(framebufferHeight - (y + height) * scale);
        int sw = Math.max(0, (int) Math.round(width * scale));
        int sh = Math.max(0, (int) Math.round(height * scale));
        return clampFramebufferScissor(sx, sy, sw, sh);
    }

    /**
     * 将裁剪矩形限制在当前帧缓冲范围内。
     *
     * @param x X 坐标
     * @param y Y 坐标
     * @param width 宽度
     * @param height 高度
     * @return 操作结果
     */
    public static LuminRenderSystem.ScissorRect clampFramebufferScissor(int x, int y, int width, int height) {
        int areaWidth = getFramebufferWidth();
        int areaHeight = getFramebufferHeight();
        int left = Math.clamp(x, 0, areaWidth);
        int top = Math.clamp(y, 0, areaHeight);
        int right = Math.clamp(x + width, 0, areaWidth);
        int bottom = Math.clamp(y + height, 0, areaHeight);
        return new LuminRenderSystem.ScissorRect(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
    }

    /**
     * 判断裁剪区域是否具有可见面积。
     *
     * @param scissor 帧缓冲裁剪矩形
     * @return 判断结果
     */
    public static boolean isVisible(LuminRenderSystem.ScissorRect scissor) {
        return isVisible(scissor.width(), scissor.height());
    }

    /**
     * 判断裁剪区域是否具有可见面积。
     *
     * @param width 宽度
     * @param height 高度
     * @return 判断结果
     */
    public static boolean isVisible(int width, int height) {
        return width > 0 && height > 0;
    }

    private static int getFramebufferWidth() {
        return mc.getWindow().getWidth();
    }

    private static int getFramebufferHeight() {
        return mc.getWindow().getHeight();
    }
}
