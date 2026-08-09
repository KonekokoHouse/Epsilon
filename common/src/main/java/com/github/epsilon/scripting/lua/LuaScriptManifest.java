package com.github.epsilon.scripting.lua;

import com.github.epsilon.modules.Category;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public record LuaScriptManifest(
        int schema,
        int api,
        String id,
        String name,
        String description,
        String version,
        List<String> authors,
        String settingsEntry,
        List<ModuleSpec> modules
) {
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");

    public LuaScriptManifest {
        authors = authors == null ? List.of() : List.copyOf(authors);
        modules = modules == null ? List.of() : List.copyOf(modules);
    }

    public void validate() {
        if (schema != 1) throw new IllegalArgumentException("不支持的脚本 manifest schema: " + schema);
        if (api != 1) throw new IllegalArgumentException("不支持的脚本 API 版本: " + api);
        validateId("package", id);
        if (modules.isEmpty()) throw new IllegalArgumentException("脚本包必须声明至少一个 Module");

        Set<String> moduleIds = new HashSet<>();
        Set<String> entries = new HashSet<>();
        if (settingsEntry != null && !settingsEntry.isBlank()) entries.add(normalizeEntry(settingsEntry));
        for (ModuleSpec module : modules) {
            if (module == null) throw new IllegalArgumentException("Module 条目不能为空");
            validateId("module", module.id());
            if (!moduleIds.add(module.id())) throw new IllegalArgumentException("重复 Module ID: " + module.id());
            String entry = normalizeEntry(module.entry());
            if (!entries.add(entry)) throw new IllegalArgumentException("重复 entrypoint: " + entry);
            module.categoryValue();
        }
    }

    public String displayName() {
        return name == null || name.isBlank() ? id : name;
    }

    static String normalizeEntry(String entry) {
        if (entry == null || entry.isBlank()) throw new IllegalArgumentException("entrypoint 不能为空");
        String normalized = entry.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.contains(":")) {
            throw new IllegalArgumentException("entrypoint 必须是相对路径: " + entry);
        }
        if (!normalized.endsWith(".lua")) throw new IllegalArgumentException("entrypoint 必须是 .lua 文件: " + entry);
        for (String segment : normalized.split("/", -1)) {
            if (segment.isBlank() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("entrypoint 包含无效路径分量: " + entry);
            }
        }
        if (normalized.startsWith("lib/") || normalized.startsWith("lang/")) {
            throw new IllegalArgumentException("entrypoint 不能位于 lib/ 或 lang/: " + entry);
        }
        return normalized;
    }

    static void validateId(String kind, String id) {
        if (id == null || !ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("无效 " + kind + " ID: " + id);
        }
    }

    public record ModuleSpec(
            String id,
            String name,
            String entry,
            String category,
            boolean defaultEnabled,
            boolean defaultHidden
    ) {
        public String displayName() {
            return name == null || name.isBlank() ? id : name;
        }

        public Category categoryValue() {
            if (category == null || category.isBlank()) throw new IllegalArgumentException("Module category 不能为空: " + id);
            try {
                return Category.valueOf(category.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("未知 Module category: " + category, exception);
            }
        }
    }
}
