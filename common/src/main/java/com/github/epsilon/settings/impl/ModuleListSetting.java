package com.github.epsilon.settings.impl;

import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.Setting;

import java.util.*;

public class ModuleListSetting extends Setting<List<Module>> {

    public ModuleListSetting(String name, List<Module> defaultValue, Dependency dependency) {
        super(name, dependency, null);
        this.defaultValue = normalize(defaultValue);
        this.value = new ArrayList<>(this.defaultValue);
    }

    @Override
    public void setValue(List<Module> value) {
        super.setValue(normalize(value));
    }

    @Override
    public void setValueSilently(List<Module> value) {
        super.setValueSilently(normalize(value));
    }

    @Override
    public void reset() {
        setValue(new ArrayList<>(defaultValue));
    }

    public boolean contains(Module module) {
        return value.contains(module);
    }

    public List<String> getModuleNames() {
        List<String> names = new ArrayList<>();
        for (Module module : value) {
            names.add(module.getName());
        }
        return names;
    }

    public void setModuleNames(Collection<String> names, List<Module> availableModules) {
        List<Module> modules = new ArrayList<>();
        if (names != null && availableModules != null) {
            for (String name : names) {
                for (Module module : availableModules) {
                    if (module.getName().equals(name)) {
                        modules.add(module);
                        break;
                    }
                }
            }
        }
        setValue(modules);
    }

    public int size() {
        return value.size();
    }

    public boolean isEmpty() {
        return value.isEmpty();
    }

    private static List<Module> normalize(List<Module> source) {
        if (source == null) return new ArrayList<>();
        List<Module> modules = new ArrayList<>();
        for (Module m : source) {
            if (m != null && !modules.contains(m)) modules.add(m);
        }
        return modules;
    }
}
