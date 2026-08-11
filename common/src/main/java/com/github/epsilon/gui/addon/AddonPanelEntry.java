package com.github.epsilon.gui.addon;

import com.github.epsilon.settings.Setting;

import java.util.List;

public interface AddonPanelEntry {
    enum Kind {
        JAVA_ADDON,
        LUA_SCRIPT,
        LUA_ERROR
    }

    String getAddonId();

    String getDisplayId();

    String getDisplayName();

    String getDescription();

    String getVersion();

    List<String> getAuthors();

    List<Setting<?>> getSettings();

    int getModuleCount();

    Kind getKind();

    default boolean isLua() {
        return getKind() != Kind.JAVA_ADDON;
    }

    default boolean canToggle() {
        return false;
    }

    default boolean isEnabled() {
        return true;
    }

    default void toggle() {
    }

    default boolean canReload() {
        return false;
    }

    default void reload() {
    }

    default String getError() {
        return "";
    }
}
