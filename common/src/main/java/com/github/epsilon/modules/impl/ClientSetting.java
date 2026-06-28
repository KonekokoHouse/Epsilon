package com.github.epsilon.modules.impl;

import com.github.epsilon.assets.i18n.EpsilonLanguage;
import com.github.epsilon.assets.i18n.EpsilonLanguageManager;
import com.github.epsilon.gui.dropdown.DropdownScreen;
import com.github.epsilon.gui.hudeditor.HudEditorScreen;
import com.github.epsilon.gui.panel.MD3Theme;
import com.github.epsilon.gui.panel.PanelScreen;
import com.github.epsilon.gui.screen.MainMenuScreen;
import com.github.epsilon.holders.TextureCacheHolder;
import com.github.epsilon.holders.TranslateHolder;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.managers.impl.rotations.RotationManager;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.*;
import com.mojang.blaze3d.platform.IconSet;
import net.minecraft.SharedConstants;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.io.IOException;

public class ClientSetting extends Module {

    public static final ClientSetting INSTANCE = new ClientSetting();

    private ClientSetting() {
        super("Client Setting", null);
    }

    public enum GuiMode {
        Dropdown,
        Panel
    }

    public enum ModuleSort {
        Name,
        EnabledFirst,
        Addon
    }

    public enum ThemePreset {
        TonalSpot,
        Neutral,
        Vibrant,
        Expressive,
        Fidelity,
        Content,
        Rainbow,
        FruitSalad,
        Monochrome
    }

    public enum ThemeMode {
        Dark,
        Light
    }

    public enum IconMode {
        Vanilla,
        Minecraft_1_8_9,
        Epsilon
    }

    public enum TitleMode {
        Vanilla,
        Minecraft_1_8_9,
        Epsilon
    }

    public enum HideMode {
        None,
        Hide,
        Vanilla
    }
    @SuppressWarnings("unused")
    private final ButtonSetting openHUDEditor = buttonSetting("Open HUD Editor", () -> mc.setScreen(HudEditorScreen.INSTANCE));

    // General
    public final KeybindSetting guiKeybind = keybindSetting("Gui Keybind", GLFW.GLFW_KEY_RIGHT_SHIFT);

    public final EnumSetting<GuiMode> guiMode = enumSetting("Gui Mode", GuiMode.Dropdown, _ -> mc.setScreen(switch (ClientSetting.INSTANCE.guiMode.getValue()) {
        case Panel -> PanelScreen.INSTANCE;
        case Dropdown -> DropdownScreen.INSTANCE;
    }));

    public final EnumSetting<ModuleSort> moduleSort = enumSetting("Module Sort", ModuleSort.Name);

    public final EnumSetting<EpsilonLanguage> language = enumSetting("Language", EpsilonLanguage.English, EpsilonLanguageManager.INSTANCE::selectLanguage);

    public final StringSetting customLanguage = stringSetting("Custom Language", "", () -> language.is(EpsilonLanguage.Custom), _ -> EpsilonLanguageManager.INSTANCE.refreshCustomLanguage());

    private final DoubleSetting renderScale = doubleSetting("Render Scale", 2.0, 1.0, 6.0, 0.5);

    public final BoolSetting i18nFallback = boolSetting("I18n Fallback", true, _ -> {
        TranslateHolder.INSTANCE.refresh();
        TextureCacheHolder.INSTANCE.clearCache();
    });

    public final BoolSetting fontAntiAliasing = boolSetting("Font Anti Aliasing", true);

    public final BoolSetting replaceMinecraftFont = boolSetting("Replace Minecraft Font", true);

    public final BoolSetting closeOnOutside = boolSetting("Close Gui On Outside", false, () -> guiMode.is(GuiMode.Panel));

    public final BoolSetting dropdownHints = boolSetting("Dropdown Hints", true, () -> guiMode.is(GuiMode.Dropdown));

    // Anti Cheat
    public final EnumSetting<RotationManager.RotationMode> rotationMode =
            enumSetting("Rotation Mode", RotationManager.RotationMode.SILENT, mode -> {
                if (Managers.ROTATION != null) {
                    Managers.switchRotationManager(mode);
                }
            });

    public final BoolSetting modifyCrosshair = boolSetting("Modify Crosshair", true);

    public final EnumSetting<HideMode> hideMode = enumSetting("Hide Mode", HideMode.None);

    // Appearance
    public final EnumSetting<ThemeMode> themeMode = enumSetting("Theme Mode", ThemeMode.Dark, _ -> MD3Theme.syncFromSettings());

    public final EnumSetting<ThemePreset> themePreset = enumSetting("Theme Preset", ThemePreset.TonalSpot, _ -> MD3Theme.syncFromSettings());

    public final EnumSetting<IconMode> customIcon = enumSetting("Custom Icon", IconMode.Epsilon, _ -> {
        try {
            mc.getWindow().setIcon(mc.getVanillaPackResources(), SharedConstants.getCurrentVersion().stable() ? IconSet.RELEASE : IconSet.SNAPSHOT);
        } catch (IOException ignored) {
        }
    });

    public final EnumSetting<TitleMode> customTitle = enumSetting("Custom Title", TitleMode.Epsilon, _ -> mc.updateTitle());

    public final BoolSetting useMainMenu = boolSetting("Use MainMenu", true);

    public final EnumSetting<MainMenuScreen.Background> mainMenuBackground = enumSetting("MainMenu Background", MainMenuScreen.Background.PLANET, useMainMenu::getValue);

    public final BoolSetting showWelcomeScreen = boolSetting("Show Welcome Screen", true).rootSetting();

    // Notification
    public final BoolSetting soundNotify = boolSetting("Sound Notify", true);

    public final BoolSetting chatNotify = boolSetting("Chat Notify", true);

    public final BoolSetting animatedChatPrefix = boolSetting("Animated Chat Prefix", true);

    public final ColorSetting chatPrefixColorStart = colorSetting("Chat Prefix Color Start", new Color(255, 175, 210), animatedChatPrefix::getValue);

    public final ColorSetting chatPrefixColorEnd = colorSetting("Chat Prefix Color End", new Color(150, 220, 255), animatedChatPrefix::getValue);

    public final DoubleSetting chatPrefixGradientSpeed = doubleSetting("Chat Prefix Gradient Speed", 0.5, 0.1, 1, 0.1, animatedChatPrefix::getValue);

    public double getScale() {
        return renderScale.getValue();
    }

    public boolean snapRotation() {
        return rotationMode.is(RotationManager.RotationMode.SNAP);
    }

    public boolean silentRotation() {
        return rotationMode.is(RotationManager.RotationMode.SILENT);
    }

}
