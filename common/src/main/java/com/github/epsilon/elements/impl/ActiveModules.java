package com.github.epsilon.elements.impl;

import com.github.epsilon.elements.HudModule;
import com.github.epsilon.graphics.renderers.RectRenderer;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.graphics.text.StaticFontLoader;
import com.github.epsilon.holders.ModuleHolder;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.utils.render.animation.Easing;
import com.google.common.base.Suppliers;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class ActiveModules extends HudModule {

    public static final ActiveModules INSTANCE = new ActiveModules();

    private ActiveModules() {
        super("Active Modules", 0f, 2f, 96f, 20f);
    }

    private enum Style {

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

    private final EnumSetting<Style> style = enumSetting("Style", );
    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.LEFT_TAG);
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

    private final Supplier<RectRenderer> rectRendererSupplier = Suppliers.memoize(RectRenderer::create);
    private final Supplier<TextRenderer> textRendererSupplier = Suppliers.memoize(TextRenderer::create);

    private final Map<Module, ModuleToggleFlag> toggleFlags = new HashMap<>();

    private static final long ANIMATION_DURATION_MS = 300L;
    private static final float MIN_BOUNDS = 20.0f;

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        TextRenderer textRenderer = textRendererSupplier.get();
        RectRenderer rectRenderer = rectRendererSupplier.get();

        float s = scale.getValue().floatValue();
        float textScale = 0.72f * s;
        float lineHeight = textRenderer.getHeight(textScale, StaticFontLoader.DEFAULT) + 2.0f * s;
        float paddingX = 2.0f * s;
        float tagWidth = mode.is(Mode.FRAME) ? 0.0f : 2.0f * s;

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

            ModuleLine line = ModuleLine.create(module, textRenderer, textScale);
            float rowWidth = line.width + paddingX * 2.0f + tagWidth;
            rows.add(new RenderRow(module, line, progress, rowWidth));
        }

        rows.sort(rowComparator());

        float maxWidth = MIN_BOUNDS;
        float totalHeight = MIN_BOUNDS;
        if (!rows.isEmpty()) {
            totalHeight = 0.0f;
            for (RenderRow row : rows) {
                maxWidth = Math.max(maxWidth, row.rowWidth);
                totalHeight += lineHeight * row.progress;
            }
        }

        setBounds(maxWidth, Math.max(totalHeight, MIN_BOUNDS));
        if (rows.isEmpty()) return;

        boolean rightAligned = getHorizontalAnchor() == HorizontalAnchor.Right;
        boolean bottomAligned = getVerticalAnchor() == VerticalAnchor.Bottom;
        float currentY = bottomAligned ? this.y + this.height : this.y;
        float timedHue = timedHue();

        for (int i = 0; i < rows.size(); i++) {
            RenderRow row = rows.get(i);
            float visibleHeight = lineHeight * row.progress;
            float rowY = bottomAligned ? currentY - visibleHeight : currentY;
            float targetX = computeRowX(row.rowWidth);
            float slideOffset = row.rowWidth * (1.0f - row.progress);
            float rowX = rightAligned ? targetX + slideOffset : targetX - slideOffset;
            Color accent = rainbow.getValue() ? rainbowColor(timedHue, i) : textColor.getValue();

            drawRow(rectRenderer, textRenderer, row, rowX, rowY, visibleHeight, paddingX, tagWidth, textScale, accent);

            if (bottomAligned) {
                currentY -= visibleHeight;
            } else {
                currentY += visibleHeight;
            }
        }

        rectRenderer.drawAndClear();
        textRenderer.drawAndClear();
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
        return module.isEnabled()
                && (showHidden.getValue() || (!module.isHidden() && (!bindOnly.getValue() || module.getKeyBind() != -1)));
    }

    private float computeRowX(float rowWidth) {
        return switch (getHorizontalAnchor()) {
            case Right -> this.x + this.width - rowWidth;
            case Center -> this.x + (this.width - rowWidth) / 2.0f;
            default -> this.x;
        };
    }

    private void drawRow(
            RectRenderer rectRenderer,
            TextRenderer textRenderer,
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

        rectRenderer.addRect(backgroundX, rowY, backgroundWidth, rowHeight, rowBackground);

        if (mode.is(Mode.LEFT_TAG)) {
            rectRenderer.addRect(rowX, rowY, tagWidth, rowHeight, withAlpha(accent, row.progress));
        } else if (mode.is(Mode.RIGHT_TAG)) {
            rectRenderer.addRect(backgroundX + backgroundWidth, rowY, tagWidth, rowHeight, withAlpha(accent, row.progress));
        }

        float textX = backgroundX + paddingX;
        float textY = rowY + Math.max(0.0f, (rowHeight - textRenderer.getHeight(textScale, StaticFontLoader.DEFAULT)) / 2.0f);
        drawLine(textRenderer, row.line, textX, textY, textScale, withAlpha(accent, row.progress), row.progress);
    }

    private void drawLine(TextRenderer textRenderer, ModuleLine line, float x, float y, float textScale, Color nameColor, float alpha) {
        textRenderer.addText(line.name, x, y, textScale, nameColor, StaticFontLoader.DEFAULT);
        float cursorX = x + line.nameWidth;

        if (line.info.isEmpty()) return;

        Color bracket = withAlpha(bracketColor.getValue(), alpha);
        Color info = withAlpha(infoColor.getValue(), alpha);

        textRenderer.addText(" [", cursorX, y, textScale, bracket, StaticFontLoader.DEFAULT);
        cursorX += line.openBracketWidth;
        textRenderer.addText(line.info, cursorX, y, textScale, info, StaticFontLoader.DEFAULT);
        cursorX += line.infoWidth;
        textRenderer.addText("]", cursorX, y, textScale, bracket, StaticFontLoader.DEFAULT);
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

            float delta = Mth.clamp((now - lastChangeMs) / (float) ANIMATION_DURATION_MS, 0.0f, 1.0f);
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

    private static class ModuleLine {
        private final String name;
        private final String info;
        private final float nameWidth;
        private final float openBracketWidth;
        private final float infoWidth;
        private final float width;

        private ModuleLine(String name, String info, float nameWidth, float openBracketWidth, float infoWidth, float closeBracketWidth) {
            this.name = name;
            this.info = info;
            this.nameWidth = nameWidth;
            this.openBracketWidth = openBracketWidth;
            this.infoWidth = infoWidth;
            this.width = nameWidth + (info.isEmpty() ? 0.0f : openBracketWidth + infoWidth + closeBracketWidth);
        }

        private static ModuleLine create(Module module, TextRenderer textRenderer, float textScale) {
            String name = module.getTranslatedName();
            String info = module.getInfo();
            if (info == null || info.isBlank()) {
                info = "";
            }

            float nameWidth = textRenderer.getWidth(name, textScale, StaticFontLoader.DEFAULT);
            float openBracketWidth = info.isEmpty() ? 0.0f : textRenderer.getWidth(" [", textScale, StaticFontLoader.DEFAULT);
            float infoWidth = info.isEmpty() ? 0.0f : textRenderer.getWidth(info, textScale, StaticFontLoader.DEFAULT);
            float closeBracketWidth = info.isEmpty() ? 0.0f : textRenderer.getWidth("]", textScale, StaticFontLoader.DEFAULT);
            return new ModuleLine(name, info, nameWidth, openBracketWidth, infoWidth, closeBracketWidth);
        }
    }

}
