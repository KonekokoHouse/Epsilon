package com.github.epsilon.settings.impl;

import java.util.Collection;

public class StringListSetting extends RegistryListSetting<String> {

    public StringListSetting(String name, Collection<String> defaultValue, Dependency dependency) {
        super(name, defaultValue, Type.STRING_LIST, null, dependency);
    }

    public String get(int index) {
        return getValue().get(index);
    }
}
