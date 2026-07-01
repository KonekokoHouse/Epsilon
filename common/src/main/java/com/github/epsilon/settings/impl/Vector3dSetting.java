package com.github.epsilon.settings.impl;

import com.github.epsilon.settings.Setting;
import org.joml.Vector3d;

public class Vector3dSetting extends Setting<Vector3d> {

    private final double min;
    private final double max;

    public Vector3dSetting(String name, Vector3d defaultValue, double min, double max, Dependency dependency) {
        super(name, dependency, null);
        this.min = min;
        this.max = max;
        this.defaultValue = new Vector3d(defaultValue);
        this.value = new Vector3d(defaultValue);
    }

    @Override
    public void setValue(Vector3d value) {
        if (value != null) {
            value.x = clamp(value.x);
            value.y = clamp(value.y);
            value.z = clamp(value.z);
        }
        super.setValue(value);
    }

    @Override
    public void reset() {
        if (value == null) value = new Vector3d();
        value.set(defaultValue);
    }

    public void set(double x, double y, double z) {
        if (value == null) value = new Vector3d();
        value.set(x, y, z);
        setValue(value);
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    private double clamp(double v) {
        return Math.max(min, Math.min(max, v));
    }
}
