package com.github.epsilon.gui.dropdown.widget;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.assets.i18n.TranslateComponent;
import com.github.epsilon.gui.dropdown.DropdownScreen;
import com.github.epsilon.settings.impl.ModuleListSetting;

public class ModuleListSettingWidget extends AbstractSetSettingWidget<ModuleListSetting> {

    public ModuleListSettingWidget(ModuleListSetting setting) {
        super(setting);
    }

    @Override
    protected int elementCount() { return setting.size(); }

    @Override
    protected TranslateComponent labelComponent() { return EpsilonTranslations.Gui.LIST_MODULES; }

    @Override
    protected void openPopup() { DropdownScreen.INSTANCE.openModuleListSettingPopup(setting); }
}
