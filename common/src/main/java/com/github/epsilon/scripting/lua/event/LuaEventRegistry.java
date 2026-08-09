package com.github.epsilon.scripting.lua.event;

import com.github.epsilon.events.impl.*;

import java.util.LinkedHashMap;
import java.util.Map;

public final class LuaEventRegistry {
    private static final Map<String, Class<?>> BY_NAME = new LinkedHashMap<>();
    private static final Map<String, Class<?>> BY_ID = new LinkedHashMap<>();

    static {
        register("AfterRender3DEvent", AfterRender3DEvent.class);
        register("AttackEntityEvent", AttackEntityEvent.class);
        register("AttackSlowDownEvent", AttackSlowDownEvent.class);
        register("AttackYawEvent", AttackYawEvent.class);
        register("BlockCollisionEvent", BlockCollisionEvent.class);
        register("ChunkOcclusionEvent", ChunkOcclusionEvent.class);
        register("ClientTickEvent.Pre", ClientTickEvent.Pre.class, "client_tick.pre");
        register("ClientTickEvent.Post", ClientTickEvent.Post.class, "client_tick.post");
        register("DestroyBlockEvent", DestroyBlockEvent.class);
        register("FallFlyingEvent", FallFlyingEvent.class);
        register("FireworkRotationEvent", FireworkRotationEvent.class);
        register("GameJoinedEvent", GameJoinedEvent.class);
        register("GameLeftEvent", GameLeftEvent.class);
        register("JumpEvent", JumpEvent.class);
        register("KeyboardInputEvent", KeyboardInputEvent.class);
        register("KeyPressEvent", KeyPressEvent.class);
        register("LevelUpdateEvent", LevelUpdateEvent.class);
        register("MousePressEvent", MousePressEvent.class);
        register("MouseScrollEvent", MouseScrollEvent.class);
        register("MoveEvent", MoveEvent.class);
        register("OpenScreenEvent", OpenScreenEvent.class);
        register("PacketEvent.Send", PacketEvent.Send.class, "packet.send");
        register("PacketEvent.Receive", PacketEvent.Receive.class, "packet.receive");
        register("PlayerTickEvent.Pre", PlayerTickEvent.Pre.class, "player_tick.pre");
        register("PlayerTickEvent.Post", PlayerTickEvent.Post.class, "player_tick.post");
        register("RaytraceEvent", RaytraceEvent.class);
        register("Render2DEvent.Level", Render2DEvent.Level.class, "render2d.level");
        register("Render2DEvent.HUD", Render2DEvent.HUD.class, "render2d.hud");
        register("Render3DEvent", Render3DEvent.class, "render3d");
        register("RespawnEvent", RespawnEvent.class);
        register("RightClickEvent", RightClickEvent.class);
        register("RotationAnimationEvent", RotationAnimationEvent.class);
        register("SendPositionEvent", SendPositionEvent.class);
        register("SlowdownEvent", SlowdownEvent.class);
        register("StartDestroyBlockEvent", StartDestroyBlockEvent.class);
        register("StartUseItemEvent", StartUseItemEvent.class);
        register("StrafeEvent", StrafeEvent.class);
        register("SwingHandEvent", SwingHandEvent.class);
        register("TravelEvent", TravelEvent.class);
        register("UseItemEvent", UseItemEvent.class);
        register("UseItemRaytraceEvent", UseItemRaytraceEvent.class);
    }

    private LuaEventRegistry() {
    }

    public static Class<?> resolveName(String name) {
        Class<?> eventClass = BY_NAME.get(name);
        if (eventClass == null) throw new IllegalArgumentException("未知 Epsilon event: " + name);
        return eventClass;
    }

    public static Class<?> resolveId(String id) {
        Class<?> eventClass = BY_ID.get(id);
        if (eventClass == null) throw new IllegalArgumentException("未知 Epsilon event ID: " + id);
        return eventClass;
    }

    private static void register(String name, Class<?> type, String... ids) {
        if (BY_NAME.putIfAbsent(name, type) != null) throw new IllegalStateException("重复 event name: " + name);
        for (String id : ids) {
            if (BY_ID.putIfAbsent(id, type) != null) throw new IllegalStateException("重复 event ID: " + id);
        }
    }
}
