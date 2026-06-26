package com.github.epsilon.elements.impl;

import com.github.epsilon.elements.HudModule;
import com.github.epsilon.graphics.renderers.RoundRectRenderer;
import com.github.epsilon.graphics.renderers.ShadowRenderer;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.graphics.shaders.BlurShader;
import com.github.epsilon.graphics.text.StaticFontLoader;
import com.github.epsilon.holders.ModuleHolder;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.google.common.base.Suppliers;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Supplier;

public class ModuleList extends HudModule {

    public static final ModuleList INSTANCE = new ModuleList();

    private ModuleList() {
        super("Module List HUD", 0f, 0f, 50f, 50f);
    }

    private final DoubleSetting scale = doubleSetting("Scale", 1.0, 0.5, 2.0, 0.1);
    private final DoubleSetting textScaleOffset = doubleSetting("Text Scale Offset", -0.2, -0.5, 0.5, 0.05);
    private final DoubleSetting cornerRadius = doubleSetting("Corner Radius", 4.0, 0.0, 14.0, 0.5);
    private final DoubleSetting animSpeed = doubleSetting("Animation Speed", 10.0, 1.0, 20.0, 0.5);

<<<<<<< HEAD
=======
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
>>>>>>> 0d2c546 (使 GUI 基本所有元素保持垂直居中 (#309))
    private final ColorSetting backgroundColor = colorSetting("Background Color", new Color(15, 15, 15, 145));
    private final BoolSetting showCategory = boolSetting("Show Category", false);
    private final BoolSetting showIcon = boolSetting("Show Icon", true);

    private final BoolSetting drawShadow = boolSetting("Drop Shadow", true);
    private final DoubleSetting shadowBlur = doubleSetting("Shadow Blur", 2.2, 0.1, 32.0, 0.5, drawShadow::getValue);
    private final ColorSetting shadowColor = colorSetting("Shadow Color", new Color(0, 0, 0, 70), drawShadow::getValue);

    private final BoolSetting backgroundBlur = boolSetting("Background Blur", false); // 好他妈掉帧啊
    private final IntSetting blurStrength = intSetting("Blur Strength", 5, 1, 16, 1);

    private static final float ROW_HEIGHT = 18.0f;
    private static final float ROW_SPACING = 2.0f;
    private static final float NAME_PADDING_START = 3.5f;
    private static final float NAME_PADDING_END = 5.0f;
    private static final float ICON_GAP = 2.0f;
    private static final float HUD_INFO_PADDING_START = 2.5f;
    private static final float HUD_INFO_PADDING_END = 3.5f;

    private final Map<Module, Float> moduleAlphaMap = new HashMap<>();

    private final Supplier<TextRenderer> textRendererSupplier = Suppliers.memoize(TextRenderer::create);
    private final Supplier<RoundRectRenderer> roundRectRendererSupplier = Suppliers.memoize(RoundRectRenderer::create);
    private final Supplier<ShadowRenderer> shadowRendererSupplier = Suppliers.memoize(ShadowRenderer::create);

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        List<ItemInfo> items = collectItems(deltaTracker);
        if (items.isEmpty()) return;

        TextRenderer textRenderer = textRendererSupplier.get();
<<<<<<< HEAD
=======
        float s = scale.getValue().floatValue();
        float textScale = style.is(Style.Open) ? Math.max(0.1f, s + openTextScaleOffset.getValue().floatValue()) : 0.72f * s;
        List<RenderRow> rows = collectRows(textRenderer, textScale);

        switch (style.getValue()) {
            case Compact -> renderCompact(textRenderer, rows, s, textScale);
            case Open -> renderOpen(textRenderer, rows, s, textScale);
        }
    }

    private List<RenderRow> collectRows(TextRenderer textRenderer, float textScale) {
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

            ModuleLine line = ModuleLine.create(module, textRenderer, textScale, showOpenCategory.getValue() && style.is(Style.Open));
            rows.add(new RenderRow(module, line, progress, 0.0f));
        }

        rows.sort(rowComparator());
        return rows;
    }

    private void renderCompact(TextRenderer textRenderer, List<RenderRow> rows, float s, float textScale) {
        RectRenderer rectRenderer = rectRendererSupplier.get();
        float lineHeight = textRenderer.getHeight(textScale) + 2.0f * s;
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

            drawCompactRow(rectRenderer, textRenderer, row, rowX, rowY, visibleHeight, paddingX, tagWidth, textScale, accent);

            if (bottomAligned) {
                currentY -= visibleHeight;
            } else {
                currentY += visibleHeight;
            }
        }

