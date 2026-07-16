package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;

public class Eagle extends Module {

    public static final Eagle INSTANCE = new Eagle();

    private Eagle() {
        super("Eagle", Category.MOVEMENT);
    }

}
