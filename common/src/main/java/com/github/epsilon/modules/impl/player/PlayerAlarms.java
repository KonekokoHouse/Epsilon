package com.github.epsilon.modules.impl.player;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.assets.i18n.TranslateComponent;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.SettingGroup;
import com.github.epsilon.settings.impl.*;
import com.github.epsilon.utils.player.ChatUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Entry;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

import java.util.*;

public class PlayerAlarms extends Module {

    public static final PlayerAlarms INSTANCE = new PlayerAlarms();

    private PlayerAlarms() {
        super("Player Alarms", Category.PLAYER);
    }

    // ==================== Setting Groups ====================
    private final SettingGroup sgGeneral = settingGroup("General");
    private final SettingGroup sgJoin = settingGroup("Join");
    private final SettingGroup sgLeave = settingGroup("Leave");
    private final SettingGroup sgEnterRD = settingGroup("Enter Render Distance");
    private final SettingGroup sgLeaveRD = settingGroup("Leave Render Distance");
    private final SettingGroup sgGamemode = settingGroup("Gamemode Change");

    // ==================== General ====================
    private final BoolSetting showGamemodeInChat = boolSetting("show-gamemode-in-chat", false).group(sgGeneral);

    private final StringListSetting names = stringListSetting("names",
        List.of("ssy_", "e_2", "山水圆")).group(sgGeneral);

    // ==================== Join Settings ====================
    private final IntSetting joinRings = intSetting("join-rings", 5, 1, 10, 1).group(sgJoin);
    private final IntSetting joinRingDelay = intSetting("join-ring-delay", 20, 1, 100, 1).group(sgJoin);
    private final DoubleSetting joinVolume = doubleSetting("join-volume", 1.0, 0.0, 1.0, 0.05).group(sgJoin);
    private final DoubleSetting joinPitch = doubleSetting("join-pitch", 1.0, 0.5, 2.0, 0.05).group(sgJoin);
    private final SoundEventListSetting joinSound = soundEventListSetting("join-sound",
        List.of(SoundEvents.BELL_BLOCK)).group(sgJoin);
    private final BoolSetting joinChatMessage = boolSetting("join-chat-message", true).group(sgJoin);

    // ==================== Leave Settings ====================
    private final IntSetting leaveRings = intSetting("leave-rings", 3, 1, 10, 1).group(sgLeave);
    private final IntSetting leaveRingDelay = intSetting("leave-ring-delay", 20, 1, 100, 1).group(sgLeave);
    private final DoubleSetting leaveVolume = doubleSetting("leave-volume", 1.0, 0.0, 1.0, 0.05).group(sgLeave);
    private final DoubleSetting leavePitch = doubleSetting("leave-pitch", 1.0, 0.5, 2.0, 0.05).group(sgLeave);
    private final SoundEventListSetting leaveSound = soundEventListSetting("leave-sound",
        List.of(SoundEvents.ANVIL_LAND)).group(sgLeave);
    private final BoolSetting leaveChatMessage = boolSetting("leave-chat-message", true).group(sgLeave);

    // ==================== Enter RD Settings ====================
    private final IntSetting enterRDRings = intSetting("enter-rd-rings", 2, 1, 10, 1).group(sgEnterRD);
    private final IntSetting enterRDRingDelay = intSetting("enter-rd-ring-delay", 20, 1, 100, 1).group(sgEnterRD);
    private final DoubleSetting enterRDVolume = doubleSetting("enter-rd-volume", 1.0, 0.0, 1.0, 0.05).group(sgEnterRD);
    private final DoubleSetting enterRDPitch = doubleSetting("enter-rd-pitch", 1.0, 0.5, 2.0, 0.05).group(sgEnterRD);
    private final SoundEventListSetting enterRDSound = soundEventListSetting("enter-rd-sound",
        List.of(SoundEvents.ANVIL_DESTROY)).group(sgEnterRD);
    private final BoolSetting enterRDChatMessage = boolSetting("enter-rd-chat-message", true).group(sgEnterRD);

