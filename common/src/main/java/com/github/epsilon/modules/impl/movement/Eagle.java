package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.bus.EventPriority;
import com.github.epsilon.events.impl.KeyboardInputEvent;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.math.MathUtils;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.AABB;

import java.util.Objects;

/**
 * Myau skid lol.
 *
 * @author 06789
 */
public class Eagle extends Module {

    public static final Eagle INSTANCE = new Eagle();

    private Eagle() {
        super("Eagle", Category.MOVEMENT);
    }

    private final IntSetting minDelay = intSetting("Min Delay", 2, 0, 10, 1);
    private final IntSetting maxDelay = intSetting("Max Delay", 3, 0, 10, 1);
    private final BoolSetting directionCheck = boolSetting("Direction Check", true);
    private final BoolSetting jumpCheck = boolSetting("Jump Check", true);
    private final BoolSetting pitchCheck = boolSetting("Pitch Check", true);
    private final BoolSetting blocksOnly = boolSetting("Blocks Only", true);
    private final BoolSetting sneakOnly = boolSetting("Sneaking Only", false);

    private int sneakDelay;

    @Override
    public String getInfo() {
        return Objects.equals(minDelay.getValue(), maxDelay.getValue()) ? String.valueOf(minDelay.getValue()) : String.format("%d-%d", minDelay.getValue(), maxDelay.getValue());
    }

    @Override
    protected void onDisable() {
        sneakDelay = 0;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        if (sneakDelay > 0) {
            sneakDelay--;
        }

        if (sneakDelay == 0 && canMoveSafely()) {
            sneakDelay = MathUtils.getRandom(minDelay.getValue(), maxDelay.getValue());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onKeyboardInput(KeyboardInputEvent event) {
        if (mc.screen != null) return;

        if (sneakOnly.getValue() && isSneakKeyDown() && shouldSneak()) {
            event.setSneak(false);
        }

        if (!event.isSneak() && shouldSneak() && (sneakDelay > 0 || canMoveSafely())) {
            event.setSneak(true);
        }
    }

    private boolean canMoveSafely() {
        double[] offset = predictMovement();
        AABB box = mc.player.getBoundingBox().move(
                mc.player.getDeltaMovement().x + offset[0],
                -1.0,
                mc.player.getDeltaMovement().z + offset[1]
        );
        return !mc.level.getCollisions(mc.player, box).iterator().hasNext();
    }

    private boolean shouldSneak() {
        if (directionCheck.getValue() && mc.options.keyUp.isDown()) {
            return false;
        }

        if (jumpCheck.getValue() && mc.options.keyJump.isDown()) {
            return false;
        }

        if (pitchCheck.getValue() && mc.player.getXRot() < 69.0f) {
            return false;
        }

        if (sneakOnly.getValue() && !isSneakKeyDown()) {
            return false;
        }

        return (!blocksOnly.getValue() || mc.player.getMainHandItem().getItem() instanceof BlockItem) && mc.player.onGround();
    }

    private double[] predictMovement() {
        float strafe = KeyboardInput.calculateImpulse(mc.options.keyLeft.isDown(), mc.options.keyRight.isDown()) * 0.98F;
        float forward = KeyboardInput.calculateImpulse(mc.options.keyUp.isDown(), mc.options.keyDown.isDown()) * 0.98F;
        float inputMagnitude = strafe * strafe + forward * forward;

        if (inputMagnitude < 1.0E-4F) {
            return new double[]{0.0, 0.0};
        }

        inputMagnitude = Mth.sqrt(inputMagnitude);
        if (inputMagnitude < 1.0F) {
            inputMagnitude = 1.0F;
        }

        float speed = getAllowedHorizontalDistance() / inputMagnitude;
        float sinYaw = Mth.sin(mc.player.getYRot() * Mth.DEG_TO_RAD);
        float cosYaw = Mth.cos(mc.player.getYRot() * Mth.DEG_TO_RAD);

        strafe *= speed;
        forward *= speed;

        return new double[]{
                strafe * cosYaw - forward * sinYaw,
                forward * cosYaw + strafe * sinYaw
        };
    }

    private float getAllowedHorizontalDistance() {
        BlockPos posBelow = BlockPos.containing(mc.player.getX(), mc.player.getBoundingBox().minY - 1.0, mc.player.getZ());
        float friction = mc.level.getBlockState(posBelow).getBlock().getFriction() * 0.91F;
        return mc.player.getSpeed() * (0.21600002F / (friction * friction * friction));
    }

    private boolean isSneakKeyDown() {
        return InputConstants.isKeyDown(mc.getWindow(), mc.options.keyShift.getDefaultKey().getValue());
    }

}
