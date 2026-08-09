package com.github.epsilon.scripting.lua.render;

import com.github.epsilon.scripting.lua.LuaRuntime;
import com.github.epsilon.gui.theme.EpsilonUiTheme;
import com.github.slmpc.lumingraphics.mc.v2612.runtime.MinecraftUiRuntime2612;
import com.github.slmpc.lumingraphics.ui.geometry.UiRect;
import com.github.slmpc.lumingraphics.ui.tree.UiTree;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;

import java.awt.Color;

public final class LuaUiContext {
    private LuaUiContext() {
    }

    public static LuaTable create(LuaRuntime runtime, UiTree.Scope scope) {
        LuaTable api = new LuaTable();
        api.set("rect", function(args -> {
            scope.rect(number(args, 2), number(args, 3), number(args, 4), number(args, 5), color(args.arg(6)));
            return LuaValue.NONE;
        }));
        api.set("round_rect", function(args -> {
            scope.roundRect(number(args, 2), number(args, 3), number(args, 4), number(args, 5),
                    number(args, 6), color(args.arg(7)));
            return LuaValue.NONE;
        }));
        api.set("outline", function(args -> {
            scope.outline(number(args, 2), number(args, 3), number(args, 4), number(args, 5),
                    number(args, 6), number(args, 7), color(args.arg(8)));
            return LuaValue.NONE;
        }));
        api.set("shadow", function(args -> {
            scope.shadow(number(args, 2), number(args, 3), number(args, 4), number(args, 5),
                    number(args, 6), number(args, 7), color(args.arg(8)));
            return LuaValue.NONE;
        }));
        api.set("text", function(args -> {
            String font = optionalString(args.arg(7));
            scope.text(args.arg(2).checkjstring(), number(args, 3), number(args, 4), number(args, 5),
                    color(args.arg(6)), font);
            return LuaValue.NONE;
        }));
        api.set("rotated_text", function(args -> {
            scope.rotatedText(args.arg(2).checkjstring(), number(args, 3), number(args, 4), number(args, 5),
                    EpsilonUiTheme.lumin(color(args.arg(6))), optionalString(args.arg(7)),
                    number(args, 8), number(args, 9), number(args, 10));
            return LuaValue.NONE;
        }));
        api.set("texture", function(args -> {
            scope.texture(args.arg(2).checkjstring(), number(args, 3), number(args, 4), number(args, 5),
                    number(args, 6), color(args.arg(7)));
            return LuaValue.NONE;
        }));
        api.set("triangle", function(args -> {
            scope.triangle(number(args, 2), number(args, 3), number(args, 4), number(args, 5), color(args.arg(6)));
            return LuaValue.NONE;
        }));
        api.set("layer", nested(runtime, scope, NestedKind.LAYER));
        api.set("scissor", nested(runtime, scope, NestedKind.SCISSOR));
        api.set("push_absolute", nested(runtime, scope, NestedKind.ABSOLUTE));
        api.set("text_width", function(args -> LuaValue.valueOf(MinecraftUiRuntime2612.current().textMetrics()
                .textWidth(args.arg(2).checkjstring(), number(args, 3), optionalString(args.arg(4))))));
        api.set("text_height", function(args -> LuaValue.valueOf(MinecraftUiRuntime2612.current().textMetrics()
                .textHeight(number(args, 2), optionalString(args.arg(3))))));
        api.set("raw_scope", function(args -> CoerceJavaToLua.coerce(scope)));
        return api;
    }

    private static VarArgFunction nested(LuaRuntime runtime, UiTree.Scope scope, NestedKind kind) {
        return function(args -> {
            LuaValue callback;
            switch (kind) {
                case LAYER -> {
                    int layer = exactInt(args.arg(2), "layer");
                    callback = args.arg(3).checkfunction();
                    scope.layer(layer, child -> runtime.invoke(callback, create(runtime, child)));
                }
                case SCISSOR -> {
                    callback = args.arg(6).checkfunction();
                    UiRect rect = new UiRect(number(args, 2), number(args, 3), number(args, 4), number(args, 5));
                    scope.scissor(rect, child -> runtime.invoke(callback, create(runtime, child)));
                }
                case ABSOLUTE -> {
                    callback = args.arg(6).checkfunction();
                    UiRect rect = new UiRect(number(args, 2), number(args, 3), number(args, 4), number(args, 5));
                    scope.pushAbsolute(rect, child -> runtime.invoke(callback, create(runtime, child)));
                }
            }
            return LuaValue.NONE;
        });
    }

    private static float number(Varargs args, int index) {
        return (float) finite(args.arg(index), "参数 #" + (index - 1));
    }

    private static Color color(LuaValue value) {
        double number = finite(value, "color");
        if (number != Math.rint(number) || number < Integer.MIN_VALUE || number > 0xFFFF_FFFFL) {
            throw new LuaError("color 必须是 32-bit ARGB 整数");
        }
        return new Color((int) (long) number, true);
    }

    private static String optionalString(LuaValue value) {
        return value.isnil() ? null : value.checkjstring();
    }

    private static int exactInt(LuaValue value, String name) {
        double number = finite(value, name);
        if (number != Math.rint(number) || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
            throw new LuaError(name + " 必须是 32-bit 整数");
        }
        return (int) number;
    }

    private static double finite(LuaValue value, String name) {
        double number = value.checkdouble();
        if (!Double.isFinite(number)) throw new LuaError(name + " 必须是有限 number");
        return number;
    }

    private static VarArgFunction function(java.util.function.Function<Varargs, LuaValue> body) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return body.apply(args);
            }
        };
    }

    private enum NestedKind {
        LAYER, SCISSOR, ABSOLUTE
    }
}
