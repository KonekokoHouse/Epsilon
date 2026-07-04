package com.github.epsilon.gui.dropdown.widget;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.assets.i18n.TranslateComponent;
import com.github.epsilon.gui.dropdown.DropdownScreen;
import com.github.epsilon.settings.impl.ItemListSetting;

public class ItemListSettingWidget extends AbstractSetSettingWidget<ItemListSetting> {

    public ItemListSettingWidget(ItemListSetting setting) {
        super(setting);
    }

    @Override
    protected int elementCount() { return setting.size(); }

    @Override
    protected TranslateComponent labelComponent() { return EpsilonTranslations.Gui.LIST_ITEMS; }

    @Override
    protected void openPopup() { DropdownScreen.INSTANCE.openItemListSettingPopup(setting); }
}
