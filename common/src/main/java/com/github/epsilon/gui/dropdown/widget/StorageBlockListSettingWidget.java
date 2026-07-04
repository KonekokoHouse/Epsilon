package com.github.epsilon.gui.dropdown.widget;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.assets.i18n.TranslateComponent;
import com.github.epsilon.gui.dropdown.DropdownScreen;
import com.github.epsilon.settings.impl.RegistryListSetting;

public class StorageBlockListSettingWidget extends AbstractSetSettingWidget<RegistryListSetting<?>> {
    public StorageBlockListSettingWidget(RegistryListSetting<?> s) { super(s); }
    protected int elementCount() { return setting.size(); }
    protected TranslateComponent labelComponent() { return EpsilonTranslations.Gui.LIST_STORAGE; }
    @SuppressWarnings("unchecked")
    protected void openPopup() { DropdownScreen.INSTANCE.openStorageBlockListSettingPopup((RegistryListSetting) setting); }
}
