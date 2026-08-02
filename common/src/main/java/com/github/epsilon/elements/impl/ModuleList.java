package com.github.epsilon.elements.impl;

import com.github.epsilon.elements.HudModule;
import com.github.slmpc.lumingraphics.mc.v2612.runtime.MinecraftBlurRegion2612;
import com.github.slmpc.lumingraphics.mc.v2612.runtime.MinecraftUiRuntime2612;
import com.github.slmpc.lumingraphics.ui.geometry.UiRect;
import com.github.slmpc.lumingraphics.ui.tree.UiTree;
import com.github.epsilon.holders.ModuleHolder;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.*;
import com.github.epsilon.utils.render.animation.Easing;
import net.minecraft.client.DeltaTracker;
import net.minecraft.util.Mth;

import java.awt.*;
import java.util.*;
import java.util.List;

public class ModuleList extends HudModule {

    public static final ModuleList INSTANCE = new ModuleList();

    private ModuleList() {
        super("Module List", 0f, 2f, 96f, 20f);
    }

    private enum Style {
        Compact,
        Open
    }

    private enum Mode {
        LEFT_TAG,
        RIGHT_TAG,
        FRAME
    }

    private enum SortingMode {
        LENGTH,
        ALPHABET,
        CATEGORY
    }

    private final EnumSetting<Style> style = enumSetting("Style", Style.Open);
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

    private final BoolSetting showOpenCategory = boolSetting("Show Category", false, () -> style.is(Style.Open));
    private final BoolSetting showOpenIcon = boolSetting("Show Icon", true, () -> style.is(Style.Open));
    private final DoubleSetting openTextScaleOffset = doubleSetting("Text Scale Offset", -0.2, -0.5, 0.5, 0.05, () -> style.is(Style.Open));
    private final DoubleSetting openCornerRadius = doubleSetting("Corner Radius", 4.0, 0.0, 14.0, 0.5, () -> style.is(Style.Open));
    private final BoolSetting drawOpenShadow = boolSetting("Drop Shadow", true, () -> style.is(Style.Open));
    private final DoubleSetting openShadowBlur = doubleSetting("Shadow Blur", DEFAULT_SHADOW_BLUR, MIN_SHADOW_BLUR, MAX_SHADOW_BLUR, SHADOW_BLUR_STEP, () -> style.is(Style.Open) && drawOpenShadow.getValue());
    private final ColorSetting openShadowColor = colorSetting("Shadow Color", DEFAULT_SHADOW_COLOR, () -> style.is(Style.Open) && drawOpenShadow.getValue());
    private final BoolSetting openBackgroundBlur = boolSetting("Background Blur", false, () -> style.is(Style.Open));
    private final IntSetting openBlurStrength = intSetting("Blur Strength", 5, 1, 16, 1, () -> style.is(Style.Open) && openBackgroundBlur.getValue());

    private final Map<Module, ModuleToggleFlag> toggleFlags = new HashMap<>();

    private static final float MIN_BOUNDS = 20.0f;
    private static final float OPEN_ROW_HEIGHT = 18.0f;
    private static final float OPEN_ROW_SPACING = 2.0f;
    private static final float OPEN_NAME_PADDING_START = 3.5f;
    private static final float OPEN_NAME_PADDING_END = 5.0f;
    private static final float OPEN_ICON_GAP = 2.0f;
    private static final float OPEN_INFO_PADDING_START = 2.5f;
    private static final float OPEN_INFO_PADDING_END = 3.5f;

    @Override
    public void render(DeltaTracker deltaTracker) {
        float s = scale.getValue().floatValue();
        float textScale = style.is(Style.Open) ? Math.max(0.1f, s + openTextScaleOffset.getValue().floatValue()) : 0.72f * s;
        List<RenderRow> rows = collectRows(textScale);

        switch (style.getValue()) {
            case Compact -> renderCompact(rows, s, textScale);
            case Open -> renderOpen(rows, s, textScale);
        }
    }

