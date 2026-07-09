package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.bus.EventPriority;
import com.github.epsilon.events.impl.EntityMoveEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ButtonSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.settings.impl.StringSetting;
import com.github.epsilon.utils.movement.AutoPilotUtil;
import net.minecraft.world.phys.Vec3;

public class AutoPilot extends Module {

    public static final AutoPilot INSTANCE = new AutoPilot();

    private AutoPilot() {
        super("AutoPilot", Category.MOVEMENT);
    }

    private final IntSetting cruiseHeight = intSetting("Cruise Height", 320, -1000, 4000, 1);
    private final StringSetting destinationX = stringSetting("Destination X", "0");
    private final StringSetting destinationZ = stringSetting("Destination Z", "0");
    private final ButtonSetting resetDestBtn = buttonSetting("Reset Destination", () -> {
        destinationX.setValue("0");
        destinationZ.setValue("0");
    });
    private final ButtonSetting pasteCoordsBtn = buttonSetting("Paste Coords", () -> {
        String clip = AutoPilotUtil.getClipboardText();
        if (clip == null || clip.isEmpty()) return;

        double[] coords = AutoPilotUtil.parseCoordinates(clip);
        if (coords == null) return;

        destinationX.setValue(String.valueOf((int) coords[0]));
        destinationZ.setValue(String.valueOf((int) coords[1]));
    });
    private final BoolSetting toggleOffOnArrival = boolSetting("Toggle Off On Arrival", true);
    private final BoolSetting pauseInUnloadedChunks = boolSetting("Pause In Unloaded Chunks", false);
    private final BoolSetting playerDodge = boolSetting("Player Dodge", false);

    @EventHandler(priority = EventPriority.LOW)
    private void onEntityMove(EntityMoveEvent event) {
        if (nullCheck()) return;

        EntityControl entityControl = EntityControl.INSTANCE;
        if (!entityControl.canControl(event.entity)) return;

        float autoYaw = AutoPilotUtil.calcAutoMoveYaw(
                destinationX.getValue(), destinationZ.getValue(),
                cruiseHeight.getValue(), playerDodge.getValue());
        if (autoYaw != -999.0F) {
            event.movement = getAutoPilotMovement(autoYaw, entityControl.getHorizontalSpeed());
            return;
        }

        tryToggleOffOnArrival();
    }

    private Vec3 getAutoPilotMovement(float autoYaw, double horizontalSpeed) {
        double speedVal = horizontalSpeed / 20.0;
        double rad = Math.toRadians(autoYaw + 90.0);
        double motionX = Math.cos(rad) * speedVal;
        double motionZ = Math.sin(rad) * speedVal;

        if (!pauseInUnloadedChunks.getValue()) {
            return new Vec3(motionX, 0.0, motionZ);
        }

        int cx = (int) (mc.player.getX() / 16);
        int cz = (int) (mc.player.getZ() / 16);
        if (!mc.level.getChunkSource().hasChunk(cx, cz)) {
            return Vec3.ZERO;
        }
        return new Vec3(motionX, 0.0, motionZ);
    }

    private void tryToggleOffOnArrival() {
        if (!toggleOffOnArrival.getValue()) return;

        try {
            double dx = Double.parseDouble(destinationX.getValue());
            double dz = Double.parseDouble(destinationZ.getValue());
            double distanceX = mc.player.getX() - dx;
            double distanceZ = mc.player.getZ() - dz;
            if (Math.sqrt(distanceX * distanceX + distanceZ * distanceZ) <= 40.0) {
                setEnabled(false);
            }
        } catch (NumberFormatException ignored) {
        }
    }
}
