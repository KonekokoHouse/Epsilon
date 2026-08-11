package com.github.epsilon.settings.impl;

import com.github.epsilon.assets.i18n.TranslateComponent;
import com.github.epsilon.settings.Setting;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class ChoiceSetting extends Setting<String> {
    private final List<String> choices;
    private final Map<String, TranslateComponent> choiceTranslations = new LinkedHashMap<>();

    public ChoiceSetting(String name, String defaultValue, List<String> choices,
                         Dependency dependency, Consumer<String> onChanged) {
        super(name, dependency, onChanged);
        if (choices == null || choices.isEmpty()) throw new IllegalArgumentException("ChoiceSetting choices 不能为空");
        if (choices.stream().anyMatch(choice -> choice == null || choice.isBlank())) {
            throw new IllegalArgumentException("ChoiceSetting choice 不能为空");
        }
        if (choices.stream().distinct().count() != choices.size()) throw new IllegalArgumentException("ChoiceSetting choice 不能重复");
        if (!choices.contains(defaultValue)) throw new IllegalArgumentException("ChoiceSetting default 不在 choices 中");
        this.choices = List.copyOf(choices);
        this.value = defaultValue;
        this.defaultValue = defaultValue;
    }

    @Override
    public void initTranslateComponent(TranslateComponent component) {
        super.initTranslateComponent(component);
        choiceTranslations.clear();
        for (String choice : choices) choiceTranslations.put(choice, component.createChild(choice.toLowerCase()));
    }

    @Override
    public void setValue(String value) {
        if (!choices.contains(value)) throw new IllegalArgumentException("未知 choice: " + value);
        super.setValue(value);
    }

    @Override
    public void setValueSilently(String value) {
        if (!choices.contains(value)) throw new IllegalArgumentException("未知 choice: " + value);
        super.setValueSilently(value);
    }

    public List<String> getChoices() {
        return choices;
    }

    public int getChoiceIndex() {
        return choices.indexOf(value);
    }

    public String getTranslatedValue() {
        return getTranslatedChoice(value);
    }

    public String getTranslatedChoice(String choice) {
        TranslateComponent component = choiceTranslations.get(choice);
        return component == null ? choice : component.getTranslatedName();
    }
}
