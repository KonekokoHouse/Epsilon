package com.github.epsilon.modules.impl.hud.notification;

import com.github.epsilon.assets.i18n.EpsilonTranslateComponent;
import com.github.epsilon.assets.i18n.TranslateComponent;

import java.util.*;

import static com.github.epsilon.Constants.mc;

public class NotificationManager {

    public static final NotificationManager INSTANCE = new NotificationManager();

    private static final int MAX_NOTIFICATIONS = 5;

    private final Queue<Notification> notifications = new ArrayDeque<>();
    private final Map<Integer, Notification> hashCodeMap = new HashMap<>();

    private final TranslateComponent enableComponent = EpsilonTranslateComponent.create("modules.notifications hud", "enabled");
    private final TranslateComponent disableComponent = EpsilonTranslateComponent.create("modules.notifications hud", "disabled");


    public void post(String title, String subTitle, NotificationMode mode, int displayTime) {
        makeRoomIfNeeded();
        Notification notification = new Notification(title, subTitle, mode, displayTime, getScreenHeight(), false);
        notifications.add(notification);
    }

    public void postModuleNotification(String moduleName, boolean enabled, int displayTime) {
        int hashCode = moduleName.hashCode();

        // 检查是否已存在相同模块的通知
        Notification existing = hashCodeMap.get(hashCode);
        if (existing != null) {
            if (existing.isExiting()) {
                // 旧通知已经在退出动画中：直接淘汰，避免被强制复活，也避免与新通知同时显示
                notifications.remove(existing);
                hashCodeMap.remove(hashCode);
            } else {
                // 仍处于正常显示阶段：原地更新内容与计时
                String newTitle = enabled ? enableComponent.getTranslatedName() : disableComponent.getTranslatedName();
                NotificationMode mode = enabled ? NotificationMode.Success : NotificationMode.Warning;
                existing.updateModuleState(newTitle, moduleName, mode, displayTime);
                return;
            }
        }

        // 不存在，创建新的
        makeRoomIfNeeded();
        String title = enabled ? enableComponent.getTranslatedName() : disableComponent.getTranslatedName();
        NotificationMode mode = enabled ? NotificationMode.Success : NotificationMode.Warning;
        Notification notification = new Notification(hashCode, title, moduleName, mode, displayTime, getScreenHeight(), true);
        notifications.add(notification);
        hashCodeMap.put(hashCode, notification);
    }

    public void update() {
        Iterator<Notification> iterator = notifications.iterator();
        while (iterator.hasNext()) {
            Notification notification = iterator.next();
            notification.update();
            if (notification.isExpired()) {
                iterator.remove();
                hashCodeMap.remove(notification.getHashCode());
            }
        }
    }

    public Queue<Notification> getNotifications() {
        return notifications;
    }

    public boolean isEmpty() {
        return notifications.isEmpty();
    }

    public void clear() {
        notifications.clear();
        hashCodeMap.clear();
    }

    private void makeRoomIfNeeded() {
        if (notifications.size() >= MAX_NOTIFICATIONS) {
            Notification oldest = notifications.poll();
            if (oldest != null) {
                hashCodeMap.remove(oldest.getHashCode());
            }
        }
    }

    private float getScreenHeight() {
        if (mc.getWindow() == null) {
            return 0;
        }
        return mc.getWindow().getGuiScaledHeight();
    }

}
