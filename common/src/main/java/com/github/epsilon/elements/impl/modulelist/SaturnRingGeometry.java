package com.github.epsilon.elements.impl.modulelist;

/**
 * 土星环的采样几何缓存。
 *
 * <p>Lumin 的 2D 图元只有矩形、圆角矩形、描边和三角形，没有椭圆或圆弧，因此土星环由沿倾斜
 * 椭圆排布的圆形段（半径等于边长一半的圆角矩形）拼出。段数、半径和倾角只在玩家改设置或
 * HUD 缩放变化时才会变，所以三角函数全部预计算成表，每帧只做“查表 + 平移”，渲染循环内
 * 不再出现 {@code sin}/{@code cos} 调用。</p>
 *
 * <p>实例只在渲染线程使用，不持有 GPU 或 Minecraft 资源，因此没有生命周期方法。</p>
 */
public final class SaturnRingGeometry {

    /** 采样段数下限；再低椭圆会出现可见折角。 */
    public static final int MIN_SEGMENTS = 24;
    /** 采样段数上限，用于约束单帧节点规模。 */
    public static final int MAX_SEGMENTS = 240;

    private static final float TWO_PI = (float) (Math.PI * 2.0);
    private static final float TOLERANCE = 1.0e-4f;
    private static final float[] NO_FLOATS = new float[0];
    private static final boolean[] NO_FLAGS = new boolean[0];

    /** 单位圆采样表，只随段数变化。 */
    private float[] unitCos = NO_FLOATS;
    private float[] unitSin = NO_FLOATS;
    /** 每个采样点的高光权重，用于让环带出现流动的明暗变化。 */
    private float[] shimmer = NO_FLOATS;
    /** 采样点是否位于靠近观察者的前半环。 */
    private boolean[] front = NO_FLAGS;

    /** 倾斜后的屏幕空间偏移，随段数、半径或倾角变化。 */
    private float[] offsetX = NO_FLOATS;
    private float[] offsetY = NO_FLOATS;

    private int segments;
    private float radiusX = Float.NaN;
    private float radiusY = Float.NaN;
    private float tiltDegrees = Float.NaN;
    private float halfWidth;
    private float halfHeight;

    /**
     * 按当前设置刷新缓存；参数未变化时直接返回。
     *
     * @param segments    采样段数，会夹到 {@link #MIN_SEGMENTS}~{@link #MAX_SEGMENTS} 并向下取偶数，
     *                    保证前后半环各占一半索引
     * @param radiusX     环在自身平面内的长半轴
     * @param radiusY     环在自身平面内的短半轴，等价于俯视角的压扁量
     * @param tiltDegrees 环在屏幕平面内的旋转角度
     */
    public void configure(int segments, float radiusX, float radiusY, float tiltDegrees) {
        int resolved = Math.clamp(segments, MIN_SEGMENTS, MAX_SEGMENTS);
        resolved -= resolved & 1;

        boolean tableDirty = resolved != this.segments;
        if (!tableDirty
                && same(radiusX, this.radiusX)
                && same(radiusY, this.radiusY)
                && same(tiltDegrees, this.tiltDegrees)) {
            return;
        }
        if (tableDirty) {
            rebuildUnitTable(resolved);
        }

        this.radiusX = radiusX;
        this.radiusY = radiusY;
        this.tiltDegrees = tiltDegrees;
        rebuildOffsets();
    }

    /** 当前采样段数。 */
    public int segments() {
        return segments;
    }

    /** 采样点相对环心的横向偏移。 */
    public float offsetX(int index) {
        return offsetX[index];
    }

    /** 采样点相对环心的纵向偏移。 */
    public float offsetY(int index) {
        return offsetY[index];
    }

    /** 采样点是否属于前半环；前半环需要绘制在行星上方。 */
    public boolean isFront(int index) {
        return front[index];
    }

    /** 倾斜后环的屏幕半宽，用于 HUD 尺寸计算。 */
    public float halfWidth() {
        return halfWidth;
    }

    /** 倾斜后环的屏幕半高，用于 HUD 尺寸计算。 */
    public float halfHeight() {
        return halfHeight;
    }

    /**
     * 把 0~1 的动画相位换算成采样表的索引位移，使高光流动只是一次查表偏移。
     */
    public int phaseOffset(float phase) {
        if (segments == 0) {
            return 0;
        }
        int shift = (int) (phase * segments);
        shift %= segments;
        return shift < 0 ? shift + segments : shift;
    }

    /**
     * 读取带相位位移的高光权重，取值 0~1。
     *
     * @param index       采样点索引
     * @param phaseOffset {@link #phaseOffset(float)} 的返回值
     */
    public float shimmerAt(int index, int phaseOffset) {
        int shifted = index + phaseOffset;
        if (shifted >= segments) {
            shifted -= segments;
        }
        return shimmer[shifted];
    }

    private void rebuildUnitTable(int count) {
        segments = count;
        unitCos = new float[count];
        unitSin = new float[count];
        shimmer = new float[count];
        front = new boolean[count];
        offsetX = new float[count];
        offsetY = new float[count];

        for (int index = 0; index < count; index++) {
            double angle = TWO_PI * index / (double) count;
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            unitCos[index] = cos;
            unitSin[index] = sin;
            // 屏幕 Y 轴向下，正 sin 一侧靠近观察者；sin 为 0 的两个节点归到后半环，
            // 保证前后半环的采样点数量相同。
            front[index] = sin > 0.0f;
            shimmer[index] = 0.5f + 0.5f * sin;
        }
    }

    private void rebuildOffsets() {
        double tilt = Math.toRadians(tiltDegrees);
        float cosTilt = (float) Math.cos(tilt);
        float sinTilt = (float) Math.sin(tilt);

        for (int index = 0; index < segments; index++) {
            float planeX = radiusX * unitCos[index];
            float planeY = radiusY * unitSin[index];
            offsetX[index] = planeX * cosTilt - planeY * sinTilt;
            offsetY[index] = planeX * sinTilt + planeY * cosTilt;
        }

        // 倾斜椭圆的屏幕半轴有闭式解，无需扫描采样点求极值。
        halfWidth = (float) Math.hypot(radiusX * cosTilt, radiusY * sinTilt);
        halfHeight = (float) Math.hypot(radiusX * sinTilt, radiusY * cosTilt);
    }

    private static boolean same(float left, float right) {
        return Math.abs(left - right) < TOLERANCE;
    }
}
