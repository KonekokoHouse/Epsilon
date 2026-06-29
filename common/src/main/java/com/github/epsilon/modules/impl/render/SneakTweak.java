package com.github.epsilon.modules.impl.render;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
import net.minecraft.client.CameraType;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

public class SneakTweak extends Module {

    public static final SneakTweak INSTANCE = new SneakTweak();

    private enum SneakingEyeHeight {
        Default,
        Pre_1_14,
        Pre_1_9,
        Custom
    }

    private final EnumSetting<SneakingEyeHeight> sneakingEyeHeight = enumSetting("Sneaking Eye Height", SneakingEyeHeight.Default);
    private final DoubleSetting customSneakingEyeHeight = doubleSetting("Custom Sneaking Eye Height", 1.27, 0.0, 1.8, 0.01, () -> sneakingEyeHeight.is(SneakingEyeHeight.Custom), _ -> refreshPlayerDimensions());
    private final BoolSetting thirdPersonEyeHeight = boolSetting("Third Person Eye Height", true, _ -> refreshPlayerDimensions());

    private SneakTweak() {
        super("Sneak Tweak", Category.RENDER);
    }

}
