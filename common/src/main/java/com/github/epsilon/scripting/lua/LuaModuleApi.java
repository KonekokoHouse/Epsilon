package com.github.epsilon.scripting.lua;

import com.github.epsilon.scripting.lua.event.LuaEventRegistry;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;

final class LuaModuleApi {
    private LuaModuleApi() {
    }

    static LuaTable create(LuaRuntime runtime, LuaModule module) {
        LuaTable api = LuaSettingApi.create(runtime, module, true,
                module.getAddonId() + ":" + module.getModuleId());
        api.set("on_enable", function(args -> {
            module.setEnableCallback(argument(args, 2));
            return LuaValue.NONE;
        }));
        api.set("on_disable", function(args -> {
            module.setDisableCallback(argument(args, 2));
            return LuaValue.NONE;
        }));
        api.set("on_cleanup", function(args -> {
            module.setCleanupCallback(argument(args, 2));
            return LuaValue.NONE;
        }));
        api.set("on", function(args -> {
            Class<?> eventClass = LuaEventRegistry.resolveId(argument(args, 2).checkjstring());
            int priority = argument(args, 3).checkint();
            validatePriority(eventClass, priority);
            module.addEventListener(eventClass, priority, argument(args, 4));
            return LuaValue.NONE;
        }));
        api.set("on_class", function(args -> {
            Object userdata = argument(args, 2).checkuserdata(Class.class);
            int priority = argument(args, 3).checkint();
            validatePriority((Class<?>) userdata, priority);
            module.addEventListener((Class<?>) userdata, priority, argument(args, 4));
            return LuaValue.NONE;
        }));
        api.set("java_module", function(args -> CoerceJavaToLua.coerce(module)));
        api.set("storage", module.storage().createApi(runtime));
        return api;
    }

    private static void validatePriority(Class<?> eventClass, int priority) {
        if (eventClass == com.github.epsilon.events.impl.Render3DEvent.class && priority <= -999) {
            throw new LuaError("Render3DEvent priority 必须高于 -999");
        }
    }

    private static LuaValue argument(Varargs args, int index) {
        LuaValue value = args.arg(index);
        if (value.isnil()) throw new LuaError("缺少参数 #" + (index - 1));
        return value;
    }

    private static VarArgFunction function(java.util.function.Function<Varargs, LuaValue> body) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return body.apply(args);
            }
        };
    }
}
