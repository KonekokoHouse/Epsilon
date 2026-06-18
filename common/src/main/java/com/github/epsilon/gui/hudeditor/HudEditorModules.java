package com.github.epsilon.gui.hudeditor;

import com.github.epsilon.holders.ModuleHolder;
import com.github.epsilon.modules.HudModule;
import com.github.epsilon.modules.Module;

import java.util.ArrayList;
import java.util.List;

public class HudEditorModules {

    private HudEditorModules() {
    }

    public static List<HudModule> collectEnabledHudModules() {
        List<HudModule> hudModules = new ArrayList<>();
        List<Module> modules = ModuleHolder.INSTANCE.getModules();
        if (modules == null) {
            return hudModules;
        }

        for (Module module : modules) {
            if (module.isEnabled() && module instanceof HudModule hudModule) {
                hudModule.updateLayout();
                hudModules.add(hudModule);
            }
        }

        return hudModules;
    }

    public static List<HudModule> collectHudModules() {
        List<HudModule> hudModules = new ArrayList<>();
        List<Module> modules = ModuleHolder.INSTANCE.getModules();
        if (modules == null) {
            return hudModules;
        }

        for (Module module : modules) {
            if (module instanceof HudModule hudModule) {
                hudModule.updateLayout();
                hudModules.add(hudModule);
            }
        }

        return hudModules;
    }

    public static HudModule findTopmost(List<HudModule> hudModules, double mouseX, double mouseY) {
        // Later modules render last, so they win hit testing in overlap cases.
        for (int i = hudModules.size() - 1; i >= 0; i--) {
            HudModule hudModule = hudModules.get(i);
            if (hudModule.contains(mouseX, mouseY)) {
                return hudModule;
            }
        }

        return null;
    }

}
