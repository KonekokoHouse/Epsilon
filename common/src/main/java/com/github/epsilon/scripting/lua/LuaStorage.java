package com.github.epsilon.scripting.lua;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LuaStorage {
    private static final int MAX_DEPTH = 64;
    private final Map<String, JsonElement> values = new LinkedHashMap<>();

    public synchronized LuaTable createApi(LuaRuntime runtime) {
        LuaTable api = new LuaTable();
        api.set("get", function(args -> {
            runtime.requireAlive();
            String key = argument(args, 2).checkjstring();
            return fromJson(values.get(key));
        }));
        api.set("set", function(args -> {
            runtime.requireAlive();
            String key = argument(args, 2).checkjstring();
            LuaValue value = argument(args, 3);
            if (value.isnil()) values.remove(key);
            else values.put(key, toJson(value, new IdentityHashMap<>(), 0));
            return LuaValue.NONE;
        }));
        api.set("remove", function(args -> {
            runtime.requireAlive();
            values.remove(argument(args, 2).checkjstring());
            return LuaValue.NONE;
        }));
        api.set("clear", function(args -> {
            runtime.requireAlive();
            values.clear();
            return LuaValue.NONE;
        }));
        return api;
    }

    public synchronized JsonObject toJson() {
        JsonObject object = new JsonObject();
        values.forEach((key, value) -> object.add(key, value.deepCopy()));
        return object;
    }

    public synchronized void load(JsonObject object) {
        values.clear();
        if (object == null) return;
        object.entrySet().forEach(entry -> values.put(entry.getKey(), entry.getValue().deepCopy()));
    }

    public synchronized void clear() {
        values.clear();
    }

    private static JsonElement toJson(LuaValue value, IdentityHashMap<LuaValue, Boolean> visited, int depth) {
        if (depth > MAX_DEPTH) throw new LuaError("storage table 嵌套超过 " + MAX_DEPTH + " 层");
        if (value.isnil()) return JsonNull.INSTANCE;
        if (value.isboolean()) return new JsonPrimitive(value.toboolean());
        if (value.isstring()) return new JsonPrimitive(value.tojstring());
        if (value.isnumber()) {
            double number = value.todouble();
            if (!Double.isFinite(number)) throw new LuaError("storage number 必须有限");
            if (number == Math.rint(number) && number >= Long.MIN_VALUE && number <= Long.MAX_VALUE) {
                return new JsonPrimitive((long) number);
            }
            return new JsonPrimitive(number);
        }
        if (!value.istable()) throw new LuaError("storage 只接受 nil/boolean/number/string/table");
        if (visited.put(value, Boolean.TRUE) != null) throw new LuaError("storage table 不能循环引用");
        try {
            LuaTable table = value.checktable();
            if (isArray(table)) {
                JsonArray array = new JsonArray();
                for (int index = 1; index <= table.length(); index++) {
                    array.add(toJson(table.get(index), visited, depth + 1));
                }
                return array;
            }
            JsonObject object = new JsonObject();
            LuaValue key = LuaValue.NIL;
            while (true) {
                Varargs next = table.next(key);
                key = next.arg1();
                if (key.isnil()) break;
                if (!key.isstring()) throw new LuaError("storage object key 必须是 string");
                object.add(key.checkjstring(), toJson(next.arg(2), visited, depth + 1));
            }
            return object;
        } finally {
            visited.remove(value);
        }
    }

    private static boolean isArray(LuaTable table) {
        int length = table.length();
        int count = 0;
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs next = table.next(key);
            key = next.arg1();
            if (key.isnil()) break;
            count++;
            if (!key.isint() || key.toint() < 1 || key.toint() > length) return false;
        }
        return count == length;
    }

    private static LuaValue fromJson(JsonElement value) {
        if (value == null || value.isJsonNull()) return LuaValue.NIL;
        if (value.isJsonArray()) {
            LuaTable table = new LuaTable();
            int index = 1;
            for (JsonElement entry : value.getAsJsonArray()) table.set(index++, fromJson(entry));
            return table;
        }
        if (value.isJsonObject()) {
            LuaTable table = new LuaTable();
            value.getAsJsonObject().entrySet().forEach(entry -> table.set(entry.getKey(), fromJson(entry.getValue())));
            return table;
        }
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (primitive.isBoolean()) return LuaValue.valueOf(primitive.getAsBoolean());
        if (primitive.isNumber()) return LuaValue.valueOf(primitive.getAsDouble());
        return LuaValue.valueOf(primitive.getAsString());
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