    // ==================== Leave RD Settings ====================
    private final IntSetting leaveRDRings = intSetting("leave-rd-rings", 2, 1, 10, 1).group(sgLeaveRD);
    private final IntSetting leaveRDRingDelay = intSetting("leave-rd-ring-delay", 20, 1, 100, 1).group(sgLeaveRD);
    private final DoubleSetting leaveRDVolume = doubleSetting("leave-rd-volume", 1.0, 0.0, 1.0, 0.05).group(sgLeaveRD);
    private final DoubleSetting leaveRDPitch = doubleSetting("leave-rd-pitch", 1.0, 0.5, 2.0, 0.05).group(sgLeaveRD);
    private final SoundEventListSetting leaveRDSound = soundEventListSetting("leave-rd-sound",
        List.of(SoundEvents.BELL_BLOCK)).group(sgLeaveRD);
    private final BoolSetting leaveRDChatMessage = boolSetting("leave-rd-chat-message", true).group(sgLeaveRD);

    // ==================== Gamemode Change Settings ====================
    private final IntSetting gamemodeRings = intSetting("gamemode-rings", 3, 1, 10, 1).group(sgGamemode);
    private final IntSetting gamemodeRingDelay = intSetting("gamemode-ring-delay", 20, 1, 100, 1).group(sgGamemode);
    private final DoubleSetting gamemodeVolume = doubleSetting("gamemode-volume", 1.0, 0.0, 1.0, 0.05).group(sgGamemode);
    private final DoubleSetting gamemodePitch = doubleSetting("gamemode-pitch", 1.0, 0.5, 2.0, 0.05).group(sgGamemode);
    private final SoundEventListSetting gamemodeSound = soundEventListSetting("gamemode-sound",
        List.of(SoundEvents.ARROW_HIT_PLAYER)).group(sgGamemode);
    private final BoolSetting gamemodeChatMessage = boolSetting("gamemode-chat-message", true).group(sgGamemode);

    // ==================== State ====================
    private final Set<UUID> playersInRender = new HashSet<>();
    private final Map<UUID, GameType> gamemodeCache = new HashMap<>();
    private final Set<UUID> alarmedJoinPlayers = new HashSet<>();
    private boolean initialJoinCheckDone = false;

    // Ring state
    private static class RingState {
        int ticks;
        int ringsLeft;
        boolean active;
    }

    private final RingState joinRing = new RingState();
    private final RingState leaveRing = new RingState();
    private final RingState enterRDRing = new RingState();
    private final RingState leaveRDRing = new RingState();
    private final RingState gamemodeRing = new RingState();

    @Override
    protected void onEnable() {
        playersInRender.clear();
        gamemodeCache.clear();
        alarmedJoinPlayers.clear();
        initialJoinCheckDone = false;
        resetRingState(joinRing);
        resetRingState(leaveRing);
        resetRingState(enterRDRing);
        resetRingState(leaveRDRing);
        resetRingState(gamemodeRing);
    }

    private void resetRingState(RingState rs) {
        rs.ticks = 0;
        rs.ringsLeft = 0;
        rs.active = false;
    }

