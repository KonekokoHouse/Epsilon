package com.github.epsilon.assets.i18n;

import com.github.epsilon.Constants;
import com.github.epsilon.holders.HudElementHolder;
import com.github.epsilon.holders.ModuleHolder;
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

        for (TranslateComponent component : EpsilonTranslations.all()) {
            root.addProperty(component.getFullKey(), "");
        }

        for (Module module : ModuleHolder.INSTANCE.getModules()) {
            addModuleKeys(root, module);
        }

        for (Module module : HudElementHolder.INSTANCE.getElements()) {
            addModuleKeys(root, module);
        }

        final var file = new File(filePath);
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                Constants.LOGGER.warn("Failed to create i18n file: {}", filePath, e);
            }
        }

        try (FileWriter writer = new FileWriter(filePath)) {
            gson.toJson(root, writer);
            Constants.LOGGER.info("I18N file generated successfully at: {}", filePath);
        } catch (IOException e) {
            Constants.LOGGER.warn("Failed to write i18n file: {}", filePath, e);
        }
    }

    private static void addModuleKeys(JsonObject root, Module module) {
        if (module.translateComponent == null) return;
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

}
