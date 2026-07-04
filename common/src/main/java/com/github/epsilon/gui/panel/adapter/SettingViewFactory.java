package com.github.epsilon.gui.panel.adapter;

import com.github.epsilon.gui.panel.component.SettingRow;
import com.github.epsilon.gui.panel.component.setting.*;
import com.github.epsilon.settings.Setting;
import com.github.epsilon.settings.impl.*;

public class SettingViewFactory {

    private SettingViewFactory() {
    }

    public static SettingRow<?> create(Setting<?> setting) {
        return switch (setting) {
            case KeybindSetting keybindSetting -> new KeybindSettingRow(keybindSetting);
            case BoolSetting boolSetting -> new BoolSettingRow(boolSetting);
            case EnumSetting<?> enumSetting -> new EnumSettingRow(enumSetting);
            case IntSetting intSetting -> new IntSettingRow(intSetting);
            case DoubleSetting doubleSetting -> new DoubleSettingRow(doubleSetting);
            case ColorSetting colorSetting -> new ColorSettingRow(colorSetting);
            case BlockListSetting blockListSetting -> new BlockListSettingRow(blockListSetting);
            case StringSetting stringSetting -> new StringSettingRow(stringSetting);
            case ButtonSetting buttonSetting -> new ButtonSettingRow(buttonSetting);
            // --- List settings (Row + Widget) ---
            case StringListSetting stringListSetting -> new StringListSettingRow(stringListSetting);
            case SoundEventListSetting soundEventListSetting -> new SoundEventListSettingRow(soundEventListSetting);
            case ItemListSetting itemListSetting -> new ItemListSettingRow(itemListSetting);

            case EntityTypeListSetting entityTypeListSetting -> new EntityTypeListSettingRow(entityTypeListSetting);
            case StatusEffectListSetting statusEffectListSetting -> new StatusEffectListSettingRow(statusEffectListSetting);
            case ParticleTypeListSetting particleTypeListSetting -> new ParticleTypeListSettingRow(particleTypeListSetting);
            case ScreenHandlerListSetting screenHandlerListSetting -> new ScreenHandlerListSettingRow(screenHandlerListSetting);
            case StorageBlockListSetting storageBlockListSetting -> new StorageBlockListSettingRow(storageBlockListSetting);
            case EnchantmentListSetting enchantmentListSetting -> new EnchantmentListSettingRow(enchantmentListSetting);
            case PacketListSetting packetListSetting -> new PacketListSettingRow(packetListSetting);
            case ModuleListSetting moduleListSetting -> new ModuleListSettingRow(moduleListSetting);
            // --- Simple settings (Row only) ---
            case BlockSetting blockSetting -> new BlockSettingRow(blockSetting);
            case ItemSetting itemSetting -> new ItemSettingRow(itemSetting);
            case BlockPosSetting blockPosSetting -> new BlockPosSettingRow(blockPosSetting);
            case Vector3dSetting vector3dSetting -> new Vector3dSettingRow(vector3dSetting);
            case StatusEffectAmplifierMapSetting statusEffectAmplifierMapSetting -> new StatusEffectAmplifierMapSettingRow(statusEffectAmplifierMapSetting);
            case null, default -> null;
        };
    }

}
