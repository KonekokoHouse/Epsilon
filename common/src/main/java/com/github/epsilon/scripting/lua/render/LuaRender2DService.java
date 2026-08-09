package com.github.epsilon.scripting.lua.render;

import com.github.epsilon.Constants;
import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.Render2DEvent;
import com.github.epsilon.gui.theme.EpsilonUiTheme;
import com.github.epsilon.modules.impl.ClientSetting;
import com.github.epsilon.scripting.lua.event.LuaEventListener;
import com.github.slmpc.lumingraphics.mc.v2612.runtime.MinecraftUiRuntime2612;
import com.github.slmpc.lumingraphics.ui.scene.UiLayer;
import com.github.slmpc.lumingraphics.ui.scene.UiScene;
import com.github.slmpc.lumingraphics.ui.tree.UiTree;

import java.util.Comparator;
import java.util.concurrent.CopyOnWriteArrayList;

public final class LuaRender2DService implements AutoCloseable {
    public static final LuaRender2DService INSTANCE = new LuaRender2DService();
    private final CopyOnWriteArrayList<LuaEventListener> hudListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<LuaEventListener> levelListeners = new CopyOnWriteArrayList<>();
    private UiScene levelScene;
    private MinecraftUiRuntime2612 levelRuntime;

    private LuaRender2DService() {
        EventBus.INSTANCE.subscribe(this);
    }

    public void register(LuaEventListener listener) {
        CopyOnWriteArrayList<LuaEventListener> target = listener.getTarget() == Render2DEvent.HUD.class
                ? hudListeners : levelListeners;
        target.addIfAbsent(listener);
        target.sort(Comparator.comparingInt(LuaEventListener::getPriority).reversed());
    }

    public void unregister(LuaEventListener listener) {
        hudListeners.remove(listener);
        levelListeners.remove(listener);
    }

    public void appendHud(UiTree.Scope scope, Render2DEvent.HUD event) {
        append(scope, event, hudListeners);
    }

    @EventHandler(priority = -500)
    private void onLevel(Render2DEvent.Level event) {
        if (levelListeners.isEmpty()) return;
        try {
            MinecraftUiRuntime2612 runtime = MinecraftUiRuntime2612.current();
            ClientSetting.INSTANCE.configureMinecraftFonts(runtime);
            UiTree.Scope scope = new UiTree.Scope();
            append(scope, event, levelListeners);
            UiTree tree = UiTree.from(scope);
            if (tree.nodeCount() > 0) runtime.render(scene(runtime), UiLayer.CONTENT, tree);
        } catch (RuntimeException failure) {
            releaseScene(failure);
            Constants.LOGGER.error("Lua Level 2D frame failed", failure);
        }
    }

    private void append(UiTree.Scope scope, Render2DEvent event,
                        CopyOnWriteArrayList<LuaEventListener> listeners) {
        for (LuaEventListener listener : listeners) {
            try {
                scope.layer(0, child -> listener.callUi(child, event));
            } catch (Throwable failure) {
                Constants.LOGGER.error("Lua 2D callback 失败: {}", listener.runtimeId(), failure);
            }
        }
    }

    private UiScene scene(MinecraftUiRuntime2612 runtime) {
        if (levelScene == null || levelRuntime != runtime) {
            releaseScene();
            levelScene = runtime.createScene(EpsilonUiTheme.lumin());
            levelRuntime = runtime;
        }
        return levelScene;
    }

    private void releaseScene() {
        UiScene previous = levelScene;
        levelScene = null;
        levelRuntime = null;
        if (previous != null) previous.close();
    }

    private void releaseScene(RuntimeException frameFailure) {
        UiScene previous = levelScene;
        levelScene = null;
        levelRuntime = null;
        if (previous == null) return;
        try {
            previous.close();
        } catch (RuntimeException cleanupFailure) {
            frameFailure.addSuppressed(cleanupFailure);
        }
    }

    @Override
    public void close() {
        hudListeners.clear();
        levelListeners.clear();
        releaseScene();
    }
}
