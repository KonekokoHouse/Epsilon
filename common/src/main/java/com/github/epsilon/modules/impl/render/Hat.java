package com.github.epsilon.modules.impl.render;

import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;

/**
 * 兼容旧配置的占位模块。1.21.1 版本不注册此模块。
 */
public final class Hat extends Module {

    public static final Hat INSTANCE = new Hat();

    private Hat() {
        super("Hat", Category.RENDER);
    }
}
