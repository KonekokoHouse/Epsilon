package com.github.epsilon.gui.panel.utils;

import net.minecraft.client.gui.screens.Screen;

import static com.github.epsilon.Constants.mc;

public class IMEFocusHelper {

    public static float activeCursorX = 0.0f;
    public static float activeCursorY = 0.0f;

    private static int refCount = 0;

    private IMEFocusHelper() {
    }

    public static void activate() {
        refCount++;
        if (refCount == 1) {
            Screen screen = mc.screen;
            if (screen != null) {
                mc.onTextInputFocusChange(screen, true);
            }
        }
    }

    public static void deactivate() {
        refCount = Math.max(0, refCount - 1);
        if (refCount == 0) {
            Screen screen = mc.screen;
            if (screen != null) {
                mc.onTextInputFocusChange(screen, false);
            }
        }
    }

    public static void forceDeactivate() {
        refCount = 0;
        Screen screen = mc.screen;
        if (screen != null) {
            mc.onTextInputFocusChange(screen, false);
        }
    }

    public static void updateCursorPos(float x, float y) {
        activeCursorX = x;
        activeCursorY = y;
    }

}
