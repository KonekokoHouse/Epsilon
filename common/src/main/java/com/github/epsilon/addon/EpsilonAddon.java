package com.github.epsilon.addon;

import com.github.epsilon.assets.i18n.DefaultTranslateComponent;
import com.github.epsilon.holders.ModuleHolder;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.Setting;
import com.github.epsilon.settings.SettingGroup;
import com.github.epsilon.settings.impl.*;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class for Epsilon addons.
 */
public abstract class EpsilonAddon {

    public final String addonId;
    private final AddonSettingHost settingHost;
    private final ArrayList<Module> registeredModules = new ArrayList<>();

    protected EpsilonAddon(String addonId) {
        this.addonId = addonId;
        this.settingHost = new AddonSettingHost(addonId);
    }

    /**
     * Called after this addon is registered.
     */
    public abstract void onSetup();

    public String getAddonId() {
        return addonId;
    }

    public String getDisplayName() {
        return addonId;
    }

    public String getDescription() {
        return "";
    }

    public String getVersion() {
        return "";
    }

    public List<String> getAuthors() {
        return List.of();
    }

    public List<Setting<?>> getSettings() {
        return Collections.unmodifiableList(settingHost.getSettings());
    }

    public List<SettingGroup> getSettingGroups() {
        return Collections.unmodifiableList(settingHost.getSettingGroups());
    }

    public List<Module> getRegisteredModules() {
        return Collections.unmodifiableList(registeredModules);
    }

    public void resetSettings() {
        for (Setting<?> setting : settingHost.getSettings()) {
            if (setting != null) {
                setting.reset();
            }
        }
    }

    public void initAddonI18n() {
        for (SettingGroup group : settingHost.getSettingGroups()) {
            if (group != null) {
                group.initTranslateComponent(DefaultTranslateComponent.create(addonId + ".settings." + group.getName().toLowerCase()));
            }
        }
        for (Setting<?> setting : settingHost.getSettings()) {
            if (setting != null) {
                setting.initTranslateComponent(DefaultTranslateComponent.create(addonId + ".settings." + setting.getName().toLowerCase()));
            }
        }
    }

    protected void registerModule(Module module) {
        if (module == null) {
            return;
        }
        ModuleHolder.INSTANCE.registerAddonModule(
                addonId,
                module,
                DefaultTranslateComponent.create(addonId + ".modules." + module.getName().toLowerCase())
        );
        if (!registeredModules.contains(module)) {
            registeredModules.add(module);
        }
    }

    protected BoolSetting boolSetting(String name, boolean defaultValue, Setting.Dependency dependency) {
        return settingHost.addBoolSetting(name, defaultValue, dependency);
    }

    protected BoolSetting boolSetting(String name, boolean defaultValue) {
        return settingHost.addBoolSetting(name, defaultValue);
    }

    protected SettingGroup settingGroup(String name) {
        return settingHost.addSettingGroup(name);
    }

    protected IntSetting intSetting(String name, int defaultValue, int min, int max, int step, Setting.Dependency dependency) {
        return settingHost.addIntSetting(name, defaultValue, min, max, step, dependency);
    }

    protected IntSetting intSetting(String name, int defaultValue, int min, int max, int step, Setting.Dependency dependency, boolean percentageMode) {
        return settingHost.addIntSetting(name, defaultValue, min, max, step, dependency, percentageMode);
    }

    protected IntSetting intSetting(String name, int defaultValue, int min, int max, int step) {
        return settingHost.addIntSetting(name, defaultValue, min, max, step);
    }

    protected DoubleSetting doubleSetting(String name, double defaultValue, double min, double max, double step, Setting.Dependency dependency) {
        return settingHost.addDoubleSetting(name, defaultValue, min, max, step, dependency);
    }

    protected DoubleSetting doubleSetting(String name, double defaultValue, double min, double max, double step, Setting.Dependency dependency, boolean percentageMode) {
        return settingHost.addDoubleSetting(name, defaultValue, min, max, step, dependency, percentageMode);
    }

    protected DoubleSetting doubleSetting(String name, double defaultValue, double min, double max, double step) {
        return settingHost.addDoubleSetting(name, defaultValue, min, max, step);
    }

    protected StringSetting stringSetting(String name, String defaultValue, Setting.Dependency dependency) {
        return settingHost.addStringSetting(name, defaultValue, dependency);
    }

    protected StringSetting stringSetting(String name, String defaultValue) {
        return settingHost.addStringSetting(name, defaultValue);
    }

    protected <E extends Enum<E>> EnumSetting<E> enumSetting(String name, E defaultValue, Setting.Dependency dependency) {
        return settingHost.addEnumSetting(name, defaultValue, dependency);
    }

