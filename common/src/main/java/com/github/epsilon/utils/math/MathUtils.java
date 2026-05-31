package com.github.epsilon.utils.math;

import java.util.concurrent.ThreadLocalRandom;

public class MathUtils {

    private MathUtils() {
    }

    public static int getRandom(int min, int max) {
        return min >= max ? min : (int) ThreadLocalRandom.current().nextLong(min, (long) max + 1L);
    }

    public static float getRandom(float min, float max) {
        return min >= max ? min : ThreadLocalRandom.current().nextFloat(min, Math.nextUp(max));
    }

    public static double getRandom(double min, double max) {
        return min >= max ? min : ThreadLocalRandom.current().nextDouble(min, Math.nextUp(max));
    }

}
