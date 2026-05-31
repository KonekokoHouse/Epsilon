package com.github.epsilon.modules.impl.player;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.AttackEntityEvent;
import com.github.epsilon.managers.sound.SoundKey;
import com.github.epsilon.managers.sound.SoundManager;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.math.MathUtils;

public class SoundFX extends Module {

    public static final SoundFX INSTANCE = new SoundFX();

    private SoundFX() {
        super("Sound FX", Category.PLAYER);
    }

    private enum HitSound {
        UwU,
        Nya,
        Moan,
        OFF
    }

    private final IntSetting volume = intSetting("Volume", 100, 1, 100, 5);
    private final EnumSetting<HitSound> hitSound = enumSetting("Hit Sound", HitSound.OFF);

    @EventHandler
    private void onAttackEntity(AttackEntityEvent event) {
        playHitSound(hitSound.getValue());
    }

    private void playHitSound(HitSound value) {
        switch (value) {
            case UwU -> playSound(SoundKey.UWU);
            case Nya -> playSound(SoundKey.NYA);
            case Moan -> {
                SoundKey sound = switch (MathUtils.getRandom(0, 3)) {
                    case 0 -> SoundKey.MOAN1;
                    case 1 -> SoundKey.MOAN2;
                    case 2 -> SoundKey.MOAN3;
                    default -> SoundKey.MOAN4;
                };
                playSound(sound);
            }
        }
    }

    private void playSound(SoundKey key) {
        SoundManager.INSTANCE.playSound(key, 1.0f, volume.getValue().floatValue() / 100.0f);
    }

}
