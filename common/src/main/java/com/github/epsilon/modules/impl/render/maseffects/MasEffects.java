package com.github.epsilon.modules.impl.render.maseffects;

import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * 兼容旧 Mixin 调用的占位模块。1.21.1 版本不注册 26.x MasEffects 渲染功能。
 */
public final class MasEffects extends Module {

    public static final MasEffects INSTANCE = new MasEffects();

    private MasEffects() {
        super("Mas Effects", Category.RENDER);
    }

    public void onLevelEvent(int type, Vec3 position) {
    }

    public void onWindSeed(double x, double y, double z) {
    }

    public boolean renderCustomHitbox(Entity entity, float partialTicks) {
        return false;
    }

    public boolean shouldHideVanillaTotemParticles() {
        return false;
    }
}
