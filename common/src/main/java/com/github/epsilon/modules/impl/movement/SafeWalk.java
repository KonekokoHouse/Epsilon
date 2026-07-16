package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;

public class SafeWalk extends Module {

    public static final SafeWalk INSTANCE = new SafeWalk();

    private SafeWalk() {
        super("Safe Walk", Category.MOVEMENT);
    }

}
