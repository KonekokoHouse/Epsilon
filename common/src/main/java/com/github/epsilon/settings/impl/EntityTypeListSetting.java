package com.github.epsilon.settings.impl;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import java.util.*;
import java.util.function.Predicate;

public class EntityTypeListSetting extends RegistryListSetting<EntityType<?>> {

    private static final List<String> GROUPS = List.of("animal", "wateranimal", "monster", "ambient", "misc");

    // ---- 实体分类（动态构建，覆盖全部注册表实体） ----

    /** 手动指定的友善实体 ID（作为分类基础） */
    private static final Set<String> FRIENDLY_BASE = Set.of(
            "minecraft:allay", "minecraft:armadillo", "minecraft:axolotl", "minecraft:bat",
            "minecraft:bee", "minecraft:camel", "minecraft:cat", "minecraft:chicken",
            "minecraft:cod", "minecraft:cow", "minecraft:dolphin", "minecraft:donkey",
            "minecraft:fox", "minecraft:frog", "minecraft:glow_squid", "minecraft:goat",
            "minecraft:horse", "minecraft:iron_golem", "minecraft:llama", "minecraft:mooshroom",
            "minecraft:mule", "minecraft:ocelot", "minecraft:panda", "minecraft:parrot",
            "minecraft:pig", "minecraft:polar_bear", "minecraft:rabbit", "minecraft:salmon",
            "minecraft:sheep", "minecraft:skeleton_horse", "minecraft:sniffer",
            "minecraft:snow_golem", "minecraft:squid", "minecraft:strider", "minecraft:tadpole",
            "minecraft:trader_llama", "minecraft:tropical_fish", "minecraft:turtle",
            "minecraft:villager", "minecraft:wandering_trader", "minecraft:wolf",
            "minecraft:zombie_horse",
            "minecraft:copper_golem", "minecraft:happy_ghast", "minecraft:nautilus"
    );

    /** 手动指定的敌对实体 ID */
    private static final Set<String> HOSTILE_BASE = Set.of(
            "minecraft:blaze", "minecraft:bogged", "minecraft:breeze", "minecraft:cave_spider",
            "minecraft:creaking", "minecraft:creeper", "minecraft:drowned",
            "minecraft:elder_guardian", "minecraft:enderman", "minecraft:endermite",
            "minecraft:evoker", "minecraft:ghast", "minecraft:guardian", "minecraft:hoglin",
            "minecraft:husk", "minecraft:magma_cube", "minecraft:phantom", "minecraft:piglin",
            "minecraft:piglin_brute", "minecraft:pillager", "minecraft:ravager",
            "minecraft:shulker", "minecraft:silverfish", "minecraft:skeleton", "minecraft:slime",
            "minecraft:spider", "minecraft:stray", "minecraft:vex", "minecraft:vindicator",
            "minecraft:warden", "minecraft:witch", "minecraft:wither", "minecraft:wither_skeleton",
            "minecraft:zoglin", "minecraft:zombie", "minecraft:zombie_villager",
            "minecraft:zombified_piglin",
            "minecraft:camel_husk", "minecraft:giant", "minecraft:illusioner",
            "minecraft:zombie_nautilus"
    );

    /** 手动指定的可骑乘实体 ID */
    private static final Set<String> RIDEABLE_BASE = Set.of(
            "minecraft:camel", "minecraft:camel_husk", "minecraft:donkey",
            "minecraft:horse", "minecraft:llama", "minecraft:mule",
            "minecraft:nautilus", "minecraft:pig", "minecraft:skeleton_horse",
            "minecraft:strider", "minecraft:trader_llama", "minecraft:zombie_horse",
            "minecraft:zombie_nautilus",
            "minecraft:oak_boat", "minecraft:spruce_boat", "minecraft:birch_boat",
            "minecraft:jungle_boat", "minecraft:acacia_boat", "minecraft:cherry_boat",
            "minecraft:dark_oak_boat", "minecraft:mangrove_boat", "minecraft:pale_oak_boat",
            "minecraft:bamboo_raft",
            "minecraft:oak_chest_boat", "minecraft:spruce_chest_boat",
            "minecraft:birch_chest_boat", "minecraft:jungle_chest_boat",
            "minecraft:acacia_chest_boat", "minecraft:cherry_chest_boat",
            "minecraft:dark_oak_chest_boat", "minecraft:mangrove_chest_boat",
            "minecraft:pale_oak_chest_boat", "minecraft:bamboo_chest_raft",
            "minecraft:minecart", "minecraft:chest_minecart", "minecraft:furnace_minecart",
            "minecraft:hopper_minecart"
    );

    private static final Set<String> TECHNICAL_BASE = Set.of(
            "minecraft:area_effect_cloud", "minecraft:armor_stand",
            "minecraft:block_display", "minecraft:end_crystal",
            "minecraft:experience_bottle", "minecraft:experience_orb",
            "minecraft:eye_of_ender", "minecraft:falling_block",
            "minecraft:fishing_bobber", "minecraft:glow_item_frame",
            "minecraft:interaction", "minecraft:item", "minecraft:item_display",
            "minecraft:item_frame", "minecraft:leash_knot", "minecraft:lightning_bolt",
            "minecraft:marker", "minecraft:ominous_item_spawner",
            "minecraft:painting", "minecraft:text_display"
    );

