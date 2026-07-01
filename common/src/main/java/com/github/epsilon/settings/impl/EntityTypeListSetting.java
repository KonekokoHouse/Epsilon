package com.github.epsilon.settings.impl;

import com.github.epsilon.settings.Setting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import java.util.*;
import java.util.function.Predicate;

public class EntityTypeListSetting extends Setting<Set<EntityType<?>>> {

    private static final List<String> GROUPS = List.of("animal", "wateranimal", "monster", "ambient", "misc");

    private final Predicate<EntityType<?>> filter;

    public EntityTypeListSetting(String name, Set<EntityType<?>> defaultValue, Predicate<EntityType<?>> filter, Dependency dependency) {
        super(name, dependency, null);
        this.filter = filter;
        this.defaultValue = normalize(defaultValue);
        this.value = new LinkedHashSet<>(this.defaultValue);
    }

    @Override
    public void setValue(Set<EntityType<?>> value) {
        super.setValue(normalize(value));
    }

    @Override
    public void setValueSilently(Set<EntityType<?>> value) {
        super.setValueSilently(normalize(value));
    }

    @Override
    public void reset() {
        setValue(new LinkedHashSet<>(defaultValue));
    }

    public boolean contains(EntityType<?> entityType) {
        return value.contains(entityType);
    }

    public void add(EntityType<?> entityType) {
        if (entityType == null || value.contains(entityType)) return;
        if (filter != null && !filter.test(entityType)) return;
        Set<EntityType<?>> next = new LinkedHashSet<>(value);
        next.add(entityType);
        setValue(next);
    }

    public void remove(EntityType<?> entityType) {
        if (!value.contains(entityType)) return;
        Set<EntityType<?>> next = new LinkedHashSet<>(value);
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
        Set<EntityType<?>> types = new LinkedHashSet<>();
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
        Set<EntityType<?>> next = new LinkedHashSet<>(value);
        next.addAll(resolveGroup(groupName.trim().toLowerCase()));
        setValue(next);
    }

    private Set<EntityType<?>> resolveGroup(String group) {
        Set<EntityType<?>> result = new LinkedHashSet<>();
        for (EntityType<?> entityType : BuiltInRegistries.ENTITY_TYPE) {
            if (filter != null && !filter.test(entityType)) continue;
            switch (group) {
                case "animal" -> {
                    if (entityType.getCategory() == MobCategory.CREATURE) result.add(entityType);
                }
                case "wateranimal" -> {
                    MobCategory cat = entityType.getCategory();
                    if (cat == MobCategory.WATER_AMBIENT
                            || cat == MobCategory.WATER_CREATURE
                            || cat == MobCategory.UNDERGROUND_WATER_CREATURE
                            || cat == MobCategory.AXOLOTLS) result.add(entityType);
                }
                case "monster" -> {
                    if (entityType.getCategory() == MobCategory.MONSTER) result.add(entityType);
                }
                case "ambient" -> {
                    if (entityType.getCategory() == MobCategory.AMBIENT) result.add(entityType);
                }
                case "misc" -> {
                    if (entityType.getCategory() == MobCategory.MISC) result.add(entityType);
                }
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

    private static Set<EntityType<?>> normalize(Set<EntityType<?>> source) {
        if (source == null) return new LinkedHashSet<>();
        return new LinkedHashSet<>(source);
    }
}
