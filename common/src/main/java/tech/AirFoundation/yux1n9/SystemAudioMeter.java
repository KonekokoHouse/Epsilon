package tech.AirFoundation.yux1n9;

import com.github.epsilon.Constants;
import com.github.epsilon.utils.client.ClientUtils;

import java.util.Locale;

public final class SystemAudioMeter {
    private static final String DLL_RESOURCE = "/assets/client/native/audio_meter_native.dll";
    private static final Object LOCK = new Object();
    private static final int DEFAULT_SPECTRUM_BANDS = 32;

    private static volatile boolean libraryLoaded;
    private static volatile boolean started;
    private static volatile float smoothedLevel;
    private static volatile long lastSampleNanos;
    private static volatile boolean spectrumUnavailable;

    private SystemAudioMeter() {
    }

    public static boolean start() {
        if (!isWindows()) {
            return false;
        }
        synchronized (LOCK) {
            if (!libraryLoaded) {
                libraryLoaded = ClientUtils.loadNativeLibrary(DLL_RESOURCE, "audio_meter_native");
            }
            if (started) {
                return true;
            }
            started = nStart();
            lastSampleNanos = System.nanoTime();
            if (!started) {
                Constants.LOGGER.warn("SystemAudioMeter start failed: {}", getLastError());
            }
            return started;
        }
    }

    public static void shutdown() {
        synchronized (LOCK) {
            if (!started) {
                return;
            }
            nStop();
            started = false;
            smoothedLevel = 0.0f;
            lastSampleNanos = 0L;
        }
    }

    public static boolean isRunning() {
        return started;
    }

    public static float getPeakLevel() {
        if (!started && !start()) {
            return 0.0f;
        }
        return clamp01(nGetPeakLevel());
    }

    public static float getSmoothedLevel() {
        return getSmoothedLevel(18.0f, 4.5f);
    }

    public static float getSmoothedLevel(float attackPerSecond, float releasePerSecond) {
        float raw = getPeakLevel();
        long now = System.nanoTime();
        long previous = lastSampleNanos;
        lastSampleNanos = now;
        float dt = previous == 0L ? (1.0f / 60.0f) : Math.max(0.0f, (now - previous) / 1_000_000_000.0f);

        float current = smoothedLevel;
        float rate = raw > current ? Math.max(0.0f, attackPerSecond) : Math.max(0.0f, releasePerSecond);
        float next = moveTowards(current, raw, rate * dt);
        smoothedLevel = next;
        return next;
    }

    public static String getLastError() {
        if (!libraryLoaded) {
            return "Library not loaded";
        }
        String error = nGetLastError();
        return error == null ? "" : error;
    }

    public static int getDefaultSpectrumBands() {
        return DEFAULT_SPECTRUM_BANDS;
    }

    public static boolean fillSpectrum(float[] destination) {
        if (destination == null || destination.length == 0) {
            return false;
        }
        if (!started && !start()) {
            return false;
        }
        if (spectrumUnavailable) {
            java.util.Arrays.fill(destination, 0.0f);
            return false;
        }

        try {
            return nGetSpectrum(destination);
        } catch (UnsatisfiedLinkError ignored) {
            spectrumUnavailable = true;
            java.util.Arrays.fill(destination, 0.0f);
            return false;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static float moveTowards(float current, float target, float maxDelta) {
        if (target > current) {
            return Math.min(target, current + maxDelta);
        }
        return Math.max(target, current - maxDelta);
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static native boolean nStart();

    private static native void nStop();

    private static native float nGetPeakLevel();

    private static native boolean nGetSpectrum(float[] destination);

    private static native String nGetLastError();
}
