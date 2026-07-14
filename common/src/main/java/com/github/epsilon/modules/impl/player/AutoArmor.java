package com.github.epsilon.modules.impl.player;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.bus.EventPriority;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.modules.impl.movement.elytrafly.ElytraFly;
import com.github.epsilon.settings.SettingGroup;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.player.InvHelper;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.equipment.Equippable;

public class AutoArmor extends Module {

    public static final AutoArmor INSTANCE = new AutoArmor();

    public enum ChestSwapMode {
        PriorManualElytraSwap,
        Automatic
    }

    public enum ChestMode {
        ChestPrior,
        ElytraPrior
    }

    private enum ElytraPriority {
        HighestQuality,
        LowestQuality
    }

    private enum ProtectionPriority {
        Protection,
        BlastProtection,
        FireProtection,
        ProjectileProtection
    }

    private final SettingGroup sgGeneral = settingGroup("General");
    private final SettingGroup sgArmor = settingGroup("Armor");
    private final SettingGroup sgElytra = settingGroup("Elytra");

    private final EnumSetting<ChestSwapMode> chestSwapMode = enumSetting("Chest Swap Mode", ChestSwapMode.Automatic).group(sgGeneral);
    private final EnumSetting<ChestMode> chestMode = enumSetting("Chest Mode", ChestMode.ChestPrior).group(sgGeneral);
    private final IntSetting delay = intSetting("Delay", 2, 0, 20, 1).group(sgGeneral);
    private final BoolSetting inventoryOnly = boolSetting("Inventory Only", true).group(sgGeneral);

    private final BoolSetting helmet = boolSetting("Helmet", true).group(sgArmor);
    private final BoolSetting chest = boolSetting("Chest", true).group(sgArmor);
    private final BoolSetting leggings = boolSetting("Leggings", true).group(sgArmor);
    private final BoolSetting boots = boolSetting("Boots", true).group(sgArmor);
    private final EnumSetting<ProtectionPriority> protectionPriority = enumSetting("Protection Priority", ProtectionPriority.Protection).group(sgArmor);

    private final EnumSetting<ElytraPriority> elytraPriority = enumSetting("Elytra Priority", ElytraPriority.HighestQuality).group(sgElytra);
    private final IntSetting minimumDurability = intSetting("Minimum Durability", 2, 1, 432, 1).group(sgElytra);
    private final BoolSetting autoReplaceElytra = boolSetting("Auto Replace Elytra", true).group(sgElytra);
    private final IntSetting replaceDurabilityThreshold = intSetting("Replace Durability Threshold", 32, 1, 432, 1).group(sgElytra);
    private final BoolSetting preferMending = boolSetting("Prefer Mending", true).group(sgElytra);
    private final BoolSetting preferUnbreaking = boolSetting("Prefer Unbreaking", true).group(sgElytra);

    private int cooldown;

    private AutoArmor() {
        super("Auto Armor", Category.PLAYER);
    }

    public ChestSwapMode getChestSwapMode() {
        return chestSwapMode.getValue();
    }

    public void setChestSwapMode(ChestSwapMode chestSwapMode) {
        if (chestSwapMode != null) this.chestSwapMode.setValue(chestSwapMode);
    }

    public ChestMode getChestMode() {
        return chestMode.getValue();
    }

    public void setChestMode(ChestMode chestMode) {
        if (chestMode != null) this.chestMode.setValue(chestMode);
    }

    @Override
    protected void onEnable() {
        cooldown = 0;
    }

    @Override
    protected void onDisable() {
        cooldown = 0;
    }

    @EventHandler(priority = EventPriority.LOW)
    private void onTick(PlayerTickEvent.Pre event) {
        if (nullCheck() || InvHelper.shouldDisableFeatures() || Stealer.INSTANCE.isWorking()) return;
        if (!isInventoryAvailable()) return;
        if (cooldown > 0) {
            cooldown--;
            return;
        }

        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (EquipmentSlot slot : slots) {
            if (!isSlotEnabled(slot)) continue;
            if (slot == EquipmentSlot.CHEST
                    && (ElytraSwap.INSTANCE.isBusy() || ElytraFly.INSTANCE.isManagingChestSlot())) continue;

            ItemStack current = mc.player.getItemBySlot(slot);
            int candidateSlot = findCandidate(slot, current);
            if (candidateSlot == -1) continue;

            ItemStack candidate = mc.player.getInventory().getItem(candidateSlot);
            if (!shouldEquip(slot, current, candidate)) continue;

            swapIntoArmorSlot(candidateSlot, slot);
            cooldown = delay.getValue();
            return;
        }
    }

