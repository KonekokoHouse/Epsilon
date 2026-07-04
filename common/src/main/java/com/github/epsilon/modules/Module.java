package com.github.epsilon.modules;

import com.github.epsilon.assets.i18n.TranslateComponent;
import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.settings.Setting;
import com.github.epsilon.settings.SettingGroup;
import com.github.epsilon.settings.impl.*;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.joml.Vector3d;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

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
        for (SettingGroup group : settingGroups) {
            if (group.getName().equalsIgnoreCase(name)) {
                return group;
            }
        }
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

    protected IntSetting intSetting(String name, int defaultValue, int min, int max, int step) {
        return addSetting(new IntSetting(name, defaultValue, min, max, step, () -> true, null));
    }

    protected IntSetting intSetting(String name, int defaultValue, int min, int max, int step, Consumer<Integer> onChanged) {
        return addSetting(new IntSetting(name, defaultValue, min, max, step, () -> true, onChanged));
    }

    protected IntSetting intSetting(String name, int defaultValue, int min, int max, int step, Setting.Dependency dependency) {
        return addSetting(new IntSetting(name, defaultValue, min, max, step, dependency, null));
    }

    protected IntSetting intSetting(String name, int defaultValue, int min, int max, int step, Setting.Dependency dependency, Consumer<Integer> onChanged) {
        return addSetting(new IntSetting(name, defaultValue, min, max, step, dependency, onChanged));
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

    protected DoubleSetting doubleSetting(String name, double defaultValue, double min, double max, double step) {
        return addSetting(new DoubleSetting(name, defaultValue, min, max, step, () -> true, null));
    }

    protected DoubleSetting doubleSetting(String name, double defaultValue, double min, double max, double step, Setting.Dependency dependency) {
        return addSetting(new DoubleSetting(name, defaultValue, min, max, step, dependency, null));
    }

    protected DoubleSetting doubleSetting(String name, double defaultValue, double min, double max, double step, Setting.Dependency dependency, Consumer<Double> onChanged) {
        return addSetting(new DoubleSetting(name, defaultValue, min, max, step, dependency, onChanged));
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

    protected RegistryListSetting<Block> blockListSetting(String name, Collection<Block> defaultValue, Setting.Dependency dependency) {
        return addSetting(new RegistryListSetting<>(name, defaultValue, RegistryListSetting.Type.BLOCK, null, dependency));
    }

    protected RegistryListSetting<Block> blockListSetting(String name, Collection<Block> defaultValue) {
        return addSetting(new RegistryListSetting<>(name, defaultValue, RegistryListSetting.Type.BLOCK, null, () -> true));
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

    // --- BlockSetting ---

    protected BlockSetting blockSetting(String name, Block defaultValue, Predicate<Block> filter, Setting.Dependency dependency) {
        return addSetting(new BlockSetting(name, defaultValue, filter, dependency));
    }

    protected BlockSetting blockSetting(String name, Block defaultValue, Setting.Dependency dependency) {
        return addSetting(new BlockSetting(name, defaultValue, null, dependency));
    }

    protected BlockSetting blockSetting(String name, Block defaultValue, Predicate<Block> filter) {
        return addSetting(new BlockSetting(name, defaultValue, filter, () -> true));
    }

    protected BlockSetting blockSetting(String name, Block defaultValue) {
        return addSetting(new BlockSetting(name, defaultValue, null, () -> true));
    }

    // --- ItemSetting ---

    protected ItemSetting itemSetting(String name, Item defaultValue, Predicate<Item> filter, Setting.Dependency dependency) {
        return addSetting(new ItemSetting(name, defaultValue, filter, dependency));
    }

    protected ItemSetting itemSetting(String name, Item defaultValue, Setting.Dependency dependency) {
        return addSetting(new ItemSetting(name, defaultValue, null, dependency));
    }

    protected ItemSetting itemSetting(String name, Item defaultValue, Predicate<Item> filter) {
        return addSetting(new ItemSetting(name, defaultValue, filter, () -> true));
    }

    protected ItemSetting itemSetting(String name, Item defaultValue) {
        return addSetting(new ItemSetting(name, defaultValue, null, () -> true));
    }

    // --- ItemListSetting ---

    protected RegistryListSetting<Item> itemListSetting(String name, Collection<Item> defaultValue, Predicate<Item> filter, Setting.Dependency dependency) {
        return addSetting(new RegistryListSetting<>(name, defaultValue, RegistryListSetting.Type.ITEM, filter, dependency));
    }

    protected RegistryListSetting<Item> itemListSetting(String name, Collection<Item> defaultValue, Setting.Dependency dependency) {
        return addSetting(new RegistryListSetting<>(name, defaultValue, RegistryListSetting.Type.ITEM, null, dependency));
    }

    protected RegistryListSetting<Item> itemListSetting(String name, Collection<Item> defaultValue, Predicate<Item> filter) {
        return addSetting(new RegistryListSetting<>(name, defaultValue, RegistryListSetting.Type.ITEM, filter, () -> true));
    }

    protected RegistryListSetting<Item> itemListSetting(String name, Collection<Item> defaultValue) {
        return addSetting(new RegistryListSetting<>(name, defaultValue, RegistryListSetting.Type.ITEM, null, () -> true));
    }

    // --- EntityTypeListSetting ---

    protected EntityTypeListSetting entityTypeListSetting(String name, Collection<EntityType<?>> defaultValue, Predicate<EntityType<?>> filter, Setting.Dependency dependency) {
        return addSetting(new EntityTypeListSetting(name, defaultValue, filter, dependency));
    }

    protected EntityTypeListSetting entityTypeListSetting(String name, Collection<EntityType<?>> defaultValue, Setting.Dependency dependency) {
        return addSetting(new EntityTypeListSetting(name, defaultValue, null, dependency));
    }

    protected EntityTypeListSetting entityTypeListSetting(String name, Collection<EntityType<?>> defaultValue, Predicate<EntityType<?>> filter) {
        return addSetting(new EntityTypeListSetting(name, defaultValue, filter, () -> true));
    }

    protected EntityTypeListSetting entityTypeListSetting(String name, Collection<EntityType<?>> defaultValue) {
        return addSetting(new EntityTypeListSetting(name, defaultValue, null, () -> true));
    }

    // --- StringListSetting ---

    protected StringListSetting stringListSetting(String name, Collection<String> defaultValue, Setting.Dependency dependency) {
        return addSetting(new StringListSetting(name, defaultValue, dependency));
    }

    protected StringListSetting stringListSetting(String name, Collection<String> defaultValue) {
        return addSetting(new StringListSetting(name, defaultValue, () -> true));
    }

    // --- BlockPosSetting ---

    protected BlockPosSetting blockPosSetting(String name, BlockPos defaultValue, Setting.Dependency dependency) {
        return addSetting(new BlockPosSetting(name, defaultValue, dependency));
    }

    protected BlockPosSetting blockPosSetting(String name, BlockPos defaultValue) {
        return addSetting(new BlockPosSetting(name, defaultValue, () -> true));
    }

    // --- Vector3dSetting ---

    protected Vector3dSetting vector3dSetting(String name, Vector3d defaultValue, double min, double max, Setting.Dependency dependency) {
        return addSetting(new Vector3dSetting(name, defaultValue, min, max, dependency));
    }

    protected Vector3dSetting vector3dSetting(String name, Vector3d defaultValue, double min, double max) {
        return addSetting(new Vector3dSetting(name, defaultValue, min, max, () -> true));
    }

    protected Vector3dSetting vector3dSetting(String name, Vector3d defaultValue, Setting.Dependency dependency) {
        return addSetting(new Vector3dSetting(name, defaultValue, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, dependency));
    }

    protected Vector3dSetting vector3dSetting(String name, Vector3d defaultValue) {
        return addSetting(new Vector3dSetting(name, defaultValue, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, () -> true));
    }

    // --- SoundEventListSetting ---

    protected RegistryListSetting<SoundEvent> soundEventListSetting(String name, Collection<SoundEvent> defaultValue, Setting.Dependency dependency) {
        return addSetting(new RegistryListSetting<>(name, defaultValue, RegistryListSetting.Type.SOUND_EVENT, null, dependency));
    }

    protected RegistryListSetting<SoundEvent> soundEventListSetting(String name, Collection<SoundEvent> defaultValue) {
        return addSetting(new RegistryListSetting<>(name, defaultValue, RegistryListSetting.Type.SOUND_EVENT, null, () -> true));
    }


    // --- StatusEffectListSetting ---

    protected RegistryListSetting<MobEffect> statusEffectListSetting(String name, Collection<MobEffect> defaultValue, Setting.Dependency dependency) {
        return addSetting(new RegistryListSetting<>(name, defaultValue, RegistryListSetting.Type.MOB_EFFECT, null, dependency));
    }

    protected RegistryListSetting<MobEffect> statusEffectListSetting(String name, Collection<MobEffect> defaultValue) {
        return addSetting(new RegistryListSetting<>(name, defaultValue, RegistryListSetting.Type.MOB_EFFECT, null, () -> true));
    }


    // --- EnchantmentListSetting ---

    protected EnchantmentListSetting enchantmentListSetting(String name, Collection<String> defaultValue, Setting.Dependency dependency) {
        return addSetting(new EnchantmentListSetting(name, defaultValue, dependency));
    }

    protected EnchantmentListSetting enchantmentListSetting(String name, Collection<String> defaultValue) {
        return addSetting(new EnchantmentListSetting(name, defaultValue, () -> true));
    }

    // --- StatusEffectAmplifierMapSetting ---

    protected StatusEffectAmplifierMapSetting statusEffectAmplifierMapSetting(String name, Map<MobEffect, Integer> defaultValue, Setting.Dependency dependency) {
        return addSetting(new StatusEffectAmplifierMapSetting(name, defaultValue, dependency));
    }

    protected StatusEffectAmplifierMapSetting statusEffectAmplifierMapSetting(String name, Map<MobEffect, Integer> defaultValue) {
        return addSetting(new StatusEffectAmplifierMapSetting(name, defaultValue, () -> true));
    }

    // --- PacketListSetting ---

    protected PacketListSetting packetListSetting(String name, Collection<Class<? extends Packet<?>>> defaultValue,
                                                   Predicate<Class<? extends Packet<?>>> filter, Setting.Dependency dependency) {
        return addSetting(new PacketListSetting(name, defaultValue, filter, dependency));
    }

    protected PacketListSetting packetListSetting(String name, Collection<Class<? extends Packet<?>>> defaultValue, Setting.Dependency dependency) {
        return addSetting(new PacketListSetting(name, defaultValue, null, dependency));
    }

    protected PacketListSetting packetListSetting(String name, Collection<Class<? extends Packet<?>>> defaultValue) {
        return addSetting(new PacketListSetting(name, defaultValue, null, () -> true));
    }

    // --- ModuleListSetting ---

    protected StringListSetting moduleListSetting(String name, Collection<String> defaultValue, Setting.Dependency dependency) {
        return addSetting(new StringListSetting(name, defaultValue, dependency));
    }

    protected StringListSetting moduleListSetting(String name, Collection<String> defaultValue) {
        return addSetting(new StringListSetting(name, defaultValue, () -> true));
    }

    protected void resetCustomState() {
    }

    public JsonObject saveCustomState() {
        return null;
    }

    public void loadCustomState(JsonObject state) {
    }

}
