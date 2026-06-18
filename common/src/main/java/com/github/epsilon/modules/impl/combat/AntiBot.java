package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.events.impl.RespawnEvent;
import com.github.epsilon.events.impl.SendPositionEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AntiBot extends Module {

    public static final AntiBot INSTANCE = new AntiBot();

    private AntiBot() {
        super("Anti Bot", Category.COMBAT);
    }

    private static final Map<UUID, Long> uuids = new ConcurrentHashMap<>();
    private static final Set<Integer> ids = new HashSet<>();


    public boolean isBot(Entity entity) {
        return isEnabled() && (ids.contains(entity.getId()) || !mc.getConnection().getOnlinePlayerIds().contains(entity.getUUID()));
    }

    @EventHandler
    private void onRespawn(RespawnEvent event) {
        if (mc.player.tickCount <= 1) {
            ids.clear();
            uuids.clear();
        }
    }

    @EventHandler
    private void onSendPosition(SendPositionEvent event) {
        for (Map.Entry<UUID, Long> entry : uuids.entrySet()) {
            if (System.currentTimeMillis() - entry.getValue() > 500L) {
                uuids.remove(entry.getKey());
            }
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundPlayerInfoUpdatePacket packet) {
            if (packet.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER)) {
                for (ClientboundPlayerInfoUpdatePacket.Entry entry : packet.entries()) {
                    if (entry.displayName() != null && entry.profile() != null && entry.displayName().getSiblings().isEmpty() && entry.gameMode() == GameType.SURVIVAL) {
                        UUID uuid = entry.profile().id();
                        uuids.put(uuid, System.currentTimeMillis());
                    }
                }
            }
        } else if (event.getPacket() instanceof ClientboundAddEntityPacket packet && packet.getType() == EntityType.PLAYER) {
            if (uuids.containsKey(packet.getUUID())) {
                uuids.remove(packet.getUUID());
                ids.add(packet.getId());
            }
        } else if (event.getPacket() instanceof ClientboundRemoveEntitiesPacket packet) {
            for (Integer entityId : packet.getEntityIds()) {
                ids.remove(entityId);
            }
        }
    }

}