    private boolean isInventoryAvailable() {
        if (inventoryOnly.getValue() && !(mc.screen instanceof InventoryScreen)) return false;
        if (mc.screen instanceof AbstractContainerScreen<?> screen
                && screen.getMenu().containerId != mc.player.inventoryMenu.containerId) return false;
        return true;
    }

    private boolean isSlotEnabled(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> helmet.getValue();
            case CHEST -> chest.getValue();
            case LEGS -> leggings.getValue();
            case FEET -> boots.getValue();
            default -> false;
        };
    }

    private int findCandidate(EquipmentSlot slot, ItemStack current) {
        if (slot == EquipmentSlot.CHEST) return findChestCandidate(current);
        return findBestArmor(slot);
    }

    private int findChestCandidate(ItemStack current) {
        int chestplateSlot = findBestArmor(EquipmentSlot.CHEST);
        int elytraSlot = findBestElytra();
        ElytraSwap.SwapTarget manualTarget = getManualTarget();

        if (manualTarget != null) {
            return manualTarget == ElytraSwap.SwapTarget.Chestplate ? chestplateSlot : elytraSlot;
        }

        return switch (chestMode.getValue()) {
            case ChestPrior -> (isChestplate(current) || chestplateSlot != -1) ? chestplateSlot : elytraSlot;
            case ElytraPrior -> (isElytraCandidate(current) || elytraSlot != -1) ? elytraSlot : chestplateSlot;
        };
    }

    private ElytraSwap.SwapTarget getManualTarget() {
        if (!chestSwapMode.is(ChestSwapMode.PriorManualElytraSwap) || !ElytraSwap.INSTANCE.isEnabled()) return null;
        return ElytraSwap.INSTANCE.getLastSwapTarget();
    }

    private int findBestArmor(EquipmentSlot slot) {

        int bestSlot = -1;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int inventorySlot = 0; inventorySlot < mc.player.getInventory().getNonEquipmentItems().size(); inventorySlot++) {
            ItemStack stack = mc.player.getInventory().getItem(inventorySlot);
            if (!isArmorCandidate(stack, slot)) continue;

            double score = armorScore(stack, slot);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = inventorySlot;
            }
        }
        return bestSlot;
    }

    private int findBestElytra() {
        int selectedSlot = -1;
        long selectedScore = elytraPriority.is(ElytraPriority.HighestQuality) ? Long.MIN_VALUE : Long.MAX_VALUE;

        for (int inventorySlot = 0; inventorySlot < mc.player.getInventory().getNonEquipmentItems().size(); inventorySlot++) {
            ItemStack stack = mc.player.getInventory().getItem(inventorySlot);
            if (!isElytraCandidate(stack)) continue;

            long score = elytraScore(stack);
            boolean better = elytraPriority.is(ElytraPriority.HighestQuality) ? score > selectedScore : score < selectedScore;
            if (selectedSlot == -1 || better) {
                selectedSlot = inventorySlot;
                selectedScore = score;
            }
        }
        return selectedSlot;
    }

    private boolean isArmorCandidate(ItemStack stack, EquipmentSlot slot) {
        if (stack.isEmpty() || hasBindingCurse(stack)) return false;
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable != null && equippable.slot() == slot
                && (slot != EquipmentSlot.CHEST || !isElytra(stack));
    }

    private boolean isElytraCandidate(ItemStack stack) {
        if (stack.isEmpty() || !isElytra(stack) || hasBindingCurse(stack)
                || remainingDurability(stack) < minimumDurability.getValue()) return false;
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable != null && equippable.slot() == EquipmentSlot.CHEST;
    }

    private boolean shouldEquip(EquipmentSlot slot, ItemStack current, ItemStack candidate) {
        if (hasBindingCurse(current)) return false;

        if (slot == EquipmentSlot.CHEST && isElytra(candidate)) {
            if (!isElytra(current)) return true;
            if (!autoReplaceElytra.getValue()) return false;
            if (remainingDurability(current) > replaceDurabilityThreshold.getValue()) return false;
            return remainingDurability(current) < remainingDurability(candidate);
        }

        if (current.isEmpty()) return true;
        if (slot == EquipmentSlot.CHEST && isElytra(current)) return true;
        return armorScore(candidate, slot) > armorScore(current, slot);
    }

    private void swapIntoArmorSlot(int inventorySlot, EquipmentSlot equipmentSlot) {
        int sourceContainerSlot = toContainerSlot(inventorySlot);
        int armorContainerSlot = armorContainerSlot(equipmentSlot);
        AbstractContainerMenu menu = mc.player.inventoryMenu;

        mc.gameMode.handleContainerInput(menu.containerId, sourceContainerSlot, 0, ContainerInput.PICKUP, mc.player);
        mc.gameMode.handleContainerInput(menu.containerId, armorContainerSlot, 0, ContainerInput.PICKUP, mc.player);
        mc.gameMode.handleContainerInput(menu.containerId, sourceContainerSlot, 0, ContainerInput.PICKUP, mc.player);
    }

    private int toContainerSlot(int inventorySlot) {
        return inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
    }

    private int armorContainerSlot(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> 5;
            case CHEST -> 6;
            case LEGS -> 7;
            case FEET -> 8;
            default -> throw new IllegalArgumentException("Unsupported armor slot: " + slot);
        };
    }

    private double armorScore(ItemStack stack, EquipmentSlot slot) {
        final double[] score = {0.0};
        stack.forEachModifier(slot, (attribute, modifier) -> {
            if (attribute.equals(Attributes.ARMOR)) score[0] += modifier.amount() * 100.0;
            if (attribute.equals(Attributes.ARMOR_TOUGHNESS)) score[0] += modifier.amount() * 10.0;
        });

        score[0] += getEnchantmentLevel(stack, Enchantments.PROTECTION);
        score[0] += getEnchantmentLevel(stack, Enchantments.BLAST_PROTECTION);
        score[0] += getEnchantmentLevel(stack, Enchantments.FIRE_PROTECTION);
        score[0] += getEnchantmentLevel(stack, Enchantments.PROJECTILE_PROTECTION);
        score[0] += getEnchantmentLevel(stack, protectionKey()) * 2.0;
        score[0] += getEnchantmentLevel(stack, Enchantments.UNBREAKING) * 0.5;
        score[0] += getEnchantmentLevel(stack, Enchantments.MENDING);
        score[0] += remainingDurability(stack) * 0.001;
        return score[0];
    }

    private ResourceKey<Enchantment> protectionKey() {
        return switch (protectionPriority.getValue()) {
            case Protection -> Enchantments.PROTECTION;
            case BlastProtection -> Enchantments.BLAST_PROTECTION;
            case FireProtection -> Enchantments.FIRE_PROTECTION;
            case ProjectileProtection -> Enchantments.PROJECTILE_PROTECTION;
        };
    }

    private long elytraScore(ItemStack stack) {
        long score = remainingDurability(stack);
        if (preferMending.getValue() && getEnchantmentLevel(stack, Enchantments.MENDING) > 0) score += 500;
        if (preferUnbreaking.getValue() && getEnchantmentLevel(stack, Enchantments.UNBREAKING) > 0) score += 200;
        return score;
    }

    private int remainingDurability(ItemStack stack) {
        return stack.isDamageableItem() ? stack.getMaxDamage() - stack.getDamageValue() : Integer.MAX_VALUE;
    }

    private boolean isElytra(ItemStack stack) {
        return stack.has(DataComponents.GLIDER);
    }

    private boolean isChestplate(ItemStack stack) {
        if (stack.isEmpty() || isElytra(stack)) return false;
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable != null && equippable.slot() == EquipmentSlot.CHEST;
    }

    private boolean hasBindingCurse(ItemStack stack) {
        return getEnchantmentLevel(stack, Enchantments.BINDING_CURSE) > 0;
    }

    private int getEnchantmentLevel(ItemStack stack, ResourceKey<Enchantment> key) {
        for (var entry : stack.getOrDefault(DataComponents.ENCHANTMENTS, net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY).entrySet()) {
            if (entry.getKey().is(key)) return entry.getIntValue();
        }
        return 0;
    }
}