        rectRenderer.drawAndClear();
        textRenderer.drawAndClear();
    }

    private void renderOpen(TextRenderer textRenderer, List<RenderRow> rows, float s, float textScale) {
>>>>>>> 0d2c546 (使 GUI 基本所有元素保持垂直居中 (#309))
        RoundRectRenderer roundRectRenderer = roundRectRendererSupplier.get();
        ShadowRenderer shadowRenderer = shadowRendererSupplier.get();

        float moduleScale = scale.getValue().floatValue();
        float renderScale = moduleScale + textScaleOffset.getValue().floatValue();
        float namePadStart = NAME_PADDING_START * moduleScale;
        float hudInfoPadStart = HUD_INFO_PADDING_START * moduleScale;
        float radius = cornerRadius.getValue().floatValue() * moduleScale;
        float rowHeight = ROW_HEIGHT * moduleScale;
        float spacing = ROW_SPACING * moduleScale;
        float iconGap = ICON_GAP * moduleScale;

        HorizontalAnchor hAnchor = getHorizontalAnchor();
        boolean iconOnLeft = hAnchor == HorizontalAnchor.Left;

        float currentY = this.y;
        boolean first = true;

        float maxWidth = 0.0f;
        float totalHeight = 0f;

        for (ItemInfo item : items) {
            if (item.alpha() <= 0.001f) continue;

            float alpha = Mth.clamp(item.alpha(), 0.0f, 1.0f);

            // Track bounds
            if (item.totalWidth() > maxWidth) maxWidth = item.totalWidth();
            totalHeight += (rowHeight + (first ? 0f : spacing)) * alpha;

            // Update render position
            if (!first) currentY += spacing * alpha;
            first = false;
            float boxWidth = item.boxWidth();
            float totalWidth = item.totalWidth();
            float rowX = computeRowX(totalWidth, hAnchor);

            float textBoxX, iconBoxX;
            Color textColor = new Color(255, 255, 255, (int) (235 * alpha));

            if (showIcon.getValue()) {
                boolean hasHudInfo = item.hudInfoWidth() > 0;

                if (iconOnLeft) {
                    iconBoxX = rowX;
                    textBoxX = rowX + rowHeight + iconGap;
                } else {
                    iconBoxX = rowX + totalWidth - rowHeight;
                    if (hasHudInfo) {
                        textBoxX = rowX + totalWidth - rowHeight - iconGap - item.hudInfoWidth() - iconGap - boxWidth;
                    } else {
                        textBoxX = rowX + totalWidth - rowHeight - iconGap - boxWidth;
                    }
                }

                if (backgroundBlur.getValue()) {
                    BlurShader.INSTANCE.render(iconBoxX, currentY, rowHeight, rowHeight, radius, blurStrength.getValue());
                }
                if (drawShadow.getValue()) {
                    shadowRenderer.addShadow(iconBoxX, currentY, rowHeight, rowHeight, radius, shadowBlur.getValue().floatValue(), withAlpha(shadowColor.getValue(), alpha));
                }
                roundRectRenderer.addRoundRect(iconBoxX, currentY, rowHeight, rowHeight, radius, withAlpha(backgroundColor.getValue(), alpha));

                String iconChar = item.module().getCategory().icon;
                float iconWidth = textRenderer.getWidth(iconChar, moduleScale, StaticFontLoader.ICONS);
                float iconHeight = textRenderer.getHeight(moduleScale, StaticFontLoader.ICONS);
                float iconX = iconBoxX + (rowHeight - iconWidth) / 2.0f - 1;
                float iconY = currentY + (rowHeight - iconHeight) / 2.0f - 2;
                textRenderer.addText(iconChar, iconX, iconY, moduleScale, new Color(255, 255, 255, (int) (180 * alpha)), StaticFontLoader.ICONS);

                if (hasHudInfo) {
                    float hudInfoBoxX;
                    if (iconOnLeft) {
                        hudInfoBoxX = textBoxX + boxWidth + iconGap;
                    } else {
                        hudInfoBoxX = iconBoxX - iconGap - item.hudInfoWidth();
                    }

                    if (backgroundBlur.getValue()) {
                        BlurShader.INSTANCE.render(hudInfoBoxX, currentY, item.hudInfoWidth(), rowHeight, radius, blurStrength.getValue());
                    }
                    if (drawShadow.getValue()) {
                        shadowRenderer.addShadow(hudInfoBoxX, currentY, item.hudInfoWidth(), rowHeight, radius, shadowBlur.getValue().floatValue(), withAlpha(shadowColor.getValue(), alpha));
                    }
                    roundRectRenderer.addRoundRect(hudInfoBoxX, currentY, item.hudInfoWidth(), rowHeight, radius, withAlpha(backgroundColor.getValue(), alpha));

                    float hudTextX = hudInfoBoxX + hudInfoPadStart;
                    float hudTextY = currentY + (rowHeight - textRenderer.getHeight(renderScale)) / 2.0f;
                    textRenderer.addText(item.hudInfo(), hudTextX, hudTextY - 1, renderScale, textColor);
                }
            } else {
                textBoxX = rowX;
                boxWidth = totalWidth;
            }

            if (backgroundBlur.getValue()) {
                BlurShader.INSTANCE.render(textBoxX, currentY, boxWidth, rowHeight, radius, blurStrength.getValue());
            }
            if (drawShadow.getValue()) {
                shadowRenderer.addShadow(textBoxX, currentY, boxWidth, rowHeight, radius, shadowBlur.getValue().floatValue(), withAlpha(shadowColor.getValue(), alpha));
            }
            roundRectRenderer.addRoundRect(textBoxX, currentY, boxWidth, rowHeight, radius, withAlpha(backgroundColor.getValue(), alpha));

            float textX = textBoxX + namePadStart;
            float textY = currentY + (rowHeight - textRenderer.getHeight(renderScale)) / 2.0f;
            textRenderer.addText(item.text(), textX, textY - 1, renderScale, textColor);

            currentY += rowHeight * alpha;
        }

        if (drawShadow.getValue()) shadowRenderer.drawAndClear();
        roundRectRenderer.drawAndClear();
        textRenderer.drawAndClear();

        setBounds(maxWidth, totalHeight);
    }

<<<<<<< HEAD
    private float computeRowX(float rowWidth, HorizontalAnchor hAnchor) {
        return switch (hAnchor) {
=======
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
>>>>>>> 0d2c546 (使 GUI 基本所有元素保持垂直居中 (#309))
            case Right -> this.x + this.width - rowWidth;
            case Center -> this.x + (this.width - rowWidth) / 2.0f;
            default -> this.x;
        };
    }

    private List<ItemInfo> collectItems(DeltaTracker delta) {
        List<Module> allModules = ModuleHolder.INSTANCE.getModules();
        float frameTime = delta == null ? 0.05f : delta.getGameTimeDeltaTicks() / 20.0f;
        float speed = animSpeed.getValue().floatValue();

        for (Module module : allModules) {
            float target = module.isEnabled() ? 1.0f : 0.0f;
            float current = moduleAlphaMap.getOrDefault(module, 0.0f);

<<<<<<< HEAD
            if (Math.abs(current - target) > 0.001f) {
                current = Mth.lerp(speed * frameTime, current, target);
                moduleAlphaMap.put(module, current);
=======
        if (mode.is(Mode.LEFT_TAG)) {
            rectRenderer.addRect(rowX, rowY, tagWidth, rowHeight, withAlpha(accent, row.progress));
        } else if (mode.is(Mode.RIGHT_TAG)) {
            rectRenderer.addRect(backgroundX + backgroundWidth, rowY, tagWidth, rowHeight, withAlpha(accent, row.progress));
        }

        float textX = backgroundX + paddingX;
        float textY = rowY + Math.max(0.0f, (rowHeight - textRenderer.getHeight(textScale)) / 2.0f);
        drawCompactLine(textRenderer, row.line, textX, textY, textScale, withAlpha(accent, row.progress), row.progress);
    }

    private void drawOpenRow(
            RoundRectRenderer roundRectRenderer,
            ShadowRenderer shadowRenderer,
            TextRenderer textRenderer,
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
>>>>>>> 0d2c546 (使 GUI 基本所有元素保持垂直居中 (#309))
            } else {
                moduleAlphaMap.put(module, target);
            }
        }

        List<Module> activeModules = allModules.stream()
                .filter(m -> !m.isHidden() && moduleAlphaMap.getOrDefault(m, 0.0f) > 0.001f)
                .sorted(Comparator.comparingInt(m -> -getRowWidth(m)))
                .toList();

        TextRenderer textRenderer = textRendererSupplier.get();
        float moduleScale = scale.getValue().floatValue();
        float renderScale = moduleScale + textScaleOffset.getValue().floatValue();
        float namePadStart = NAME_PADDING_START * moduleScale;
        float namePadEnd = NAME_PADDING_END * moduleScale;
        float hudInfoPadStart = HUD_INFO_PADDING_START * moduleScale;
        float hudInfoPadEnd = HUD_INFO_PADDING_END * moduleScale;

        List<ItemInfo> items = new ArrayList<>();
        for (Module module : activeModules) {
            String text = getFormattedName(module);
            float alpha = moduleAlphaMap.get(module);

            float textWidth = textRenderer.getWidth(text, renderScale);
            float boxWidth = namePadStart + textWidth + namePadEnd;

            String hudInfo = module.getInfo();
            float hudInfoWidth = 0;
            if (showIcon.getValue() && hudInfo != null && !hudInfo.isEmpty()) {
                hudInfoWidth = hudInfoPadStart + textRenderer.getWidth(hudInfo, renderScale) + hudInfoPadEnd;
            }

            float totalWidth = boxWidth;
            if (showIcon.getValue()) {
                totalWidth = boxWidth + ICON_GAP * moduleScale + ROW_HEIGHT * moduleScale;
                if (hudInfoWidth > 0) {
                    totalWidth += ICON_GAP * moduleScale + hudInfoWidth;
                }
            }

            items.add(new ItemInfo(module, text, boxWidth, totalWidth, alpha, hudInfo, hudInfoWidth));
        }

        return items;
    }

    private int getRowWidth(Module module) {
        TextRenderer textRenderer = textRendererSupplier.get();
        float moduleScale = scale.getValue().floatValue();
        float renderScale = moduleScale + textScaleOffset.getValue().floatValue();
        float namePadStart = NAME_PADDING_START * moduleScale;
        float namePadEnd = NAME_PADDING_END * moduleScale;
        float hudInfoPadStart = HUD_INFO_PADDING_START * moduleScale;
        float hudInfoPadEnd = HUD_INFO_PADDING_END * moduleScale;
        float textWidth = textRenderer.getWidth(getFormattedName(module), renderScale);
        float boxWidth = namePadStart + textWidth + namePadEnd;

        String hudInfo = module.getInfo();
        float hudInfoWidth = 0;
        if (showIcon.getValue() && hudInfo != null && !hudInfo.isEmpty()) {
            hudInfoWidth = hudInfoPadStart + textRenderer.getWidth(hudInfo, renderScale) + hudInfoPadEnd;
        }

        float total = boxWidth;
        if (showIcon.getValue()) {
            total = boxWidth + ICON_GAP * moduleScale + ROW_HEIGHT * moduleScale;
            if (hudInfoWidth > 0) {
                total += ICON_GAP * moduleScale + hudInfoWidth;
            }
        }
        return (int) total;
    }

    private String getFormattedName(Module module) {
        String text = module.getTranslatedName();
        if (showCategory.getValue()) {
            text += " [" + module.getCategory().getName() + "]";
        }
        return text;
    }

    private static Color withAlpha(Color color, float alphaMul) {
        int a = Mth.clamp((int) (color.getAlpha() * alphaMul), 0, 255);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), a);
    }

<<<<<<< HEAD
    private record ItemInfo(Module module, String text, float boxWidth, float totalWidth, float alpha, String hudInfo,
                            float hudInfoWidth) {
=======
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

        private static ModuleLine create(Module module, TextRenderer textRenderer, float textScale, boolean showCategory) {
            String name = module.getTranslatedName();
            if (showCategory && module.getCategory() != null) {
                name += " [" + module.getCategory().getName() + "]";
            }

            String info = module.getInfo();
            if (info == null || info.isBlank()) {
                info = "";
            }

            float nameWidth = textRenderer.getWidth(name, textScale);
            float openBracketWidth = info.isEmpty() ? 0.0f : textRenderer.getWidth(" [", textScale);
            float infoWidth = info.isEmpty() ? 0.0f : textRenderer.getWidth(info, textScale);
            float closeBracketWidth = info.isEmpty() ? 0.0f : textRenderer.getWidth("]", textScale);
            return new ModuleLine(name, info, nameWidth, openBracketWidth, infoWidth, closeBracketWidth);
        }
>>>>>>> 0d2c546 (使 GUI 基本所有元素保持垂直居中 (#309))
    }

}
