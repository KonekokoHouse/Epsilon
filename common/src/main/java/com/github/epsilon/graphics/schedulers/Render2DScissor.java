package com.github.epsilon.graphics.schedulers;

import java.util.Objects;

public record Render2DScissor(int x, int y, int width, int height) {

    public boolean visible() {
        return width > 0 && height > 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Render2DScissor that)) return false;
        return x == that.x && y == that.y && width == that.width && height == that.height;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, width, height);
    }

}
