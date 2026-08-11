package com.github.epsilon.neoforge;

import com.github.epsilon.Constants;
import com.github.epsilon.EpsilonCommon;
import com.github.epsilon.addon.AddonBootstrap;
import com.github.epsilon.assets.i18n.LanguageReloadListener;
import com.github.epsilon.neoforge.addon.EpsilonAddonSetupEvent;
import com.github.epsilon.neoforge.addon.NeoForgeSelfAddonRegistrar;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.common.NeoForge;

@EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT)
public class EpsilonNeoForge {

    public static void init() {
        NeoForgeSelfAddonRegistrar.register();

        EpsilonAddonSetupEvent addonEvent = NeoForge.EVENT_BUS.post(new EpsilonAddonSetupEvent());
        AddonBootstrap.registerAddons(addonEvent.getAddons());

        EpsilonCommon.init();
    }

    @SubscribeEvent
    private static void onResourcesReload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new LanguageReloadListener());
    }

}
