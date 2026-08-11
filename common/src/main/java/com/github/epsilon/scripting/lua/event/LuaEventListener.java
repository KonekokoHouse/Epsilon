package com.github.epsilon.scripting.lua.event;

import com.github.epsilon.scripting.lua.LuaRuntime;
import com.github.epsilon.scripting.lua.LuaModule;
import com.github.epsilon.events.bus.listeners.IListener;
import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.impl.Render2DEvent;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.scripting.lua.render.LuaRender2DService;
import com.github.epsilon.scripting.lua.render.LuaRender3DContext;
import com.github.epsilon.scripting.lua.render.LuaUiContext;
import com.github.slmpc.lumingraphics.ui.tree.UiTree;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;

public final class LuaEventListener implements IListener {
    private final LuaRuntime runtime;
    private final LuaModule module;
    private final Class<?> target;
    private final int priority;
    private final LuaValue callback;

    public LuaEventListener(LuaRuntime runtime, LuaModule module, Class<?> target, int priority, LuaValue callback) {
        this.runtime = runtime;
        this.module = module;
        this.target = target;
        this.priority = priority;
        this.callback = callback.checkfunction();
    }

    @Override
    public void call(Object event) {
        try {
            if (event instanceof Render3DEvent) {
                runtime.invoke(callback, LuaRender3DContext.create(), CoerceJavaToLua.coerce(event));
            } else {
                runtime.invoke(callback, CoerceJavaToLua.coerce(event));
            }
            module.recordCallbackSuccess();
        } catch (Throwable failure) {
            module.recordCallbackFailure(target.getName(), failure);
        }
    }

    public void callUi(UiTree.Scope scope, Render2DEvent event) {
        try {
            runtime.invoke(callback, LuaUiContext.create(runtime, scope), CoerceJavaToLua.coerce(event));
            module.recordCallbackSuccess();
        } catch (Throwable failure) {
            module.recordCallbackFailure(target.getName(), failure);
        }
    }

    public void subscribe() {
        if (Render2DEvent.class.isAssignableFrom(target)) LuaRender2DService.INSTANCE.register(this);
        else EventBus.INSTANCE.subscribe(this);
    }

    public void unsubscribe() {
        if (Render2DEvent.class.isAssignableFrom(target)) LuaRender2DService.INSTANCE.unregister(this);
        else EventBus.INSTANCE.unsubscribe(this);
    }

    public String runtimeId() {
        return runtime.id();
    }

    @Override
    public Class<?> getTarget() {
        return target;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public boolean isStatic() {
        return false;
    }
}
