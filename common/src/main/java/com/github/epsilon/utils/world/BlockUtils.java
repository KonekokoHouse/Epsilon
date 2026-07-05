package com.github.epsilon.utils.world;

import com.github.epsilon.modules.impl.combat.FeetTrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle;
import net.minecraft.world.phys.AABB;

import static com.github.epsilon.Constants.mc;

public class BlockUtils {

    public static boolean canPlaceAt(BlockPos blockPos, boolean ignoreCrystals) {
        if (FeetTrap.INSTANCE.getPlaceSide(blockPos) == null) return false;
        if (!mc.level.getBlockState(blockPos).canBeReplaced()) return false;
        return noEntity(blockPos, ignoreCrystals);
    }

    public static boolean noEntity(BlockPos blockPos, boolean ignoreCrystals) {
        return mc.level.getEntities((Entity) null, new AABB(blockPos), entity -> !(entity instanceof ItemEntity || entity instanceof ExperienceOrb || entity instanceof ThrownExperienceBottle || entity instanceof Arrow || ignoreCrystals && entity instanceof EndCrystal)).isEmpty();
    }

    public static boolean isSolidBlock(BlockPos pos) {
        return mc.level.getBlockState(pos).isSolidRender();
    }

}
