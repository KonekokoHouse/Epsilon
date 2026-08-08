package com.github.epsilon.gui.screen;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainMenuTextScaleContractTest {
    private static final Path MAIN_MENU = Path.of("src/main/java/com/github/epsilon/gui/screen/MainMenuScreen.java");

    @Test
    void mainMenuUsesOneTextScaleWithoutPerLabelWidthShrinkage() throws IOException {
        String source = Files.readString(MAIN_MENU);
        int drawMenuStart = source.indexOf("private void drawMenu(");
        int drawMenuEnd = source.indexOf("private static float easeOutCubic(", drawMenuStart);
        assertTrue(drawMenuStart >= 0, "Main menu draw method must remain source-visible");
        assertTrue(drawMenuEnd > drawMenuStart, "Main menu draw method boundary must remain source-visible");
        String drawMenu = source.substring(drawMenuStart, drawMenuEnd);

        assertTrue(source.contains("private static final float MENU_TEXT_SCALE = 0.62f;"));
        assertTrue(drawMenu.contains("layer.text(title, titleX, titleY, MENU_TEXT_SCALE, titleColor);"));
        assertTrue(drawMenu.contains("layer.text(subtitle, titleX, subtitleY, MENU_TEXT_SCALE"));
        assertTrue(drawMenu.contains("layer.text(label, drawX, textY, MENU_TEXT_SCALE"));
        assertFalse(drawMenu.contains("menuTextScale"));
        assertFalse(drawMenu.contains("titleScale"));
        assertFalse(drawMenu.contains("subtitleScale"));
        assertFalse(drawMenu.contains("preferredButtonTextScale"));
        assertFalse(drawMenu.contains("buttonTextScale"));
        assertFalse(drawMenu.contains("labelWidth"));
        assertTrue(drawMenu.contains("float rowInset = Math.min(14.0f * scale, width * 0.5f);"));
        assertTrue(drawMenu.contains("textMetrics.textWidth(labels[index], MENU_TEXT_SCALE, null)"));
        assertTrue(drawMenu.contains("float buttonWidth = buttonWidths[index];"));
    }
}
