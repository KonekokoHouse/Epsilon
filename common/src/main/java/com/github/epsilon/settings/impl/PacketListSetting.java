package com.github.epsilon.settings.impl;

import com.github.epsilon.settings.Setting;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class PacketListSetting extends Setting<List<Class<? extends Packet<?>>>> {

    private final Predicate<Class<? extends Packet<?>>> filter;

    public PacketListSetting(String name, Collection<Class<? extends Packet<?>>> defaultValue,
                             Predicate<Class<? extends Packet<?>>> filter, Dependency dependency) {
        super(name, dependency, null);
        this.filter = filter;
        this.defaultValue = normalize(defaultValue);
        this.value = new ArrayList<>(this.defaultValue);
    }

    @Override
    public void setValue(List<Class<? extends Packet<?>>> value) {
        super.setValue(normalize(value));
    }

    @Override
    public void setValueSilently(List<Class<? extends Packet<?>>> value) {
        super.setValueSilently(normalize(value));
    }

    @Override
    public void reset() {
        setValue(new ArrayList<>(defaultValue));
    }

    public boolean contains(Class<? extends Packet<?>> packetClass) {
        return value.contains(packetClass);
    }

    public void add(Class<? extends Packet<?>> packetClass) {
        if (packetClass == null || value.contains(packetClass)) return;
        if (filter != null && !filter.test(packetClass)) return;
        List<Class<? extends Packet<?>>> next = new ArrayList<>(value);
        next.add(packetClass);
        setValue(next);
    }

    public void remove(Class<? extends Packet<?>> packetClass) {
        if (!value.contains(packetClass)) return;
        List<Class<? extends Packet<?>>> next = new ArrayList<>(value);
        next.remove(packetClass);
        setValue(next);
    }

    public void toggle(Class<? extends Packet<?>> packetClass) {
        if (contains(packetClass)) {
            remove(packetClass);
        } else {
            add(packetClass);
        }
    }

    public List<String> getClassNames() {
        List<String> names = new ArrayList<>();
        for (Class<? extends Packet<?>> clazz : value) {
            names.add(clazz.getName());
        }
        return names;
    }

    @SuppressWarnings("unchecked")
    public void setClassNames(Collection<String> names) {
        List<Class<? extends Packet<?>>> classes = new ArrayList<>();
        if (names != null) {
            for (String name : names) {
                try {
                    Class<?> clazz = Class.forName(name);
                    if (Packet.class.isAssignableFrom(clazz)) {
                        Class<? extends Packet<?>> packetClass = (Class<? extends Packet<?>>) clazz;
                        if (filter == null || filter.test(packetClass)) {
                            if (!classes.contains(packetClass)) classes.add(packetClass);
                        }
                    }
                } catch (ClassNotFoundException ignored) {
                }
            }
        }
        setValue(classes);
    }

    public int size() {
        return value.size();
    }

    public boolean isEmpty() {
        return value.isEmpty();
    }

    public Predicate<Class<? extends Packet<?>>> getFilter() {
        return filter;
    }

    private static List<Class<? extends Packet<?>>> normalize(Collection<Class<? extends Packet<?>>> source) {
        if (source == null) return new ArrayList<>();
        List<Class<? extends Packet<?>>> result = new ArrayList<>();
        for (Class<? extends Packet<?>> c : source) {
            if (c != null && !result.contains(c)) result.add(c);
        }
        return result;
    }

    // ---- Packet registry for popup ----

    public static String formatPacketName(Class<? extends Packet<?>> clazz) {
        String name = clazz.getSimpleName();
        if (name.startsWith("Clientbound")) name = name.substring(11);
        else if (name.startsWith("Serverbound")) name = name.substring(11);
        return name;
    }

    public static boolean isS2C(Class<? extends Packet<?>> clazz) {
        return clazz.getSimpleName().startsWith("Clientbound");
    }

    public static boolean isC2S(Class<? extends Packet<?>> clazz) {
        return clazz.getSimpleName().startsWith("Serverbound");
    }

    public static final Registry<Class<? extends Packet<?>>> PACKET_REGISTRY = new PacketRegistry();

    private static class PacketRegistry extends MappedRegistry<Class<? extends Packet<?>>> {
        private static final List<Class<? extends Packet<?>>> KNOWN_PACKETS = collectKnownPackets();

        PacketRegistry() {
            super(ResourceKey.createRegistryKey(Identifier.tryParse("epsilon:packets")), Lifecycle.stable());
        }

        @SuppressWarnings("unchecked")
        private static List<Class<? extends Packet<?>>> collectKnownPackets() {
            List<Class<? extends Packet<?>>> list = new ArrayList<>();
            String[] classNames = {
                    "net.minecraft.network.protocol.game.ClientboundAddEntityPacket",
                    "net.minecraft.network.protocol.game.ClientboundAnimatePacket",
                    "net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket",
                    "net.minecraft.network.protocol.game.ClientboundBossEventPacket",
                    "net.minecraft.network.protocol.game.ClientboundChatPacket",
                    "net.minecraft.network.protocol.game.ClientboundDamageEventPacket",
                    "net.minecraft.network.protocol.game.ClientboundEntityEventPacket",
                    "net.minecraft.network.protocol.game.ClientboundExplodePacket",
                    "net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket",
                    "net.minecraft.network.protocol.game.ClientboundLevelChunkPacket",
                    "net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket",
                    "net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket",
                    "net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket",
                    "net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket",
                    "net.minecraft.network.protocol.game.ClientboundSetHealthPacket",
                    "net.minecraft.network.protocol.game.ClientboundSoundPacket",
                    "net.minecraft.network.protocol.game.ClientboundSystemChatPacket",
                    "net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket",
                    "net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket",
                    "net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket",
                    "net.minecraft.network.protocol.game.ServerboundChatPacket",
                    "net.minecraft.network.protocol.game.ServerboundInteractPacket",
                    "net.minecraft.network.protocol.game.ServerboundMovePlayerPacket",
                    "net.minecraft.network.protocol.game.ServerboundPlayerActionPacket",
                    "net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket",
                    "net.minecraft.network.protocol.game.ServerboundSwingPacket",
                    "net.minecraft.network.protocol.game.ServerboundUseItemOnPacket",
                    "net.minecraft.network.protocol.game.ServerboundUseItemPacket",
            };
            for (String name : classNames) {
                try { list.add((Class<? extends Packet<?>>) Class.forName(name)); } catch (Exception ignored) {}
            }
            return list;
        }

        @Override public int size() { return KNOWN_PACKETS.size(); }
        @Nullable @Override public Identifier getKey(Class<? extends Packet<?>> entry) {
            return Identifier.tryParse("epsilon:" + entry.getSimpleName().toLowerCase(Locale.ROOT));
        }
        @Override public Optional<ResourceKey<Class<? extends Packet<?>>>> getResourceKey(Class<? extends Packet<?>> entry) { return Optional.empty(); }
        @Override public int getId(@Nullable Class<? extends Packet<?>> entry) { return 0; }
        @Nullable @Override
        public Class<? extends Packet<?>> getValue(@Nullable ResourceKey<Class<? extends Packet<?>>> key) { return null; }
        @Nullable @Override
        public Class<? extends Packet<?>> getValue(@Nullable Identifier id) { return null; }
        @Override public Lifecycle registryLifecycle() { return Lifecycle.stable(); }
        @Nullable @Override public Set<Identifier> keySet() { return null; }
        @Override public boolean containsKey(Identifier id) { return false; }
        @Override public boolean containsKey(ResourceKey<Class<? extends Packet<?>>> key) { return false; }
        @Nullable @Override public Set<Map.Entry<ResourceKey<Class<? extends Packet<?>>>, Class<? extends Packet<?>>>> entrySet() { return null; }
        @Override public Optional<Holder.Reference<Class<? extends Packet<?>>>> getRandom(RandomSource random) { return Optional.empty(); }
        @Override public Registry<Class<? extends Packet<?>>> freeze() { return this; }
        @Override public Holder.Reference<Class<? extends Packet<?>>> createIntrusiveHolder(Class<? extends Packet<?>> value) { return null; }
        @Override public Optional<Holder.Reference<Class<? extends Packet<?>>>> get(int rawId) { return Optional.empty(); }
        @Override public Optional<Holder.Reference<Class<? extends Packet<?>>>> get(Identifier id) { return Optional.empty(); }
        @NotNull @Override public Iterator<Class<? extends Packet<?>>> iterator() { return KNOWN_PACKETS.iterator(); }
        @Override public Stream<Holder.Reference<Class<? extends Packet<?>>>> listElements() { return Stream.empty(); }
        @Override public Stream<HolderSet.Named<Class<? extends Packet<?>>>> getTags() { return Stream.empty(); }
        @Nullable @Override public Set<ResourceKey<Class<? extends Packet<?>>>> registryKeySet() { return null; }
    }
}
