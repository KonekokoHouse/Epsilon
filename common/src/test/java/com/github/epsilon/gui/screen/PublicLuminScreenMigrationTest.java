package com.github.epsilon.gui.screen;

import com.github.slmpc.lumingraphics.mc.v2612.runtime.MinecraftUiRuntime2612;
import com.github.slmpc.lumingraphics.ui.geometry.UiRect;
import com.github.slmpc.lumingraphics.ui.tree.UiTree;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicLuminScreenMigrationTest {
    private static final Path MAIN_MENU = Path.of("src/main/java/com/github/epsilon/gui/screen/MainMenuScreen.java");
    private static final Path HUD_EDITOR = Path.of("src/main/java/com/github/epsilon/gui/hudeditor/HudEditorScreen.java");
    private static final Path HUD_EDITOR_ADAPTER = Path.of(
            "src/main/java/com/github/epsilon/gui/hudeditor/HudEditorLuminTreeAdapter.java");
    private static final Path DROPDOWN = Path.of("src/main/java/com/github/epsilon/gui/dropdown/DropdownScreen.java");
    private static final Path PANEL = Path.of("src/main/java/com/github/epsilon/gui/panel/PanelScreen.java");

    @Test
    void screensUseOnlyThePublicLuminUiRuntime() throws IOException {
        String mainMenu = Files.readString(MAIN_MENU);
        String hudEditor = Files.readString(HUD_EDITOR);

        assertAll(
                () -> assertPublicLuminOnly(mainMenu),
                () -> assertPublicLuminOnly(hudEditor),
                () -> assertRuntimeLifecycle(mainMenu),
                () -> assertRuntimeLifecycle(hudEditor),
                () -> assertTrue(mainMenu.contains("\"epsilon-jura-light\"")),
                () -> assertTrue(mainMenu.contains("main-menu-background")),
                () -> assertFalse(mainMenu.contains("main-menu-ui")),
                () -> assertFalse(hudEditor.contains("LuminRenderSystem")),
                () -> assertFalse(hudEditor.contains("LuminRenderTarget")),
                () -> assertFalse(Files.exists(HUD_EDITOR_ADAPTER))
        );
    }

    @Test
    void publicRuntimeRejectsUnboundUseAndAllowsScissorOverflow() {
        assertThrows(IllegalStateException.class, MinecraftUiRuntime2612::current);

        UiTree clipped = UiTree.build(scope -> scope.scissor(
                new UiRect(0.0f, 0.0f, 10.0f, 10.0f), outer -> outer.scissor(
                        new UiRect(20.0f, 20.0f, 5.0f, 5.0f), inner -> { })));
        assertDoesNotThrow(clipped::validate);
    }

    @Test
    void screensExpressPainterOrderWithExplicitLayers() throws IOException {
        String dropdown = Files.readString(DROPDOWN);
        String hudEditor = Files.readString(HUD_EDITOR);
        String panel = Files.readString(PANEL);

        assertAll(
                () -> assertTrue(dropdown.contains("dropdownLayer = -10")),
                () -> assertTrue(dropdown.contains("dropdownLayer += 10")),
                () -> assertTrue(dropdown.contains(
                        "dropdownBatch.render(UiTree.from(dropdownScope), dropdownLayer)")),
                () -> assertFalse(dropdown.contains(
                        "dropdownBatch.render(UiTree.from(dropdownScope))")),
                () -> assertFalse(dropdown.contains("同层相交元素由 scheduler 自动保序")),
                () -> assertTrue(hudEditor.contains(
                        "editorBatch.render(UiTree.from(editorScope), editorLayer)")),
                () -> assertTrue(panel.contains("scene.batch(UiLayer.CONTENT, -20)")),
                () -> assertTrue(panel.contains("scene.batch(UiLayer.CONTENT, 0)")),
                () -> assertTrue(panel.contains("scene.batch(UiLayer.CONTENT, 20)")),
                () -> assertTrue(panel.contains("scene.batch(UiLayer.POPUP)"))
        );
    }

    private static void assertPublicLuminOnly(String source) {
        assertFalse(source.contains("import com.github.epsilon.gui.lib"));
        assertFalse(source.contains("import com.github.epsilon.lumin"));
    }

    private static void assertRuntimeLifecycle(String source) {
        assertTrue(source.contains("MinecraftUiRuntime2612.current()"));
        assertTrue(source.contains("runtime.createScene(EpsilonUiTheme.lumin())"));
        assertTrue(source.contains("runtime.render("));
    }
}
