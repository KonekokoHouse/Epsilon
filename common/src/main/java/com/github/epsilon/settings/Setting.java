package com.github.epsilon.settings;

import com.github.epsilon.assets.i18n.TranslateComponent;

import java.util.function.Consumer;

public abstract class Setting<V> {

    protected final String name;
    protected V value;
    protected V defaultValue;
    protected final Dependency dependency;
    protected Consumer<V> onChanged;
    protected boolean rootSetting;

    protected TranslateComponent translateComponent;

    public Setting(String name, Dependency dependency, Consumer<V> onChanged) {
        this.name = name;
        this.dependency = dependency;
        this.onChanged = onChanged;
    }

    public void initTranslateComponent(TranslateComponent component) {
        this.translateComponent = component;
    }

    public TranslateComponent getTranslateComponent() {
        return translateComponent;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return translateComponent != null ? translateComponent.getTranslatedName() : name;
    }

    public V getValue() {
        return value;
    }

    public void setValue(V value) {
        this.value = value;
        if (this.onChanged != null) this.onChanged.accept(value);
    }

    public void setValueSilently(V value) {
        this.value = value;
    }

    public void reset() {
        this.value = this.defaultValue;
    }

    public V getDefaultValue() {
        return defaultValue;
    }

    public boolean isAvailable() {
        return dependency != null && this.dependency.check();
    }

    @SuppressWarnings("unchecked")
    public <S extends Setting<V>> S rootSetting() {
        this.rootSetting = true;
        return (S) this;
    }

    public boolean isRootSetting() {
        return rootSetting;
    }

    @FunctionalInterface
    public interface Dependency {
        boolean check();
    }

    public Dependency getDependency() {
        return dependency;
    }

}
