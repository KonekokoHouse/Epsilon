package com.github.epsilon.settings.impl;

import com.github.epsilon.settings.Setting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

import java.util.*;
import java.util.function.Predicate;

public class EntityTypeListSetting extends Setting<List<EntityType<?>>> {

    private static final List<String> GROUPS = List.of("animal", "wateranimal", "monster", "ambient", "misc");

    // ---- 实体分类（基于 entity ID，不依赖 MC 原版 MobCategory） ----

    /** 友善生物 */
    public static final Set<String> FRIENDLY_IDS = Set.of(
            "minecraft:bat", "minecraft:cat", "minecraft:chicken", "minecraft:cod",
            "minecraft:cow", "minecraft:donkey", "minecraft:fox", "minecraft:frog",
            "minecraft:glow_squid", "minecraft:horse", "minecraft:mooshroom",
            "minecraft:mule", "minecraft:ocelot", "minecraft:parrot", "minecraft:pig",
            "minecraft:rabbit", "minecraft:salmon", "minecraft:sheep", "minecraft:squid",
            "minecraft:strider", "minecraft:tadpole", "minecraft:tropical_fish",
            "minecraft:turtle", "minecraft:villager", "minecraft:wandering_trader",
            "minecraft:bee", "minecraft:camel", "minecraft:sniffer",
            "minecraft:armadillo", "minecraft:axolotl", "minecraft:dolphin",
            "minecraft:goat", "minecraft:iron_golem", "minecraft:llama",
            "minecraft:panda", "minecraft:polar_bear", "minecraft:snow_golem",
            "minecraft:trader_llama", "minecraft:wolf", "minecraft:horse",
            "minecraft:skeleton_horse", "minecraft:zombie_horse",
            "minecraft:allay"
    );

    /** 敌对生物 */
    public static final Set<String> HOSTILE_IDS = Set.of(
            "minecraft:blaze", "minecraft:bogged", "minecraft:breeze",
            "minecraft:cave_spider", "minecraft:creeper", "minecraft:drowned",
            "minecraft:elder_guardian", "minecraft:enderman",
            "minecraft:endermite", "minecraft:evoker", "minecraft:ghast",
            "minecraft:guardian", "minecraft:hoglin", "minecraft:husk",
            "minecraft:magma_cube", "minecraft:phantom", "minecraft:piglin",
            "minecraft:piglin_brute", "minecraft:pillager", "minecraft:ravager",
            "minecraft:shulker", "minecraft:silverfish", "minecraft:skeleton",
            "minecraft:slime", "minecraft:spider", "minecraft:stray",
            "minecraft:vex", "minecraft:vindicator", "minecraft:warden",
            "minecraft:witch", "minecraft:wither_skeleton", "minecraft:zoglin",
            "minecraft:zombie", "minecraft:zombie_villager",
            "minecraft:zombified_piglin", "minecraft:creaking"
    );

    /** 中立生物（包括未驯服时中立、被激怒时攻击的） */
    public static final Set<String> NEUTRAL_IDS = Set.of(
            "minecraft:bee", "minecraft:cave_spider", "minecraft:dolphin",
            "minecraft:enderman", "minecraft:goat", "minecraft:iron_golem",
            "minecraft:llama", "minecraft:panda", "minecraft:piglin",
            "minecraft:polar_bear", "minecraft:spider", "minecraft:trader_llama",
            "minecraft:wolf", "minecraft:zombified_piglin"
    );

    /** 可骑乘实体 */
    public static final Set<String> RIDEABLE_IDS = Set.of(
            "minecraft:horse", "minecraft:donkey", "minecraft:mule",
            "minecraft:skeleton_horse", "minecraft:zombie_horse",
            "minecraft:pig", "minecraft:strider", "minecraft:camel",
            "minecraft:llama", "minecraft:trader_llama"
    );

