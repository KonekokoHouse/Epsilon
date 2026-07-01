package com.github.epsilon.utils.math;

import static com.github.epsilon.Constants.mc;

import java.util.concurrent.ThreadLocalRandom;

public class MathUtils {

    private MathUtils() {
    }

    // 返回 [min, max] 的闭区间随机整数
    public static int getRandom(int min, int max) {
        return min >= max ? min : (int) ThreadLocalRandom.current().nextLong(min, (long) max + 1L);
    }

    // 返回 [min, max] 的闭区间随机浮点数
    public static float getRandom(float min, float max) {
        return min >= max ? min : ThreadLocalRandom.current().nextFloat(min, Math.nextUp(max));
    }

    // 返回 [min, max] 的闭区间随机双精度数
    public static double getRandom(double min, double max) {
        return min >= max ? min : ThreadLocalRandom.current().nextDouble(min, Math.nextUp(max));
    }

    //以下代码传统control平飞和自动巡航要用
    public static float getYaw() {
        float forward = mc.player.input.getMoveVector().y;
        float side = mc.player.input.getMoveVector().x;
        float yaw = mc.player.yRotO + (mc.player.getViewYRot(1.0F) - mc.player.yRotO) * mc.getFrameTimeNs()/1_000_000_000.0F;
        if (forward == 0.0F && side == 0.0F) return yaw;
        if (forward != 0.0F) {
            if (side >= 1.0F) {
                yaw += ((forward > 0.0F) ? -45 : -135);
                side = 0.0F;
            } else if (side <= -1.0F) {
                yaw += ((forward > 0.0F) ? 45 : 135);
                side = 0.0F;
            }
        } else {
            yaw += side * -90.0F;
        }
        return yaw;
    }

    public static double[] transformStrafe(double speed, boolean autoMove, float yaw) {
        float forward = mc.player.input.getMoveVector().y;
        float side = mc.player.input.getMoveVector().x;
        if (!autoMove) {
            yaw = mc.player.yRotO + (mc.player.getViewYRot(1.0F) - mc.player.yRotO) * mc.getFrameTimeNs()/1_000_000_000.0F;
        } else {
            return new double[]{
                Math.sin(Math.toRadians(yaw + 90.0F)) * speed,
                speed * Math.cos(Math.toRadians(yaw + 90.0F))            
            };
        }
        if (forward == 0.0F && side == 0.0F) return new double[]{0.0, 0.0};
        if (forward != 0.0F) {
            if (side >= 1.0F) {
                yaw += ((forward > 0.0F) ? -45 : 45);
                side = 0.0F;
            } else if (side <= -1.0F) {
                yaw += ((forward > 0.0F) ? 45 : -45);
                side = 0.0F;
            }
            if (forward > 0.0F) {
                forward = 1.0F;
            } else if (forward < 0.0F) {
                forward = -1.0F;
            }
        }
        double mx = Math.cos(Math.toRadians((yaw + 90.0F)));
        double mz = Math.sin(Math.toRadians((yaw + 90.0F)));
        double velX = forward * speed * mx + side * speed * mz;
        double velZ = forward * speed * mz - side * speed * mx;
        return new double[]{velX, velZ};
    }
}
