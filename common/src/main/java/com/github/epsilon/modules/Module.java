package com.github.epsilon.modules;

import com.github.epsilon.assets.i18n.TranslateComponent;
import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.settings.Setting;
import com.github.epsilon.settings.SettingGroup;
import com.github.epsilon.settings.impl.*;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.Block;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

public class Module {

    private final String name;

    private String addonId;

    private final Category category;

    private int keyBind = -1;

    public enum BindMode {
        Toggle,
        Hold
    }

    private BindMode bindMode = BindMode.Toggle;

    private boolean hidden = true;

    private boolean enabled;

    private boolean defaultHidden = true;

    private boolean defaultEnabled = false;

    public final List<Setting<?>> settings = new ArrayList<>();
    public final List<SettingGroup> settingGroups = new ArrayList<>();

    protected final Minecraft mc;

    public TranslateComponent translateComponent;

    public Module(String name, Category category) {
        this.name = name;
        this.category = category;
        mc = Minecraft.getInstance();
    }

    public void initI18n(TranslateComponent moduleComponent) {
        this.translateComponent = moduleComponent;
        for (SettingGroup group : settingGroups) {
            group.initTranslateComponent(moduleComponent.createChild(group.getName().toLowerCase()));
        }
        for (Setting<?> setting : settings) {
            setting.initTranslateComponent(moduleComponent.createChild(setting.getName().toLowerCase()));
        }
    }

    public void setAddonId(String addonId) {
        this.addonId = addonId;
    }

    public String getAddonId() {
        return addonId;
    }

