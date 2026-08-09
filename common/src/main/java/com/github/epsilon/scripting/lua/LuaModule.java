package com.github.epsilon.scripting.lua;

import com.github.epsilon.Constants;
import com.github.epsilon.assets.i18n.TranslateComponent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.scripting.lua.event.LuaEventListener;
import org.luaj.vm2.LuaValue;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class LuaModule extends Module implements AutoCloseable {
    private final List<LuaEventListener> listeners = new ArrayList<>();
    private LuaRuntime runtime;
    private LuaValue enableCallback = LuaValue.NIL;
    private LuaValue disableCallback = LuaValue.NIL;
    private LuaValue cleanupCallback = LuaValue.NIL;
    private final LuaStorage storage = new LuaStorage();
    private final AtomicInteger consecutiveCallbackFailures = new AtomicInteger();

    public LuaModule(String moduleId, String name, Category category, boolean defaultEnabled, boolean defaultHidden) {
        super(moduleId, name, category);
        setDefaultHidden(defaultHidden);
        setDefaultEnabledValue(defaultEnabled);
    }

    @Override
    public void initI18n(TranslateComponent moduleComponent) {
        translateComponent = moduleComponent;
        getSettingGroups().forEach(group -> group.initTranslateComponent(
                moduleComponent.createChild("groups." + group.getName().toLowerCase())));
        getSettings().forEach(setting -> setting.initTranslateComponent(
                moduleComponent.createChild("settings." + setting.getName().toLowerCase())));
    }

    void attachRuntime(LuaRuntime runtime) {
        if (this.runtime != null) throw new IllegalStateException("LuaModule runtime 已绑定: " + getModuleId());
        this.runtime = runtime;
    }

    void setEnableCallback(LuaValue callback) {
        enableCallback = callback.checkfunction();
    }

    void setDisableCallback(LuaValue callback) {
        disableCallback = callback.checkfunction();
    }

    void setCleanupCallback(LuaValue callback) {
        cleanupCallback = callback.checkfunction();
    }

    void addEventListener(Class<?> eventClass, int priority, LuaValue callback) {
        listeners.add(new LuaEventListener(runtime, this, eventClass, priority, callback));
    }

    public void recordCallbackSuccess() {
        consecutiveCallbackFailures.set(0);
    }

    public void recordCallbackFailure(String callback, Throwable failure) {
        int failures = consecutiveCallbackFailures.incrementAndGet();
        Constants.LOGGER.error("Lua callback 失败 ({}/3): {}:{} -> {}", failures, getAddonId(), getModuleId(), callback, failure);
        if (failures < 3) return;
        consecutiveCallbackFailures.set(0);
        Runnable disable = () -> setEnabled(false);
        if (Constants.mc == null || Constants.mc.isSameThread()) disable.run();
        else Constants.mc.execute(disable);
    }

    LuaStorage storage() {
        return storage;
    }

    @Override
    protected void resetCustomState() {
        storage.clear();
    }

    @Override
    public JsonObject saveCustomState() {
        JsonObject state = new JsonObject();
        state.add("luaStorage", storage.toJson());
        return state;
    }

    @Override
    public void loadCustomState(JsonObject state) {
        if (state != null && state.has("luaStorage") && state.get("luaStorage").isJsonObject()) {
            storage.load(state.getAsJsonObject("luaStorage"));
        }
    }

    @Override
    protected void onEnable() {
        consecutiveCallbackFailures.set(0);
        for (LuaEventListener listener : listeners) listener.subscribe();
        invokeLifecycle("on_enable", enableCallback);
    }

    @Override
    protected void onDisable() {
        for (LuaEventListener listener : listeners) listener.unsubscribe();
        invokeLifecycle("on_disable", disableCallback);
    }

    private void invokeLifecycle(String phase, LuaValue callback) {
        if (runtime == null || callback.isnil()) return;
        try {
            runtime.invoke(callback);
        } catch (Throwable failure) {
            Constants.LOGGER.error("Lua Module {}:{} {} 失败", getAddonId(), getModuleId(), phase, failure);
        }
    }

    @Override
    public void close() {
        setEnabled(false);
        invokeLifecycle("on_cleanup", cleanupCallback);
        listeners.clear();
        if (runtime != null) runtime.close();
    }
}
