package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.KeyboardInputEvent;
import com.github.epsilon.events.impl.SlowdownEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Items;

/**
 * 别找了这没你想要的 GrimAC 全速度 NoSlow
 * NoSlow 在群文件名字叫 epsilon_plus 是本客户端 Addon 代码经过混淆
 */

public class NoSlow extends Module {

    public static final NoSlow INSTANCE = new NoSlow();

    private NoSlow() {
        super("No Slow", Category.MOVEMENT);
    }

    private enum Mode {
        Vanilla,
        Jump,
        Grim1_2,
        Grim1_3
    }

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.Vanilla);
    private final BoolSetting food = boolSetting("Food", true);
    private final BoolSetting bow = boolSetting("Bow", true);
    private final BoolSetting crossbow = boolSetting("Crossbow", true);

    private int onGroundTick = 0;

    @Override
    protected void onEnable() {
        onGroundTick = 0;
    }

    @EventHandler
    private void onSlowdown(SlowdownEvent event) {
        if (nullCheck()) return;

        if (mc.player.onGround()) {
            onGroundTick++;
        } else {
            onGroundTick = 0;
        }

        if (!food.getValue() && mc.player.getUseItem().has(DataComponents.FOOD)) return;
        if (!bow.getValue() && mc.player.getUseItem().is(Items.BOW)) return;
        if (!crossbow.getValue() && mc.player.getUseItem().is(Items.CROSSBOW)) return;

        switch (mode.getValue()) {
            case Vanilla -> cancel(event);
            case Jump -> jump(event);
            case Grim1_2 -> grim50(event);
            case Grim1_3 -> grim33(event);
        }
    }

    @EventHandler
    private void onKeyboardInput(KeyboardInputEvent event) {
        if (mode.is(Mode.Jump) && mc.player.onGround() && mc.player.isUsingItem() && (event.getForward() != 0 || event.getStrafe() != 0)) {
            event.setJump(true);
        }
    }

    private void cancel(SlowdownEvent event) {
        event.setSlowdown(false);
    }

    private void jump(SlowdownEvent event) {
        if (onGroundTick == 1 && mc.player.getUseItemRemainingTicks() <= 30) {
            event.setSlowdown(false);
        }
    }

    private void grim50(SlowdownEvent event) {
        if (mc.player.getUseItemRemainingTicks() % 2 == 0 && mc.player.getUseItemRemainingTicks() <= 30) {
            event.setSlowdown(false);
        }
    }

    private void grim33(SlowdownEvent event) {
        if (mc.player.getUseItemRemainingTicks() % 3 == 0 && mc.player.getUseItemRemainingTicks() <= 30) {
            event.setSlowdown(false);
        }
    }

}
