package com.github.epsilon.modules.impl.render;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.Render2DEvent;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.renderers.RectRenderer;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.utils.render.WorldToScreen;
import com.google.common.base.Suppliers;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.joml.Vector4d;

import java.awt.*;
import java.util.function.Supplier;

public class ESP2D extends Module {

    public static final ESP2D INSTANCE = new ESP2D();

    private ESP2D() {
        super("ESP 2D", Category.RENDER);
    }

    private final BoolSetting players = boolSetting("Players", true);
    private final BoolSetting friends = boolSetting("Friends", true);
    private final BoolSetting crystals = boolSetting("Crystals", true);
    private final BoolSetting creatures = boolSetting("Creatures", false);
    private final BoolSetting monsters = boolSetting("Monsters", false);
    private final BoolSetting ambients = boolSetting("Ambients", false);
    private final BoolSetting others = boolSetting("Others", false);
    private final EnumSetting<ColorMode> colorMode = enumSetting("Color Mode", ColorMode.Sync);
    private final BoolSetting renderHealth = boolSetting("Render Health", true);
    private final DoubleSetting healthBarWidth = doubleSetting("Health Bar Width", 2.0, 0.5, 6.0, 0.5, renderHealth::getValue);
    private final BoolSetting renderBox = boolSetting("Render Box", true);
    private final BoolSetting outline = boolSetting("Outline", true, renderBox::getValue);

    private final ColorSetting playersColor = colorSetting("Players Color", new Color(0xFF9200), false, () -> colorMode.is(ColorMode.Custom));
    private final ColorSetting friendsColor = colorSetting("Friends Color", new Color(0x30FF00), false, () -> colorMode.is(ColorMode.Custom));
    private final ColorSetting crystalsColor = colorSetting("Crystals Color", new Color(0x00BBFF), false, () -> colorMode.is(ColorMode.Custom));
    private final ColorSetting creaturesColor = colorSetting("Creatures Color", new Color(0xA0A4A6), false, () -> colorMode.is(ColorMode.Custom));
    private final ColorSetting monstersColor = colorSetting("Monsters Color", new Color(0xFF0000), false, () -> colorMode.is(ColorMode.Custom));
    private final ColorSetting ambientsColor = colorSetting("Ambients Color", new Color(0x7B00FF), false, () -> colorMode.is(ColorMode.Custom));
    private final ColorSetting othersColor = colorSetting("Others Color", new Color(0xFF0062), false, () -> colorMode.is(ColorMode.Custom));
    private final ColorSetting healthBottomColor = colorSetting("Health Bottom Color", new Color(0xFF1100), false, () -> colorMode.is(ColorMode.Custom) && renderHealth.getValue());
    private final ColorSetting healthTopColor = colorSetting("Health Top Color", new Color(0x2FFF00), false, () -> colorMode.is(ColorMode.Custom) && renderHealth.getValue());

    private final Supplier<RectRenderer> rectRendererSupplier = Suppliers.memoize(RectRenderer::create);

    @EventHandler
    private void onRender2D(Render2DEvent.Level event) {
        if (nullCheck() || mc.options.hideGui) return;

        RectRenderer rectRenderer = rectRendererSupplier.get();
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        float screenWidth = LuminRenderSystem.getScaledWidth();
        float screenHeight = LuminRenderSystem.getScaledHeight();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!shouldRender(entity)) continue;

            Vector4d position = getEntityPositionOn2D(entity, partialTick);
            if (position == null) continue;
            if (position.z < 0.0 || position.w < 0.0 || position.x > screenWidth || position.y > screenHeight) continue;

            float x = (float) position.x;
            float y = (float) position.y;
            float endX = (float) position.z;
            float endY = (float) position.w;

            if (renderBox.getValue()) {
                if (outline.getValue()) {
                    Color black = Color.BLACK;
                    rectRenderer.addRect(x - 1.0f, y, 1.5f, endY - y + 0.5f, black);
                    rectRenderer.addRect(x - 1.0f, y - 0.5f, endX - x + 1.5f, 1.0f, black);
                    rectRenderer.addRect(endX - 1.0f, y, 1.5f, endY - y + 0.5f, black);
                    rectRenderer.addRect(x - 1.0f, endY - 1.0f, endX - x + 1.5f, 1.5f, black);
                }

                if (colorMode.is(ColorMode.Custom)) {
                    Color color = getEntityColor(entity);
                    drawSolidBox(rectRenderer, x, y, endX, endY, color);
                } else {
                    drawSyncBox(rectRenderer, x, y, endX, endY);
                }
            }

