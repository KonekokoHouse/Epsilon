package com.github.epsilon.scripting.lua;

import com.github.epsilon.settings.Setting;
import com.github.epsilon.settings.SettingGroup;
import com.github.epsilon.settings.SettingHost;
import com.github.epsilon.settings.impl.*;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Pattern;

final class LuaSettingApi {
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9][a-z0-9._ -]{0,63}");

    private LuaSettingApi() {
    }

    static LuaTable create(LuaRuntime runtime, SettingHost host, boolean declarationAllowed, String owner) {
        LuaTable api = new LuaTable();
        api.set("group", function(args -> {
            ensureDeclaration(runtime, declarationAllowed, owner);
            String id = settingId(argument(args, 2), "group");
            host.settingGroup(id);
            return LuaValue.valueOf(id);
        }));
        api.set("setting", function(args -> createHandle(runtime, find(host, settingId(argument(args, 2), "setting")), owner)));
        api.set("boolSetting", function(args -> {
            ensureDeclaration(runtime, declarationAllowed, owner);
            LuaTable spec = argument(args, 2).checktable();
            String id = newSettingId(host, spec);
            BoolSetting setting = host.addSetting(new BoolSetting(id, required(spec, "default").checkboolean(),
                    dependency(runtime, spec), changed(runtime, spec)));
            applyGroup(host, setting, spec);
            return createHandle(runtime, setting, owner);
        }));
        api.set("intSetting", function(args -> {
            ensureDeclaration(runtime, declarationAllowed, owner);
            LuaTable spec = argument(args, 2).checktable();
            String id = newSettingId(host, spec);
            int defaultValue = exactInt(required(spec, "default"), "default");
            int min = exactInt(required(spec, "min"), "min");
            int max = exactInt(required(spec, "max"), "max");
            int step = exactInt(required(spec, "step"), "step");
            if (min > max || defaultValue < min || defaultValue > max || step <= 0) {
                throw new LuaError("intSetting 范围无效: " + id);
            }
            IntSetting setting = host.addSetting(new IntSetting(id, defaultValue, min, max, step,
                    dependency(runtime, spec), changed(runtime, spec)));
            applyGroup(host, setting, spec);
            return createHandle(runtime, setting, owner);
        }));
        api.set("doubleSetting", function(args -> {
            ensureDeclaration(runtime, declarationAllowed, owner);
            LuaTable spec = argument(args, 2).checktable();
            String id = newSettingId(host, spec);
            double defaultValue = finiteDouble(required(spec, "default"), "default");
            double min = finiteDouble(required(spec, "min"), "min");
            double max = finiteDouble(required(spec, "max"), "max");
            double step = finiteDouble(required(spec, "step"), "step");
            if (min > max || defaultValue < min || defaultValue > max || step <= 0.0) {
                throw new LuaError("doubleSetting 范围无效: " + id);
            }
            DoubleSetting setting = host.addSetting(new DoubleSetting(id, defaultValue, min, max, step,
                    dependency(runtime, spec), changed(runtime, spec)));
            applyGroup(host, setting, spec);
            return createHandle(runtime, setting, owner);
        }));
        api.set("stringSetting", function(args -> {
            ensureDeclaration(runtime, declarationAllowed, owner);
            LuaTable spec = argument(args, 2).checktable();
            String id = newSettingId(host, spec);
            StringSetting setting = host.addSetting(new StringSetting(id, required(spec, "default").checkjstring(),
                    dependency(runtime, spec), changed(runtime, spec)));
            applyGroup(host, setting, spec);
            return createHandle(runtime, setting, owner);
        }));
        api.set("choiceSetting", function(args -> {
            ensureDeclaration(runtime, declarationAllowed, owner);
            LuaTable spec = argument(args, 2).checktable();
            String id = newSettingId(host, spec);
            List<String> choices = stringList(required(spec, "choices"));
            ChoiceSetting setting = host.addSetting(new ChoiceSetting(id, required(spec, "default").checkjstring(), choices,
                    dependency(runtime, spec), changed(runtime, spec)));
            applyGroup(host, setting, spec);
            return createHandle(runtime, setting, owner);
        }));
        api.set("colorSetting", function(args -> {
            ensureDeclaration(runtime, declarationAllowed, owner);
            LuaTable spec = argument(args, 2).checktable();
            String id = newSettingId(host, spec);
            boolean alpha = optionalBoolean(spec, "allowAlpha", true);
            Color color = new Color(argb(required(spec, "default"), "default"), true);
            if (!alpha) color = new Color(color.getRed(), color.getGreen(), color.getBlue());
            ColorSetting setting = host.addSetting(new ColorSetting(id, color, alpha, dependency(runtime, spec)));
            applyGroup(host, setting, spec);
            return createHandle(runtime, setting, owner);
        }));
        api.set("keybindSetting", function(args -> {
            ensureDeclaration(runtime, declarationAllowed, owner);
            LuaTable spec = argument(args, 2).checktable();
            String id = newSettingId(host, spec);
            KeybindSetting setting = host.addSetting(new KeybindSetting(id,
                    exactInt(required(spec, "default"), "default"), dependency(runtime, spec)));
            applyGroup(host, setting, spec);
            return createHandle(runtime, setting, owner);
        }));
        api.set("stringListSetting", function(args -> {
            ensureDeclaration(runtime, declarationAllowed, owner);
            LuaTable spec = argument(args, 2).checktable();
            String id = newSettingId(host, spec);
            StringListSetting setting = host.addSetting(new StringListSetting(id,
                    stringList(required(spec, "default")), dependency(runtime, spec)));
            applyGroup(host, setting, spec);
            return createHandle(runtime, setting, owner);
        }));
        return api;
    }

    private static LuaTable createHandle(LuaRuntime runtime, Setting<?> setting, String owner) {
        LuaTable handle = new LuaTable();
        handle.set("get", function(args -> {
            runtime.requireAlive();
            return toLua(setting.getValue());
        }));
        handle.set("set", function(args -> {
            runtime.requireAlive();
            set(setting, argument(args, 2));
            return toLua(setting.getValue());
        }));
        handle.set("java_setting", function(args -> {
            runtime.requireAlive();
            return CoerceJavaToLua.coerce(setting);
        }));
        handle.set("id", LuaValue.valueOf(setting.getName()));
        handle.set("owner", LuaValue.valueOf(owner));
        return handle;
    }

    private static void set(Setting<?> setting, LuaValue value) {
        if (setting instanceof BoolSetting typed) typed.setValue(value.checkboolean());
        else if (setting instanceof IntSetting typed) typed.setValue(exactInt(value, "value"));
        else if (setting instanceof DoubleSetting typed) typed.setValue(finiteDouble(value, "value"));
        else if (setting instanceof StringSetting typed) typed.setValue(value.checkjstring());
        else if (setting instanceof ChoiceSetting typed) typed.setValue(value.checkjstring());
        else if (setting instanceof KeybindSetting typed) typed.setValue(exactInt(value, "value"));
        else if (setting instanceof ColorSetting typed) {
            Color color = new Color(argb(value, "value"), true);
            if (!typed.isAllowAlpha()) color = new Color(color.getRed(), color.getGreen(), color.getBlue());
            typed.setValue(color);
        } else if (setting instanceof StringListSetting typed) typed.setValue(stringList(value));
        else throw new LuaError("不支持写入 Setting 类型: " + setting.getClass().getName());
    }

    private static LuaValue toLua(Object value) {
        if (value == null) return LuaValue.NIL;
        if (value instanceof Boolean typed) return LuaValue.valueOf(typed);
        if (value instanceof Integer typed) return LuaValue.valueOf(typed);
        if (value instanceof Double typed) return LuaValue.valueOf(typed);
        if (value instanceof String typed) return LuaValue.valueOf(typed);
        if (value instanceof Color typed) return LuaValue.valueOf(typed.getRGB());
        if (value instanceof List<?> list) {
            LuaTable table = new LuaTable();
            int index = 1;
            for (Object entry : list) table.set(index++, toLua(entry));
            return table;
        }
        return CoerceJavaToLua.coerce(value);
    }

    private static Setting.Dependency dependency(LuaRuntime runtime, LuaTable spec) {
        LuaValue callback = spec.get("available");
        if (callback.isnil()) return () -> true;
        callback.checkfunction();
        return () -> {
            try {
                return runtime.invoke(callback).checkboolean();
            } catch (Throwable failure) {
                throw new LuaError("Setting available callback 失败: " + runtime.id() + " (" + failure + ")");
            }
        };
    }

    private static <T> Consumer<T> changed(LuaRuntime runtime, LuaTable spec) {
        LuaValue callback = spec.get("changed");
        if (callback.isnil()) return null;
        callback.checkfunction();
        return value -> runtime.invoke(callback, toLua(value));
    }

    private static void applyGroup(SettingHost host, Setting<?> setting, LuaTable spec) {
        LuaValue group = spec.get("group");
        if (!group.isnil()) setting.group(host.settingGroup(settingId(group, "group")));
    }

    private static Setting<?> find(SettingHost host, String id) {
        return host.mutableSettings().stream()
                .filter(setting -> setting.getName().equals(id))
                .findFirst()
                .orElseThrow(() -> new LuaError("未知 Setting: " + id));
    }

    private static String newSettingId(SettingHost host, LuaTable spec) {
        String id = settingId(required(spec, "id"), "setting");
        if (host.mutableSettings().stream().anyMatch(setting -> setting.getName().equals(id))) {
            throw new LuaError("重复 Setting ID: " + id);
        }
        return id;
    }

    private static String settingId(LuaValue value, String kind) {
        String id = value.checkjstring();
        if (!id.equals(id.toLowerCase(Locale.ROOT)) || !ID_PATTERN.matcher(id).matches()
                || id.contains("..") || id.endsWith(" ")) {
            throw new LuaError("无效 " + kind + " ID: " + id);
        }
        return id;
    }

    private static LuaValue required(LuaTable spec, String key) {
        LuaValue value = spec.get(key);
        if (value.isnil()) throw new LuaError("缺少 Setting 参数: " + key);
        return value;
    }

    private static LuaValue argument(Varargs args, int index) {
        LuaValue value = args.arg(index);
        if (value.isnil()) throw new LuaError("缺少参数 #" + (index - 1));
        return value;
    }

    private static int exactInt(LuaValue value, String name) {
        double number = value.checkdouble();
        if (!Double.isFinite(number) || number != Math.rint(number)
                || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
            throw new LuaError(name + " 必须是 32-bit 有限整数");
        }
        return (int) number;
    }

    private static double finiteDouble(LuaValue value, String name) {
        double number = value.checkdouble();
        if (!Double.isFinite(number)) throw new LuaError(name + " 必须是有限 number");
        return number;
    }

    private static int argb(LuaValue value, String name) {
        double number = finiteDouble(value, name);
        if (number != Math.rint(number) || number < Integer.MIN_VALUE || number > 0xFFFF_FFFFL) {
            throw new LuaError(name + " 必须是 32-bit ARGB 整数");
        }
        return (int) (long) number;
    }

    private static boolean optionalBoolean(LuaTable spec, String key, boolean fallback) {
        LuaValue value = spec.get(key);
        return value.isnil() ? fallback : value.checkboolean();
    }

    private static List<String> stringList(LuaValue value) {
        LuaTable table = value.checktable();
        List<String> result = new ArrayList<>();
        for (int index = 1; index <= table.length(); index++) result.add(table.get(index).checkjstring());
        return result;
    }

    private static void ensureDeclaration(LuaRuntime runtime, boolean allowed, String owner) {
        if (!allowed) throw new LuaError("Setting 声明阶段已结束: " + owner);
        runtime.requireDeclarationsOpen(owner);
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