    @EventHandler
    private void onTick(PlayerTickEvent.Pre event) {
        if (nullCheck()) return;

        // Handle all rings
        handleRing(joinRing, () -> playSound(joinVolume.getValue(), joinPitch.getValue(), joinSound.getValue()));
        handleRing(leaveRing, () -> playSound(leaveVolume.getValue(), leavePitch.getValue(), leaveSound.getValue()));
        handleRing(enterRDRing, () -> playSound(enterRDVolume.getValue(), enterRDPitch.getValue(), enterRDSound.getValue()));
        handleRing(leaveRDRing, () -> playSound(leaveRDVolume.getValue(), leaveRDPitch.getValue(), leaveRDSound.getValue()));
        handleRing(gamemodeRing, () -> playSound(gamemodeVolume.getValue(), gamemodePitch.getValue(), gamemodeSound.getValue()));

        // Initial active check: check already-online target players on module enable
        if (!initialJoinCheckDone && mc.getConnection() != null) {
            for (var entry : mc.getConnection().getListedOnlinePlayers()) {
                UUID id = entry.getProfile().id();
                String playerName = entry.getProfile().name();
                if (!alarmedJoinPlayers.contains(id) && shouldAlarm(playerName)) {
                    startRing(joinRing, joinRings.getValue(), joinRingDelay.getValue());
                    sendChat(joinChatMessage.getValue(), EpsilonTranslations.PlayerAlarms.JOIN_CHAT_TEXT, playerName, id, ChatFormatting.RED);
                    alarmedJoinPlayers.add(id);
                }
            }
            initialJoinCheckDone = true;
        }

        // Render distance detection
        Set<UUID> currentInRender = new HashSet<>();
        for (var entity : mc.level.entitiesForRendering()) {
            if (entity instanceof Player && entity != mc.player) {
                currentInRender.add(entity.getUUID());
            }
        }

        // Enter RD
        for (UUID id : currentInRender) {
            if (!playersInRender.contains(id)) {
                String playerName = getPlayerName(id);
                if (playerName != null && shouldAlarm(playerName)) {
                    startRing(enterRDRing, enterRDRings.getValue(), enterRDRingDelay.getValue());
                    sendChat(enterRDChatMessage.getValue(), EpsilonTranslations.PlayerAlarms.ENTER_RD_CHAT_TEXT, playerName, id, ChatFormatting.DARK_RED);
                }
            }
        }

        // Leave RD
        for (UUID id : playersInRender) {
            if (!currentInRender.contains(id)) {
                String playerName = getPlayerName(id);
                if (playerName != null && shouldAlarm(playerName)) {
                    startRing(leaveRDRing, leaveRDRings.getValue(), leaveRDRingDelay.getValue());
                    sendChat(leaveRDChatMessage.getValue(), EpsilonTranslations.PlayerAlarms.LEAVE_RD_CHAT_TEXT, playerName, id, ChatFormatting.DARK_GREEN);
                }
            }
        }

        playersInRender.clear();
        playersInRender.addAll(currentInRender);
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (nullCheck()) return;

        // --- Player Join ---
        if (event.getPacket() instanceof ClientboundPlayerInfoUpdatePacket packet) {
            if (packet.actions().contains(Action.ADD_PLAYER)) {
                for (Entry entry : packet.entries()) {
                    String playerName = entry.profile().name();
                    gamemodeCache.put(entry.profileId(), entry.gameMode());
                    if (!alarmedJoinPlayers.contains(entry.profileId()) && shouldAlarm(playerName)) {
                        startRing(joinRing, joinRings.getValue(), joinRingDelay.getValue());
                        sendChat(joinChatMessage.getValue(), EpsilonTranslations.PlayerAlarms.JOIN_CHAT_TEXT, playerName, entry.profileId(), ChatFormatting.RED);
                        alarmedJoinPlayers.add(entry.profileId());
                    }
                }
            }

            // --- Gamemode Change ---
            if (packet.actions().contains(Action.UPDATE_GAME_MODE)) {
                for (Entry entry : packet.entries()) {
                    UUID id = entry.profileId();
                    GameType newMode = entry.gameMode();

                    var playerInfo = mc.getConnection().getPlayerInfo(id);
                    if (playerInfo == null) continue;

                    GameType oldMode = playerInfo.getGameMode();
                    if (oldMode != newMode) {
                        String playerName = getPlayerName(id);
                        if (playerName != null && shouldAlarm(playerName)) {
                            startRing(gamemodeRing, gamemodeRings.getValue(), gamemodeRingDelay.getValue());
                            if (gamemodeChatMessage.getValue()) {
                                String msg = EpsilonTranslations.PlayerAlarms.GAMEMODE_CHAT_TEXT.getTranslatedName()
                                    .replace("{name}", playerName)
                                    .replace("{old_gamemode}", translateGamemode(oldMode))
                                    .replace("{new_gamemode}", translateGamemode(newMode));
                                ChatUtils.addChatMessage(Component.literal(msg).withStyle(ChatFormatting.YELLOW));
                            }
                        }
                    }
                }
            }
        }

        // --- Player Leave ---
        if (event.getPacket() instanceof ClientboundPlayerInfoRemovePacket removePacket) {
            for (UUID id : removePacket.profileIds()) {
                String playerName = getPlayerName(id);
                if (playerName == null) playerName = id.toString();
                if (shouldAlarm(playerName)) {
                    startRing(leaveRing, leaveRings.getValue(), leaveRingDelay.getValue());
                    sendChat(leaveChatMessage.getValue(), EpsilonTranslations.PlayerAlarms.LEAVE_CHAT_TEXT, playerName, id, ChatFormatting.GREEN);
                }
                gamemodeCache.remove(id);
                playersInRender.remove(id);
                alarmedJoinPlayers.remove(id);
            }
        }
    }

