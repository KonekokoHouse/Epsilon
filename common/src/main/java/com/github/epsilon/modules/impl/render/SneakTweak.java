package com.github.epsilon.modules.impl.render;

import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.EnumSetting;

public class SneakTweak extends Module {

    public static final SneakTweak INSTANCE = new SneakTweak();

    private SneakTweak() {
        super("Sneak Tweak", Category.RENDER);
    }

    private enum SneakingEyeHeight {
        Default,
        Pre_1_14,
        Pre_1_9,
        Custom
    }

    private final EnumSetting<SneakingEyeHeight> sneakingEyeHeight = enumSetting("Sneaking Eye Height", SneakingEyeHeight.Default);
    private final DoubleSetting customSneakingEyeHeight = doubleSetting("Custom Sneaking Eye Height", 1.27, 0.0, 1.8, 0.01, () -> sneakingEyeHeight.is(SneakingEyeHeight.Custom));
    private final BoolSetting thirdPersonEyeHeight = boolSetting("Third Person Eye Height", true);

}
