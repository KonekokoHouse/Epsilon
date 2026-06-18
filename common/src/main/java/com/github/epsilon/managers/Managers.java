package com.github.epsilon.managers;

import com.github.epsilon.managers.impl.FriendManager;
import com.github.epsilon.managers.impl.HealthManager;
import com.github.epsilon.managers.impl.NotificationManager;
import com.github.epsilon.managers.impl.RotationManager;
import com.github.epsilon.managers.impl.network.ClientboundPacketManager;
import com.github.epsilon.managers.impl.network.ServerboundPacketManager;
import com.github.epsilon.managers.impl.sound.SoundManager;
import com.github.epsilon.managers.impl.target.TargetManager;

public class Managers {

    public static RotationManager ROTATION;
    public static TargetManager TARGET;
    public static HealthManager HEALTH;
    public static ServerboundPacketManager C2SPACKET;
    public static ClientboundPacketManager S2CPACKET;
    public static FriendManager FRIEND;
    public static SoundManager SOUND;
    public static NotificationManager NOTIFICATION;

    public static void initManagers() {
        ROTATION = new RotationManager();
        TARGET = new TargetManager();
        HEALTH = new HealthManager();
        C2SPACKET = new ServerboundPacketManager();
        S2CPACKET = new ClientboundPacketManager();
        FRIEND = new FriendManager();
        SOUND = new SoundManager();
        NOTIFICATION = new NotificationManager();
    }

}