    protected boolean nullCheck() {
        return mc.player == null || mc.level == null;
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            this.enabled = enabled;
            if (enabled) {
                EventBus.INSTANCE.subscribe(this);
                if (!nullCheck()) {
                    Managers.NOTIFICATION.moduleState(this.getTranslatedName(), getNotificationHash(), true);
                }
                onEnable();
            } else {
                EventBus.INSTANCE.unsubscribe(this);
                if (!nullCheck()) {
                    Managers.NOTIFICATION.moduleState(this.getTranslatedName(), getNotificationHash(), false);
                }
                onDisable();
            }
        }
    }

    protected void setDefaultEnabled(boolean defaultEnabled) {
        this.defaultEnabled = defaultEnabled;
        setEnabled(defaultEnabled);
    }

    protected void setDefaultHidden(boolean defaultHidden) {
        this.defaultHidden = defaultHidden;
        this.hidden = defaultHidden;
    }

    private int getNotificationHash() {
        String owner = addonId != null ? addonId : "epsilon";
        return (owner + ":" + name).hashCode();
    }

    public void reset() {
        setEnabled(false);
        keyBind = -1;
        bindMode = BindMode.Toggle;
        hidden = defaultHidden;
        resetCustomState();
        for (Setting<?> setting : settings) {
            setting.reset();
        }
        if (defaultEnabled) {
            setEnabled(true);
        }
    }

    protected <T extends Setting<?>> T addSetting(T setting) {
        settings.add(setting);
        return setting;
    }

    protected SettingGroup settingGroup(String name) {
        SettingGroup group = new SettingGroup(name);
        settingGroups.add(group);
        return group;
    }

    public List<Setting<?>> getSettings() {
        return settings;
    }

    public List<SettingGroup> getSettingGroups() {
        return settingGroups;
    }


    public Category getCategory() {
        return category;
    }

    public int getKeyBind() {
        return keyBind;
    }

    public void setKeyBind(int keyBind) {
        this.keyBind = keyBind;
    }

    public BindMode getBindMode() {
        return bindMode;
    }

    public void setBindMode(BindMode bindMode) {
        this.bindMode = bindMode;
    }

    public String getName() {
        return name;
    }

    public String getTranslatedName() {
        return translateComponent != null ? translateComponent.getTranslatedName() : name;
    }

    public String getInfo() {
        return null;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    protected IntSetting intSetting(String name, int defaultValue, int min, int max, int step, Setting.Dependency dependency) {
        return addSetting(new IntSetting(name, defaultValue, min, max, step, dependency, false));
    }

    protected IntSetting intSetting(String name, int defaultValue, int min, int max, int step, Setting.Dependency dependency, boolean percentageMode) {
        return addSetting(new IntSetting(name, defaultValue, min, max, step, dependency, percentageMode));
    }

    protected IntSetting intSetting(String name, int defaultValue, int min, int max, int step) {
        return addSetting(new IntSetting(name, defaultValue, min, max, step, () -> true, false));
    }

    protected BoolSetting boolSetting(String name, boolean defaultValue, Setting.Dependency dependency) {
        return addSetting(new BoolSetting(name, defaultValue, dependency, null));
    }

    protected BoolSetting boolSetting(String name, boolean defaultValue) {
        return addSetting(new BoolSetting(name, defaultValue, () -> true, null));
    }

    protected BoolSetting boolSetting(String name, boolean defaultValue, Setting.Dependency dependency, Consumer<Boolean> onChanged) {
        return addSetting(new BoolSetting(name, defaultValue, dependency, onChanged));
    }

    protected BoolSetting boolSetting(String name, boolean defaultValue, Consumer<Boolean> onChanged) {
        return addSetting(new BoolSetting(name, defaultValue, () -> true, onChanged));
    }

    protected DoubleSetting doubleSetting(String name, double defaultValue, double min, double max, double step, Setting.Dependency dependency) {
        return addSetting(new DoubleSetting(name, defaultValue, min, max, step, dependency, false));
    }

    protected DoubleSetting doubleSetting(String name, double defaultValue, double min, double max, double step, Setting.Dependency dependency, boolean percentageMode) {
        return addSetting(new DoubleSetting(name, defaultValue, min, max, step, dependency, percentageMode));
    }

    protected DoubleSetting doubleSetting(String name, double defaultValue, double min, double max, double step) {
        return addSetting(new DoubleSetting(name, defaultValue, min, max, step, () -> true, false));
    }

    protected StringSetting stringSetting(String name, String defaultValue, Setting.Dependency dependency) {
        return addSetting(new StringSetting(name, defaultValue, dependency));
    }

    protected StringSetting stringSetting(String name, String defaultValue, Setting.Dependency dependency, Consumer<String> onChanged) {
        return addSetting(new StringSetting(name, defaultValue, dependency, onChanged));
    }

    protected StringSetting stringSetting(String name, String defaultValue) {
        return addSetting(new StringSetting(name, defaultValue, () -> true));
    }

    protected StringSetting stringSetting(String name, String defaultValue, Consumer<String> onChanged) {
        return addSetting(new StringSetting(name, defaultValue, () -> true, onChanged));
    }

    protected BlockListSetting blockListSetting(String name, Collection<Block> defaultValue, Setting.Dependency dependency) {
        return addSetting(new BlockListSetting(name, defaultValue, dependency));
    }

    protected BlockListSetting blockListSetting(String name, Collection<Block> defaultValue) {
        return addSetting(new BlockListSetting(name, defaultValue, () -> true));
    }

    protected <E extends Enum<E>> EnumSetting<E> enumSetting(String name, E defaultValue, Setting.Dependency dependency, Consumer<E> onChanged) {
        return addSetting(new EnumSetting<>(name, defaultValue, dependency, onChanged));
    }

    protected <E extends Enum<E>> EnumSetting<E> enumSetting(String name, E defaultValue, Consumer<E> onChanged) {
        return addSetting(new EnumSetting<>(name, defaultValue, () -> true, onChanged));
    }

    protected <E extends Enum<E>> EnumSetting<E> enumSetting(String name, E defaultValue, Setting.Dependency dependency) {
        return addSetting(new EnumSetting<>(name, defaultValue, dependency, null));
    }

    protected <E extends Enum<E>> EnumSetting<E> enumSetting(String name, E defaultValue) {
        return addSetting(new EnumSetting<>(name, defaultValue, () -> true, null));
    }

    protected ColorSetting colorSetting(String name, Color defaultValue, boolean allowAlpha, Setting.Dependency dependency) {
        return addSetting(new ColorSetting(name, defaultValue, allowAlpha, dependency));
    }

    protected ColorSetting colorSetting(String name, Color defaultValue, Setting.Dependency dependency) {
        return addSetting(new ColorSetting(name, defaultValue, true, dependency));
    }

    protected ColorSetting colorSetting(String name, Color defaultValue, boolean allowAlpha) {
        return addSetting(new ColorSetting(name, defaultValue, allowAlpha, () -> true));
    }

    protected ColorSetting colorSetting(String name, Color defaultValue) {
        return addSetting(new ColorSetting(name, defaultValue, true, () -> true));
    }

    protected KeybindSetting keybindSetting(String name, int defaultValue, Setting.Dependency dependency) {
        return addSetting(new KeybindSetting(name, defaultValue, dependency));
    }

    protected KeybindSetting keybindSetting(String name, int defaultValue) {
        return addSetting(new KeybindSetting(name, defaultValue, () -> true));
    }

    protected ButtonSetting buttonSetting(String name, Runnable func, Setting.Dependency dependency) {
        return addSetting(new ButtonSetting(name, func, dependency));
    }

    protected ButtonSetting buttonSetting(String name, Runnable func) {
        return addSetting(new ButtonSetting(name, func, () -> true));
    }

    protected void resetCustomState() {
    }

    public JsonObject saveCustomState() {
        return null;
    }

    public void loadCustomState(JsonObject state) {
    }

}
