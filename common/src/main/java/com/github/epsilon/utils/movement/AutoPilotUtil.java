package com.github.epsilon.utils.movement;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.github.epsilon.Constants.mc;

/**
 * Autopilot navigation utility
 * Calculates movement yaw toward a destination coordinate,
 * with optional player dodge and manual override detection.
 */
public class AutoPilotUtil {

    /**
     * Calculates the yaw angle to navigate toward the destination.
     * Returns -999 if autopilot is not active or no destination is set.
     *
     * @param destinationX destination X coordinate string (parsed as double)
     * @param destinationZ destination Z coordinate string (parsed as double)
     * @param autoPlaneY    minimum Y level to engage autopilot
     * @param autoPlane     whether autopilot is enabled
     * @param playerDodge   whether to dodge nearby non-friend players
     * @return yaw angle in degrees, or -999.F if inactive
     */
    public static float calcAutoMoveYaw(String destinationX, String destinationZ, int autoPlaneY,
                                         boolean autoPlane, boolean playerDodge) {
        float yaw = -999.0F;
        if (autoPlane && mc.player.getY() > autoPlaneY && !isMoveBindPress() && !mc.options.keyJump.isDown()) {
            Double x = null;
            Double z = null;
            try {
                x = Double.valueOf(destinationX);
                z = Double.valueOf(destinationZ);
            } catch (NumberFormatException ignored) {}
            if (x == null || z == null) return -999.0F;
            Vec3 destination = new Vec3(x, mc.player.getY(), z);
            if (Math.sqrt(mc.player.distanceToSqr(destination)) > 40.0D) {
                yaw = getLegitRotations(destination)[0];
            }
        }
        if (!autoPlane) return -999.0F;
        if (playerDodge && yaw == -999.0F && !isMoveBindPress() && !mc.options.keyJump.isDown()) {
            List<AbstractClientPlayer> players = mc.level.players().stream()
                    .filter(p -> mc.player.distanceTo(p) <= 16.0F && !mc.player.equals(p))
                    .collect(Collectors.toList());
            players.sort(Comparator.comparingDouble(mc.player::distanceTo));
            if (!players.isEmpty()) {
                float[] rotations = getLegitRotations(players.get(0).position());
                yaw = rotations[0] + 180.0F;
            }
        }
        return yaw;
    }

    /**
     * Calculates rotation angles (yaw, pitch) to look at a target position.
     */
    public static float[] getLegitRotations(Vec3 target) {
        Vec3 eyesPos = mc.player.getEyePosition();
        double diffX = target.x - eyesPos.x;
        double diffY = target.y - eyesPos.y;
        double diffZ = target.z - eyesPos.z;
        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(diffY, diffXZ));
        return new float[]{
                mc.player.getYHeadRot() + Mth.wrapDegrees(yaw - mc.player.getYHeadRot()),
                mc.player.getXRot() + Mth.wrapDegrees(pitch - mc.player.getXRot())
        };
    }

    /**
     * Checks if any WASD movement key is pressed.
     */
    public static boolean isMoveBindPress() {
        return mc.options.keyUp.isDown() || mc.options.keyDown.isDown()
                || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown();
    }

    /**
     * Gets clipboard text as string.
     */
    public static String getClipboardText() {
        try {
            String text = org.lwjgl.glfw.GLFW.glfwGetClipboardString(mc.getWindow().handle());
            if (text != null) return text;
        } catch (Exception ignored) {}
        try {
            java.awt.datatransfer.Clipboard clipboard = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
            java.awt.datatransfer.Transferable contents = clipboard.getContents(null);
            if (contents != null && contents.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor)) {
                return (String) contents.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor);
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Parses coordinate numbers from text. Returns [x, z] or null.
     */
    public static double[] parseCoordinates(String text) {
        if (text == null || text.isEmpty()) return null;
        Pattern pattern = Pattern.compile("-?\\d+(?:\\.\\d+)?");
        Matcher matcher = pattern.matcher(text);
        List<Double> numbers = new ArrayList<>();
        while (matcher.find()) {
            try {
                numbers.add(Double.parseDouble(matcher.group()));
            } catch (NumberFormatException ignored) {}
        }
        if (numbers.size() < 2) return null;
        return new double[]{numbers.get(0), numbers.get(numbers.size() - 1)};
    }
}
