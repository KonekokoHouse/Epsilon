package com.github.epsilon.holders;

import com.github.epsilon.assets.i18n.EpsilonTranslateComponent;
import com.github.epsilon.elements.HudModule;
import com.github.epsilon.elements.impl.*;
import com.github.epsilon.elements.impl.notification.Notifications;
import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.Render2DEvent;
import com.github.epsilon.gui.hudeditor.HudEditorScreen;
import com.github.epsilon.gui.theme.EpsilonUiTheme;
import com.github.epsilon.modules.impl.ClientSetting;
import com.github.epsilon.utils.client.ClientUtils;
import com.github.slmpc.lumingraphics.mc.v2612.runtime.MinecraftUiRuntime2612;
import com.github.slmpc.lumingraphics.ui.scene.UiLayer;
import com.github.slmpc.lumingraphics.ui.scene.UiScene;

import java.util.ArrayList;
import java.util.List;

import static com.github.epsilon.Constants.LOGGER;

import static com.github.epsilon.Constants.mc;

public class HudElementHolder {

    public static final HudElementHolder INSTANCE = new HudElementHolder();

    private HudElementHolder() {
        EventBus.INSTANCE.subscribe(this);
    }

    private final List<HudModule> elements = new ArrayList<>();
    private UiScene scene;
    private MinecraftUiRuntime2612 sceneRuntime;

    public void initElements() {
        addElement(Notifications.INSTANCE);
        addElement(BPS.INSTANCE);
        addElement(MTF.INSTANCE);
        addElement(Inventory.INSTANCE);
        addElement(ModuleList.INSTANCE);
        addElement(Potions.INSTANCE);
        addElement(ScaffoldBlock.INSTANCE);
        addElement(TargetHUD.INSTANCE);
        addElement(Watermark.INSTANCE);
    }

    private void addElement(HudModule module) {
        elements.add(module);
        module.setAddonId("epsilon");
        module.initI18n(EpsilonTranslateComponent.create("elements", module.getName().toLowerCase()));
    }

    public List<HudModule> getElements() {
        return elements;
    }

    @EventHandler
    private void onRender2D(Render2DEvent.HUD event) {
        if (ClientUtils.isLoading() || mc.level == null || mc.screen instanceof HudEditorScreen) return;

        try {
            MinecraftUiRuntime2612 runtime = MinecraftUiRuntime2612.current();
            configureFonts(runtime);
            runtime.render(scene(runtime), activeScene -> {
                for (HudModule element : elements) {
                    if (!element.isEnabled()) continue;
                    try {
                        renderElement(activeScene, element);
                    } catch (RuntimeException failure) {
                        LOGGER.error("HUD content '{}' failed", element.getName(), failure);
                    }
                }
            });
        } catch (RuntimeException failure) {
            releaseScene(failure);
            LOGGER.error("HUD frame failed", failure);
        }

        for (HudModule element : elements) {
            if (!element.isEnabled()) continue;
            try {
                element.renderOverlay(event.getGuiGraphics(), mc.getDeltaTracker());
            } catch (RuntimeException failure) {
                LOGGER.error("HUD overlay '{}' failed", element.getName(), failure);
            }
        }
    }

    private UiScene scene(MinecraftUiRuntime2612 runtime) {
        if (scene == null || sceneRuntime != runtime) {
            scene = runtime.createScene(EpsilonUiTheme.lumin());
            sceneRuntime = runtime;
        }
        return scene;
    }

    private void releaseScene(RuntimeException frameFailure) {
        UiScene failedScene = scene;
        scene = null;
        sceneRuntime = null;
        if (failedScene == null) return;
        try {
            failedScene.close();
        } catch (RuntimeException cleanupFailure) {
            frameFailure.addSuppressed(cleanupFailure);
        }
    }

    private void configureFonts(MinecraftUiRuntime2612 runtime) {
        ClientSetting.INSTANCE.configureMinecraftFonts(runtime);
    }

    private static void renderElement(UiScene scene, HudModule element) {
        element.updateLayout();
        element.renderWithBatch(mc.getDeltaTracker(), scene.batch(UiLayer.CONTENT));
    }

}
