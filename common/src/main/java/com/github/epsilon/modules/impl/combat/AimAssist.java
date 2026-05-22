package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.events.impl.TickEvent;
import com.github.epsilon.managers.target.TargetManager;
import com.github.epsilon.managers.target.TargetRequest;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.SettingGroup;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3d;

public class AimAssist extends Module {

    public static final AimAssist INSTANCE = new AimAssist();

    private AimAssist() {
        super("Aim Assist", Category.COMBAT);
    }

    private final SettingGroup sgGeneral = settingGroup("General");
    private final SettingGroup sgAimSpeed = settingGroup("Aim Speed");

    private enum AimMode {
        Head,
        Body,
        Feet
    }

    private final DoubleSetting aimRange = doubleSetting("Aim Range", 4.0, 1.0, 6.0, 0.1).group(sgGeneral);
    private final IntSetting fov = intSetting("FOV", 180, 10, 360, 1).group(sgGeneral);
    private final BoolSetting player = boolSetting("Player", true).group(sgGeneral);
    private final BoolSetting mob = boolSetting("Mob", true).group(sgGeneral);
    private final BoolSetting animal = boolSetting("Animal", true).group(sgGeneral);
    private final BoolSetting villagers = boolSetting("Villagers", false).group(sgGeneral);
    private final BoolSetting invisible = boolSetting("Invisible", true).group(sgGeneral);
    private final EnumSetting<AimMode> aimMode = enumSetting("Aim Mode", AimMode.Body).group(sgGeneral);
    private final BoolSetting instant = boolSetting("Instant Look", false).group(sgAimSpeed);
    private final DoubleSetting speed = doubleSetting("Speed", 20.0, 1.0, 100.0, 1.0, () -> !instant.getValue()).group(sgAimSpeed);

    private LivingEntity target;
    private final Vector3d vector3d = new Vector3d();

    @EventHandler
    private void onPostTick(TickEvent.Post event) {
        target = TargetManager.INSTANCE.acquirePrimary(TargetRequest.of(
                aimRange.getValue(),
                fov.getValue().floatValue(),
                player.getValue(),
                mob.getValue(),
                animal.getValue(),
                villagers.getValue(),
                invisible.getValue(),
                1
        ));
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (target != null) {
            float delta = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);

            vector3d.x = Mth.lerp(delta, target.xOld, target.getX());
            vector3d.y = Mth.lerp(delta, target.yOld, target.getY());
            vector3d.z = Mth.lerp(delta, target.zOld, target.getZ());

            switch (aimMode.getValue()) {
                case Head -> vector3d.add(0, target.getEyeHeight(target.getPose()), 0);
                case Body -> vector3d.add(0, target.getEyeHeight(target.getPose()) / 2, 0);
            }

            double deltaX = vector3d.x - mc.player.getX();
            double deltaZ = vector3d.z - mc.player.getZ();
            double deltaY = vector3d.y - (mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()));

            // Yaw
            double angle = Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90;
            double deltaAngle;
            double toRotate;

            if (instant) {
                mc.player.setYRot((float) angle);
            } else {
                deltaAngle = Mth.wrapDegrees(angle - mc.player.getYRot());
                toRotate = speed.getValue() * (deltaAngle >= 0 ? 1 : -1) * delta;
                if ((toRotate >= 0 && toRotate > deltaAngle) || (toRotate < 0 && toRotate < deltaAngle)) {
                    toRotate = deltaAngle;
                }
                mc.player.setYRot(mc.player.getYRot() + (float) toRotate);
            }

            // Pitch
            double idk = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
            angle = -Math.toDegrees(Math.atan2(deltaY, idk));

            if (instant) {
                mc.player.setXRot((float) angle);
            } else {
                deltaAngle = Mth.wrapDegrees(angle - mc.player.getXRot());
                toRotate = speed.getValue() * (deltaAngle >= 0 ? 1 : -1) * delta;
                if ((toRotate >= 0 && toRotate > deltaAngle) || (toRotate < 0 && toRotate < deltaAngle)) {
                    toRotate = deltaAngle;
                }
                mc.player.setXRot(mc.player.getXRot() + (float) toRotate);
            }
        }
    }

}
