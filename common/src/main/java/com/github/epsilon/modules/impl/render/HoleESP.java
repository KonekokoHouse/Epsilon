package com.github.epsilon.modules.impl.render;

import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;

public class HoleESP extends Module {

    public static final HoleESP INSTANCE = new HoleESP();

    private HoleESP() {
        super("Hole ESP", Category.RENDER);
    }

}
