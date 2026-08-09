package com.github.epsilon.scripting.lua.i18n;

import com.github.epsilon.Constants;
import com.github.epsilon.assets.i18n.EpsilonLanguageManager;
import com.github.epsilon.holders.TranslateHolder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LuaTranslationCatalog implements AutoCloseable {
    private final String packageId;
    private final Path languageDirectory;
    private final List<LuaTranslateComponent> components = new ArrayList<>();
    private Map<String, String> english = Map.of();
    private Map<String, String> selected = Map.of();
    private String selectedCode = "";

    public LuaTranslationCatalog(String packageId, Path languageDirectory) {
        this.packageId = packageId;
        this.languageDirectory = languageDirectory;
        reload();
    }

    public synchronized LuaTranslateComponent create(String relativeKey, String fallback) {
        LuaTranslateComponent component = new LuaTranslateComponent(this, relativeKey, fallback);
        components.add(component);
        TranslateHolder.INSTANCE.registerTranslateComponent(component);
        return component;
    }

    public synchronized String resolve(String relativeKey, String fallback) {
        ensureCurrentLanguage();
        String translated = selected.get(relativeKey);
        if (translated == null) translated = english.get(relativeKey);
        return translated != null ? translated : formatFallback(fallback == null ? relativeKey : fallback);
    }

    public synchronized void reload() {
        selectedCode = EpsilonLanguageManager.INSTANCE.getSelectedLanguageCode();
        english = readLanguage("en_us");
        selected = "en_us".equals(selectedCode) ? english : readLanguage(selectedCode);
        for (LuaTranslateComponent component : components) component.clearCache();
    }

    String fullKey(String relativeKey) {
        return relativeKey == null || relativeKey.isBlank() ? "lua." + packageId : "lua." + packageId + "." + relativeKey;
    }

    private void ensureCurrentLanguage() {
        if (!selectedCode.equals(EpsilonLanguageManager.INSTANCE.getSelectedLanguageCode())) reload();
    }

    private Map<String, String> readLanguage(String code) {
        Path file = languageDirectory.resolve(code + ".json");
        if (!Files.isRegularFile(file)) return Map.of();
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (parsed == null || !parsed.isJsonObject()) throw new JsonParseException("语言文件根节点必须是 object");
            Map<String, String> values = new LinkedHashMap<>();
            readNode("", parsed.getAsJsonObject(), values);
            return Map.copyOf(values);
        } catch (IOException | RuntimeException failure) {
            Constants.LOGGER.error("读取 Lua 语言文件失败: {}", file, failure);
            return Map.of();
        }
    }

    private static void readNode(String prefix, JsonObject object, Map<String, String> output) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            if ("_value".equals(key)) {
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                    throw new JsonParseException("i18n _value 必须是 string: " + prefix);
                }
                output.put(prefix, value.getAsString());
                continue;
            }
            String childKey = prefix.isEmpty() ? key : prefix + "." + key;
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                output.put(childKey, value.getAsString());
            } else if (value.isJsonObject()) {
                readNode(childKey, value.getAsJsonObject(), output);
            } else {
                throw new JsonParseException("i18n 叶节点必须是 string: " + childKey);
            }
        }
    }

    private static String formatFallback(String value) {
        if (value == null || value.isBlank()) return "";
        String last = value.substring(value.lastIndexOf('.') + 1);
        StringBuilder output = new StringBuilder();
        for (String word : last.split(" +")) {
            if (word.isEmpty()) continue;
            if (!output.isEmpty()) output.append(' ');
            output.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return output.toString();
    }

    @Override
    public synchronized void close() {
        for (LuaTranslateComponent component : components) {
            TranslateHolder.INSTANCE.unregisterTranslateComponent(component);
        }
        components.clear();
    }
}
