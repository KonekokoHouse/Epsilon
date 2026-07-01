package com.github.epsilon.utils.timer;

public class TimerUtils {

    private long startTime = -1L;

    public TimerUtils() {
        reset();
    }

    public void reset() {
        startTime = System.currentTimeMillis();
    }

    public long getMs() {
        return System.currentTimeMillis() - startTime;
    }
    
    public long getPassedTimeMs() {
        return getMs();
    }

    public long getTime() {
        return startTime;
    }

    public void setMs(long ms) {
        startTime = System.currentTimeMillis() - ms;
    }

    public boolean passedSecond(double seconds) {
        return passedMillise((long) seconds * 1000L);
    }

    public boolean hasDelayed(int ticks) {
        return passedMillise((long) ticks * 50L);
    }

    public boolean every(long ms) {
        if (passedMillise(ms)) {
            reset();
            return true;
        }
        return false;
    }

    public boolean passedMillise(double ms) {
        return passedMillise((long) ms);
    }

    public boolean passedMillise(long ms) {
        return System.currentTimeMillis() - startTime >= ms;
    }

    // === Shorter aliases ===

    public boolean passed(int ms) {
        return passedMillise(ms);
    }

    public boolean passed(double ms) {
        return passedMillise((long) ms);
    }

    public boolean passedMs(long ms) {
        return passedMillise(ms);
    }

    public boolean passedMs(double ms) {
        return passedMillise(ms);
    }

    // === Time unit convenience methods ===

    public boolean passedM(double m) {
        return passedMillise((long) (m * 1000.0D * 60.0D));
    }

    public boolean passedDms(double dms) {
        return passedMillise((long) (dms * 10.0D));
    }

    public boolean passedDs(double ds) {
        return passedMillise((long) (ds * 100.0D));
    }

    // === Sleep mechanism ===

    public boolean sleep(long l) {
        if (System.nanoTime() / 1000000L - l >= l) {
            reset();
            return true;
        }
        return false;
    }
}
