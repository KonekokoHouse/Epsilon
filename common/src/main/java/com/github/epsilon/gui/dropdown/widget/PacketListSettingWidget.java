package com.github.epsilon.gui.dropdown.widget;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.assets.i18n.TranslateComponent;
import com.github.epsilon.gui.dropdown.DropdownScreen;
import com.github.epsilon.settings.impl.PacketListSetting;

public class PacketListSettingWidget extends AbstractSetSettingWidget<PacketListSetting> {

    public PacketListSettingWidget(PacketListSetting setting) {
        super(setting);
    }

    @Override
    protected int elementCount() { return setting.size(); }

    @Override
    protected TranslateComponent labelComponent() { return EpsilonTranslations.Gui.LIST_PACKETS; }

    @Override
    protected void openPopup() { DropdownScreen.INSTANCE.openPacketListSettingPopup(setting); }
}
