package com.github.epsilon.utils.world;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import java.util.Set;

public final class EntityTypeCategories {

    private static final Set<EntityType<?>> NEUTRAL = Set.of(
            EntityType.BEE,
            EntityType.IRON_GOLEM,
            EntityType.NAUTILUS,
            EntityType.POLAR_BEAR,
            EntityType.WOLF,
            EntityType.ENDERMAN,
            EntityType.ZOMBIE_NAUTILUS,
            EntityType.ZOMBIFIED_PIGLIN
    );

    private static final Set<EntityType<?>> FRIENDLY_OVERRIDES = Set.of(
            EntityType.CAMEL_HUSK,
            EntityType.COPPER_GOLEM,
            EntityType.SNOW_GOLEM,
            EntityType.VILLAGER,
            EntityType.ZOMBIE_HORSE
    );

    private static final Set<EntityType<?>> RIDEABLE = Set.of(
            EntityType.HORSE, EntityType.DONKEY, EntityType.MULE,
            EntityType.SKELETON_HORSE, EntityType.ZOMBIE_HORSE,
            EntityType.PIG, EntityType.STRIDER,
            EntityType.CAMEL, EntityType.CAMEL_HUSK, EntityType.LLAMA, EntityType.TRADER_LLAMA,
            EntityType.HAPPY_GHAST, EntityType.NAUTILUS, EntityType.ZOMBIE_NAUTILUS,
            EntityType.OAK_BOAT, EntityType.SPRUCE_BOAT, EntityType.BIRCH_BOAT,
            EntityType.JUNGLE_BOAT, EntityType.ACACIA_BOAT, EntityType.DARK_OAK_BOAT,
            EntityType.CHERRY_BOAT, EntityType.MANGROVE_BOAT, EntityType.PALE_OAK_BOAT,
            EntityType.BAMBOO_RAFT,
            EntityType.OAK_CHEST_BOAT, EntityType.SPRUCE_CHEST_BOAT, EntityType.BIRCH_CHEST_BOAT,
            EntityType.JUNGLE_CHEST_BOAT, EntityType.ACACIA_CHEST_BOAT, EntityType.DARK_OAK_CHEST_BOAT,
            EntityType.CHERRY_CHEST_BOAT, EntityType.MANGROVE_CHEST_BOAT, EntityType.PALE_OAK_CHEST_BOAT,
            EntityType.BAMBOO_CHEST_RAFT
    );

    private EntityTypeCategories() {
    }

    public static boolean isFriendly(EntityType<?> entityType) {
        if (isNeutral(entityType)) return false;
        MobCategory category = entityType.getCategory();
        return FRIENDLY_OVERRIDES.contains(entityType)
                || category.isFriendly() && category != MobCategory.MISC;
    }

    public static boolean isNeutral(EntityType<?> entityType) {
        return NEUTRAL.contains(entityType);
    }

    public static boolean isHostile(EntityType<?> entityType) {
        return !entityType.getCategory().isFriendly() && !isNeutral(entityType);
    }

    public static boolean isRideable(EntityType<?> entityType) {
        return RIDEABLE.contains(entityType);
    }

    public static boolean isMisc(EntityType<?> entityType) {
        return !isFriendly(entityType) && !isNeutral(entityType) && !isHostile(entityType);
    }

    public static Set<EntityType<?>> rideable() {
        return RIDEABLE;
    }
}
