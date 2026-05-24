package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.TickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.utils.player.InvHelper;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public class AutoTotem extends Module {

    public static final AutoTotem INSTANCE = new AutoTotem();

    private final BoolSetting strict = boolSetting("Strict", true);
    private final DoubleSetting health = doubleSetting("Health", 16.0, 0.0, 36.0, 0.5);
    private final BoolSetting checkGapple = boolSetting("Check Gapple", true);

    private AutoTotem() {
        super("Auto Totem", Category.COMBAT);
    }

    @Override
    public String hudInfo() {
        if (nullCheck()) return null;
        return String.valueOf(InvHelper.getItemCount(Items.TOTEM_OF_UNDYING));
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (nullCheck() || mc.gameMode == null) return;

        if (!shouldHoldTotem()) {
            return;
        }

        if (mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            return;
        }

        int slot = InvHelper.getItemSlot(Items.TOTEM_OF_UNDYING);
        if (slot == -1) {
            return;
        }

        moveItemToOffhand(slot);
    }

    private boolean shouldHoldTotem() {
        float totalHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount();

        if (totalHealth <= health.getValue().floatValue()) {
            return true;
        }

        // Keep an empty offhand safe by filling it with a totem.
        if (mc.player.getOffhandItem().isEmpty()) {
            return true;
        }

        // Simple void safety check for modern overworld min Y.
        if (mc.player.getY() < -64.0) {
            return true;
        }

        if (checkGapple.getValue()) {
            Item mainHandItem = mc.player.getMainHandItem().getItem();
            if (mainHandItem == Items.GOLDEN_APPLE || mainHandItem == Items.ENCHANTED_GOLDEN_APPLE) {
                return true;
            }
        }

        return false;
    }

    private void moveItemToOffhand(int slot) {
        if (slot < 9) {
            slot += 36;
        }

        if (!strict.getValue()) {
            mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, slot, 40, ContainerInput.SWAP, mc.player);
            return;
        }

        mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, slot, 0, ContainerInput.PICKUP, mc.player);
        mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, 45, 0, ContainerInput.PICKUP, mc.player);

        if (!mc.player.inventoryMenu.getCarried().isEmpty()) {
            mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, slot, 0, ContainerInput.PICKUP, mc.player);
        }
    }

}