    // ==================== Helpers ====================

    private boolean shouldAlarm(String playerName) {
        if (playerName == null) return false;
        List<String> nameList = names.getValue();
        if (nameList.isEmpty()) return false;
        return nameList.stream().anyMatch(n -> n.equalsIgnoreCase(playerName));
    }

    private void startRing(RingState rs, int rings, int delay) {
        rs.ringsLeft = rings;
        rs.ticks = 0;
        rs.active = true;
    }

    private void handleRing(RingState rs, Runnable play) {
        if (!rs.active || rs.ringsLeft <= 0) {
            rs.active = false;
            return;
        }
        if (rs.ticks <= 0) {
            play.run();
            rs.ticks = (rs.ringsLeft == 1) ? 0 : getRingDelay(rs);
            rs.ringsLeft--;
            if (rs.ringsLeft <= 0) rs.active = false;
        } else {
            rs.ticks--;
        }
    }

    private int getRingDelay(RingState rs) {
        if (rs == joinRing) return joinRingDelay.getValue();
        if (rs == leaveRing) return leaveRingDelay.getValue();
        if (rs == enterRDRing) return enterRDRingDelay.getValue();
        if (rs == leaveRDRing) return leaveRDRingDelay.getValue();
        if (rs == gamemodeRing) return gamemodeRingDelay.getValue();
        return 20;
    }

    private void sendChat(boolean enabled, TranslateComponent template, String playerName, UUID playerId, ChatFormatting color) {
        if (!enabled) return;
        String msg = template.getTranslatedName().replace("{name}", playerName);
        if (showGamemodeInChat.getValue() && playerId != null) {
            msg = msg.replace("{gamemode}", getGamemodeName(playerId));
        } else {
            msg = msg.replace("{gamemode}", "");
        }
        ChatUtils.addChatMessage(Component.literal(msg).withStyle(color));
    }

    private void playSound(double vol, double pitch, List<SoundEvent> sounds) {
        if (mc.player == null || mc.level == null || sounds.isEmpty()) return;
        SoundEvent sound = sounds.get(0);
        mc.level.playLocalSound(mc.player.blockPosition(), sound, SoundSource.PLAYERS, (float) vol, (float) pitch, false);
    }

    private String getPlayerName(UUID id) {
        if (mc.level == null) return null;
        Player player = mc.level.getPlayerByUUID(id);
        if (player != null) return player.getGameProfile().name();
        // fallback: try to get from network player list
        if (mc.getConnection() != null) {
            var entry = mc.getConnection().getPlayerInfo(id);
            if (entry != null && entry.getProfile() != null) return entry.getProfile().name();
        }
        return null;
    }

    private String getGamemodeName(UUID id) {
        GameType mode = gamemodeCache.get(id);
        if (mode != null) return translateGamemode(mode);
        // fallback: try to get from network info
        if (mc.getConnection() != null) {
            var info = mc.getConnection().getPlayerInfo(id);
            if (info != null) {
                mode = info.getGameMode();
                if (mode != null) return translateGamemode(mode);
            }
        }
        return "Unknown";
    }

    private static String translateGamemode(GameType gameType) {
        if (gameType == GameType.SURVIVAL) return EpsilonTranslations.PlayerAlarms.GAMEMODE_SURVIVAL.getTranslatedName();
        if (gameType == GameType.CREATIVE) return EpsilonTranslations.PlayerAlarms.GAMEMODE_CREATIVE.getTranslatedName();
        if (gameType == GameType.ADVENTURE) return EpsilonTranslations.PlayerAlarms.GAMEMODE_ADVENTURE.getTranslatedName();
        if (gameType == GameType.SPECTATOR) return EpsilonTranslations.PlayerAlarms.GAMEMODE_SPECTATOR.getTranslatedName();
        return gameType.getName();
    }
}
