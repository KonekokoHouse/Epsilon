package com.github.epsilon.modules.impl.player;

import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;

public class Shaders extends Module {

    public static final Shaders INSTANCE = new Shaders();

    private Shaders() {
        super("Shaders", Category.RENDER);
    }

}
