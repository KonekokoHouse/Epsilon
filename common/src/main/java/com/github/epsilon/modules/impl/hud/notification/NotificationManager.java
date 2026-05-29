package com.github.epsilon.modules.impl.hud.notification;

import com.github.epsilon.assets.i18n.EpsilonTranslateComponent;
import com.github.epsilon.assets.i18n.TranslateComponent;

import java.util.*;

public class NotificationManager {

    public static final NotificationManager INSTANCE = new NotificationManager();

    private static final int MAX_NOTIFICATIONS = 5;

    private final Queue<Notification> notifications = new ArrayDeque<>();
    private final Map<Integer, Notification> hashCodeMap = new HashMap<>();

    private final TranslateComponent enableComponent = EpsilonTranslateComponent.create("modules.notifications hud", "enabled");
    private final TranslateComponent disableComponent = EpsilonTranslateComponent.create("modules.notifications hud", "disabled");

    public void post(String title, String subTitle, NotificationMode mode) {
        enqueue(new Notification(title, subTitle, mode, false));
    }

    public void postModuleNotification(String moduleName, boolean enabled) {
        int notificationId = moduleName.hashCode();
        String subTitle = getModuleStateText(enabled);
        NotificationMode mode = NotificationMode.fromEnabled(enabled);

        Notification existing = hashCodeMap.get(notificationId);
        if (existing != null && !existing.isExiting()) {
            existing.refresh(moduleName, subTitle, mode);
            return;
        }

        remove(existing);

        Notification notification = new Notification(notificationId, moduleName, subTitle, mode, true);
        enqueue(notification);
        hashCodeMap.put(notificationId, notification);
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

    public void clear() {
        notifications.clear();
        hashCodeMap.clear();
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
        if (notification != null && notification.isModule()) {
            hashCodeMap.remove(notification.getId());
        }
    }

    private String getModuleStateText(boolean enabled) {
        return enabled ? enableComponent.getTranslatedName() : disableComponent.getTranslatedName();
    }

    private void makeRoomIfNeeded() {
        if (notifications.size() >= MAX_NOTIFICATIONS) {
            unregister(notifications.poll());
        }
    }

}
