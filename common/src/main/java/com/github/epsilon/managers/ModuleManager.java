package com.github.epsilon.managers;

import com.github.epsilon.assets.i18n.EpsilonTranslateComponent;
import com.github.epsilon.assets.i18n.TranslateComponent;
import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.KeyPressEvent;
import com.github.epsilon.events.impl.MousePressEvent;
import com.github.epsilon.events.impl.Render2DEvent;
import com.github.epsilon.gui.dropdown.DropdownScreen;
import com.github.epsilon.gui.hudeditor.HudEditorScreen;
import com.github.epsilon.gui.panel.PanelScreen;
import com.github.epsilon.managers.sound.SoundKey;
import com.github.epsilon.managers.sound.SoundManager;
import com.github.epsilon.modules.HudModule;
import com.github.epsilon.modules.Module;
import com.github.epsilon.modules.impl.ClientSetting;
import com.github.epsilon.modules.impl.combat.*;
import com.github.epsilon.modules.impl.hud.*;
import com.github.epsilon.modules.impl.hud.notification.NotificationsHUD;
import com.github.epsilon.modules.impl.movement.*;
import com.github.epsilon.modules.impl.player.*;
import com.github.epsilon.modules.impl.render.*;
import com.github.epsilon.utils.client.ClientUtils;
import com.github.epsilon.utils.client.KeybindUtils;
import com.github.epsilon.utils.player.ChatUtils;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.DeltaTracker;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static com.github.epsilon.Constants.mc;

public class ModuleManager {

    public static final ModuleManager INSTANCE = new ModuleManager();

    private List<Module> modules;

    private ModuleManager() {
        EventBus.INSTANCE.subscribe(this);
    }

    public void initModules() {
        modules = new ArrayList<>(List.of(

                ClientSetting.INSTANCE,

                // Combat
                AimAssist.INSTANCE,
                AnchorBlast.INSTANCE,
                AntiBot.INSTANCE,
                AutoClicker.INSTANCE,
                AutoDtap.INSTANCE,
                AutoHitCrystal.INSTANCE,
                AutoMend.INSTANCE,
                AutoTotem.INSTANCE,
                AutoWeapon.INSTANCE,
                ZealotCrystalPlus.INSTANCE,
                CrystalAura.INSTANCE,
                CrystalBlocker.INSTANCE,
                FeetTrap.INSTANCE,
                DoubleAnchor.INSTANCE,
                HoverTotem.INSTANCE,
                KillAura.INSTANCE,
                KeyPearl.INSTANCE,
                MaceAura.INSTANCE,
                PacketMine.INSTANCE,
                SafeAnchor.INSTANCE,
                SafeCrystal.INSTANCE,
                SilentAim.INSTANCE,
                SpearKill.INSTANCE,
                TriggerBot.INSTANCE,

                // Player
                AutoFirework.INSTANCE,
                AutoKouZi.INSTANCE,
                AutoTool.INSTANCE,
                BreakCooldown.INSTANCE,
                Disabler.INSTANCE,
                ElytraSwap.INSTANCE,
                FakePlayer.INSTANCE,
                GhostHand.INSTANCE,
                InvManager.INSTANCE,
                JumpCooldown.INSTANCE,
                MultiTask.INSTANCE,
                NoRotate.INSTANCE,
                PacketEat.INSTANCE,
                SoundFX.INSTANCE,
                Stealer.INSTANCE,
                UseCooldown.INSTANCE,

                // Movement
                AutoSprint.INSTANCE,
                Blink.INSTANCE,
                ElytraFly.INSTANCE,
                FastWeb.INSTANCE,
                GUIMove.INSTANCE,
                MovementFix.INSTANCE,
                NoFall.INSTANCE,
                NoSlow.INSTANCE,
                Phase.INSTANCE,
                SafeWalk.INSTANCE,
                Scaffold.INSTANCE,
                Stuck.INSTANCE,
                VClip.INSTANCE,
                Velocity.INSTANCE,

                // Render
                AntiAlias.INSTANCE,
                AspectRatio.INSTANCE,
                CameraClip.INSTANCE,
                Chams.INSTANCE,
                ESP.INSTANCE,
                Filter.INSTANCE,
                Fullbright.INSTANCE,
                HandsView.INSTANCE,
                Hat.INSTANCE,
                NameTags.INSTANCE,
                NoRender.INSTANCE,
                //PopChams.INSTANCE,

                // Hud
                NotificationsHUD.INSTANCE,
                BPSHUD.INSTANCE,
                InventoryHUD.INSTANCE,
                ModuleListHUD.INSTANCE,
                PotionHUD.INSTANCE,
                ScaffoldBlockHUD.INSTANCE,
                TargetHUD.INSTANCE,
                WatermarkHUD.INSTANCE

        ));

        // Initialize i18n for all epsilon modules
        for (Module module : modules) {
            module.setAddonId("epsilon");
            module.initI18n(EpsilonTranslateComponent.create("modules", module.getName().toLowerCase()));
        }
    }

