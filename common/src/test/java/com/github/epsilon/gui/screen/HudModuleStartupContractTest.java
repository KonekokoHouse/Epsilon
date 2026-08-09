package com.github.epsilon.gui.screen;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HudModuleStartupContractTest {
    private static final Path HUD_MODULE = Path.of("src/main/java/com/github/epsilon/elements/HudModule.java");

    @Test
    void configResetDoesNotRequireMinecraftWindowDuringClientConstruction() throws IOException {
        String source = Files.readString(HUD_MODULE);
        int widthMethod = source.indexOf("private int getScreenWidth()");
        int heightMethod = source.indexOf("private int getScreenHeight()");

        assertTrue(widthMethod >= 0 && heightMethod > widthMethod, "HUD screen-size methods must remain source-visible");
        assertTrue(source.substring(widthMethod, heightMethod).contains("mc.getWindow() == null"),
                "HUD width lookup must tolerate config loading before Minecraft creates its window");
        assertTrue(source.substring(heightMethod).contains("mc.getWindow() == null"),
                "HUD height lookup must tolerate config loading before Minecraft creates its window");
    }

}
