package com.github.epsilon.graphics.text;

import com.github.epsilon.Constants;
import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.github.epsilon.graphics.text.ttf.TtfFontLoader;
import com.github.epsilon.modules.impl.ClientSetting;
import net.minecraft.resources.Identifier;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;

public class StaticFontLoader {

    private static final Identifier DEFAULT_FONT_ID = ResourceLocationUtils.getIdentifier("fonts/font.ttf");
    private static final TtfFontLoader BUILTIN_DEFAULT = new TtfFontLoader(DEFAULT_FONT_ID);

    public static volatile TtfFontLoader DEFAULT = BUILTIN_DEFAULT;

    public static final TtfFontLoader ICONS = new TtfFontLoader(ResourceLocationUtils.getIdentifier("fonts/icons.ttf"));

    public static final TtfFontLoader JURA_LIGHT = new TtfFontLoader(ResourceLocationUtils.getIdentifier("fonts/jura-light.ttf"));

    public static final TtfFontLoader OSAKA_CHIPS = new TtfFontLoader(ResourceLocationUtils.getIdentifier("fonts/osakachips.ttf"));

    private static TtfFontLoader customDefault;
    private static Path customDefaultPath;
    private static ClientSetting.FontMode appliedMode;
    private static String appliedCustomFont;

    public static TtfFontLoader defaultFont() {
        ClientSetting settings = ClientSetting.INSTANCE;
        ClientSetting.FontMode mode = settings.font.getValue();
        String fontPath = settings.customFont.getValue();
        if (mode == appliedMode && Objects.equals(fontPath, appliedCustomFont)) {
            return DEFAULT;
        }
        return applyDefaultFont(mode, fontPath);
    }

    private static synchronized TtfFontLoader applyDefaultFont(ClientSetting.FontMode mode, String fontPath) {
        if (mode == appliedMode && Objects.equals(fontPath, appliedCustomFont)) {
            return DEFAULT;
        }

        if (mode == ClientSetting.FontMode.Custom) {
            applyCustomDefault(fontPath);
        } else {
            applyBuiltinDefault();
        }
        appliedMode = mode;
        appliedCustomFont = fontPath;
        return DEFAULT;
    }

    private static void applyBuiltinDefault() {
        TtfFontLoader previous = customDefault;
        customDefault = null;
        customDefaultPath = null;
        DEFAULT = BUILTIN_DEFAULT;
        destroyCustom(previous);
    }

    private static void applyCustomDefault(String fontPath) {
        Path path = resolveCustomFont(fontPath);
        if (path == null || !Files.isRegularFile(path)) {
            applyBuiltinDefault();
            return;
        }

        if (customDefault != null && path.equals(customDefaultPath)) {
            DEFAULT = customDefault;
            return;
        }

        TtfFontLoader next;
        try {
            next = new TtfFontLoader(path);
        } catch (RuntimeException e) {
            Constants.LOGGER.warn("Failed to load custom default font: {}", path, e);
            applyBuiltinDefault();
            return;
        }

        TtfFontLoader previous = customDefault;
        customDefault = next;
        customDefaultPath = path;
        DEFAULT = next;
        destroyCustom(previous);
    }

    private static Path resolveCustomFont(String fontPath) {
        if (fontPath == null || fontPath.isBlank()) {
            return null;
        }

        String normalized = stripQuotes(fontPath.trim());
        try {
            Path path = Path.of(normalized);
            if (path.isAbsolute()) {
                return path.normalize();
            }

            Path workingDirectoryPath = path.toAbsolutePath().normalize();
            if (Files.isRegularFile(workingDirectoryPath)) {
                return workingDirectoryPath;
            }

            return Path.of(System.getProperty("user.home"), ".epsilon", "fonts")
                    .resolve(path)
                    .toAbsolutePath()
                    .normalize();
        } catch (InvalidPathException ignored) {
            return null;
        }
    }

    private static String stripQuotes(String value) {
        boolean doubleQuoted = value.startsWith("\"") && value.endsWith("\"");
        boolean singleQuoted = value.startsWith("'") && value.endsWith("'");
        if (value.length() >= 2 && (doubleQuoted || singleQuoted)) {
            return value.substring(1, value.length() - 1).trim();
        }
        return value;
    }

    private static void destroyCustom(TtfFontLoader fontLoader) {
        if (fontLoader != null) {
            fontLoader.destroy();
        }
    }

}