    /** 技术性实体（非生物实体：掉落物、展示框、经验球等） */
    public static final Set<String> TECHNICAL_IDS = Set.of(
            "minecraft:area_effect_cloud", "minecraft:armor_stand",
            "minecraft:arrow", "minecraft:boat", "minecraft:chest_boat",
            "minecraft:chest_minecart", "minecraft:command_block_minecart",
            "minecraft:dragon_fireball", "minecraft:egg", "minecraft:end_crystal",
            "minecraft:ender_pearl", "minecraft:evoker_fangs",
            "minecraft:experience_bottle", "minecraft:experience_orb",
            "minecraft:eye_of_ender", "minecraft:falling_block",
            "minecraft:fireball", "minecraft:firework_rocket",
            "minecraft:fishing_bobber", "minecraft:furnace_minecart",
            "minecraft:glow_item_frame", "minecraft:hopper_minecart",
            "minecraft:item", "minecraft:item_frame", "minecraft:leash_knot",
            "minecraft:lightning_bolt", "minecraft:llama_spit",
            "minecraft:marker", "minecraft:minecart",
            "minecraft:oak_boat", "minecraft:oak_chest_boat",
            "minecraft:painting", "minecraft:potion", "minecraft:shulker_bullet",
            "minecraft:small_fireball", "minecraft:snowball",
            "minecraft:spawner_minecart", "minecraft:spectral_arrow",
            "minecraft:text_display", "minecraft:tnt",
            "minecraft:tnt_minecart", "minecraft:trident",
            "minecraft:wither_skull", "minecraft:block_display",
            "minecraft:interaction", "minecraft:item_display",
            "minecraft:ominous_item_spawner", "minecraft:wind_charge",
            "minecraft:breeze_wind_charge"
    );

    private final Predicate<EntityType<?>> filter;

    public EntityTypeListSetting(String name, Collection<EntityType<?>> defaultValue, Predicate<EntityType<?>> filter, Dependency dependency) {
        super(name, dependency, null);
        this.filter = filter;
        this.defaultValue = normalize(defaultValue);
        this.value = new ArrayList<>(this.defaultValue);
    }

    @Override
    public void setValue(List<EntityType<?>> value) {
        super.setValue(normalize(value));
    }

    @Override
    public void setValueSilently(List<EntityType<?>> value) {
        super.setValueSilently(normalize(value));
    }

    @Override
    public void reset() {
        setValue(new ArrayList<>(defaultValue));
    }

    public boolean contains(EntityType<?> entityType) {
        return value.contains(entityType);
    }

    public void add(EntityType<?> entityType) {
        if (entityType == null || value.contains(entityType)) return;
        if (filter != null && !filter.test(entityType)) return;
        List<EntityType<?>> next = new ArrayList<>(value);
        next.add(entityType);
        setValue(next);
    }

    public void remove(EntityType<?> entityType) {
        if (!value.contains(entityType)) return;
        List<EntityType<?>> next = new ArrayList<>(value);
        next.remove(entityType);
        setValue(next);
    }

    public void toggle(EntityType<?> entityType) {
        if (contains(entityType)) {
            remove(entityType);
        } else {
            add(entityType);
        }
    }

    public List<String> getIds() {
        List<String> ids = new ArrayList<>();
        for (EntityType<?> type : value) {
            ids.add(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
        }
        return ids;
    }

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
                    if (type != null && (filter == null || filter.test(type))) {
                        types.add(type);
                    }
                }
            }
        }
        setValue(types);
    }

    public void addGroup(String groupName) {
        List<EntityType<?>> next = new ArrayList<>(value);
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
            if (type != null && (filter == null || filter.test(type))) {
                if (!result.contains(type)) result.add(type);
            }
        }
        return result;
    }

    public int size() {
        return value.size();
    }

    public boolean isEmpty() {
        return value.isEmpty();
    }

    public Predicate<EntityType<?>> getFilter() {
        return filter;
    }

    /** 根据实体 ID 字符串判断分类（用于弹窗分类标签） */
    public static String classify(String entityId) {
        if (FRIENDLY_IDS.contains(entityId)) return "friendly";
        if (HOSTILE_IDS.contains(entityId)) return "hostile";
        if (NEUTRAL_IDS.contains(entityId)) return "neutral";
        if (RIDEABLE_IDS.contains(entityId)) return "rideable";
        if (TECHNICAL_IDS.contains(entityId)) return "technical";
        return null;
    }

    private static List<EntityType<?>> normalize(Collection<EntityType<?>> source) {
        if (source == null) return new ArrayList<>();
        List<EntityType<?>> result = new ArrayList<>();
        for (EntityType<?> e : source) {
            if (e != null && !result.contains(e)) result.add(e);
        }
        return result;
    }
}
