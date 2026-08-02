package com.github.epsilon.gui.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UiCoordinateMapperScaleTest {

    @Test
    void mouseCoordinatesUseTheEffectiveMinecraftGuiExtent() {
        double mapped = UiCoordinateMapper.toProjectionCoordinate(320.5, 1921.0, 641.0, 2.5);
        double expected = 320.5 * 1921.0 / 641.0 / 2.5;

        assertEquals(expected, mapped, 0.0000001,
                "mouse coordinates must follow Minecraft's rounded GUI extent before Client Render Scale");
    }

    @Test
    void projectionViewportUsesFramebufferExtentAndClientScale() {
        assertEquals(960.0, UiCoordinateMapper.projectionExtent(1920.0, 2.0), 0.0000001,
                "custom UI layout must use the Client Render Scale viewport");
    }
}
