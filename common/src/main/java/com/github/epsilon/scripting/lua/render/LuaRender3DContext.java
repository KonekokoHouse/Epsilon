package com.github.epsilon.scripting.lua.render;

import com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;

public final class LuaRender3DContext {
    private LuaRender3DContext() {
    }

    public static LuaTable create() {
        Render3DScheduler scheduler = Render3DScheduler.INSTANCE;
        LuaTable api = new LuaTable();
        api.set("filled_box", function(args -> {
            scheduler.addFilledBox(userdata(args, 2, AABB.class), argb(args.arg(3), "color"));
            return LuaValue.NONE;
        }));
        api.set("filled_fade_box", function(args -> {
            scheduler.addFilledFadeBox(userdata(args, 2, AABB.class), argb(args.arg(3), "bottomColor"),
                    argb(args.arg(4), "topColor"));
            return LuaValue.NONE;
        }));
        api.set("blurred_box", function(args -> {
            scheduler.addBlurredBox(userdata(args, 2, AABB.class), finite(args.arg(3), "blurStrength"));
            return LuaValue.NONE;
        }));
        api.set("filled_side", function(args -> {
            scheduler.addFilledSide(userdata(args, 2, AABB.class), argb(args.arg(3), "color"),
                    direction(args.arg(4)));
            return LuaValue.NONE;
        }));
        api.set("outline_box", function(args -> {
            scheduler.addOutlineBox(userdata(args, 2, AABB.class), argb(args.arg(3), "color"),
                    (float) finite(args.arg(4), "thickness"));
            return LuaValue.NONE;
        }));
        api.set("side_outline", function(args -> {
            scheduler.addSideOutline(userdata(args, 2, AABB.class), argb(args.arg(3), "color"),
                    (float) finite(args.arg(4), "thickness"), direction(args.arg(5)));
            return LuaValue.NONE;
        }));
        api.set("line", function(args -> {
            scheduler.addLine(userdata(args, 2, Vec3.class), userdata(args, 3, Vec3.class),
                    argb(args.arg(4), "color"), (float) finite(args.arg(5), "thickness"));
            return LuaValue.NONE;
        }));
        api.set("box", function(args -> CoerceJavaToLua.coerce(new AABB(
                finite(args.arg(2), "minX"), finite(args.arg(3), "minY"), finite(args.arg(4), "minZ"),
                finite(args.arg(5), "maxX"), finite(args.arg(6), "maxY"), finite(args.arg(7), "maxZ")))));
        api.set("vec3", function(args -> CoerceJavaToLua.coerce(new Vec3(
                finite(args.arg(2), "x"), finite(args.arg(3), "y"), finite(args.arg(4), "z")))));
        api.set("raw_scheduler", function(args -> CoerceJavaToLua.coerce(scheduler)));
        return api;
    }

    private static Direction direction(LuaValue value) {
        if (value.isuserdata(Direction.class)) return (Direction) value.checkuserdata(Direction.class);
        try {
            return Direction.valueOf(value.checkjstring().toUpperCase());
        } catch (IllegalArgumentException failure) {
            throw new LuaError("未知 Direction: " + value.tojstring());
        }
    }

    private static <T> T userdata(Varargs args, int index, Class<T> type) {
        return type.cast(args.arg(index).checkuserdata(type));
    }

    private static double finite(LuaValue value, String name) {
        double number = value.checkdouble();
        if (!Double.isFinite(number)) throw new LuaError(name + " 必须是有限 number");
        return number;
    }

    private static int argb(LuaValue value, String name) {
        double number = finite(value, name);
        if (number != Math.rint(number) || number < Integer.MIN_VALUE || number > 0xFFFF_FFFFL) {
            throw new LuaError(name + " 必须是 32-bit ARGB 整数");
        }
        return (int) (long) number;
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
