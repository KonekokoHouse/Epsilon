package com.github.epsilon.utils.player;

import net.minecraft.world.inventory.ContainerInput;

import static com.github.epsilon.Constants.mc;

public class ContainerInputUtils {

    public static void handleContainerInput(int slot, int button, ContainerInput action) {
        mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, slot, button, action, mc.player);
    }

    public static void click(int slot) {
        handleContainerInput(slot, 0, ContainerInput.PICKUP);
    }

    public static void shiftClick(int slot) {
        handleContainerInput(slot, 0, ContainerInput.QUICK_MOVE);
    }

    public static void drop(int slot) {
        handleContainerInput(slot, 0, ContainerInput.THROW);
    }

    public static void dropAll(int slot) {
        handleContainerInput(slot, 1, ContainerInput.THROW);
    }

    public static void swap(int slot, int hotbarSlot) {
        handleContainerInput(slot, hotbarSlot, ContainerInput.SWAP);
    }

}
