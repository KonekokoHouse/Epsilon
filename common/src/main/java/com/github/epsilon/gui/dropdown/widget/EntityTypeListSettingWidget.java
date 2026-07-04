package com.github.epsilon.gui.dropdown.widget;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.assets.i18n.TranslateComponent;
import com.github.epsilon.gui.dropdown.DropdownScreen;
import com.github.epsilon.settings.impl.EntityTypeListSetting;

public class EntityTypeListSettingWidget extends AbstractSetSettingWidget<EntityTypeListSetting> {

    public EntityTypeListSettingWidget(EntityTypeListSetting setting) {
        super(setting);
    }

    @Override
    protected int elementCount() { return setting.size(); }

    @Override
    protected TranslateComponent labelComponent() { return EpsilonTranslations.Gui.LIST_ENTITIES; }

    @Override
    protected void openPopup() { DropdownScreen.INSTANCE.openEntityTypeListSettingPopup(setting); }
}
