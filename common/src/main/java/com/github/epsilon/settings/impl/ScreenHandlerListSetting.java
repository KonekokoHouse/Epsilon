package com.github.epsilon.settings.impl;

import net.minecraft.world.inventory.MenuType;

import java.util.Collection;

public class ScreenHandlerListSetting extends RegistryListSetting<MenuType<?>> {

    public ScreenHandlerListSetting(String name, Collection<MenuType<?>> defaultValue, Dependency dependency) {
        super(name, defaultValue, Type.MENU, null, dependency);
    }
}