    protected <E extends Enum<E>> EnumSetting<E> enumSetting(String name, E defaultValue) {
        return settingHost.addEnumSetting(name, defaultValue);
    }

    protected ColorSetting colorSetting(String name, Color defaultValue, Setting.Dependency dependency) {
        return settingHost.addColorSetting(name, defaultValue, dependency);
    }

    protected ColorSetting colorSetting(String name, Color defaultValue) {
        return settingHost.addColorSetting(name, defaultValue);
    }

    protected KeybindSetting keybindSetting(String name, int defaultValue, Setting.Dependency dependency) {
        return settingHost.addKeybindSetting(name, defaultValue, dependency);
    }

    protected KeybindSetting keybindSetting(String name, int defaultValue) {
        return settingHost.addKeybindSetting(name, defaultValue);
    }

    protected ButtonSetting buttonSetting(String name, Runnable func, Setting.Dependency dependency) {
        return settingHost.addButtonSetting(name, func, dependency);
    }

    protected ButtonSetting buttonSetting(String name, Runnable func) {
        return settingHost.addButtonSetting(name, func);
    }

    private static final class AddonSettingHost extends Module {

        private AddonSettingHost(String addonId) {
            super(addonId + " addon settings", null);
            setAddonId(addonId);
        }

        @Override
        public void reset() {
            for (Setting<?> setting : settings) {
                if (setting != null) {
                    setting.reset();
                }
            }
        }

        private BoolSetting addBoolSetting(String name, boolean defaultValue, Setting.Dependency dependency) {
            return super.boolSetting(name, defaultValue, dependency);
        }

        private BoolSetting addBoolSetting(String name, boolean defaultValue) {
            return super.boolSetting(name, defaultValue);
        }

        private SettingGroup addSettingGroup(String name) {
            return super.settingGroup(name);
        }

        private IntSetting addIntSetting(String name, int defaultValue, int min, int max, int step, Setting.Dependency dependency) {
            return super.intSetting(name, defaultValue, min, max, step, dependency);
        }

        private IntSetting addIntSetting(String name, int defaultValue, int min, int max, int step, Setting.Dependency dependency, boolean percentageMode) {
            return super.intSetting(name, defaultValue, min, max, step, dependency, percentageMode);
        }

        private IntSetting addIntSetting(String name, int defaultValue, int min, int max, int step) {
            return super.intSetting(name, defaultValue, min, max, step);
        }

        private DoubleSetting addDoubleSetting(String name, double defaultValue, double min, double max, double step, Setting.Dependency dependency) {
            return super.doubleSetting(name, defaultValue, min, max, step, dependency);
        }

        private DoubleSetting addDoubleSetting(String name, double defaultValue, double min, double max, double step, Setting.Dependency dependency, boolean percentageMode) {
            return super.doubleSetting(name, defaultValue, min, max, step, dependency, percentageMode);
        }

        private DoubleSetting addDoubleSetting(String name, double defaultValue, double min, double max, double step) {
            return super.doubleSetting(name, defaultValue, min, max, step);
        }

        private StringSetting addStringSetting(String name, String defaultValue, Setting.Dependency dependency) {
            return super.stringSetting(name, defaultValue, dependency);
        }

        private StringSetting addStringSetting(String name, String defaultValue) {
            return super.stringSetting(name, defaultValue);
        }

        private <E extends Enum<E>> EnumSetting<E> addEnumSetting(String name, E defaultValue, Setting.Dependency dependency) {
            return super.enumSetting(name, defaultValue, dependency);
        }

        private <E extends Enum<E>> EnumSetting<E> addEnumSetting(String name, E defaultValue) {
            return super.enumSetting(name, defaultValue);
        }

        private ColorSetting addColorSetting(String name, Color defaultValue, Setting.Dependency dependency) {
            return super.colorSetting(name, defaultValue, dependency);
        }

        private ColorSetting addColorSetting(String name, Color defaultValue) {
            return super.colorSetting(name, defaultValue);
        }

        private KeybindSetting addKeybindSetting(String name, int defaultValue, Setting.Dependency dependency) {
            return super.keybindSetting(name, defaultValue, dependency);
        }

        private KeybindSetting addKeybindSetting(String name, int defaultValue) {
            return super.keybindSetting(name, defaultValue);
        }

        private ButtonSetting addButtonSetting(String name, Runnable func, Setting.Dependency dependency) {
            return super.buttonSetting(name, func, dependency);
        }

        private ButtonSetting addButtonSetting(String name, Runnable func) {
            return super.buttonSetting(name, func);
        }
    }

}
