package com.github.epsilon.modules.impl.player;

import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.SettingGroup;
import com.github.epsilon.settings.impl.*;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Items;
import org.joml.Vector3d;

import java.awt.*;
import java.util.List;
import java.util.Set;

public class SettingsTest extends Module {

    public static final SettingsTest INSTANCE = new SettingsTest();

    private SettingsTest() {
        super("Settings Test", Category.PLAYER);
    }

    // ==================== Groups ====================
    private final SettingGroup sgBasic = settingGroup("Basic");
    private final SettingGroup sgLists = settingGroup("Lists");
    private final SettingGroup sgRegistry = settingGroup("Registry Lists");
    private final SettingGroup sgAdvanced = settingGroup("Advanced");

    // ==================== Basic ====================
    private final BoolSetting myBool = boolSetting("my-bool", true).group(sgBasic);
    private final IntSetting myInt = intSetting("my-int", 42, 0, 100, 1).group(sgBasic);
    private final DoubleSetting myDouble = doubleSetting("my-double", 3.14, -10.0, 10.0, 0.1).group(sgBasic);
    private final StringSetting myString = stringSetting("my-string", "hello").group(sgBasic);
    private final ColorSetting myColor = colorSetting("my-color", Color.RED, true).group(sgBasic);
    private final KeybindSetting myKeybind = keybindSetting("my-keybind", -1).group(sgBasic);
    private final ButtonSetting myButton = buttonSetting("my-button", () -> {}).group(sgBasic);

    // ==================== Lists ====================
    private final StringListSetting myStrings = stringListSetting("my-strings", List.of("foo", "bar")).group(sgLists);
    private final BlockListSetting myBlocks = blockListSetting("my-blocks", List.of(Blocks.STONE, Blocks.DIRT)).group(sgLists);
    private final SoundEventListSetting mySounds = soundEventListSetting("my-sounds", List.of(SoundEvents.BELL_BLOCK)).group(sgLists);
    private final ColorListSetting myColors = colorListSetting("my-colors", List.of(Color.RED, Color.BLUE)).group(sgLists);
    private final ItemListSetting myItems = itemListSetting("my-items", List.of(Items.DIAMOND, Items.IRON_INGOT)).group(sgLists);
    private final EnchantmentListSetting myEnchants = enchantmentListSetting("my-enchants", Set.of("minecraft:sharpness")).group(sgLists);

    // ==================== Registry Lists ====================
    private final EntityTypeListSetting myEntities = entityTypeListSetting("my-entities", Set.of()).group(sgRegistry);
    private final StatusEffectListSetting myEffects = statusEffectListSetting("my-effects", List.of(MobEffects.SPEED.value())).group(sgRegistry);
    private final ParticleTypeListSetting myParticles = particleTypeListSetting("my-particles", List.of()).group(sgRegistry);
    private final ScreenHandlerListSetting myScreens = screenHandlerListSetting("my-screens", List.of()).group(sgRegistry);
    private final StorageBlockListSetting myStorages = storageBlockListSetting("my-storages", List.of()).group(sgRegistry);
    private final PacketListSetting myPackets = packetListSetting("my-packets", Set.of()).group(sgRegistry);
    private final ModuleListSetting myModules = moduleListSetting("my-modules", List.of()).group(sgRegistry);

    // ==================== Advanced ====================
    private final BlockSetting myBlock = blockSetting("my-block", Blocks.STONE).group(sgAdvanced);
    private final ItemSetting myItem = itemSetting("my-item", Items.DIAMOND).group(sgAdvanced);
    private final BlockPosSetting myPos = blockPosSetting("my-pos", new BlockPos(0, 64, 0)).group(sgAdvanced);
    private final Vector3dSetting myVec = vector3dSetting("my-vec", new Vector3d(1, 2, 3)).group(sgAdvanced);
    private final StatusEffectAmplifierMapSetting myEffectMap = statusEffectAmplifierMapSetting("my-effect-map", java.util.Map.of()).group(sgAdvanced);

}
