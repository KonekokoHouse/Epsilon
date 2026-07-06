package com.github.epsilon.utils.combat;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;

public final class WeaponUtils {

    private WeaponUtils() {
    }

    public static boolean isToolLike(ItemStack stack) {
        return stack.is(ItemTags.SWORDS)
                || stack.is(ItemTags.AXES)
                || stack.is(ItemTags.PICKAXES)
                || stack.is(ItemTags.SHOVELS)
                || stack.is(ItemTags.HOES);
    }
}
