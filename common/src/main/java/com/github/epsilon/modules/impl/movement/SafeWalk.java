package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import net.minecraft.world.item.BlockItem;

public class SafeWalk extends Module {

    public static final SafeWalk INSTANCE = new SafeWalk();

    private SafeWalk() {
        super("Safe Walk", Category.MOVEMENT);
    }

    private final BoolSetting onlyHoldingBlock = boolSetting("Only Holding Block", false);
    private final BoolSetting onlyBack = boolSetting("Only Backward", true);

    public boolean shouldSafeWalk() {
        boolean holdingBlock = mc.player.getInventory().getSelectedItem().getItem() instanceof BlockItem || !onlyHoldingBlock.getValue();
        boolean back = mc.options.keyDown.isDown() || !onlyBack.getValue();
        return isEnabled() && holdingBlock && back;
    }

}