            if (entity instanceof LivingEntity livingEntity && renderHealth.getValue()) {
                drawHealthBar(rectRenderer, livingEntity, x, y, endY);
            }
        }

        rectRenderer.drawAndClear();
    }

    private boolean shouldRender(Entity entity) {
        if (entity == null || mc.player == null) return false;
        if (!entity.isAlive() || entity.isSpectator()) return false;

        if (entity instanceof Player player) {
            if (entity == mc.player) return false;
            if (Managers.FRIEND.isFriend(player)) return friends.getValue();
            return players.getValue();
        }

        if (entity instanceof EndCrystal) {
            return crystals.getValue();
        }

        MobCategory category = entity.getType().getCategory();
        return switch (category) {
            case CREATURE, WATER_CREATURE, AXOLOTLS, UNDERGROUND_WATER_CREATURE -> creatures.getValue();
            case MONSTER -> monsters.getValue();
            case AMBIENT, WATER_AMBIENT -> ambients.getValue();
            default -> others.getValue();
        };
    }

    private Color getEntityColor(Entity entity) {
        if (entity instanceof Player player) {
            if (Managers.FRIEND.isFriend(player)) return friendsColor.getValue();
            return playersColor.getValue();
        }

        if (entity instanceof EndCrystal) {
            return crystalsColor.getValue();
        }

        MobCategory category = entity.getType().getCategory();
        return switch (category) {
            case CREATURE, WATER_CREATURE, AXOLOTLS, UNDERGROUND_WATER_CREATURE -> creaturesColor.getValue();
            case MONSTER -> monstersColor.getValue();
            case AMBIENT, WATER_AMBIENT -> ambientsColor.getValue();
            default -> othersColor.getValue();
        };
    }

    private void drawSolidBox(RectRenderer rectRenderer, float x, float y, float endX, float endY, Color color) {
        rectRenderer.addRect(x - 0.5f, y, 0.5f, endY - y, color);
        rectRenderer.addRect(x, endY - 0.5f, endX - x, 0.5f, color);
        rectRenderer.addRect(x - 0.5f, y, endX - x + 0.5f, 0.5f, color);
        rectRenderer.addRect(endX - 0.5f, y, 0.5f, endY - y, color);
    }

    private void drawSyncBox(RectRenderer rectRenderer, float x, float y, float endX, float endY) {
        Color c0 = syncColor(0);
        Color c90 = syncColor(90);
        Color c180 = syncColor(180);
        Color c270 = syncColor(270);

        rectRenderer.addRectGradient(x - 0.5f, y, 0.5f, endY - y, c270, c0, c0, c270);
        rectRenderer.addRectGradient(x, endY - 0.5f, endX - x, 0.5f, c0, c180, c180, c0);
        rectRenderer.addRectGradient(x - 0.5f, y, endX - x + 0.5f, 0.5f, c180, c90, c90, c180);
        rectRenderer.addRectGradient(endX - 0.5f, y, 0.5f, endY - y, c90, c270, c270, c90);
    }

    private void drawHealthBar(RectRenderer rectRenderer, LivingEntity entity, float x, float y, float endY) {
        float height = endY - y;
        if (height <= 0.0f) return;

        float health = Managers.HEALTH.getHealth(entity);
        float maxHealth = Math.max(1.0f, entity.getMaxHealth() + Math.max(0.0f, entity.getAbsorptionAmount()));
        float healthRatio = Mth.clamp(health / maxHealth, 0.0f, 1.0f);
        float fillY = endY - height * healthRatio;

        float width = healthBarWidth.getValue().floatValue();
        float barX = x - 3.0f - width;

        rectRenderer.addRect(barX, y, width, height, Color.BLACK);

        if (colorMode.is(ColorMode.Custom)) {
            rectRenderer.addRectGradient(barX, fillY, width, endY - fillY, healthBottomColor.getValue(), healthBottomColor.getValue(), healthTopColor.getValue(), healthTopColor.getValue());
        } else {
            Color top = syncColor(90);
            Color bottom = syncColor(270);
            rectRenderer.addRectGradient(barX, fillY, width, endY - fillY, top, top, bottom, bottom);
        }
    }

    private Vector4d getEntityPositionOn2D(Entity entity, float partialTick) {
        double x = Mth.lerp(partialTick, entity.xOld, entity.getX());
        double y = Mth.lerp(partialTick, entity.yOld, entity.getY());
        double z = Mth.lerp(partialTick, entity.zOld, entity.getZ());

        AABB box = entity.getBoundingBox();
        AABB renderBox = new AABB(
                box.minX - entity.getX() + x - 0.05,
                box.minY - entity.getY() + y,
                box.minZ - entity.getZ() + z - 0.05,
                box.maxX - entity.getX() + x + 0.05,
                box.maxY - entity.getY() + y + 0.15,
                box.maxZ - entity.getZ() + z + 0.05
        );

        return WorldToScreen.projectAbsoluteAABBOn2D(renderBox);
    }

    private Color syncColor(int offset) {
        float hue = Mth.frac((System.currentTimeMillis() + offset * 12L) / 4500.0f);
        Color color = Color.getHSBColor(hue, 0.65f, 1.0f);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), 255);
    }

    private enum ColorMode {
        Sync,
        Custom
    }

}
