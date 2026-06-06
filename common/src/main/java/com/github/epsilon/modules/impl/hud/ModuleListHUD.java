package com.github.epsilon.modules.impl.hud;

import com.github.epsilon.graphics.renderers.RoundRectRenderer;
import com.github.epsilon.graphics.renderers.ShadowRenderer;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.graphics.shaders.BlurShader;
import com.github.epsilon.graphics.text.StaticFontLoader;
import com.github.epsilon.managers.ModuleManager;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.HudModule;
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

public class ModuleListHUD extends HudModule {

    public static final ModuleListHUD INSTANCE = new ModuleListHUD();

    private ModuleListHUD() {
        super("Module List HUD", Category.HUD, 0f, 0f, 50f, 50f);
    }

    private final DoubleSetting scale = doubleSetting("Scale", 1.0, 0.5, 2.0, 0.1);
    private final DoubleSetting textScaleOffset = doubleSetting("Text Scale Offset", -0.2, -0.5, 0.5, 0.05);
    private final DoubleSetting cornerRadius = doubleSetting("Corner Radius", 4.0, 0.0, 14.0, 0.5);
    private final DoubleSetting animSpeed = doubleSetting("Animation Speed", 10.0, 1.0, 20.0, 0.5);

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
        if (nullCheck()) return;

        List<ItemInfo> items = collectItems(deltaTracker);
        if (items.isEmpty()) return;

        TextRenderer textRenderer = textRendererSupplier.get();
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

    private float computeRowX(float rowWidth, HorizontalAnchor hAnchor) {
        return switch (hAnchor) {
            case Right -> this.x + this.width - rowWidth;
            case Center -> this.x + (this.width - rowWidth) / 2.0f;
            default -> this.x;
        };
    }

    private List<ItemInfo> collectItems(DeltaTracker delta) {
        List<Module> allModules = ModuleManager.INSTANCE.getModules();
        float frameTime = delta == null ? 0.05f : delta.getGameTimeDeltaTicks() / 20.0f;
        float speed = animSpeed.getValue().floatValue();

        for (Module module : allModules) {
            float target = module.isEnabled() ? 1.0f : 0.0f;
            float current = moduleAlphaMap.getOrDefault(module, 0.0f);

            if (Math.abs(current - target) > 0.001f) {
                current = Mth.lerp(speed * frameTime, current, target);
                moduleAlphaMap.put(module, current);
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

    private record ItemInfo(Module module, String text, float boxWidth, float totalWidth, float alpha, String hudInfo,
                            float hudInfoWidth) {
    }

}
