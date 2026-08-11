package com.github.epsilon.mixins;

import com.github.epsilon.interfaces.AbstractContainerScreenAccessor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(AbstractContainerScreen.class)
public class MixinAbstractContainerScreen implements AbstractContainerScreenAccessor {

    @Shadow
    protected Slot hoveredSlot;

    @Override
    public Slot epsilon$getHoveredSlot() {
        return hoveredSlot;
    }
}
