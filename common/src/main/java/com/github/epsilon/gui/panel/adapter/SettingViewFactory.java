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
            case RegistryListSetting<?> r when r.getRegistryType() == RegistryListSetting.Type.BLOCK -> new BlockListSettingRow(r);
            case StringSetting stringSetting -> new StringSettingRow(stringSetting);
            case ButtonSetting buttonSetting -> new ButtonSettingRow(buttonSetting);
            // --- List settings (Row + Widget) ---
            case RegistryListSetting<?> r when r.getRegistryType() == RegistryListSetting.Type.STRING_LIST -> new StringListSettingRow((StringListSetting) r);
            case RegistryListSetting<?> r when r.getRegistryType() == RegistryListSetting.Type.SOUND_EVENT -> new SoundEventListSettingRow(r);
            case RegistryListSetting<?> r when r.getRegistryType() == RegistryListSetting.Type.ITEM -> new ItemListSettingRow(r);

            case EntityTypeListSetting entityTypeListSetting -> new EntityTypeListSettingRow(entityTypeListSetting);
            case RegistryListSetting<?> r when r.getRegistryType() == RegistryListSetting.Type.MOB_EFFECT -> new StatusEffectListSettingRow(r);
            case RegistryListSetting<?> r when r.getRegistryType() == RegistryListSetting.Type.ENCHANTMENT -> new EnchantmentListSettingRow((EnchantmentListSetting) r);
            case RegistryListSetting<?> r when r.getRegistryType() == RegistryListSetting.Type.PACKET -> new PacketListSettingRow((PacketListSetting) r);
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
