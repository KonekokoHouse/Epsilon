package com.github.epsilon.mixins;

import com.github.epsilon.interfaces.ChatComponentAccessor;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Mixin(ChatComponent.class)
public abstract class MixinChatComponentHash implements ChatComponentAccessor {

    @Shadow
    @Final
    private List<GuiMessage> allMessages;

    @Shadow
    public abstract void addMessage(Component contents, MessageSignature signature, GuiMessageTag tag);

    @Shadow
    private void refreshTrimmedMessages() {
    }

    @Unique
    private final Map<Integer, GuiMessage> epsilon$hashedMessages = new HashMap<>();

    @Override
    public void epsilon$addClientSystemMessage(Component message, int hash) {
        GuiMessage previous = epsilon$hashedMessages.remove(hash);
        if (previous != null && allMessages.remove(previous)) {
            refreshTrimmedMessages();
        }

        addMessage(message, null, GuiMessageTag.systemSinglePlayer());
        if (!allMessages.isEmpty()) {
            epsilon$hashedMessages.put(hash, allMessages.getFirst());
        }
        epsilon$pruneMissingHashedMessages();
    }

    @Unique
    private void epsilon$pruneMissingHashedMessages() {
        Iterator<Map.Entry<Integer, GuiMessage>> iterator = epsilon$hashedMessages.entrySet().iterator();
        while (iterator.hasNext()) {
            if (!allMessages.contains(iterator.next().getValue())) {
                iterator.remove();
            }
        }
    }
}
