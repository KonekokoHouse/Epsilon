package com.github.epsilon.elements.impl;

import com.github.epsilon.elements.HudModule;
import com.github.epsilon.elements.impl.modulelist.SaturnRingGeometry;
import com.github.epsilon.holders.ModuleHolder;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.Setting;
import com.github.epsilon.settings.impl.*;
import com.github.epsilon.utils.render.animation.Easing;
import com.github.epsilon.utils.rotation.Priority;
import com.github.slmpc.lumingraphics.core.geometry.LuminColor;
import com.github.slmpc.lumingraphics.mc.v2612.runtime.MinecraftBlurRegion2612;
import com.github.slmpc.lumingraphics.mc.v2612.runtime.MinecraftUiRuntime2612;
import com.github.slmpc.lumingraphics.ui.geometry.UiRect;
import com.github.slmpc.lumingraphics.ui.tree.UiTree;
import net.minecraft.client.DeltaTracker;
import net.minecraft.util.Mth;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class ModuleList extends HudModule {

    public static final ModuleList INSTANCE = new ModuleList();

    private ModuleList() {
        super("Module List", 0f, 2f, 96f, 20f);
    }

    private enum Style { Compact, Open, Saturn }
    private enum Mode { LEFT_TAG, RIGHT_TAG, FRAME }
    private enum SortingMode { LENGTH, ALPHABET, CATEGORY }

    private final EnumSetting<Style> style = enumSetting("Style", Style.Open);
    /** Open 与 Saturn 都是卡片样式，共享圆角、阴影和模糊设置；Compact 只画文本条。 */
    private final Setting.Dependency cardStyles = () -> !style.is(Style.Compact);
    private final Setting.Dependency openStyle = () -> style.is(Style.Open);
    private final Setting.Dependency saturnStyle = () -> style.is(Style.Saturn);

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.LEFT_TAG, () -> style.is(Style.Compact));
    private final EnumSetting<SortingMode> sortingMode = enumSetting("Sorting Mode", SortingMode.LENGTH);
    private final BoolSetting showHidden = boolSetting("Show Hidden", false);
    private final BoolSetting bindOnly = boolSetting("Bind Only", false, () -> !showHidden.getValue());
    private final BoolSetting rainbow = boolSetting("Rainbow", true);
    private final DoubleSetting rainbowLength = doubleSetting("Rainbow Length", 10.0, 1.0, 20.0, 0.5, rainbow::getValue);
    private final DoubleSetting indexedHue = doubleSetting("Indexed Hue", 0.5, 0.0, 1.0, 0.05, rainbow::getValue);
    private final DoubleSetting saturation = doubleSetting("Saturation", 0.5, 0.0, 1.0, 0.01, rainbow::getValue);
    private final DoubleSetting brightness = doubleSetting("Brightness", 1.0, 0.0, 1.0, 0.01, rainbow::getValue);
    private final DoubleSetting scale = doubleSetting("Scale", 1.0, 0.5, 2.5, 0.05);
    private final ColorSetting textColor = colorSetting("Text Color", new Color(208, 188, 255, 255));
    private final ColorSetting backgroundColor = colorSetting("Background Color", new Color(15, 15, 15, 145));
    private final ColorSetting infoColor = colorSetting("Info Color", new Color(255, 255, 255, 235));
    private final ColorSetting bracketColor = colorSetting("Bracket Color", new Color(165, 165, 165, 225));

    private final BoolSetting showCategory = boolSetting("Show Category", false, openStyle);
    private final BoolSetting showIcon = boolSetting("Show Icon", true, cardStyles);
    private final DoubleSetting textScaleOffset = doubleSetting("Text Scale Offset", -0.2, -0.5, 0.5, 0.05, cardStyles);
    private final DoubleSetting cornerRadius = doubleSetting("Corner Radius", 4.0, 0.0, 14.0, 0.5, openStyle);

    // 吊牌直接压在环上，环必须足够大才排得开，因此默认值比纯装饰环时更宽更圆。
    private final DoubleSetting planetRadius = doubleSetting("Planet Radius", 12.0, 4.0, 24.0, 0.5, saturnStyle);
    private final DoubleSetting ringSpread = doubleSetting("Ring Spread", 3.8, 1.2, 8.0, 0.05, saturnStyle);
    private final DoubleSetting ringFlatten = doubleSetting("Ring Flatten", 0.62, 0.05, 1.0, 0.01, saturnStyle);
    private final DoubleSetting ringTilt = doubleSetting("Ring Tilt", -16.0, -45.0, 45.0, 1.0, saturnStyle);
    private final DoubleSetting ringWidth = doubleSetting("Ring Width", 2.4, 0.6, 6.0, 0.1, saturnStyle);
    private final IntSetting ringSegments = intSetting("Ring Segments", 72,
            SaturnRingGeometry.MIN_SEGMENTS, SaturnRingGeometry.MAX_SEGMENTS, 8, saturnStyle);
    private final DoubleSetting ringSpeed = doubleSetting("Ring Speed", 0.5, 0.0, 4.0, 0.05, saturnStyle);
    private final BoolSetting showCount = boolSetting("Show Count", true, saturnStyle);

    private final BoolSetting drawShadow = boolSetting("Drop Shadow", true, cardStyles);
    /** 依赖只能读取已声明的字段，因此共享条件必须放在它引用的开关之后。 */
    private final Setting.Dependency shadowOptions = () -> !style.is(Style.Compact) && drawShadow.getValue();
    private final DoubleSetting shadowBlur = doubleSetting("Shadow Blur", DEFAULT_SHADOW_BLUR,
            MIN_SHADOW_BLUR, MAX_SHADOW_BLUR, SHADOW_BLUR_STEP, shadowOptions);
    private final ColorSetting shadowColor = colorSetting("Shadow Color", DEFAULT_SHADOW_COLOR, shadowOptions);
    private final BoolSetting backgroundBlur = boolSetting("Background Blur", false, cardStyles);
    private final Setting.Dependency blurOptions = () -> !style.is(Style.Compact) && backgroundBlur.getValue();
    private final IntSetting blurStrength = intSetting("Blur Strength", 5, 1, 16, 1, blurOptions);

    /** 逐模块复用的动画状态与文本度量缓存，稳定状态下渲染循环零分配。 */
    private final Map<Module, ModuleEntry> entries = new HashMap<>();
    /** 当前帧可见行，每帧清空后复用同一个列表。 */
    private final List<ModuleEntry> visibleRows = new ArrayList<>();
    /** 土星环采样表，只在设置或 HUD 缩放变化时重算。 */
    private final SaturnRingGeometry ring = new SaturnRingGeometry();

    private static final String FONT_TEXT = "epsilon-default";
    private static final String FONT_ICONS = "epsilon-icons";
    private static final long TOGGLE_ANIMATION_MS = 300L;

    private static final float MIN_BOUNDS = 20.0f;
    private static final float OPEN_ROW_HEIGHT = 18.0f;
    private static final float OPEN_ROW_SPACING = 2.0f;
    private static final float OPEN_NAME_PADDING_START = 3.5f;
    private static final float OPEN_NAME_PADDING_END = 5.0f;
    private static final float OPEN_ICON_GAP = 2.0f;
    private static final float OPEN_INFO_PADDING_START = 2.5f;
    private static final float OPEN_INFO_PADDING_END = 3.5f;
    /** 展开行的分类图标略微压暗，避免抢走模块名的注意力。 */
    private static final float OPEN_ICON_ALPHA = 0.82f;

    /**
     * 土星样式的遮挡顺序：后半环与挂在后半环上的吊牌留在基础层，被行星挡住形成穿插；行星、
     * 前半环、前半环吊牌依次抬高一层。批次规划器会把跨层但 BatchKey 相同的分组重新聚成一次
     * 绘制，所以按深度拆层几乎不增加 GPU 批次。
     */
    private static final int SATURN_LAYER_PLANET = 1;
    private static final int SATURN_LAYER_FRONT_RING = 2;
    private static final int SATURN_LAYER_FRONT_CHIP = 3;

    private static final float SATURN_CHIP_HEIGHT = 15.0f;
    private static final float SATURN_CHIP_PADDING_START = 4.0f;
    private static final float SATURN_CHIP_PADDING_END = 5.0f;
    private static final float SATURN_MOON_SIZE = 3.6f;
    private static final float SATURN_MOON_GAP = 3.2f;
    private static final float SATURN_ITEM_GAP = 2.4f;
    private static final float SATURN_INFO_PADDING = 3.2f;
    private static final float SATURN_ICON_SCALE = 0.9f;
    private static final float SATURN_COUNT_SCALE = 1.15f;
    /** 环上最高优先级槽位所在的圈内位置：1/4 圈即屏幕坐标下的前半环正中（下方）。 */
    private static final float SATURN_FRONT_TURN = 0.25f;
    /** 吊牌沿环换位的动画时长，比开关动画略长，让滑动看得清。 */
    private static final long SATURN_RING_ANIMATION_MS = 420L;
    /** 判断槽位是否真的变了的阈值；槽位是 1/count 的整数倍，这个量级远小于任何一格。 */
    private static final float RING_TURN_EPSILON = 1.0e-4f;
    /** 绕到行星背面的吊牌缩到这个比例，模拟透视。 */
    private static final float SATURN_BACK_CHIP_SCALE = 0.78f;
    /** 后半环吊牌的最低不透明度倍率，配合缩小共同表达纵深。 */
    private static final float SATURN_BACK_CHIP_ALPHA = 0.45f;
    /**
     * 透视缩放的量化档位。吊牌滑动时纵深连续变化，若直接用于文本缩放会让字体度量缓存每帧
     * 都换键；量化到 1/64 后整个环上只有十几个档位，稳定状态和滑动过程都能命中缓存。
     */
    private static final float SATURN_SCALE_QUANTUM = 64.0f;

    /** 主环带与外缘亮线相对采样半径的比例，两者叠加出有厚度的环。 */
    private static final float SATURN_BAND_SCALE = 0.94f;
    private static final float SATURN_EDGE_SCALE = 1.07f;
    private static final float SATURN_EDGE_THICKNESS = 0.42f;
    private static final float SATURN_EDGE_BRIGHTNESS = 1.30f;
    private static final float SATURN_BAND_ALPHA = 0.70f;
    /** 后半环整体压暗，制造绕过行星背面的纵深感。 */
    private static final float SATURN_BACK_ALPHA = 0.52f;
    /** 高光只改亮度不改透明度，避免密集圆点叠加出现色带。 */
    private static final float SATURN_SHIMMER_BASE = 0.70f;
    private static final float SATURN_SHIMMER_RANGE = 0.48f;
    private static final float SATURN_PLANET_LIT_ALPHA = 0.60f;
    private static final float SATURN_PLANET_LIT_BRIGHTNESS = 1.15f;
    private static final float SATURN_PLANET_SHADE_BRIGHTNESS = 0.55f;
    private static final float SATURN_PLANET_EDGE_BRIGHTNESS = 1.08f;
    private static final float SATURN_HIGHLIGHT_SCALE = 0.62f;
    private static final float SATURN_HIGHLIGHT_ALPHA = 0.22f;
    private static final float SATURN_HIGHLIGHT_BRIGHTNESS = 1.45f;
    private static final float SATURN_RIM_ALPHA = 0.75f;
    private static final float SATURN_COUNT_BRIGHTNESS = 1.25f;
    private static final float SATURN_MOON_BRIGHTNESS = 1.30f;
    private static final float SATURN_CHIP_LIT_BRIGHTNESS = 1.55f;
    private static final float SATURN_CHIP_OUTLINE_ALPHA = 0.32f;
    private static final float SATURN_BADGE_ALPHA = 0.22f;
    private static final float SATURN_ICON_ALPHA = 0.82f;

    private static final Comparator<ModuleEntry> LENGTH_ORDER =
            Comparator.comparingDouble((ModuleEntry entry) -> -entry.width);
    private static final Comparator<ModuleEntry> ALPHABET_ORDER =
            Comparator.comparing((ModuleEntry entry) -> entry.sortKey);
    private static final Comparator<ModuleEntry> CATEGORY_ORDER =
            Comparator.comparingInt((ModuleEntry entry) -> entry.categoryOrder)
                    .thenComparing((ModuleEntry entry) -> entry.sortKey);
    /** 转头优先级降序；不抢视角的模块权重为 -1，排在最低优先级之后。 */
    private static final Comparator<ModuleEntry> PRIORITY_ORDER =
            Comparator.comparingInt((ModuleEntry entry) -> -entry.priorityWeight);
    /**
     * 土星样式按转头优先级分层排序，同层再退回用户选的 Sorting Mode。三种组合都预先合成成
     * 静态常量，切排序方式不会每帧新建比较器。
     */
    private static final Comparator<ModuleEntry> PRIORITY_LENGTH_ORDER = PRIORITY_ORDER.thenComparing(LENGTH_ORDER);
    private static final Comparator<ModuleEntry> PRIORITY_ALPHABET_ORDER = PRIORITY_ORDER.thenComparing(ALPHABET_ORDER);
    private static final Comparator<ModuleEntry> PRIORITY_CATEGORY_ORDER = PRIORITY_ORDER.thenComparing(CATEGORY_ORDER);

    @Override
    public void render(DeltaTracker deltaTracker) {
        Style current = style.getValue();
        float s = scale.getValue().floatValue();
        float textScale = current == Style.Compact
                ? 0.72f * s
                : Math.max(0.1f, s + textScaleOffset.getValue().floatValue());
        List<ModuleEntry> rows = collectRows(current, textScale);

        switch (current) {
            case Compact -> renderCompact(rows, s, textScale);
            case Open -> renderOpen(rows, s, textScale);
            case Saturn -> renderSaturn(rows, s, textScale);
        }
    }

    private List<ModuleEntry> collectRows(Style current, float textScale) {
        List<Module> modules = ModuleHolder.INSTANCE.getModules();
        // 只有模块被移除时才需要收缩缓存，正常帧不做任何集合分配。
        if (entries.size() > modules.size()) {
            entries.keySet().retainAll(modules);
        }

        boolean withCategory = current == Style.Open && showCategory.getValue();
        long now = System.currentTimeMillis();
        visibleRows.clear();
        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            boolean state = resolveState(module);
            ModuleEntry entry = entries.get(module);
            if (entry == null) {
                entry = new ModuleEntry(module, state);
                entries.put(module, entry);
            }
            if (entry.update(state, now) <= 0.001f) continue;

            entry.measure(textScale, withCategory);
            visibleRows.add(entry);
        }

        visibleRows.sort(rowComparator(current));
        return visibleRows;
    }

    private void renderCompact(List<ModuleEntry> rows, float s, float textScale) {
        UiTree.Scope scope = renderScope();
        float lineHeight = textHeight(textScale, FONT_TEXT);
        float rowHeight = lineHeight + 2.0f * s;
        float paddingX = 2.0f * s;
        float tagWidth = mode.is(Mode.FRAME) ? 0.0f : 2.0f * s;

        float maxWidth = MIN_BOUNDS;
        float totalHeight = 0.0f;
        for (int i = 0; i < rows.size(); i++) {
            ModuleEntry entry = rows.get(i);
            entry.rowWidth = entry.width + paddingX * 2.0f + tagWidth;
            maxWidth = Math.max(maxWidth, entry.rowWidth);
            totalHeight += rowHeight * entry.progress;
        }

        setBounds(maxWidth, Math.max(totalHeight, MIN_BOUNDS));
        if (rows.isEmpty()) return;

        boolean rightAligned = getHorizontalAnchor() == HorizontalAnchor.Right;
        boolean bottomAligned = getVerticalAnchor() == VerticalAnchor.Bottom;
        float currentY = bottomAligned ? this.y + this.height : this.y;
        float timedHue = timedHue();

        for (int i = 0; i < rows.size(); i++) {
            ModuleEntry entry = rows.get(i);
            float visibleHeight = rowHeight * entry.progress;
            float rowY = bottomAligned ? currentY - visibleHeight : currentY;
            float targetX = computeRowX(entry.rowWidth);
            float slideOffset = entry.rowWidth * (1.0f - entry.progress);
            float rowX = rightAligned ? targetX + slideOffset : targetX - slideOffset;
            Color accent = rainbow.getValue() ? rainbowColor(timedHue, i) : textColor.getValue();

            drawCompactRow(scope, entry, rowX, rowY, visibleHeight, paddingX, tagWidth,
                    textScale, lineHeight, accent);

            if (bottomAligned) {
                currentY -= visibleHeight;
            } else {
                currentY += visibleHeight;
            }
        }
    }

    private void renderOpen(List<ModuleEntry> rows, float s, float textScale) {
        UiTree.Scope scope = renderScope();

        float rowHeight = OPEN_ROW_HEIGHT * s;
        float spacing = OPEN_ROW_SPACING * s;
        float iconGap = OPEN_ICON_GAP * s;
        float namePadStart = OPEN_NAME_PADDING_START * s;
        float namePadEnd = OPEN_NAME_PADDING_END * s;
        float infoPadStart = OPEN_INFO_PADDING_START * s;
        float infoPadEnd = OPEN_INFO_PADDING_END * s;
        float radius = cornerRadius.getValue().floatValue() * s;
        boolean withIcon = showIcon.getValue();

        float maxWidth = MIN_BOUNDS;
        float totalHeight = 0.0f;
        for (int i = 0; i < rows.size(); i++) {
            ModuleEntry entry = rows.get(i);
            entry.measureIcon(withIcon ? s : 0.0f);
            entry.nameBoxWidth = namePadStart + entry.nameWidth + namePadEnd;
            entry.infoBoxWidth = withIcon && entry.infoWidth > 0.0f
                    ? infoPadStart + entry.infoWidth + infoPadEnd
                    : 0.0f;
            entry.rowWidth = entry.nameBoxWidth;
            if (withIcon) {
                entry.rowWidth += rowHeight + iconGap;
                if (entry.infoBoxWidth > 0.0f) {
                    entry.rowWidth += iconGap + entry.infoBoxWidth;
                }
            }
            maxWidth = Math.max(maxWidth, entry.rowWidth);
            totalHeight += (rowHeight + (i == 0 ? 0.0f : spacing)) * entry.progress;
        }

        setBounds(maxWidth, Math.max(totalHeight, MIN_BOUNDS));
        if (rows.isEmpty()) return;

        boolean bottomAligned = getVerticalAnchor() == VerticalAnchor.Bottom;
        boolean iconOnLeft = getHorizontalAnchor() == HorizontalAnchor.Left;
        float lineHeight = textHeight(textScale, FONT_TEXT);
        float currentY = bottomAligned ? this.y + this.height : this.y;
        float timedHue = timedHue();

        for (int i = 0; i < rows.size(); i++) {
            ModuleEntry entry = rows.get(i);
            float rowStep = rowHeight * entry.progress;
            float spacingStep = i == 0 ? 0.0f : spacing * entry.progress;
            if (bottomAligned) {
                currentY -= spacingStep + rowStep;
            } else {
                currentY += spacingStep;
            }

            float rowX = computeRowX(entry.rowWidth);
            Color accent = rainbow.getValue() ? rainbowColor(timedHue, i) : textColor.getValue();
            drawOpenRow(scope, entry, rowX, currentY, rowHeight, radius, iconGap, withIcon,
                    iconOnLeft, textScale, lineHeight, accent);

            if (!bottomAligned) {
                currentY += rowStep;
            }
        }
    }

    /**
     * 土星形式：带环的行星停在锚点上，已启用模块化作吊牌直接压在环上，按转头优先级镜像交替
     * 铺满整圈——最高优先级落在前半环正中（屏幕下方），其余左右成对依次向后半环收拢。
     *
     * <p>穿插关系用四层表达：后半环与挂在后半环上的吊牌留在基础 layer，行星在
     * {@link #SATURN_LAYER_PLANET}，前半环在 {@link #SATURN_LAYER_FRONT_RING}，前半环吊牌在
     * {@link #SATURN_LAYER_FRONT_CHIP}。同一 layer 内阴影、填充、描边和文字由批次规划器按管线
     * 顺序排好，不必再拆层；跨 layer 的同类图元又会合并回同一个批次。</p>
     *
     * <p>先把每个吊牌的槽位几何算进 {@code ModuleEntry} 的暂存字段并累出并集包围盒，再拿包围盒
     * 调 {@link #setBounds}，最后按 {@code this.x/this.y} 反推环心。{@code setBounds} 保证这两个
     * 字段始终是包围盒左上角，于是 9 种锚点组合共用一套公式，不必逐锚点分支。</p>
     */
    private void renderSaturn(List<ModuleEntry> rows, float s, float textScale) {
        UiTree.Scope scope = renderScope();

        float planetR = planetRadius.getValue().floatValue() * s;
        float ringRadiusX = planetR * ringSpread.getValue().floatValue();
        float ringRadiusY = ringRadiusX * ringFlatten.getValue().floatValue();
        // 采样表只在设置或 HUD 缩放变化时重建，渲染循环里不再出现三角函数。
        ring.configure(ringSegments.getValue(), ringRadiusX, ringRadiusY,
                ringTilt.getValue().floatValue());

        float lineHeight = textHeight(textScale, FONT_TEXT);
        float chipHeight = Math.max(SATURN_CHIP_HEIGHT * s, lineHeight + 4.0f * s);
        float iconScale = showIcon.getValue() ? s * SATURN_ICON_SCALE : 0.0f;
        int count = rows.size();
        long now = System.currentTimeMillis();

        // 包围盒先由环和行星撑开，再并入每个吊牌，全程都是相对环心的偏移量。
        float minDx = -Math.max(ring.halfWidth(), planetR);
        float minDy = -Math.max(ring.halfHeight(), planetR);
        float maxDx = -minDx;
        float maxDy = -minDy;

        for (int i = 0; i < count; i++) {
            ModuleEntry entry = rows.get(i);
            entry.measureIcon(iconScale);
            entry.rowWidth = saturnChipWidth(entry, s);
            entry.layoutOnRing(ring, slotTurn(i, count), now, chipHeight);

            minDx = Math.min(minDx, entry.chipDx);
            minDy = Math.min(minDy, entry.chipDy);
            maxDx = Math.max(maxDx, entry.chipDx + entry.chipW);
            maxDy = Math.max(maxDy, entry.chipDy + entry.chipH);
        }

        float spanX = maxDx - minDx;
        float spanY = maxDy - minDy;
        float boundsWidth = Math.max(MIN_BOUNDS, spanX);
        float boundsHeight = Math.max(MIN_BOUNDS, spanY);
        setBounds(boundsWidth, boundsHeight);

        // 包围盒被 MIN_BOUNDS 撑大时把多出来的空间平分到两侧，星系仍然停在框中央。
        float centerX = this.x - minDx + (boundsWidth - spanX) / 2.0f;
        float centerY = this.y - minDy + (boundsHeight - spanY) / 2.0f;

        float timedHue = timedHue();
        // 头部始终不透明，即使一个模块都没开启也留出可拖拽的实体。
        Color headerAccent = rainbow.getValue() ? rainbowColor(timedHue, 0) : textColor.getValue();
        int phase = ring.phaseOffset(ringPhase());

        drawRingBand(scope, centerX, centerY, planetR, s, headerAccent, phase, false);
        // 后半环吊牌与后半环带同层，靠发射顺序压在环带之上，随后被行星层挡住形成穿插。
        drawSaturnChips(scope, rows, centerX, centerY, textScale, lineHeight, s, timedHue, false);
        scope.layer(SATURN_LAYER_PLANET, planetScope ->
                drawPlanet(planetScope, centerX, centerY, planetR, s, headerAccent, count));
        scope.layer(SATURN_LAYER_FRONT_RING, frontScope ->
                drawRingBand(frontScope, centerX, centerY, planetR, s, headerAccent, phase, true));
        scope.layer(SATURN_LAYER_FRONT_CHIP, chipScope ->
                drawSaturnChips(chipScope, rows, centerX, centerY, textScale, lineHeight, s, timedHue, true));
    }

    /**
     * 把排序后的序号换算成环上的槽位，单位为整圈。
     *
     * <p>镜像交替铺开：序号 0 占前半环正中，之后每两个一组分别向右、向左各挪一格，于是左右
     * 成对、整体镜像对称。奇数个模块时最大的空隙正好落在后半环正中；偶数个模块时最低优先级
     * 刚好补上那个位置。</p>
     */
    private static float slotTurn(int index, int count) {
        if (count <= 1) return SATURN_FRONT_TURN;
        float offset = ((index + 1) / 2) / (float) count;
        // 屏幕 Y 轴向下，cos 为正的一侧在右，所以奇数序号取负号才落到右边。
        float turn = (index & 1) == 1 ? SATURN_FRONT_TURN - offset : SATURN_FRONT_TURN + offset;
        return turn - (float) Math.floor(turn);
    }

    /** 按纵深分两遍绘制吊牌，{@code front} 决定这一遍只画前半环还是只画后半环上的吊牌。 */
    private void drawSaturnChips(UiTree.Scope scope, List<ModuleEntry> rows, float centerX, float centerY,
                                 float textScale, float lineHeight, float s, float timedHue, boolean front) {
        boolean useRainbow = rainbow.getValue();
        Color plainAccent = useRainbow ? null : textColor.getValue();
        for (int i = 0; i < rows.size(); i++) {
            ModuleEntry entry = rows.get(i);
            if (entry.chipFront != front) continue;
            Color accent = useRainbow ? rainbowColor(timedHue, i + 1) : plainAccent;
            drawSaturnChip(scope, entry, centerX, centerY, textScale, lineHeight, s, accent);
        }
    }

    /** 吊牌宽度：左右内边距 + 卫星点 + 可选图标 + 名称 + 可选信息徽标。 */
    private float saturnChipWidth(ModuleEntry entry, float s) {
        float width = (SATURN_CHIP_PADDING_START + SATURN_CHIP_PADDING_END) * s
                + (SATURN_MOON_SIZE + SATURN_MOON_GAP) * s
                + entry.nameWidth;
        if (entry.iconWidth > 0.0f) {
            width += entry.iconWidth + SATURN_ITEM_GAP * s;
        }
        if (entry.infoWidth > 0.0f) {
            width += entry.infoWidth + SATURN_INFO_PADDING * 2.0f * s + SATURN_ITEM_GAP * s;
        }
        return width;
    }

    /** 环带高光的动画相位，取值 0~1；速度为 0 时静止。 */
    private float ringPhase() {
        float speed = ringSpeed.getValue().floatValue();
        if (speed <= 0.0f) return 0.0f;
        float periodMs = Math.max(120.0f, 4000.0f / speed);
        return (System.currentTimeMillis() % (long) periodMs) / periodMs;
    }

    /**
     * 绘制半个环带：主环带用直径等于环宽的圆点铺出厚度，外缘再叠一圈更细更亮的点作为亮边。
     *
     * <p>高光只调亮度不动透明度，密集圆点叠加时不会出现色带。完全落在行星圆盘内的后半环
     * 采样点直接跳过，默认设置下能省掉约三分之一节点。</p>
     *
     * @param front true 绘制靠近观察者的前半环，false 绘制绕到行星背面的后半环
     * @param phase {@link SaturnRingGeometry#phaseOffset(float)} 换算出的查表位移
     */
    private void drawRingBand(UiTree.Scope scope, float centerX, float centerY, float planetR,
            float s, Color accent, int phase, boolean front) {
        float thickness = Math.max(0.5f, ringWidth.getValue().floatValue() * s);
        float bandHalf = thickness / 2.0f;
        float edgeThickness = Math.max(0.4f, thickness * SATURN_EDGE_THICKNESS);
        float edgeHalf = edgeThickness / 2.0f;
        float alpha = SATURN_BAND_ALPHA * (front ? 1.0f : SATURN_BACK_ALPHA);
        // 采样半径小于该阈值时，主环带与亮边都完全埋在行星圆盘内，可以整点丢弃。
        float cullRadius = front ? 0.0f : Math.max(0.0f, (planetR - bandHalf) / SATURN_EDGE_SCALE);
        float cullSq = cullRadius * cullRadius;

        for (int index = 0; index < ring.segments(); index++) {
            if (ring.isFront(index) != front) continue;
            float dx = ring.offsetX(index);
            float dy = ring.offsetY(index);
            if (cullRadius > 0.0f && dx * dx + dy * dy <= cullSq) continue;

            float brightness = SATURN_SHIMMER_BASE + SATURN_SHIMMER_RANGE * ring.shimmerAt(index, phase);
            float bandX = centerX + dx * SATURN_BAND_SCALE;
            float bandY = centerY + dy * SATURN_BAND_SCALE;
            scope.roundRect(bandX - bandHalf, bandY - bandHalf, thickness, thickness, bandHalf,
                    lumin(accent, alpha, brightness));

            float edgeX = centerX + dx * SATURN_EDGE_SCALE;
            float edgeY = centerY + dy * SATURN_EDGE_SCALE;
            scope.roundRect(edgeX - edgeHalf, edgeY - edgeHalf, edgeThickness, edgeThickness, edgeHalf,
                    lumin(accent, alpha, brightness * SATURN_EDGE_BRIGHTNESS));
        }
    }

    /**
     * 行星本体：四角渐变模拟受光面，左上角压一枚椭圆高光，边缘描一圈强调色轮廓，中心可选
     * 显示已启用模块数量。
     *
     * <p>同一 layer 内 SHADOW → ROUND_RECT → ROUND_RECT_OUTLINE → GLYPH 的管线顺序由批次
     * 规划器保证，因此阴影、球体、轮廓和文字不需要再拆分 layer。</p>
     */
    private void drawPlanet(UiTree.Scope scope, float centerX, float centerY, float planetR,
            float s, Color accent, int count) {
        float diameter = planetR * 2.0f;
        float left = centerX - planetR;
        float top = centerY - planetR;
        Color surface = backgroundColor.getValue();

        if (backgroundBlur.getValue()) {
            MinecraftUiRuntime2612.current().applyBlur(new MinecraftBlurRegion2612(
                    new UiRect(left, top, diameter, diameter),
                    MinecraftBlurRegion2612.CornerRadii.uniform(planetR),
                    blurStrength.getValue(),
                    List.of()
            ));
        }
        if (drawShadow.getValue()) {
            scope.shadow(left, top, diameter, diameter, planetR,
                    shadowBlur.getValue().floatValue(), lumin(shadowColor.getValue()));
        }

        // 单半径重载即可画出正圆，四角颜色从左上受光排到右下暗面。
        scope.roundRectGradient(left, top, diameter, diameter, planetR,
                lumin(accent, SATURN_PLANET_LIT_ALPHA, SATURN_PLANET_LIT_BRIGHTNESS),
                lumin(surface, 1.0f, SATURN_PLANET_EDGE_BRIGHTNESS),
                lumin(surface, 1.0f, SATURN_PLANET_SHADE_BRIGHTNESS),
                lumin(surface, 1.0f, SATURN_PLANET_EDGE_BRIGHTNESS));

        float highlightWidth = planetR * SATURN_HIGHLIGHT_SCALE;
        float highlightHeight = highlightWidth * 0.72f;
        scope.roundRect(centerX - planetR * 0.52f, centerY - planetR * 0.58f,
                highlightWidth, highlightHeight, highlightHeight / 2.0f,
                lumin(accent, SATURN_HIGHLIGHT_ALPHA, SATURN_HIGHLIGHT_BRIGHTNESS));
        scope.outline(left, top, diameter, diameter, planetR,
                Math.max(0.5f, planetR * 0.06f), lumin(accent, SATURN_RIM_ALPHA));

        if (!showCount.getValue()) return;
        String label = Integer.toString(count);
        float countScale = s * SATURN_COUNT_SCALE;
        float labelWidth = textWidth(label, countScale, FONT_TEXT);
        float labelHeight = textHeight(countScale, FONT_TEXT);
        scope.text(label, centerX - labelWidth / 2.0f, centerY - labelHeight / 2.0f, countScale,
                lumin(accent, 1.0f, SATURN_COUNT_BRIGHTNESS), FONT_TEXT);
    }

    /**
     * 药丸吊牌：圆角取可见高度一半，靠行星那一侧更亮。
     *
     * <p>位置和尺寸全部取自 {@link ModuleEntry#layoutOnRing} 写好的暂存字段——偏移相对环心，
     * 尺寸已乘过量化后的透视系数。文字、图标和内边距同样只乘这一个系数：字体度量对缩放严格
     * 线性，所以按比例换算出的宽高与重新测量完全一致，而量化又保证吊牌沿环滑动时字体度量
     * 缓存的键只在十几个档位间跳动，不会每帧换键。</p>
     */
    private void drawSaturnChip(UiTree.Scope scope, ModuleEntry entry, float centerX, float centerY,
            float textScale, float lineHeight, float s, Color accent) {
        float chipWidth = entry.chipW;
        float chipHeight = entry.chipH;
        if (chipWidth <= 0.05f || chipHeight <= 0.05f) return;

        float chipX = centerX + entry.chipDx;
        float chipY = centerY + entry.chipDy;
        float alpha = entry.chipAlpha;
        boolean mirrored = entry.chipMirrored;
        float depthScale = entry.chipScale;
        float cs = s * depthScale;
        float ts = textScale * depthScale;
        float scaledLine = lineHeight * depthScale;
        float radius = chipHeight / 2.0f;

        if (backgroundBlur.getValue()) {
            MinecraftUiRuntime2612.current().applyBlur(new MinecraftBlurRegion2612(
                    new UiRect(chipX, chipY, chipWidth, chipHeight),
                    MinecraftBlurRegion2612.CornerRadii.uniform(radius),
                    blurStrength.getValue(),
                    List.of()
            ));
        }
        if (drawShadow.getValue()) {
            scope.shadow(chipX, chipY, chipWidth, chipHeight, radius,
                    shadowBlur.getValue().floatValue(), lumin(shadowColor.getValue(), alpha));
        }

        Color background = backgroundColor.getValue();
        LuminColor lit = lumin(background, alpha, SATURN_CHIP_LIT_BRIGHTNESS);
        LuminColor plain = lumin(background, alpha);
        scope.roundRectHorizontalGradient(chipX, chipY, chipWidth, chipHeight, radius,
                mirrored ? plain : lit, mirrored ? lit : plain);
        scope.outline(chipX, chipY, chipWidth, chipHeight, radius, Math.max(0.4f, 0.6f * cs),
                lumin(accent, alpha * SATURN_CHIP_OUTLINE_ALPHA));

        // 内容按“起始侧偏移”排布，镜像只发生在 itemX 里，宽度算式与 saturnChipWidth 完全一致。
        float cursor = SATURN_CHIP_PADDING_START * cs;
        float moonSize = SATURN_MOON_SIZE * cs;
        scope.roundRect(itemX(chipX, chipWidth, cursor, moonSize, mirrored),
                chipY + (chipHeight - moonSize) / 2.0f, moonSize, moonSize, moonSize / 2.0f,
                lumin(accent, alpha, SATURN_MOON_BRIGHTNESS));
        cursor += moonSize + SATURN_MOON_GAP * cs;

        if (entry.iconWidth > 0.0f) {
            float iconWidth = entry.iconWidth * depthScale;
            scope.text(entry.icon, itemX(chipX, chipWidth, cursor, iconWidth, mirrored),
                    chipY + (chipHeight - entry.iconHeight * depthScale) / 2.0f,
                    entry.iconScale * depthScale,
                    lumin(accent, alpha * SATURN_ICON_ALPHA), FONT_ICONS);
            cursor += iconWidth + SATURN_ITEM_GAP * cs;
        }

        float nameWidth = entry.nameWidth * depthScale;
        float textY = chipY + (chipHeight - scaledLine) / 2.0f;
        scope.text(entry.name, itemX(chipX, chipWidth, cursor, nameWidth, mirrored), textY,
                ts, lumin(accent, alpha), FONT_TEXT);
        cursor += nameWidth;

        if (entry.infoWidth <= 0.0f) return;
        float badgeWidth = entry.infoWidth * depthScale + SATURN_INFO_PADDING * 2.0f * cs;
        cursor += SATURN_ITEM_GAP * cs;
        float badgeX = itemX(chipX, chipWidth, cursor, badgeWidth, mirrored);
        float badgeHeight = Math.min(chipHeight, scaledLine + 2.0f * cs);
        // 徽标是 ROUND_RECT、信息是 GLYPH，同 layer 内管线顺序保证底板永远在文字下面。
        scope.roundRect(badgeX, chipY + (chipHeight - badgeHeight) / 2.0f, badgeWidth, badgeHeight,
                badgeHeight / 2.0f, lumin(accent, alpha * SATURN_BADGE_ALPHA));
        scope.text(entry.info, badgeX + SATURN_INFO_PADDING * cs, textY, ts,
                lumin(infoColor.getValue(), alpha), FONT_TEXT);
    }

    /** 把从吊牌起始侧量起的偏移换算成屏幕 X；吊牌落在环左半边时整条内容镜像排列。 */
    private static float itemX(float chipX, float chipWidth, float offset, float itemWidth,
            boolean mirrored) {
        return mirrored ? chipX + chipWidth - offset - itemWidth : chipX + offset;
    }

    /** 紧凑行：背景条 + 可选侧边强调条 + 单行文本。 */
    private void drawCompactRow(UiTree.Scope scope, ModuleEntry entry, float rowX, float rowY,
            float rowHeight, float paddingX, float tagWidth, float textScale, float lineHeight,
            Color accent) {
        float alpha = Mth.clamp(entry.progress, 0.0f, 1.0f);
        float backgroundX = mode.is(Mode.LEFT_TAG) ? rowX + tagWidth : rowX;
        float backgroundWidth = entry.width + paddingX * 2.0f;

        scope.rect(backgroundX, rowY, backgroundWidth, rowHeight,
                lumin(backgroundColor.getValue(), alpha));
        if (mode.is(Mode.LEFT_TAG)) {
            scope.rect(rowX, rowY, tagWidth, rowHeight, lumin(accent, alpha));
        } else if (mode.is(Mode.RIGHT_TAG)) {
            scope.rect(backgroundX + backgroundWidth, rowY, tagWidth, rowHeight,
                    lumin(accent, alpha));
        }

        float textY = rowY + Math.max(0.0f, (rowHeight - lineHeight) / 2.0f);
        drawCompactLine(scope, entry, backgroundX + paddingX, textY, textScale, accent, alpha);
    }

    /** 名称、方括号和信息分三段绘制，省掉每帧的字符串拼接。 */
    private void drawCompactLine(UiTree.Scope scope, ModuleEntry entry, float x, float y,
            float textScale, Color accent, float alpha) {
        scope.text(entry.name, x, y, textScale, lumin(accent, alpha), FONT_TEXT);
        if (entry.info.isEmpty()) return;

        float cursorX = x + entry.nameWidth;
        LuminColor bracket = lumin(bracketColor.getValue(), alpha);
        scope.text(" [", cursorX, y, textScale, bracket, FONT_TEXT);
        cursorX += entry.openBracketWidth;
        scope.text(entry.info, cursorX, y, textScale, lumin(infoColor.getValue(), alpha), FONT_TEXT);
        cursorX += entry.infoWidth;
        scope.text("]", cursorX, y, textScale, bracket, FONT_TEXT);
    }

    /** 展开行：图标框、名称框和信息框各是一枚圆角卡片，Right/Center 锚点时图标排到右侧。 */
    private void drawOpenRow(UiTree.Scope scope, ModuleEntry entry, float rowX, float rowY,
            float rowHeight, float radius, float iconGap, boolean withIcon, boolean iconOnLeft,
            float textScale, float lineHeight, Color accent) {
        float alpha = Mth.clamp(entry.progress, 0.0f, 1.0f);
        float visibleHeight = rowHeight * alpha;
        float textY = rowY + (visibleHeight - lineHeight) / 2.0f;
        boolean hasInfoBox = entry.infoBoxWidth > 0.0f;
        float textBoxX = rowX;

        if (withIcon) {
            float iconBoxX;
            if (iconOnLeft) {
                iconBoxX = rowX;
                textBoxX = rowX + rowHeight + iconGap;
            } else {
                iconBoxX = rowX + entry.rowWidth - rowHeight;
                textBoxX = iconBoxX - iconGap - entry.nameBoxWidth
                        - (hasInfoBox ? entry.infoBoxWidth + iconGap : 0.0f);
            }

            drawOpenBox(scope, iconBoxX, rowY, rowHeight, visibleHeight, radius, alpha);
            if (entry.iconWidth > 0.0f) {
                scope.text(entry.icon, iconBoxX + (rowHeight - entry.iconWidth) / 2.0f,
                        rowY + (visibleHeight - entry.iconHeight) / 2.0f, entry.iconScale,
                        lumin(accent, alpha * OPEN_ICON_ALPHA), FONT_ICONS);
            }

            if (hasInfoBox) {
                float infoBoxX = iconOnLeft
                        ? textBoxX + entry.nameBoxWidth + iconGap
                        : iconBoxX - iconGap - entry.infoBoxWidth;
                drawOpenBox(scope, infoBoxX, rowY, entry.infoBoxWidth, visibleHeight, radius, alpha);
                scope.text(entry.info, infoBoxX + (entry.infoBoxWidth - entry.infoWidth) / 2.0f,
                        textY, textScale, lumin(infoColor.getValue(), alpha), FONT_TEXT);
            }
        }

        drawOpenBox(scope, textBoxX, rowY, entry.nameBoxWidth, visibleHeight, radius, alpha);
        scope.text(entry.name, textBoxX + (entry.nameBoxWidth - entry.nameWidth) / 2.0f, textY,
                textScale, lumin(accent, alpha), FONT_TEXT);
    }

    /** 展开行的卡片底：可选模糊、可选阴影，再叠一层圆角背景。 */
    private void drawOpenBox(UiTree.Scope scope, float x, float y, float width, float height,
            float radius, float alpha) {
        if (backgroundBlur.getValue()) {
            MinecraftUiRuntime2612.current().applyBlur(new MinecraftBlurRegion2612(
                    new UiRect(x, y, width, height),
                    MinecraftBlurRegion2612.CornerRadii.uniform(radius),
                    blurStrength.getValue(),
                    List.of()
            ));
        }
        if (drawShadow.getValue()) {
            scope.shadow(x, y, width, height, radius, shadowBlur.getValue().floatValue(),
                    lumin(shadowColor.getValue(), alpha));
        }
        scope.roundRect(x, y, width, height, radius, lumin(backgroundColor.getValue(), alpha));
    }

    private float computeRowX(float rowWidth) {
        return switch (getHorizontalAnchor()) {
            case Right -> this.x + this.width - rowWidth;
            case Center -> this.x + (this.width - rowWidth) / 2.0f;
            default -> this.x;
        };
    }

    private boolean resolveState(Module module) {
        return module.isEnabled() && (showHidden.getValue()
                || (!module.isHidden() && (!bindOnly.getValue() || module.getKeyBind() != -1)));
    }

    /**
     * 六个比较器都是静态常量，切换排序方式或样式都不会每帧新建 lambda。
     *
     * <p>土星样式先按转头优先级分层，同层再退回用户选的 Sorting Mode，这样环上的顺序就是
     * “抢视角越凶的模块越靠前半环正中”。{@link List#sort} 是稳定排序，完全并列的模块会保持
     * {@code ModuleHolder} 的原始次序，不会在环上互相抖动。</p>
     */
    private Comparator<ModuleEntry> rowComparator(Style current) {
        boolean byPriority = current == Style.Saturn;
        return switch (sortingMode.getValue()) {
            case LENGTH -> byPriority ? PRIORITY_LENGTH_ORDER : LENGTH_ORDER;
            case ALPHABET -> byPriority ? PRIORITY_ALPHABET_ORDER : ALPHABET_ORDER;
            case CATEGORY -> byPriority ? PRIORITY_CATEGORY_ORDER : CATEGORY_ORDER;
        };
    }

    /** 彩虹动画相位，取值 0~1。 */
    private float timedHue() {
        float lengthMs = Math.max(1.0f, rainbowLength.getValue().floatValue() * 1000.0f);
        return (System.currentTimeMillis() % (long) lengthMs) / lengthMs;
    }

    private Color rainbowColor(float timedHue, int index) {
        float hue = timedHue + indexedHue.getValue().floatValue() * 0.05f * index;
        return new Color(Color.HSBtoRGB(hue, saturation.getValue().floatValue(),
                brightness.getValue().floatValue()));
    }

    /** 按倍率缩放透明度，直接产出 {@link LuminColor}，省掉中间的 {@link Color} 对象。 */
    private static LuminColor lumin(Color color, float alphaMultiplier) {
        return lumin(color, alphaMultiplier, 1.0f);
    }

    /**
     * 同时缩放透明度与亮度。亮度倍率大于 1 时颜色向白色靠拢，用于环带高光与卫星点；
     * 四个通道都会夹回 0~1。
     */
    private static LuminColor lumin(Color color, float alphaMultiplier, float brightnessMultiplier) {
        return new LuminColor(
                Mth.clamp(color.getRed() / 255.0f * brightnessMultiplier, 0.0f, 1.0f),
                Mth.clamp(color.getGreen() / 255.0f * brightnessMultiplier, 0.0f, 1.0f),
                Mth.clamp(color.getBlue() / 255.0f * brightnessMultiplier, 0.0f, 1.0f),
                Mth.clamp(color.getAlpha() / 255.0f * alphaMultiplier, 0.0f, 1.0f));
    }

    /**
     * 每个模块一份的渲染状态：开关动画 + 缓存好的文本度量。
     *
     * <p>实例常驻 {@link #entries}，因此稳定状态下渲染循环没有任何对象分配。文本宽度只在
     * 名称、信息、缩放或分类后缀真的变化时才重新测量；图标度量单独按缩放缓存，Open 与
     * Saturn 两种样式用的图标缩放不同也不会互相打断缓存。</p>
     */
    private static final class ModuleEntry {

        private final Module module;
        /** 转头优先级权重，构造时定格；不接管视角的模块记为 -1，排在最低优先级之后。 */
        private final int priorityWeight;

        /** 动画状态。 */
        private boolean target;
        private float progress;
        private float startProgress;
        private long lastChangeMs;

        /** 文本度量缓存的键。 */
        private String measuredName;
        private String measuredInfo;
        private String measuredCategory;
        private float measuredScale = Float.NaN;

        /** 文本度量缓存。 */
        private String name = "";
        private String info = "";
        private String sortKey = "";
        private int categoryOrder = Integer.MAX_VALUE;
        private float nameWidth;
        private float openBracketWidth;
        private float infoWidth;
        private float closeBracketWidth;
        private float width;

        /** 图标度量缓存，键为图标缩放；缩放为 0 表示本帧不画图标。 */
        private String icon = "";
        private float iconScale = Float.NaN;
        private float iconWidth;
        private float iconHeight;

        /** 每帧重算的布局暂存值，避免为此另建列表。 */
        private float rowWidth;
        private float nameBoxWidth;
        private float infoBoxWidth;

        /**
         * 沿环滑动的动画状态。{@code ringTurn} 为 {@code NaN} 表示这一轮还没落过位，
         * 此时直接吸附到目标槽位而不是从旧位置滑过来。
         */
        private float ringTurn = Float.NaN;
        private float ringStart;
        private float ringTarget;
        private long ringChangeMs;

        /** 土星样式每帧重算的吊牌几何，偏移相对环心。 */
        private float chipDx;
        private float chipDy;
        private float chipW;
        private float chipH;
        private float chipScale = 1.0f;
        private float chipAlpha;
        private boolean chipMirrored;
        private boolean chipFront;

        private ModuleEntry(Module module, boolean target) {
            this.module = module;
            Priority priority = module.rotationPriority();
            this.priorityWeight = priority == null ? -1 : priority.priority;
            this.target = target;
            this.progress = target ? 1.0f : 0.0f;
            this.startProgress = progress;
            this.lastChangeMs = System.currentTimeMillis();
        }

        /**
         * 把吊牌压到环上的目标槽位，并把这一帧的几何写进暂存字段。
         *
         * @param ring       环的采样表
         * @param targetTurn 目标槽位在环上的位置，单位为整圈
         * @param now        当前时间戳，用于推进沿环滑动的动画
         * @param baseHeight 未经透视缩放的吊牌高度
         */
        private void layoutOnRing(SaturnRingGeometry ring, float targetTurn, long now, float baseHeight) {
            ring.sampleAt(advanceRingTurn(targetTurn, now));
            float depth = ring.sampledDepth();
            float lateral = ring.sampledLateral();
            // depth 是 -1~1 的纵深，换成 0~1 的插值系数后同时驱动缩放和压暗。
            float mix = 0.5f + 0.5f * depth;

            float raw = SATURN_BACK_CHIP_SCALE + (1.0f - SATURN_BACK_CHIP_SCALE) * mix;
            chipScale = Math.round(raw * SATURN_SCALE_QUANTUM) / SATURN_SCALE_QUANTUM;
            chipW = rowWidth * chipScale;
            chipH = baseHeight * chipScale * progress;
            // 纵向压在采样点上；横向按左右分量在“向右展开”和“向左展开”之间插值，
            // 环的正前正后两点正好居中跨在环上。
            chipDx = ring.sampledX() - chipW * (0.5f - 0.5f * lateral);
            chipDy = ring.sampledY() - chipH / 2.0f;
            chipAlpha = Mth.clamp(progress * (SATURN_BACK_CHIP_ALPHA
                    + (1.0f - SATURN_BACK_CHIP_ALPHA) * mix), 0.0f, 1.0f);
            // 环左半边的吊牌整体镜像，卫星点和渐变亮端始终朝着行星。
            chipMirrored = lateral < 0.0f;
            chipFront = depth > 0.0f;
        }

        /**
         * 推进沿环滑动的动画并返回当前所在位置。
         *
         * <p>目标每次变化都从当前位置重新起算，并把差值折回 {@code [-0.5, 0.5)} 后沿最短弧
         * 滑动，因此换位不会绕整圈；起点同时归一化回 {@code [0, 1)}，多次换位也不会让数值
         * 越走越大而丢精度。</p>
         */
        private float advanceRingTurn(float target, long now) {
            if (Float.isNaN(ringTurn)) {
                ringTurn = target;
                ringStart = target;
                ringTarget = target;
                ringChangeMs = now;
                return ringTurn;
            }
            if (Math.abs(target - ringTarget) > RING_TURN_EPSILON) {
                ringStart = ringTurn - (float) Math.floor(ringTurn);
                ringTarget = target;
                ringChangeMs = now;
            }

            float delta = Mth.clamp((now - ringChangeMs) / (float) SATURN_RING_ANIMATION_MS, 0.0f, 1.0f);
            if (delta >= 1.0f) {
                ringTurn = ringTarget;
                ringStart = ringTarget;
            } else {
                float diff = ringTarget - ringStart;
                diff -= (float) Math.floor(diff + 0.5f);
                float eased = Easing.EASE_OUT_CUBIC.getFunction().apply(delta);
                ringTurn = ringStart + diff * eased;
            }
            return ringTurn;
        }

        /** 推进开关动画并返回当前进度；切换方向时从当前进度重新起算，不会跳变。 */
        private float update(boolean target, long now) {
            if (this.target != target) {
                this.target = target;
                this.startProgress = progress;
                this.lastChangeMs = now;
            }

            float delta = Mth.clamp((now - lastChangeMs) / (float) TOGGLE_ANIMATION_MS, 0.0f, 1.0f);
            if (this.target) {
                float eased = Easing.EASE_OUT_CUBIC.getFunction().apply(delta);
                progress = startProgress + (1.0f - startProgress) * eased;
            } else {
                float eased = Easing.EASE_IN_CUBIC.getFunction().apply(delta);
                progress = startProgress * (1.0f - eased);
            }

            if (delta >= 1.0f) {
                progress = this.target ? 1.0f : 0.0f;
                startProgress = progress;
                // 彻底隐去后丢掉环上的落位，下次启用时直接吸附到新槽位，不从旧位置滑过来。
                if (progress <= 0.0f) {
                    ringTurn = Float.NaN;
                }
            }
            return progress;
        }

        /**
         * 按需重新测量文本。缓存键是（模块名、信息、缩放、分类后缀），全部未变时直接返回，
         * 因此稳定状态下每帧只有四次浮点比较，没有字符串拼接也没有字体测量。
         */
        private void measure(float textScale, boolean withCategory) {
            Category category = module.getCategory();
            String rawName = module.getTranslatedName();
            String rawInfo = module.getInfo();
            if (rawInfo == null || rawInfo.isBlank()) {
                rawInfo = "";
            }
            String categorySuffix = withCategory && category != null ? category.getName() : null;

            if (textScale == measuredScale
                    && rawName.equals(measuredName)
                    && rawInfo.equals(measuredInfo)
                    && Objects.equals(categorySuffix, measuredCategory)) {
                return;
            }
            measuredScale = textScale;
            measuredName = rawName;
            measuredInfo = rawInfo;
            measuredCategory = categorySuffix;

            name = categorySuffix == null ? rawName : rawName + " [" + categorySuffix + "]";
            info = rawInfo;
            sortKey = rawName.toLowerCase(Locale.ROOT);
            categoryOrder = category == null ? Integer.MAX_VALUE : category.ordinal();

            nameWidth = textWidth(name, textScale, FONT_TEXT);
            boolean hasInfo = !info.isEmpty();
            openBracketWidth = hasInfo ? textWidth(" [", textScale, FONT_TEXT) : 0.0f;
            infoWidth = hasInfo ? textWidth(info, textScale, FONT_TEXT) : 0.0f;
            closeBracketWidth = hasInfo ? textWidth("]", textScale, FONT_TEXT) : 0.0f;
            width = nameWidth + openBracketWidth + infoWidth + closeBracketWidth;
        }

        /**
         * 按需重新测量分类图标。缓存键只是图标缩放，因为分类在运行期不会变；缩放传 0 表示
         * 本帧不画图标，此时清空度量，让调用方用 {@code iconWidth > 0} 判断即可。
         */
        private void measureIcon(float scale) {
            if (scale == iconScale) {
                return;
            }
            iconScale = scale;

            Category category = module.getCategory();
            icon = scale <= 0.0f || category == null ? "" : category.icon;
            if (icon.isEmpty()) {
                iconWidth = 0.0f;
                iconHeight = 0.0f;
                return;
            }
            iconWidth = textWidth(icon, scale, FONT_ICONS);
            iconHeight = textHeight(scale, FONT_ICONS);
        }
    }
}