    public static final Set<String> FRIENDLY_IDS;
    public static final Set<String> HOSTILE_IDS;
    public static final Set<String> NEUTRAL_IDS;
    public static final Set<String> RIDEABLE_IDS;
    public static final Set<String> TECHNICAL_IDS;

    static {
        Set<String> allIds = new LinkedHashSet<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (id != null) allIds.add(id.toString());
        }
        Set<String> friendly = new LinkedHashSet<>(FRIENDLY_BASE);
        for (String id : allIds) {
            if (!friendly.contains(id) && !HOSTILE_BASE.contains(id)
                    && !RIDEABLE_BASE.contains(id) && !TECHNICAL_BASE.contains(id)) {
                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.tryParse(id)).orElse(null);
                if (type != null && type.getCategory() == MobCategory.CREATURE) friendly.add(id);
            }
        }
        Set<String> hostile = new LinkedHashSet<>(HOSTILE_BASE);
        for (String id : allIds) {
            if (!friendly.contains(id) && !hostile.contains(id)
                    && !RIDEABLE_BASE.contains(id) && !TECHNICAL_BASE.contains(id)) {
                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.tryParse(id)).orElse(null);
                if (type != null && type.getCategory() == MobCategory.MONSTER) hostile.add(id);
            }
        }
        Set<String> rideable = new LinkedHashSet<>(RIDEABLE_BASE);
        Set<String> technical = new LinkedHashSet<>(TECHNICAL_BASE);
        for (String id : allIds) {
            if (!friendly.contains(id) && !hostile.contains(id) && !rideable.contains(id)) {
                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.tryParse(id)).orElse(null);
                if (type != null && type.getCategory() == MobCategory.MISC) technical.add(id);
            }
        }
        for (String id : allIds) {
            if (!friendly.contains(id) && !hostile.contains(id) && !rideable.contains(id) && !technical.contains(id))
                technical.add(id);
        }
        Set<String> neutral = new LinkedHashSet<>();
        for (String id : allIds) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.tryParse(id)).orElse(null);
            if (type != null && type.getCategory() == MobCategory.AMBIENT) neutral.add(id);
        }
        neutral.addAll(Set.of("minecraft:bee", "minecraft:dolphin", "minecraft:enderman",
                "minecraft:goat", "minecraft:iron_golem", "minecraft:llama",
                "minecraft:panda", "minecraft:polar_bear", "minecraft:spider",
                "minecraft:cave_spider", "minecraft:trader_llama", "minecraft:wolf",
                "minecraft:zombified_piglin", "minecraft:piglin"));
        FRIENDLY_IDS = Collections.unmodifiableSet(friendly);
        HOSTILE_IDS = Collections.unmodifiableSet(hostile);
        NEUTRAL_IDS = Collections.unmodifiableSet(neutral);
        RIDEABLE_IDS = Collections.unmodifiableSet(rideable);
        TECHNICAL_IDS = Collections.unmodifiableSet(technical);
    }

    public EntityTypeListSetting(String name, Collection<EntityType<?>> defaultValue,
                                  Predicate<EntityType<?>> filter, Dependency dependency) {
        super(name, defaultValue, Type.ENTITY_TYPE, filter, dependency);
    }

    /** 支持分组名解析的 setIds（如 "animal", "monster" 等） */
    @Override
    public void setIds(Collection<String> ids) {
        List<EntityType<?>> types = new ArrayList<>();
        if (ids != null) {
            for (String id : ids) {
                String lowerValue = id.trim().toLowerCase();
                if (GROUPS.contains(lowerValue)) {
                    types.addAll(resolveGroup(lowerValue));
                } else {
                    Identifier loc = Identifier.tryParse(id);
                    if (loc == null) continue;
                    EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(loc).orElse(null);
                    if (type != null) { types.add(type); }
                }
            }
        }
        setValue(types);
    }

    public void addGroup(String groupName) {
        List<EntityType<?>> next = new ArrayList<>(getValue());
        next.addAll(resolveGroup(groupName.trim().toLowerCase()));
        setValue(next);
    }

    private List<EntityType<?>> resolveGroup(String group) {
        Set<String> targetIds = switch (group) {
            case "friendly", "animal" -> FRIENDLY_IDS;
            case "hostile", "monster" -> HOSTILE_IDS;
            case "neutral", "ambient" -> NEUTRAL_IDS;
            case "rideable" -> RIDEABLE_IDS;
            case "technical", "misc" -> TECHNICAL_IDS;
            default -> Set.of();
        };
        List<EntityType<?>> result = new ArrayList<>();
        for (String id : targetIds) {
            Identifier loc = Identifier.tryParse(id);
            if (loc == null) continue;
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(loc).orElse(null);
            if (type != null && !result.contains(type)) result.add(type);
        }
        return result;
    }
}
