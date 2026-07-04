package com.github.epsilon.gui.dropdown.widget;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.assets.i18n.TranslateComponent;
import com.github.epsilon.gui.dropdown.DropdownScreen;
import com.github.epsilon.settings.impl.StorageBlockListSetting;

public class StorageBlockListSettingWidget extends AbstractSetSettingWidget<StorageBlockListSetting> {

    public StorageBlockListSettingWidget(StorageBlockListSetting setting) {
        super(setting);
    }

    @Override
    protected int elementCount() { return setting.size(); }

    @Override
    protected TranslateComponent labelComponent() { return EpsilonTranslations.Gui.LIST_STORAGE; }

    @Override
    protected void openPopup() { DropdownScreen.INSTANCE.openStorageBlockListSettingPopup(setting); }
}
