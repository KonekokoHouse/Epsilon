package com.github.epsilon.elements.impl.modulelist;

/**
 * 土星环的几何解算。
 *
 * <p>Lumin 现在提供了可旋转的椭圆弧图元，一条环带就是一段圆弧，边界由着色器按解析距离场抗锯齿。
 * 因此环不再由沿倾斜椭圆排布的圆点拼出：整个环系只需十来个节点，放大后也不会露出折角。</p>
 *
 * <p>这里只负责把设置换算成每条同心环带的半径、带宽以及环系在屏幕上的包围半轴，全部是常数级的
 * 浮点运算，所以不做脏检查。实例只在渲染线程使用，不持有任何 GPU 或 Minecraft 资源。</p>
 */
public final class SaturnRingGeometry {

    /** 同心环带数量下限。 */
    public static final int MIN_BANDS = 1;

    /** 同心环带数量上限，用来约束单帧的节点规模。 */
    public static final int MAX_BANDS = 5;

    /** 单条环带占据自身径向格位的比例，余下的部分留作环缝。 */
    private static final float BAND_FILL = 0.62f;

    /** 亮边线相对环系总厚度的外扩量。 */
    private static final float EDGE_GAP = 0.35f;

    /** 亮边线相对环系总厚度的线宽。 */
    private static final float EDGE_THICKNESS = 0.22f;

    /** 环带半轴的下限，避免极端设置组合把内圈算成零或负数。 */
    private static final float MIN_RADIUS = 0.1f;

    private int bands = MIN_BANDS;
    private float radiusX = 1.0f;
    private float radiusY = 1.0f;
    private float tiltDegrees;
    private float bandStep;
    private float bandWidth;
    private float edgeOffset;
    private float edgeThickness;
    private float halfWidth;
    private float halfHeight;

    /**
     * 解算当前设置对应的环几何。
     *
     * @param bands       同心环带数量，会夹到 {@link #MIN_BANDS}~{@link #MAX_BANDS}
     * @param radiusX     环在自身平面内的长半轴
     * @param radiusY     环在自身平面内的短半轴，等价于俯视角带来的压扁量
     * @param tiltDegrees 环绕自身中心在屏幕平面内的旋转角度
     * @param thickness   环系的总径向厚度，会被均分给各条环带
     */
    public void configure(int bands, float radiusX, float radiusY, float tiltDegrees, float thickness) {
        this.bands = Math.clamp(bands, MIN_BANDS, MAX_BANDS);
        this.radiusX = Math.max(MIN_RADIUS, radiusX);
        this.radiusY = Math.max(MIN_RADIUS, radiusY);
        this.tiltDegrees = tiltDegrees;
        this.bandStep = thickness / this.bands;
        this.bandWidth = Math.max(0.35f, bandStep * BAND_FILL);
        this.edgeThickness = Math.max(0.4f, thickness * EDGE_THICKNESS);
        this.edgeOffset = thickness * 0.5f + Math.max(0.5f, thickness * EDGE_GAP);

        // 倾斜椭圆的屏幕半轴有闭式解：把参数方程代入旋转矩阵后，每个分量都是同一相位的正弦叠加，
        // 其幅值就是 hypot。这比把包围盒四角扫一遍最多紧凑 √2 倍。
        double tilt = Math.toRadians(tiltDegrees);
        float cos = (float) Math.cos(tilt);
        float sin = (float) Math.sin(tilt);
        float outerX = edgeRadiusX();
        float outerY = edgeRadiusY();
        this.halfWidth = (float) Math.hypot(outerX * cos, outerY * sin) + edgeThickness * 0.5f;
        this.halfHeight = (float) Math.hypot(outerX * sin, outerY * cos) + edgeThickness * 0.5f;
    }

    /** 同心环带数量。 */
    public int bands() {
        return bands;
    }

    /** 环绕自身中心的倾斜角度，直接喂给 {@code rotatedArc}。 */
    public float tiltDegrees() {
        return tiltDegrees;
    }

    /** 单条环带的带宽。 */
    public float bandWidth() {
        return bandWidth;
    }

    /** 亮边线的线宽。 */
    public float edgeThickness() {
        return edgeThickness;
    }

    /** 环系在屏幕上的半宽，已包含亮边线的外扩与线宽。 */
    public float halfWidth() {
        return halfWidth;
    }

    /** 环系在屏幕上的半高，已包含亮边线的外扩与线宽。 */
    public float halfHeight() {
        return halfHeight;
    }

    /** 第 {@code index} 条环带（0 为最内圈）中心线的长半轴。 */
    public float bandRadiusX(int index) {
        return Math.max(MIN_RADIUS, radiusX + (index - (bands - 1) * 0.5f) * bandStep);
    }

    /** 同一条环带的短半轴。环带彼此同心，因此两轴按同一比例缩放。 */
    public float bandRadiusY(int index) {
        return radiusY * bandRadiusX(index) / radiusX;
    }

    /** 亮边线的长半轴。 */
    public float edgeRadiusX() {
        return radiusX + edgeOffset;
    }

    /** 亮边线的短半轴。 */
    public float edgeRadiusY() {
        return radiusY * edgeRadiusX() / radiusX;
    }

    /**
     * 后半环从行星圆盘边缘露出来的半角（度）。
     *
     * <p>把中心距 {@code r(θ) = hypot(rx·cosθ, ry·sinθ)} 解到等于行星半径即得
     * {@code cos²θ = (R² - ry²) / (rx² - ry²)}，于是后半环可见的部分正是紧邻长轴两端的
     * {@code [180, 180 + θ]} 与 {@code [360 - θ, 360]}。短轴仍在行星外时整个半环可见，
     * 长轴都被行星吞掉时整条环带不可见。</p>
     */
    public static float visibleHalfSweep(float radiusX, float radiusY, float planetRadius) {
        if (radiusY >= planetRadius) {
            return 180.0f;
        }
        if (radiusX <= planetRadius) {
            return 0.0f;
        }
        float ratio = (planetRadius * planetRadius - radiusY * radiusY)
                / (radiusX * radiusX - radiusY * radiusY);
        float cos = Math.clamp((float) Math.sqrt(Math.max(0.0f, ratio)), 0.0f, 1.0f);
        return (float) Math.toDegrees(Math.acos(cos));
    }
}
