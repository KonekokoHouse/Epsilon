package com.github.epsilon.assets.i18n;

import com.github.epsilon.managers.ModuleManager;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.Setting;
import com.github.epsilon.settings.SettingGroup;
import com.github.epsilon.settings.impl.EnumSetting;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class I18NFileGenerator {

    private static final String PREFIX = "epsilon.";

    public static void generate(String filePath) {
        JsonObject root = new JsonObject();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        for (Category category : Category.values()) {
            String catKey = PREFIX + "categories." + category.toString().toLowerCase();
            root.addProperty(catKey, "");
        }

        root.addProperty(PREFIX + "keybind.none", "");
        root.addProperty(PREFIX + "keybind.toggle", "");
        root.addProperty(PREFIX + "keybind.hold", "");

        root.addProperty(PREFIX + "module.visible", "");
        root.addProperty(PREFIX + "module.hidden", "");
        root.addProperty(PREFIX + "module.chat_notify", "");
        root.addProperty(PREFIX + "module.state.enabled", "");
        root.addProperty(PREFIX + "module.state.disabled", "");

        root.addProperty(PREFIX + "gui.search", "");
        root.addProperty(PREFIX + "gui.clientsettings", "");
        root.addProperty(PREFIX + "gui.no_module", "");

        root.addProperty(PREFIX + "gui.tab.general", "");
        root.addProperty(PREFIX + "gui.tab.friend", "");
        root.addProperty(PREFIX + "gui.tab.config", "");
        root.addProperty(PREFIX + "gui.tab.addon", "");

        root.addProperty(PREFIX + "gui.friend.empty", "");
        root.addProperty(PREFIX + "gui.friend.input.placeholder", "");

        root.addProperty(PREFIX + "gui.config.input.placeholder", "");
        root.addProperty(PREFIX + "gui.config.current", "");
        root.addProperty(PREFIX + "gui.config.switch_hint", "");
        root.addProperty(PREFIX + "gui.config.empty", "");
        root.addProperty(PREFIX + "gui.config.action.saveas", "");
        root.addProperty(PREFIX + "gui.config.action.reload", "");
        root.addProperty(PREFIX + "gui.config.action.export", "");
        root.addProperty(PREFIX + "gui.config.action.import", "");
        root.addProperty(PREFIX + "gui.config.action.new", "");
        root.addProperty(PREFIX + "gui.config.action.open_folder", "");
        root.addProperty(PREFIX + "gui.config.delete.confirm.title", "");
        root.addProperty(PREFIX + "gui.config.delete.confirm.message", "");
        root.addProperty(PREFIX + "gui.config.delete.confirm.confirm", "");
        root.addProperty(PREFIX + "gui.config.delete.confirm.cancel", "");
        root.addProperty(PREFIX + "gui.config.error.title", "");
        root.addProperty(PREFIX + "gui.config.error.ok", "");
        root.addProperty(PREFIX + "gui.config.error.save", "");
        root.addProperty(PREFIX + "gui.config.error.reload", "");
        root.addProperty(PREFIX + "gui.config.error.export", "");
        root.addProperty(PREFIX + "gui.config.error.import", "");
        root.addProperty(PREFIX + "gui.config.error.open_folder", "");
        root.addProperty(PREFIX + "gui.config.error.switch", "");
        root.addProperty(PREFIX + "gui.config.error.delete", "");
        root.addProperty(PREFIX + "gui.config.error.delete_last", "");
        root.addProperty(PREFIX + "gui.config.export.success.title", "");
        root.addProperty(PREFIX + "gui.config.export.success.message", "");

        root.addProperty(PREFIX + "gui.dropdown.status.saved", "");
        root.addProperty(PREFIX + "gui.dropdown.status.reloaded", "");
        root.addProperty(PREFIX + "gui.dropdown.status.exported", "");
        root.addProperty(PREFIX + "gui.dropdown.status.imported", "");
        root.addProperty(PREFIX + "gui.dropdown.status.deleted", "");
        root.addProperty(PREFIX + "gui.dropdown.status.switched", "");
        root.addProperty(PREFIX + "gui.dropdown.status.created", "");

        root.addProperty(PREFIX + "gui.modules", "");

        root.addProperty(PREFIX + "gui.addon.empty", "");
        root.addProperty(PREFIX + "gui.addon.no_settings", "");
        root.addProperty(PREFIX + "gui.addon.info.id", "");
        root.addProperty(PREFIX + "gui.addon.info.version", "");
        root.addProperty(PREFIX + "gui.addon.info.authors", "");
        root.addProperty(PREFIX + "gui.addon.info.modules", "");

        root.addProperty(PREFIX + "gui.inspector", "");
        root.addProperty(PREFIX + "gui.inspector.select", "");

        for (Module module : ModuleManager.INSTANCE.getModules()) {
            if (module.translateComponent == null) continue;
            String moduleKey = module.translateComponent.getFullKey();
            root.addProperty(moduleKey, "");

            for (SettingGroup group : module.getSettingGroups()) {
                TranslateComponent groupComp = group.getTranslateComponent();
                if (groupComp == null) continue;
                root.addProperty(groupComp.getFullKey(), "");
            }

            for (Setting<?> setting : module.getSettings()) {
                TranslateComponent settingComp = setting.getTranslateComponent();
                if (settingComp == null) continue;
                String settingKey = settingComp.getFullKey();
                root.addProperty(settingKey, "");

                if (setting instanceof EnumSetting<?> enumSetting) {
                    for (final var mode : enumSetting.getModes()) {
                        root.addProperty(settingKey + "." + mode.toString().toLowerCase(), "");
                    }
                }
            }
        }

        // NotificationManager 手动创建的 enabled/disabled 键
        root.addProperty(PREFIX + "modules.notifications hud.enabled", "");
        root.addProperty(PREFIX + "modules.notifications hud.disabled", "");

        final var file = new File(filePath);
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try (FileWriter writer = new FileWriter(filePath)) {
            gson.toJson(root, writer);
            System.out.println("I18N file generated successfully at: " + filePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
