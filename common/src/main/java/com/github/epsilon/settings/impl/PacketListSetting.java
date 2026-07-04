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
                    // -- Clientbound (S2C) --
                    "net.minecraft.network.protocol.game.ClientboundAddEntityPacket",
                    "net.minecraft.network.protocol.game.ClientboundAddExperienceOrbPacket",
                    "net.minecraft.network.protocol.game.ClientboundAnimatePacket",
                    "net.minecraft.network.protocol.game.ClientboundAwardStatsPacket",
                    "net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket",
                    "net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket",
                    "net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket",
                    "net.minecraft.network.protocol.game.ClientboundBlockEventPacket",
                    "net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket",
                    "net.minecraft.network.protocol.game.ClientboundBossEventPacket",
                    "net.minecraft.network.protocol.game.ClientboundBundleDelimiterPacket",
                    "net.minecraft.network.protocol.game.ClientboundBundlePacket",
                    "net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket",
                    "net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket",
                    "net.minecraft.network.protocol.game.ClientboundChunkBatchStartPacket",
                    "net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket",
                    "net.minecraft.network.protocol.game.ClientboundClearTitlesPacket",
                    "net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket",
                    "net.minecraft.network.protocol.game.ClientboundCommandsPacket",
                    "net.minecraft.network.protocol.game.ClientboundContainerClosePacket",
                    "net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket",
                    "net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket",
                    "net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket",
                    "net.minecraft.network.protocol.game.ClientboundCooldownPacket",
                    "net.minecraft.network.protocol.game.ClientboundCustomChatCompletionsPacket",
                    "net.minecraft.network.protocol.game.ClientboundDamageEventPacket",
                    "net.minecraft.network.protocol.game.ClientboundDebugBlockValuePacket",
                    "net.minecraft.network.protocol.game.ClientboundDebugChunkValuePacket",
                    "net.minecraft.network.protocol.game.ClientboundDebugEntityValuePacket",
                    "net.minecraft.network.protocol.game.ClientboundDebugEventPacket",
                    "net.minecraft.network.protocol.game.ClientboundDebugSamplePacket",
                    "net.minecraft.network.protocol.game.ClientboundDeleteChatPacket",
                    "net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket",
                    "net.minecraft.network.protocol.game.ClientboundEntityEventPacket",
                    "net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket",
                    "net.minecraft.network.protocol.game.ClientboundExplodePacket",
                    "net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket",
                    "net.minecraft.network.protocol.game.ClientboundGameEventPacket",
                    "net.minecraft.network.protocol.game.ClientboundGameRuleValuesPacket",
                    "net.minecraft.network.protocol.game.ClientboundGameTestHighlightPosPacket",
                    "net.minecraft.network.protocol.game.ClientboundHorseStepPacket",
                    "net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket",
                    "net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket",
                    "net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket",
                    "net.minecraft.network.protocol.game.ClientboundLevelEventPacket",
                    "net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket",
                    "net.minecraft.network.protocol.game.ClientboundLightUpdatePacket",
                    "net.minecraft.network.protocol.game.ClientboundLoginPacket",
                    "net.minecraft.network.protocol.game.ClientboundLowDiskSpaceWarningPacket",
                    "net.minecraft.network.protocol.game.ClientboundMapItemDataPacket",
                    "net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket",
                    "net.minecraft.network.protocol.game.ClientboundMountScreenOpenPacket",
                    "net.minecraft.network.protocol.game.ClientboundMoveMinecartAlongTrackPacket",
                    "net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket",
                    "net.minecraft.network.protocol.game.ClientboundOpenBookPacket",
                    "net.minecraft.network.protocol.game.ClientboundOpenScreenPacket",
                    "net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket",
                    "net.minecraft.network.protocol.game.ClientboundPlaceGhostRecipePacket",
                    "net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket",
                    "net.minecraft.network.protocol.game.ClientboundPlayerChatPacket",
                    "net.minecraft.network.protocol.game.ClientboundPlayerCombatEndPacket",
                    "net.minecraft.network.protocol.game.ClientboundPlayerCombatEnterPacket",
                    "net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket",
                    "net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket",
                    "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket",
                    "net.minecraft.network.protocol.game.ClientboundPlayerLookAtPacket",
                    "net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket",
                    "net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket",
                    "net.minecraft.network.protocol.game.ClientboundProjectilePowerPacket",
                    "net.minecraft.network.protocol.game.ClientboundRecipeBookAddPacket",
                    "net.minecraft.network.protocol.game.ClientboundRecipeBookRemovePacket",
                    "net.minecraft.network.protocol.game.ClientboundRecipeBookSettingsPacket",
                    "net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket",
                    "net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket",
                    "net.minecraft.network.protocol.game.ClientboundResetScorePacket",
                    "net.minecraft.network.protocol.game.ClientboundRespawnPacket",
                    "net.minecraft.network.protocol.game.ClientboundRotateHeadPacket",
                    "net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket",
                    "net.minecraft.network.protocol.game.ClientboundSelectAdvancementsTabPacket",
                    "net.minecraft.network.protocol.game.ClientboundServerDataPacket",
                    "net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket",
                    "net.minecraft.network.protocol.game.ClientboundSetBorderCenterPacket",
                    "net.minecraft.network.protocol.game.ClientboundSetBorderLerpSizePacket",
                    "net.minecraft.network.protocol.game.ClientboundSetBorderSizePacket",
                    "net.minecraft.network.protocol.game.ClientboundSetBorderWarningDelayPacket",
                    "net.minecraft.network.protocol.game.ClientboundSetBorderWarningDistancePacket",
                    "net.minecraft.network.protocol.game.ClientboundSetCameraPacket",
                    "net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket",
                    "net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket",
                    "net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket",
                    "net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket",
                    "net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket",
                    "net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket",
                    "net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket",
                    "net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket",
                    "net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket",
                    "net.minecraft.network.protocol.game.ClientboundSetExperiencePacket",
                    "net.minecraft.network.protocol.game.ClientboundSetHealthPacket",
                    "net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket",
                    "net.minecraft.network.protocol.game.ClientboundSetObjectivePacket",
                    "net.minecraft.network.protocol.game.ClientboundSetPassengersPacket",
                    "net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket",
                    "net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket",
                    "net.minecraft.network.protocol.game.ClientboundSetScorePacket",
                    "net.minecraft.network.protocol.game.ClientboundSetSimulationDistancePacket",
                    "net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket",
                    "net.minecraft.network.protocol.game.ClientboundSetTimePacket",
                    "net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket",
                    "net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket",
                    "net.minecraft.network.protocol.game.ClientboundSoundEntityPacket",
                    "net.minecraft.network.protocol.game.ClientboundSoundPacket",
                    "net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket",
                    "net.minecraft.network.protocol.game.ClientboundStopSoundPacket",
                    "net.minecraft.network.protocol.game.ClientboundSystemChatPacket",
                    "net.minecraft.network.protocol.game.ClientboundTabListPacket",
                    "net.minecraft.network.protocol.game.ClientboundTagQueryPacket",
                    "net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket",
                    "net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket",
                    "net.minecraft.network.protocol.game.ClientboundTestInstanceBlockStatusPacket",
                    "net.minecraft.network.protocol.game.ClientboundTickingStatePacket",
                    "net.minecraft.network.protocol.game.ClientboundTickingStepPacket",
                    "net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket",
                    "net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket",
                    "net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket",
                    "net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket",
                    "net.minecraft.network.protocol.game.ClientboundWaypointPacket",
                    "net.minecraft.network.protocol.game.ClientboundMoveEntityPacketPos",
                    "net.minecraft.network.protocol.game.ClientboundMoveEntityPacketPosRot",
                    "net.minecraft.network.protocol.game.ClientboundMoveEntityPacketRot",
                    // -- Serverbound (C2S) --
                    "net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket",
                    "net.minecraft.network.protocol.game.ServerboundBlockEntityTagQueryPacket",
                    "net.minecraft.network.protocol.game.ServerboundChangeDifficultyPacket",
                    "net.minecraft.network.protocol.game.ServerboundChangeGameModePacket",
                    "net.minecraft.network.protocol.game.ServerboundChatAckPacket",
                    "net.minecraft.network.protocol.game.ServerboundChatCommandPacket",
                    "net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket",
                    "net.minecraft.network.protocol.game.ServerboundChatPacket",
                    "net.minecraft.network.protocol.game.ServerboundChatSessionUpdatePacket",
                    "net.minecraft.network.protocol.game.ServerboundChunkBatchReceivedPacket",
                    "net.minecraft.network.protocol.game.ServerboundClientCommandPacket",
                    "net.minecraft.network.protocol.game.ServerboundClientTickEndPacket",
                    "net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket",
                    "net.minecraft.network.protocol.game.ServerboundConfigurationAcknowledgedPacket",
                    "net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket",
                    "net.minecraft.network.protocol.game.ServerboundContainerClickPacket",
                    "net.minecraft.network.protocol.game.ServerboundContainerClosePacket",
                    "net.minecraft.network.protocol.game.ServerboundContainerSlotStateChangedPacket",
                    "net.minecraft.network.protocol.game.ServerboundDebugSubscriptionRequestPacket",
                    "net.minecraft.network.protocol.game.ServerboundEditBookPacket",
                    "net.minecraft.network.protocol.game.ServerboundEntityTagQueryPacket",
                    "net.minecraft.network.protocol.game.ServerboundInteractPacket",
                    "net.minecraft.network.protocol.game.ServerboundJigsawGeneratePacket",
                    "net.minecraft.network.protocol.game.ServerboundLockDifficultyPacket",
                    "net.minecraft.network.protocol.game.ServerboundMovePlayerPacket",
                    "net.minecraft.network.protocol.game.ServerboundMovePlayerPosPacket",
                    "net.minecraft.network.protocol.game.ServerboundMovePlayerPosRotPacket",
                    "net.minecraft.network.protocol.game.ServerboundMovePlayerRotPacket",
                    "net.minecraft.network.protocol.game.ServerboundMovePlayerStatusOnlyPacket",
                    "net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket",
                    "net.minecraft.network.protocol.game.ServerboundPaddleBoatPacket",
                    "net.minecraft.network.protocol.game.ServerboundPickItemFromBlockPacket",
                    "net.minecraft.network.protocol.game.ServerboundPickItemFromEntityPacket",
                    "net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket",
                    "net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket",
                    "net.minecraft.network.protocol.game.ServerboundPlayerActionPacket",
                    "net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket",
                    "net.minecraft.network.protocol.game.ServerboundPlayerInputPacket",
                    "net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket",
                    "net.minecraft.network.protocol.game.ServerboundRecipeBookChangeSettingsPacket",
                    "net.minecraft.network.protocol.game.ServerboundRecipeBookSeenRecipePacket",
                    "net.minecraft.network.protocol.game.ServerboundRenameItemPacket",
                    "net.minecraft.network.protocol.game.ServerboundSeenAdvancementsPacket",
                    "net.minecraft.network.protocol.game.ServerboundSelectBundleItemPacket",
                    "net.minecraft.network.protocol.game.ServerboundSelectTradePacket",
                    "net.minecraft.network.protocol.game.ServerboundSetBeaconPacket",
                    "net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket",
                    "net.minecraft.network.protocol.game.ServerboundSetCommandBlockPacket",
                    "net.minecraft.network.protocol.game.ServerboundSetCommandMinecartPacket",
                    "net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket",
                    "net.minecraft.network.protocol.game.ServerboundSetGameRulePacket",
                    "net.minecraft.network.protocol.game.ServerboundSetJigsawBlockPacket",
                    "net.minecraft.network.protocol.game.ServerboundSetStructureBlockPacket",
                    "net.minecraft.network.protocol.game.ServerboundSetTestBlockPacket",
                    "net.minecraft.network.protocol.game.ServerboundSignUpdatePacket",
                    "net.minecraft.network.protocol.game.ServerboundSpectateEntityPacket",
                    "net.minecraft.network.protocol.game.ServerboundSwingPacket",
                    "net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket",
                    "net.minecraft.network.protocol.game.ServerboundTestInstanceBlockActionPacket",
                    "net.minecraft.network.protocol.game.ServerboundUseItemOnPacket",
                    "net.minecraft.network.protocol.game.ServerboundUseItemPacket",
                    // -- Other protocol --
                    "net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket",
                    "net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket",
                    "net.minecraft.network.protocol.common.ClientboundKeepAlivePacket",
                    "net.minecraft.network.protocol.common.ServerboundKeepAlivePacket",
                    "net.minecraft.network.protocol.common.ClientboundPingPacket",
                    "net.minecraft.network.protocol.common.ServerboundPongPacket",
                    "net.minecraft.network.protocol.common.ClientboundDisconnectPacket",
                    "net.minecraft.network.protocol.common.ClientboundResourcePackPopPacket",
                    "net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket",
                    "net.minecraft.network.protocol.common.ServerboundResourcePackPacket",
                    "net.minecraft.network.protocol.common.ClientboundStoreCookiePacket",
                    "net.minecraft.network.protocol.common.ServerboundCookieResponsePacket",
                    "net.minecraft.network.protocol.common.ClientboundTransferPacket",
                    "net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket",
                    "net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket",
                    "net.minecraft.network.protocol.login.ServerboundLoginAcknowledgedPacket",
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
