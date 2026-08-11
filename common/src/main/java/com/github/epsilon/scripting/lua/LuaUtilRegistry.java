// 由 scripts/generate_epsilon_lib.py 自动生成，请勿手工编辑。
package com.github.epsilon.scripting.lua;

import java.util.LinkedHashMap;
import java.util.Map;

public final class LuaUtilRegistry {
    private static final Map<String, Class<?>> BY_NAME = new LinkedHashMap<>();

    static {
        register("BlockRegistryUtils", com.github.epsilon.utils.world.BlockRegistryUtils.class);
        register("BlockUtils", com.github.epsilon.utils.world.BlockUtils.class);
        register("ChatUtils", com.github.epsilon.utils.player.ChatUtils.class);
        register("ClickSlotUtils", com.github.epsilon.utils.player.ClickSlotUtils.class);
        register("ClientUtils", com.github.epsilon.utils.client.ClientUtils.class);
        register("ColorUtils", com.github.epsilon.utils.render.ColorUtils.class);
        register("DamageUtils", com.github.epsilon.utils.combat.DamageUtils.class);
        register("EnchantmentUtils", com.github.epsilon.utils.player.EnchantmentUtils.class);
        register("HoleUtils", com.github.epsilon.utils.world.hole.HoleUtils.class);
        register("InteractionUtils", com.github.epsilon.utils.player.InteractionUtils.class);
        register("InvUtils", com.github.epsilon.utils.player.InvUtils.class);
        register("KeybindUtils", com.github.epsilon.utils.client.KeybindUtils.class);
        register("MathUtils", com.github.epsilon.utils.math.MathUtils.class);
        register("MoveUtils", com.github.epsilon.utils.player.MoveUtils.class);
        register("PacketUtils", com.github.epsilon.utils.network.PacketUtils.class);
        register("PlayerUtils", com.github.epsilon.utils.player.PlayerUtils.class);
        register("RaytraceUtils", com.github.epsilon.utils.rotation.RaytraceUtils.class);
        register("RotationUtils", com.github.epsilon.utils.rotation.RotationUtils.class);
        register("ScissorUtils", com.github.epsilon.utils.render.ScissorUtils.class);
        register("TimerUtils", com.github.epsilon.utils.timer.TimerUtils.class);
        register("WorldToScreen", com.github.epsilon.utils.render.WorldToScreen.class);
    }

    private LuaUtilRegistry() {
    }

    public static Class<?> resolve(String name) {
        Class<?> utilClass = BY_NAME.get(name);
        if (utilClass == null) {
            throw new IllegalArgumentException("未知 Epsilon util: " + name
                    + "，可用名称: " + String.join(", ", BY_NAME.keySet()));
        }
        return utilClass;
    }

    private static void register(String name, Class<?> type) {
        Class<?> previous = BY_NAME.putIfAbsent(name, type);
        if (previous != null) throw new IllegalStateException("重复 Epsilon util name: " + name);
    }
}