    private List<RenderRow> collectRows(float textScale) {
        List<Module> modules = ModuleHolder.INSTANCE.getModules();
        Set<Module> liveModules = new HashSet<>(modules);
        toggleFlags.keySet().removeIf(module -> !liveModules.contains(module));

        List<RenderRow> rows = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Module module : modules) {
            boolean state = resolveState(module);
            ModuleToggleFlag flag = toggleFlags.computeIfAbsent(module, ignored -> new ModuleToggleFlag(state));
            float progress = flag.update(state, now);
            if (progress <= 0.001f) continue;

            ModuleLine line = ModuleLine.create(module, textScale, showOpenCategory.getValue() && style.is(Style.Open));
            rows.add(new RenderRow(module, line, progress, 0.0f));
        }

        rows.sort(rowComparator());
        return rows;
    }

    private void renderCompact(List<RenderRow> rows, float s, float textScale) {
        UiTree.Scope scope = renderScope();
        float lineHeight = textHeight(textScale, "epsilon-default") + 2.0f * s;
        float paddingX = 2.0f * s;
        float tagWidth = mode.is(Mode.FRAME) ? 0.0f : 2.0f * s;

        List<RenderRow> sizedRows = new ArrayList<>(rows.size());
        float maxWidth = MIN_BOUNDS;
        float totalHeight = rows.isEmpty() ? MIN_BOUNDS : 0.0f;
        for (RenderRow row : rows) {
            float rowWidth = row.line.width + paddingX * 2.0f + tagWidth;
            sizedRows.add(new RenderRow(row.module, row.line, row.progress, rowWidth));
            maxWidth = Math.max(maxWidth, rowWidth);
            totalHeight += lineHeight * row.progress;
        }

        setBounds(maxWidth, Math.max(totalHeight, MIN_BOUNDS));
        if (sizedRows.isEmpty()) return;

        boolean rightAligned = getHorizontalAnchor() == HorizontalAnchor.Right;
        boolean bottomAligned = getVerticalAnchor() == VerticalAnchor.Bottom;
        float currentY = bottomAligned ? this.y + this.height : this.y;
        float timedHue = timedHue();

        for (int i = 0; i < sizedRows.size(); i++) {
            RenderRow row = sizedRows.get(i);
            float visibleHeight = lineHeight * row.progress;
            float rowY = bottomAligned ? currentY - visibleHeight : currentY;
            float targetX = computeRowX(row.rowWidth);
            float slideOffset = row.rowWidth * (1.0f - row.progress);
            float rowX = rightAligned ? targetX + slideOffset : targetX - slideOffset;
            Color accent = rainbow.getValue() ? rainbowColor(timedHue, i) : textColor.getValue();

            drawCompactRow(scope, row, rowX, rowY, visibleHeight, paddingX, tagWidth, textScale, accent);

            if (bottomAligned) {
                currentY -= visibleHeight;
            } else {
                currentY += visibleHeight;
            }
        }

    }

    private void renderOpen(List<RenderRow> rows, float s, float textScale) {
        UiTree.Scope scope = renderScope();

        float rowHeight = OPEN_ROW_HEIGHT * s;
        float spacing = OPEN_ROW_SPACING * s;
        float iconGap = OPEN_ICON_GAP * s;
        float namePadStart = OPEN_NAME_PADDING_START * s;
        float namePadEnd = OPEN_NAME_PADDING_END * s;
        float infoPadStart = OPEN_INFO_PADDING_START * s;
        float infoPadEnd = OPEN_INFO_PADDING_END * s;
        float radius = openCornerRadius.getValue().floatValue() * s;

        List<RenderRow> sizedRows = new ArrayList<>(rows.size());
        float maxWidth = MIN_BOUNDS;
        float totalHeight = rows.isEmpty() ? MIN_BOUNDS : 0.0f;
        boolean first = true;
        for (RenderRow row : rows) {
            float infoBoxWidth = row.line.info.isEmpty() || !showOpenIcon.getValue() ? 0.0f : infoPadStart + row.line.infoWidth + infoPadEnd;
            float nameBoxWidth = namePadStart + row.line.nameWidth + namePadEnd;
            float rowWidth = nameBoxWidth;
            if (showOpenIcon.getValue()) {
                rowWidth += rowHeight + iconGap;
                if (infoBoxWidth > 0.0f) {
                    rowWidth += iconGap + infoBoxWidth;
                }
            }

            RenderRow sizedRow = new RenderRow(row.module, row.line.withOpenWidths(nameBoxWidth, infoBoxWidth), row.progress, rowWidth);
            sizedRows.add(sizedRow);
            maxWidth = Math.max(maxWidth, rowWidth);
            totalHeight += (rowHeight + (first ? 0.0f : spacing)) * row.progress;
            first = false;
        }

        setBounds(maxWidth, Math.max(totalHeight, MIN_BOUNDS));
        if (sizedRows.isEmpty()) return;

        boolean bottomAligned = getVerticalAnchor() == VerticalAnchor.Bottom;
        boolean iconOnLeft = getHorizontalAnchor() == HorizontalAnchor.Left;
        float currentY = bottomAligned ? this.y + this.height : this.y;
        float timedHue = timedHue();
        boolean firstRow = true;

        for (int i = 0; i < sizedRows.size(); i++) {
            RenderRow row = sizedRows.get(i);
            float rowStep = rowHeight * row.progress;
            float spacingStep = firstRow ? 0.0f : spacing * row.progress;
            if (bottomAligned) {
                currentY -= spacingStep + rowStep;
            } else {
                currentY += spacingStep;
            }
            firstRow = false;

            float rowX = computeRowX(row.rowWidth);
            Color accent = rainbow.getValue() ? rainbowColor(timedHue, i) : textColor.getValue();
            drawOpenRow(scope, row, rowX, currentY, rowHeight, radius, iconGap, iconOnLeft, textScale, accent);

            if (!bottomAligned) {
                currentY += rowStep;
            }
        }

    }

    private Comparator<RenderRow> rowComparator() {
        return switch (sortingMode.getValue()) {
            case LENGTH -> Comparator.comparingDouble((RenderRow row) -> -row.line.width);
            case ALPHABET -> Comparator.comparing(row -> row.module.getTranslatedName().toLowerCase(Locale.ROOT));
            case CATEGORY -> Comparator
                    .comparingInt((RenderRow row) -> categoryOrder(row.module.getCategory()))
                    .thenComparing(row -> row.module.getTranslatedName().toLowerCase(Locale.ROOT));
        };
    }

    private int categoryOrder(Category category) {
        return category == null ? Integer.MAX_VALUE : category.ordinal();
    }

    private boolean resolveState(Module module) {
        return module.isEnabled() && (showHidden.getValue() || (!module.isHidden() && (!bindOnly.getValue() || module.getKeyBind() != -1)));
    }

    private float computeRowX(float rowWidth) {
        return switch (getHorizontalAnchor()) {
            case Right -> this.x + this.width - rowWidth;
            case Center -> this.x + (this.width - rowWidth) / 2.0f;
            default -> this.x;
        };
    }

    private void drawCompactRow(
            UiTree.Scope scope,
            RenderRow row,
            float rowX,
            float rowY,
            float rowHeight,
            float paddingX,
            float tagWidth,
            float textScale,
            Color accent
    ) {
        float backgroundX = mode.is(Mode.LEFT_TAG) ? rowX + tagWidth : rowX;
        float backgroundWidth = row.line.width + paddingX * 2.0f;
        Color rowBackground = withAlpha(backgroundColor.getValue(), row.progress);

        scope.rect(backgroundX, rowY, backgroundWidth, rowHeight, lumin(rowBackground));

        if (mode.is(Mode.LEFT_TAG)) {
            scope.rect(rowX, rowY, tagWidth, rowHeight, lumin(withAlpha(accent, row.progress)));
        } else if (mode.is(Mode.RIGHT_TAG)) {
            scope.rect(backgroundX + backgroundWidth, rowY, tagWidth, rowHeight, lumin(withAlpha(accent, row.progress)));
        }

        float textX = backgroundX + paddingX;
        float textY = rowY + Math.max(0.0f, (rowHeight - textHeight(textScale, "epsilon-default")) / 2.0f);
        drawCompactLine(scope, row.line, textX, textY, textScale, withAlpha(accent, row.progress), row.progress);
    }

    private void drawOpenRow(
            UiTree.Scope scope,
            RenderRow row,
            float rowX,
            float rowY,
            float rowHeight,
            float radius,
            float iconGap,
            boolean iconOnLeft,
            float textScale,
            Color accent
    ) {
        float alpha = Mth.clamp(row.progress, 0.0f, 1.0f);
        float visibleHeight = rowHeight * alpha;
        float textBoxX;
        float iconBoxX;
        boolean hasInfoBox = row.line.openInfoBoxWidth > 0.0f;

        if (showOpenIcon.getValue()) {
            if (iconOnLeft) {
                iconBoxX = rowX;
                textBoxX = rowX + rowHeight + iconGap;
            } else {
                iconBoxX = rowX + row.rowWidth - rowHeight;
                textBoxX = hasInfoBox
                        ? iconBoxX - iconGap - row.line.openInfoBoxWidth - iconGap - row.line.openNameBoxWidth
                        : iconBoxX - iconGap - row.line.openNameBoxWidth;
            }

            drawOpenBox(scope, iconBoxX, rowY, rowHeight, visibleHeight, radius, alpha);

            String iconChar = row.module.getCategory() == null ? "" : row.module.getCategory().icon;
            if (!iconChar.isEmpty()) {
                float iconScale = scale.getValue().floatValue();
                float iconWidth = textWidth(iconChar, iconScale, "epsilon-icons");
                float iconHeight = textHeight(iconScale, "epsilon-icons");
                float iconX = iconBoxX + (rowHeight - iconWidth) / 2.0f;
                float iconY = rowY + (visibleHeight - iconHeight) / 2.0f;
                scope.text(iconChar, iconX, iconY, iconScale, lumin(withAlpha(accent, alpha * 0.82f)), "epsilon-icons");
            }

            if (hasInfoBox) {
                float infoBoxX = iconOnLeft
                        ? textBoxX + row.line.openNameBoxWidth + iconGap
                        : iconBoxX - iconGap - row.line.openInfoBoxWidth;
                drawOpenBox(scope, infoBoxX, rowY, row.line.openInfoBoxWidth, visibleHeight, radius, alpha);
                float infoX = infoBoxX + (row.line.openInfoBoxWidth - row.line.infoWidth) / 2.0f;
                float infoY = rowY + (visibleHeight - textHeight(textScale, "epsilon-default")) / 2.0f;
                scope.text(row.line.info, infoX, infoY, textScale, lumin(withAlpha(infoColor.getValue(), alpha)));
            }
        } else {
            textBoxX = rowX;
        }

        drawOpenBox(scope, textBoxX, rowY, row.line.openNameBoxWidth, visibleHeight, radius, alpha);
        float textX = textBoxX + (row.line.openNameBoxWidth - row.line.nameWidth) / 2.0f;
        float textY = rowY + (visibleHeight - textHeight(textScale, "epsilon-default")) / 2.0f;
        scope.text(row.line.name, textX, textY, textScale, lumin(withAlpha(accent, alpha)));
    }

    private void drawOpenBox(UiTree.Scope scope, float x, float y, float width, float height, float radius, float alpha) {
        Color background = withAlpha(backgroundColor.getValue(), alpha);
        if (openBackgroundBlur.getValue()) {
            MinecraftUiRuntime2612.current().applyBlur(new MinecraftBlurRegion2612(
                    new UiRect(x, y, width, height),
                    MinecraftBlurRegion2612.CornerRadii.uniform(radius),
                    openBlurStrength.getValue(),
                    List.of()
            ));
        }
        if (drawOpenShadow.getValue()) {
            scope.shadow(x, y, width, height, radius, openShadowBlur.getValue().floatValue(),
                    lumin(withAlpha(openShadowColor.getValue(), alpha)));
        }
        scope.roundRect(x, y, width, height, radius, lumin(background));
    }

    private void drawCompactLine(UiTree.Scope scope, ModuleLine line, float x, float y, float textScale, Color nameColor, float alpha) {
        scope.text(line.name, x, y, textScale, lumin(nameColor));
        float cursorX = x + line.nameWidth;

        if (line.info.isEmpty()) return;

        Color bracket = withAlpha(bracketColor.getValue(), alpha);
        Color info = withAlpha(infoColor.getValue(), alpha);

        scope.text(" [", cursorX, y, textScale, lumin(bracket));
        cursorX += line.openBracketWidth;
        scope.text(line.info, cursorX, y, textScale, lumin(info));
        cursorX += line.infoWidth;
        scope.text("]", cursorX, y, textScale, lumin(bracket));
    }

    private float timedHue() {
        float lengthMs = Math.max(1.0f, rainbowLength.getValue().floatValue() * 1000.0f);
        return (System.currentTimeMillis() % (long) lengthMs) / lengthMs;
    }

    private Color rainbowColor(float timedHue, int index) {
        float hue = timedHue + indexedHue.getValue().floatValue() * 0.05f * index;
        int rgb = Color.HSBtoRGB(hue, saturation.getValue().floatValue(), brightness.getValue().floatValue());
        return new Color(rgb);
    }

    private Color withAlpha(Color color, float alphaMultiplier) {
        float multiplier = Mth.clamp(alphaMultiplier, 0.0f, 1.0f);
        int alpha = Mth.clamp((int) (color.getAlpha() * multiplier), 0, 255);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private static class ModuleToggleFlag {
        private boolean target;
        private float startProgress;
        private float progress;
        private long lastChangeMs;

        private ModuleToggleFlag(boolean target) {
            this.target = target;
            this.progress = target ? 1.0f : 0.0f;
            this.startProgress = progress;
            this.lastChangeMs = System.currentTimeMillis();
        }

        private float update(boolean target, long now) {
            if (this.target != target) {
                this.target = target;
                this.startProgress = progress;
                this.lastChangeMs = now;
            }

            float delta = Mth.clamp((now - lastChangeMs) / (float) 300L, 0.0f, 1.0f);
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
            }

            return progress;
        }
    }

    private record RenderRow(Module module, ModuleLine line, float progress, float rowWidth) {
    }

    private record ModuleLine(String name, String info, float nameWidth, float openBracketWidth, float infoWidth,
                              float width, float openNameBoxWidth, float openInfoBoxWidth) {
        private ModuleLine(String name, String info, float nameWidth, float openBracketWidth, float infoWidth, float closeBracketWidth) {
            this(name, info, nameWidth, openBracketWidth, infoWidth, nameWidth + (info.isEmpty() ? 0.0f : openBracketWidth + infoWidth + closeBracketWidth), 0.0f, 0.0f);
        }

        private ModuleLine withOpenWidths(float openNameBoxWidth, float openInfoBoxWidth) {
            return new ModuleLine(name, info, nameWidth, openBracketWidth, infoWidth, width, openNameBoxWidth, openInfoBoxWidth);
        }

        private static ModuleLine create(Module module, float textScale, boolean showCategory) {
            String name = module.getTranslatedName();
            if (showCategory && module.getCategory() != null) {
                name += " [" + module.getCategory().getName() + "]";
            }

            String info = module.getInfo();
            if (info == null || info.isBlank()) {
                info = "";
            }

            float nameWidth = textWidth(name, textScale, "epsilon-default");
            float openBracketWidth = info.isEmpty() ? 0.0f : textWidth(" [", textScale, "epsilon-default");
            float infoWidth = info.isEmpty() ? 0.0f : textWidth(info, textScale, "epsilon-default");
            float closeBracketWidth = info.isEmpty() ? 0.0f : textWidth("]", textScale, "epsilon-default");
            return new ModuleLine(name, info, nameWidth, openBracketWidth, infoWidth, closeBracketWidth);
        }
    }

}
