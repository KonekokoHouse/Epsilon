package com.github.epsilon.graphics;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LuminMouseCoordinateContractTest {
    private static final Path PANEL = Path.of("src/main/java/com/github/epsilon/gui/panel/PanelScreen.java");
    private static final Path DROPDOWN = Path.of("src/main/java/com/github/epsilon/gui/dropdown/DropdownScreen.java");
    private static final Path HUD_EDITOR = Path.of("src/main/java/com/github/epsilon/gui/hudeditor/HudEditorScreen.java");
    private static final Path MAIN_MENU = Path.of("src/main/java/com/github/epsilon/gui/screen/MainMenuScreen.java");
    private static final Path MAPPER = Path.of("src/main/java/com/github/epsilon/gui/utils/UiCoordinateMapper.java");
    private static final Path REGISTRY_POPUP = Path.of(
            "src/main/java/com/github/epsilon/gui/panel/popup/RegistryListSelectPopup.java");

    @Test
    void customScreensUseProjectionSpaceForMouseRenderingAndInput() throws IOException {
        String panel = Files.readString(PANEL);
        String dropdown = Files.readString(DROPDOWN);
        String hudEditor = Files.readString(HUD_EDITOR);
        String mainMenu = Files.readString(MAIN_MENU);
        String mapper = Files.readString(MAPPER);

        assertAll(
                () -> assertContains(panel, "int epsilonMouseX = UiCoordinateMapper.toProjectionX(mouseX);"),
                () -> assertContains(panel, "MouseButtonEvent epsilonEvent = UiCoordinateMapper.toProjectionEvent(event);"),
                () -> assertContains(panel, "double epsilonDeltaX = UiCoordinateMapper.toProjectionX(deltaX);"),
                () -> assertContains(panel, "double epsilonDeltaY = UiCoordinateMapper.toProjectionY(deltaY);"),
                () -> assertContains(dropdown, "int epsilonMouseX = UiCoordinateMapper.toProjectionX(mouseX);"),
                () -> assertContains(dropdown, "MouseButtonEvent epsilonEvent = UiCoordinateMapper.toProjectionEvent(event);"),
                () -> assertContains(hudEditor, "pendingMouseX = UiCoordinateMapper.toProjectionX(mouseX);"),
                () -> assertContains(hudEditor, "MouseButtonEvent epsilonEvent = UiCoordinateMapper.toProjectionEvent(event);"),
                () -> assertContains(mainMenu, "pendingMouseX = UiCoordinateMapper.toProjectionX(mouseX);"),
                () -> assertContains(mainMenu, "MouseButtonEvent epsilonEvent = UiCoordinateMapper.toProjectionEvent(event);"),
                () -> assertContains(mapper, "mc.getWindow().getGuiScaledWidth()"),
                () -> assertContains(mapper, "mc.getWindow().getGuiScaledHeight()"),
                () -> assertContains(panel, "UiCoordinateMapper.getProjectionWidthInt()"),
                () -> assertContains(dropdown, "UiCoordinateMapper.getProjectionWidth()"),
                () -> assertContains(hudEditor, "UiCoordinateMapper.getProjectionHeight()"),
                () -> assertContains(mainMenu, "UiCoordinateMapper.getProjectionWidthInt()")
        );
    }

    @Test
    void nativeGuiOverlayCoordinatesReturnToMinecraftSpace() throws IOException {
        String panel = Files.readString(PANEL);
        String dropdown = Files.readString(DROPDOWN);
        String registryPopup = Files.readString(REGISTRY_POPUP);

        assertAll(
                () -> assertContains(panel, "UiCoordinateMapper.toMinecraftX(IMEFocusHelper.activeCursorX)"),
                () -> assertContains(panel, "UiCoordinateMapper.toMinecraftY(IMEFocusHelper.activeCursorY)"),
                () -> assertContains(dropdown, "UiCoordinateMapper.toMinecraftX(IMEFocusHelper.activeCursorX)"),
                () -> assertContains(dropdown, "UiCoordinateMapper.toMinecraftY(IMEFocusHelper.activeCursorY)"),
                () -> assertContains(registryPopup, "return (float) UiCoordinateMapper.toMinecraftX(uiX);"),
                () -> assertContains(registryPopup, "return (float) UiCoordinateMapper.toMinecraftY(uiY);")
        );
    }

    private static void assertContains(String source, String expected) {
        assertTrue(source.contains(expected), "Expected source contract: " + expected);
    }
}
