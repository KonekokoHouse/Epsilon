package com.github.epsilon.settings.impl;

import com.mojang.serialization.Lifecycle;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Stream;

public class EnchantmentListSetting extends RegistryListSetting<String> {

    public EnchantmentListSetting(String name, Collection<String> defaultValue, Dependency dependency) {
        super(name, defaultValue, Type.ENCHANTMENT, null, dependency);
    }

    /** 从动态注册表获取附魔 ID 的翻译名，如 "minecraft:sharpness" → "Sharpness" */
    public static String getEnchantmentDisplayName(String enchantId) {
        Identifier loc = Identifier.tryParse(enchantId);
        if (loc == null) return enchantId;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return loc.getPath().replace('_', ' ');
        var lookup = mc.level.registryAccess().lookup(Registries.ENCHANTMENT);
        if (lookup.isEmpty()) return loc.getPath().replace('_', ' ');
        var ench = lookup.get().getValue(ResourceKey.create(Registries.ENCHANTMENT, loc));
        if (ench == null) return loc.getPath().replace('_', ' ');
        return ench.description().getString();
    }

    /** 构建附魔 ID 伪注册表（从动态注册表获取） */
    @SuppressWarnings("unchecked")
    public static Registry<String> getEnchantmentRegistry() {
        return new MappedRegistry<String>(
                ResourceKey.createRegistryKey(Identifier.tryParse("epsilon:enchantments")),
                Lifecycle.stable()
        ) {
            private List<String> entries;
            private List<String> collect() {
                Set<String> set = new LinkedHashSet<>();
                var mc = Minecraft.getInstance();
                if (mc.level != null) {
                    var lookup = mc.level.registryAccess().lookup(Registries.ENCHANTMENT);
                    lookup.ifPresent(reg -> reg.keySet().forEach(k -> set.add(k.toString())));
                }
                return new ArrayList<>(set);
            }
            private List<String> entries() {
                if (entries == null) entries = collect();
                return entries;
            }
            @Override public int size() { return entries().size(); }
            @Nullable @Override public Identifier getKey(String entry) {
                return Identifier.tryParse(entry);
            }
            @Override public Optional<ResourceKey<String>> getResourceKey(String entry) { return Optional.empty(); }
            @Override public int getId(@Nullable String entry) { return 0; }
            @Nullable @Override public String getValue(@Nullable ResourceKey<String> key) { return null; }
            @Nullable @Override public String getValue(@Nullable Identifier id) { return null; }
            @Override public Lifecycle registryLifecycle() { return Lifecycle.stable(); }
            @Nullable @Override public Set<Identifier> keySet() { return null; }
            @Override public boolean containsKey(Identifier id) { return false; }
            @Override public boolean containsKey(ResourceKey<String> key) { return false; }
            @Nullable @Override public Set<Map.Entry<ResourceKey<String>, String>> entrySet() { return null; }
            @Override public Optional<Holder.Reference<String>> getRandom(RandomSource random) { return Optional.empty(); }
            @Override public Registry<String> freeze() { return this; }
            @Override public Holder.Reference<String> createIntrusiveHolder(String value) { return null; }
            @Override public Optional<Holder.Reference<String>> get(int rawId) { return Optional.empty(); }
            @Override public Optional<Holder.Reference<String>> get(Identifier id) { return Optional.empty(); }
            @NotNull @Override public Iterator<String> iterator() { return entries().iterator(); }
            @Override public Stream<Holder.Reference<String>> listElements() { return Stream.empty(); }
            @Override public Stream<HolderSet.Named<String>> getTags() { return Stream.empty(); }
            @Nullable @Override public Set<ResourceKey<String>> registryKeySet() { return null; }
        };
    }
}
