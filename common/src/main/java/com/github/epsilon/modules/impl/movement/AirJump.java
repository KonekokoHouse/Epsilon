package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.CollisionEvent;
import com.github.epsilon.events.impl.JumpEvent;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.EnumSetting;
import net.minecraft.world.level.block.Blocks;

public class AirJump extends Module {

    public static final AirJump INSTANCE = new AirJump();

    private AirJump() {
        super("Air Jump", Category.MOVEMENT);
    }

    public enum Mode {
        JumpFreely,
        DoubleJump,
        GhostBlock
    }

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.JumpFreely);

    private boolean doubleJump = true;

    public boolean allowJump() {
        if (!isEnabled()) return false;
        return switch (mode.getValue()) {
            case JumpFreely -> true;
            case DoubleJump -> doubleJump;
            case GhostBlock -> false;
        };
    }

    @Override
    protected void onEnable() {
        doubleJump = true;
    }

    @EventHandler
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        if (nullCheck()) return;
        if (mc.player.onGround()) {
            doubleJump = true;
        }
    }

    @EventHandler
    private void onJump(JumpEvent event) {
        if (nullCheck()) return;
        if (mode.getValue() == Mode.DoubleJump && doubleJump && !mc.player.onGround()) {
            doubleJump = false;
        }
    }

    @EventHandler
    private void onCollision(CollisionEvent event) {
        if (nullCheck()) return;
        if (mode.getValue() != Mode.GhostBlock) return;
        if (event.getPos().getY() < mc.player.blockPosition().getY() && mc.options.keyJump.isDown()) {
            event.setState(Blocks.STONE.defaultBlockState());
        }
    }

}
