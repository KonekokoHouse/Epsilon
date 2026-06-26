package com.github.epsilon.elements.impl.notification;

import java.awt.*;

public enum NotificationMode {

    Success(118, 185, 0),

    Info(255, 255, 255),

    Error(255, 75, 75);

    private final int red;
    private final int green;
    private final int blue;

    NotificationMode(int red, int green, int blue) {
        this.red = red;
        this.green = green;
        this.blue = blue;
    }

    public static NotificationMode fromEnabled(boolean enabled) {
        return enabled ? Success : Error;
    }

    public Color getColor() {
        return getColor(255);
    }

    public Color getColor(int alpha) {
        return new Color(red, green, blue, alpha);
    }

}
