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
import com.github.slmpc.lumingraphics.ui.tree.UiTree;
import net.minecraft.client.DeltaTracker;

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

        DeltaTracker deltaTracker = mc.getDeltaTracker();
        try {
            MinecraftUiRuntime2612 runtime = MinecraftUiRuntime2612.current();
            configureFonts(runtime);
            runtime.render(scene(runtime), activeScene -> submitHudTree(activeScene, 0, deltaTracker));
        } catch (RuntimeException failure) {
            releaseScene(failure);
            LOGGER.error("HUD frame failed", failure);
        }

        for (HudModule element : elements) {
            if (!element.isEnabled()) continue;
            try {
                element.renderOverlay(event.getGuiGraphics(), deltaTracker);
            } catch (RuntimeException failure) {
                LOGGER.error("HUD overlay '{}' failed", element.getName(), failure);
            }
        }
    }

    private UiScene scene(MinecraftUiRuntime2612 runtime) {
        if (scene != null && sceneRuntime != runtime) {
            releaseScene();
        }
        if (scene == null) {
            scene = runtime.createScene(EpsilonUiTheme.lumin());
            sceneRuntime = runtime;
        }
        return scene;
    }

    private void releaseScene() {
        UiScene previous = scene;
        scene = null;
        sceneRuntime = null;
        if (previous != null) previous.close();
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

    /**
     * 构建并提交独立 HUD 树；调用方的 GUI 树不会接收任何 HUD 节点。
     */
    public void submitHudTree(UiScene targetScene, int relativeLayer, DeltaTracker deltaTracker) {
        targetScene.submit(UiLayer.CONTENT, relativeLayer, buildHudTree(deltaTracker));
    }

    private UiTree buildHudTree(DeltaTracker deltaTracker) {
        UiTree.Scope hudScope = new UiTree.Scope();
        for (HudModule element : elements) {
            if (!element.isEnabled()) continue;
            try {
                element.updateLayout();
                hudScope.layer(0, elementScope -> element.appendToTree(deltaTracker, elementScope));
            } catch (RuntimeException failure) {
                LOGGER.error("HUD content '{}' failed", element.getName(), failure);
            }
        }
        return UiTree.from(hudScope);
    }

}
