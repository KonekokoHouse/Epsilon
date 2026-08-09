package com.github.epsilon.gui.screen;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientSettingFontBudgetContractTest {
    private static final Path CLIENT_SETTING =
            Path.of("src/main/java/com/github/epsilon/modules/impl/ClientSetting.java");

    @Test
    void configuredRuntimeAndSettingCallbackBothApplyTheGlyphBudget() throws IOException {
        String source = Files.readString(CLIENT_SETTING);
        int configure = source.indexOf("configureMinecraftFonts(MinecraftUiRuntime2612 runtime)");
        int callback = source.indexOf("applyFontGlyphUploadBudget(int maxGlyphsPerFrame)");

        assertTrue(configure >= 0 && callback > configure, "font budget methods must remain source-visible");
        assertTrue(source.substring(configure, callback).contains(
                        "runtime.setFontGlyphsPerFrame(getFontGlyphsPerFrame())"),
                "each frame must synchronize the setting before font measurement or drawing");
        assertTrue(source.substring(callback).contains("fontRuntime.setFontGlyphsPerFrame"),
                "changing the setting must update an already-bound runtime");
    }
}
