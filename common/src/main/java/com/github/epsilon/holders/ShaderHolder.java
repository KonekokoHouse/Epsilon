package com.github.epsilon.holders;

/**
 * 保留旧配置使用的 shader 模式枚举。
 *
 * <p>Minecraft 1.21.1 不提供 26.x 的 RenderPipeline 后处理接口，因此该版本不注册
 * 对应 shader 模块。</p>
 */
public final class ShaderHolder {

    private ShaderHolder() {
    }

    public enum Shader {
        Default,
        Smoke,
        Gradient,
        Snow,
        Fade
    }
}