    public void registerAddonModule(String addonId, Module module, TranslateComponent moduleComponent) {
        module.setAddonId(addonId);
        module.initI18n(moduleComponent);
        modules.add(module);
    }

    public List<Module> getModules() {
        return modules;
    }

    @EventHandler
    private void onRender2D(Render2DEvent.HUD event) {
        if (ClientUtils.isLoading() || mc.level == null || mc.screen instanceof HudEditorScreen) return;

        for (Module m : modules) {
            if (m instanceof HudModule module && module.isEnabled()) {
                DeltaTracker delta = mc.getDeltaTracker();
                module.updateLayout();
                module.render(event.getGuiGraphics(), delta);
            }
        }
    }

    @EventHandler
    private void onKeyPress(KeyPressEvent event) {
        if (mc.level == null || mc.screen != null || event.getKey() == GLFW.GLFW_KEY_UNKNOWN) return;

        int keyCode = event.getKey();
        int action = event.getAction();

        ClientSetting cs = ClientSetting.INSTANCE;
        if (keyCode == cs.guiKeybind.getValue() && action == InputConstants.PRESS) {
            mc.setScreen(switch (ClientSetting.INSTANCE.guiMode.getValue()) {
                case Panel -> PanelScreen.INSTANCE;
                case Dropdown -> DropdownScreen.INSTANCE;
            });
        }

        dispatchKeyBind(keyCode, action);
    }

    @EventHandler
    private void onMousePress(MousePressEvent event) {
        if (mc.level != null && mc.screen == null) {
            dispatchKeyBind(KeybindUtils.encodeMouseButton(event.getButton()), event.getAction());
        }
    }

    private void dispatchKeyBind(int keyCode, int action) {
        boolean isPress = action == InputConstants.PRESS;
        boolean isRelease = action == InputConstants.RELEASE;

        List<Module> affectedModules = new ArrayList<>();
        boolean hasEnabling = false;

        for (Module module : modules) {
            if (module.getKeyBind() != keyCode) continue;

            if (module.getBindMode() == Module.BindMode.Toggle && isPress) {
                if (!module.isEnabled()) {
                    hasEnabling = true;
                }
                affectedModules.add(module);
            } else if (module.getBindMode() == Module.BindMode.Hold) {
                if (isPress && !module.isEnabled()) {
                    hasEnabling = true;
                    affectedModules.add(module);
                } else if (isRelease && module.isEnabled()) {
                    affectedModules.add(module);
                }
            }
        }

        for (Module module : affectedModules) {
            if (module.getBindMode() == Module.BindMode.Toggle) {
                module.toggle();
            } else if (module.getBindMode() == Module.BindMode.Hold) {
                if (isPress && !module.isEnabled()) {
                    module.setEnabled(true);
                } else if (isRelease && module.isEnabled()) {
                    module.setEnabled(false);
                }
            }
            if (ClientSetting.INSTANCE.chatNotify.getValue()) {
                ChatUtils.addChatMessage(module.getTranslatedName() + " is now " + (module.isEnabled() ? "enabled" : "disabled"));
            }
        }

        if (!affectedModules.isEmpty() && ClientSetting.INSTANCE.soundNotify.getValue()) {
            if (hasEnabling) {
                SoundManager.INSTANCE.playInUi(SoundKey.ENABLE);
            } else {
                SoundManager.INSTANCE.playInUi(SoundKey.DISABLE);
            }
        }
    }

}
