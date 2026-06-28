package com.github.epsilon.managers.impl;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.elements.impl.notification.Notification;
import com.github.epsilon.elements.impl.notification.NotificationMode;
import com.github.epsilon.modules.impl.ClientSetting;
import com.github.epsilon.utils.player.ChatUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.*;

public class NotificationManager {

    private static final int MAX_NOTIFICATIONS = 5;

    private final Queue<Notification> notifications = new ArrayDeque<>();
    private final Map<Integer, Notification> hashCodeMap = new HashMap<>();

    public void success(String title, String subTitle) {
        notify(title, subTitle, NotificationMode.Success, ChatFormatting.GREEN);
    }

    public void success(String title, String subTitle, int hash) {
        notify(title, subTitle, NotificationMode.Success, ChatFormatting.GREEN, hash);
    }

    public void info(String title, String subTitle) {
        notify(title, subTitle, NotificationMode.Info, ChatFormatting.WHITE);
    }

    public void info(String title, String subTitle, int hash) {
        notify(title, subTitle, NotificationMode.Info, ChatFormatting.WHITE, hash);
    }

    public void error(String title, String subTitle) {
        notify(title, subTitle, NotificationMode.Error, ChatFormatting.RED);
    }

    public void error(String title, String subTitle, int hash) {
        notify(title, subTitle, NotificationMode.Error, ChatFormatting.RED, hash);
    }

    public void moduleState(String moduleName, boolean enabled) {
        moduleState(moduleName, moduleName.hashCode(), enabled);
    }

    public void moduleState(String moduleName, int hash, boolean enabled) {
        String subTitle = getModuleStateText(enabled);
        NotificationMode mode = NotificationMode.fromEnabled(enabled);

        notifyReplaceable(hash, moduleName, subTitle, mode);

        sendNotificationMessage(moduleName, subTitle, enabled ? ChatFormatting.GREEN : ChatFormatting.RED, hash);
    }

    public FormattedCharSequence applyAnimatedPrefix(FormattedCharSequence original) {
        return ChatUtils.applyAnimatedPrefix(original);
    }

    public void update() {
        Iterator<Notification> iterator = notifications.iterator();
        while (iterator.hasNext()) {
            Notification notification = iterator.next();
            if (notification.isExpired()) {
                iterator.remove();
                unregister(notification);
            }
        }
    }

    public Queue<Notification> getNotifications() {
        return notifications;
    }

    public boolean isEmpty() {
        return notifications.isEmpty();
    }

    public void clearAll() {
        notifications.clear();
        hashCodeMap.clear();
    }

    private void notify(String title, String subTitle, NotificationMode mode, ChatFormatting chatColor) {
        enqueue(new Notification(title, subTitle, mode, false));
        sendNotificationMessage(title, subTitle, chatColor);
    }

    private void notify(String title, String subTitle, NotificationMode mode, ChatFormatting chatColor, int hash) {
        notifyReplaceable(hash, title, subTitle, mode);
        sendNotificationMessage(title, subTitle, chatColor, hash);
    }

    private void notifyReplaceable(int hash, String title, String subTitle, NotificationMode mode) {
        Notification existing = hashCodeMap.get(hash);
        if (existing != null && !existing.isExiting()) {
            existing.refresh(title, subTitle, mode);
            return;
        }

        remove(existing);

        Notification notification = new Notification(hash, title, subTitle, mode, true);
        enqueue(notification);
        hashCodeMap.put(hash, notification);
    }

    private void enqueue(Notification notification) {
        makeRoomIfNeeded();
        notifications.add(notification);
    }

    private void remove(Notification notification) {
        if (notification == null) {
            return;
        }

        notifications.remove(notification);
        unregister(notification);
    }

    private void unregister(Notification notification) {
        if (notification != null && notification.isReplaceable()) {
            hashCodeMap.remove(notification.getId());
        }
    }

    private String getModuleStateText(boolean enabled) {
        return enabled ? EpsilonTranslations.Notifications.ENABLED.getTranslatedName() : EpsilonTranslations.Notifications.DISABLED.getTranslatedName();
    }

    private void sendNotificationMessage(String title, String subTitle, ChatFormatting chatColor) {
        if (ClientSetting.INSTANCE.chatNotify.getValue()) {
            sendChatMessage(createNotificationMessage(title, subTitle, chatColor));
        }
    }

    private void sendNotificationMessage(String title, String subTitle, ChatFormatting chatColor, int hash) {
        if (ClientSetting.INSTANCE.chatNotify.getValue()) {
            sendChatMessage(createNotificationMessage(title, subTitle, chatColor), hash);
        }
    }

    private Component createNotificationMessage(String title, String subTitle, ChatFormatting chatColor) {
        return Component.literal(title)
                .append(" ")
                .append(Component.literal(subTitle).withStyle(chatColor));
    }

    private void sendChatMessage(Component message) {
        ChatUtils.addChatMessage(message);
    }

    private void sendChatMessage(Component message, int hash) {
        ChatUtils.addChatMessage(message, hash);
    }

    private void makeRoomIfNeeded() {
        if (notifications.size() >= MAX_NOTIFICATIONS) {
            unregister(notifications.poll());
        }
    }

}
