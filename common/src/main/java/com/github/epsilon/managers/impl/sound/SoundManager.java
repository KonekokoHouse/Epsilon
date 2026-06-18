package com.github.epsilon.managers.impl.sound;

import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

import static com.github.epsilon.Constants.mc;

public class SoundManager {

    private static final long UI_DEBOUNCE_MS = 40L;

    private long lastUiPlayTimeMs;

    public void playInUi(SoundKey key) {
        playSound(key, 1.2f);
    }

    public void playSound(SoundKey key, float volume) {
        playSound(key, 1.0f, volume);
    }

    public void playSound(SoundKey key, float pitch, float volume) {
        long now = System.currentTimeMillis();
        if (now - lastUiPlayTimeMs < UI_DEBOUNCE_MS) return;
        lastUiPlayTimeMs = now;

        SimpleSoundInstance instance = new SimpleSoundInstance(
                key.id(),
                SoundSource.UI,
                Math.max(0.0f, volume),
                Mth.clamp(pitch, 0.5f, 2.0f),
                SoundInstance.createUnseededRandom(),
                false,
                0,
                SoundInstance.Attenuation.NONE,
                0.0,
                0.0,
                0.0,
                true
        );

        mc.getSoundManager().play(instance);
    }

    public void stop(SoundKey key) {
        mc.getSoundManager().stop(key.id(), null);
    }

    public void stopAllSounds() {
        for (SoundKey key : SoundKey.values()) {
            mc.getSoundManager().stop(key.id(), null);
        }
    }

}
