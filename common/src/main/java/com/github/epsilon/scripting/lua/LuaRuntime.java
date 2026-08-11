package com.github.epsilon.scripting.lua;

import com.github.epsilon.Constants;
import com.github.epsilon.scripting.lua.event.LuaEventRegistry;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;
import org.luaj.vm2.lib.jse.JsePlatform;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantLock;

public final class LuaRuntime implements AutoCloseable {
    private final String id;
    private final Globals globals;
    private final ReentrantLock lock = new ReentrantLock(true);
    private volatile boolean alive = true;
    private volatile boolean declarationsOpen = true;

    public LuaRuntime(String id, Path libDirectory) {
        this.id = id;
        globals = JsePlatform.standardGlobals();
        configureLibraryPath(libDirectory);
        installEpsilonClassBinders();
        set("mc", Constants.mc);
    }

    public void set(String name, Object value) {
        requireAlive();
        globals.set(name, value instanceof LuaValue luaValue ? luaValue : CoerceJavaToLua.coerce(value));
    }

    public void execute(Path entrypoint) throws IOException {
        requireAlive();
        lock.lock();
        try (Reader reader = Files.newBufferedReader(entrypoint, StandardCharsets.UTF_8)) {
            globals.load(reader, "@" + entrypoint.toAbsolutePath()).call();
        } finally {
            lock.unlock();
        }
    }

    public LuaValue invoke(LuaValue callback, LuaValue... arguments) {
        requireAlive();
        if (callback == null || !callback.isfunction()) throw new LuaError("callback 必须是 function");
        lock.lock();
        try {
            Varargs result = callback.invoke(LuaValue.varargsOf(arguments));
            return result.arg1();
        } finally {
            lock.unlock();
        }
    }

    public boolean isAlive() {
        return alive;
    }

    public String id() {
        return id;
    }

    public void requireAlive() {
        if (!alive) throw new LuaError("Lua runtime 已失效: " + id);
    }

    public void closeDeclarations() {
        declarationsOpen = false;
    }

    public void requireDeclarationsOpen(String owner) {
        requireAlive();
        if (!declarationsOpen) throw new LuaError("Setting 声明阶段已结束: " + owner);
    }

    private void configureLibraryPath(Path libDirectory) {
        String root = libDirectory.toAbsolutePath().normalize().toString().replace('\\', '/');
        LuaValue packageTable = globals.get("package");
        packageTable.set("path", LuaValue.valueOf(root + "/?.lua;" + root + "/?/init.lua"));
        packageTable.set("cpath", LuaValue.valueOf(""));
    }

    private void installEpsilonClassBinders() {
        LuaValue luajava = globals.get("luajava");
        LuaValue bindClass = luajava.get("bindClass");
        luajava.set("bindEventClass", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue value) {
                Class<?> eventClass = LuaEventRegistry.resolveName(value.checkjstring());
                return CoerceJavaToLua.coerce(eventClass);
            }
        });
        luajava.set("bindUtilClass", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue value) {
                Class<?> utilClass = LuaUtilRegistry.resolve(value.checkjstring());
                return bindClass.call(LuaValue.valueOf(utilClass.getName()));
            }
        });
    }

    @Override
    public void close() {
        lock.lock();
        try {
            alive = false;
        } finally {
            lock.unlock();
        }
    }
}
