package com.github.epsilon.modules.impl.render;

import com.github.epsilon.managers.FriendManager;
import com.github.epsilon.managers.ShaderManager;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.SettingGroup;
import com.github.epsilon.settings.impl.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;

import java.awt.*;

public class Shaders extends Module {

    public static final Shaders INSTANCE = new Shaders();

    private Shaders() {
        super("Shaders", Category.RENDER);
    }

    private final SettingGroup select = settingGroup("Select");
    private final SettingGroup colors = settingGroup("Colors");

    private final BoolSetting hands = boolSetting("Hands", true).group(select);
    private final BoolSetting players = boolSetting("Players", true).group(select);
    private final BoolSetting self = boolSetting("Self", true, players::getValue).group(select);
    private final BoolSetting friends = boolSetting("Friends", true).group(select);
    private final BoolSetting crystals = boolSetting("Crystals", true).group(select);
    private final BoolSetting creatures = boolSetting("Creatures", false).group(select);
    private final BoolSetting monsters = boolSetting("Monsters", false).group(select);
    private final BoolSetting ambients = boolSetting("Ambients", false).group(select);
    private final BoolSetting others = boolSetting("Others", false).group(select);

    public final EnumSetting<ShaderManager.Shader> mode = enumSetting("Mode", ShaderManager.Shader.Default);
    public final EnumSetting<ShaderManager.Shader> handsMode = enumSetting("Hands Mode", ShaderManager.Shader.Default);

    public final IntSetting maxRange = intSetting("Max Range", 64, 16, 256, 1, () -> players.getValue() || crystals.getValue() || friends.getValue() || creatures.getValue() || monsters.getValue() || ambients.getValue() || others.getValue());
    public final DoubleSetting factor = doubleSetting("Gradient Factor", 2.0, 0.0, 20.0, 0.1, () -> mode.is(ShaderManager.Shader.Gradient) || handsMode.is(ShaderManager.Shader.Gradient));
    public final DoubleSetting gradient = doubleSetting("Gradient", 2.0, 0.0, 20.0, 0.1, () -> mode.is(ShaderManager.Shader.Gradient) || handsMode.is(ShaderManager.Shader.Gradient));
    public final IntSetting alpha2 = intSetting("Gradient Alpha", 170, 0, 255, 1, () -> mode.is(ShaderManager.Shader.Gradient) || handsMode.is(ShaderManager.Shader.Gradient));
    public final IntSetting lineWidth = intSetting("Line Width", 2, 0, 500, 1);
    public final IntSetting quality = intSetting("Quality", 3, 0, 6, 1);
    public final IntSetting octaves = intSetting("Smoke Octaves", 10, 5, 30, 1);
    public final IntSetting fillAlpha = intSetting("Fill Alpha", 170, 0, 255, 1);
    public final BoolSetting glow = boolSetting("Smoke Glow", true);

    public final ColorSetting outlineColor = colorSetting("Outline", new Color(255, 255, 255, 136)).group(colors);
    public final ColorSetting smokeOutlineColor1 = colorSetting("Smoke Outline", new Color(255, 0, 0, 136), () -> mode.is(ShaderManager.Shader.Smoke) || handsMode.is(ShaderManager.Shader.Smoke)).group(colors);
    public final ColorSetting smokeOutlineColor2 = colorSetting("Smoke Outline 2", new Color(255, 0, 0, 136), () -> mode.is(ShaderManager.Shader.Smoke) || handsMode.is(ShaderManager.Shader.Smoke)).group(colors);
    public final ColorSetting fillColor1 = colorSetting("Fill", new Color(255, 255, 255, 136)).group(colors);
    public final ColorSetting fillColor2 = colorSetting("Smoke Fill", new Color(255, 255, 255, 136)).group(colors);
    public final ColorSetting fillColor3 = colorSetting("Smoke Fill 2", new Color(255, 255, 255, 136)).group(colors);

    public boolean shouldRenderHands() {
        return hands.getValue();
    }

    public boolean shouldRender(Entity entity) {
        if (mc.player == null) {
            return false;
        }

        if (mc.player.distanceToSqr(entity.position()) > maxRange.getValue() * maxRange.getValue()) {
            return false;
        }

        if (entity instanceof Player player) {
            if (player == mc.player && !self.getValue()) {
                return false;
            }
            if (FriendManager.INSTANCE.isFriend(player)) {
                return friends.getValue();
            }
            return players.getValue();
        }

        if (entity instanceof EndCrystal) {
            return crystals.getValue();
        }

        return switch (entity.getType().getCategory()) {
            case CREATURE, WATER_CREATURE -> creatures.getValue();
            case MONSTER -> monsters.getValue();
            case AMBIENT, WATER_AMBIENT -> ambients.getValue();
            default -> others.getValue();
        };
    }

}
