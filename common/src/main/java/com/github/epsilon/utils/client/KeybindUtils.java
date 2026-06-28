package com.github.epsilon.utils.client;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;

import static com.github.epsilon.Constants.mc;

/**
 * Encoding/formatting helpers for module keybinds stored as a single int.
 * <p>
 * Encoding scheme:
 * <ul>
 *   <li>{@code -1} — unbound (no key)</li>
 *   <li>{@code >= 0} — keyboard GLFW key code (matches {@link InputConstants.Type#KEYSYM})</li>
 *   <li>{@code <= -2} — mouse button, where {@code button = MOUSE_OFFSET - keyBind}
 *       (so {@code -2} = mouse 0 / LMB, {@code -3} = mouse 1 / RMB, ...)</li>
 * </ul>
 */
public class KeybindUtils {

    public static final int NONE = -1;
    public static final int MOUSE_OFFSET = -2;

    private KeybindUtils() {
    }

    public static boolean isMouseButton(int keyBind) {
        return keyBind <= MOUSE_OFFSET;
    }

    public static int encodeMouseButton(int button) {
        return MOUSE_OFFSET - button;
    }

    public static int decodeMouseButton(int keyBind) {
        return MOUSE_OFFSET - keyBind;
    }

    /**
     * Tests whether the encoded keybind is currently held down.
     * Mirrors {@link InputConstants#isKeyDown(Window, int)} for keyboard keys
     * and uses {@link GLFW#glfwGetMouseButton(long, int)} for mouse buttons.
     */
    public static boolean isPressed(int keyBind) {
        if (keyBind == NONE) {
            return false;
        }
        Window window = mc.getWindow();
        if (isMouseButton(keyBind)) {
            return GLFW.glfwGetMouseButton(window.handle(), decodeMouseButton(keyBind)) == GLFW.GLFW_PRESS;
        }
        return InputConstants.isKeyDown(window, keyBind);
    }

    public static String format(int keyBind) {
        if (keyBind == NONE) {
            return EpsilonTranslations.Keybind.NONE.getTranslatedName();
        }
        if (isMouseButton(keyBind)) {
            return "Mouse " + (decodeMouseButton(keyBind) + 1);
        }
        return InputConstants.Type.KEYSYM.getOrCreate(keyBind).getDisplayName().getString();
    }

}
