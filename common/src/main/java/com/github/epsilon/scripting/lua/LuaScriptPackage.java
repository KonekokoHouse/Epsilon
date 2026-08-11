package com.github.epsilon.scripting.lua;

import com.github.epsilon.Constants;
import com.github.epsilon.holders.ConfigHolder;
import com.github.epsilon.holders.ModuleHolder;
import com.github.epsilon.settings.Setting;
import com.github.epsilon.settings.SettingGroup;
import com.github.epsilon.settings.SettingHost;
import com.github.epsilon.settings.ExternalConfigState;
import com.github.epsilon.scripting.lua.i18n.LuaTranslateComponent;
import com.github.epsilon.scripting.lua.i18n.LuaTranslationCatalog;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LuaScriptPackage implements SettingHost, ExternalConfigState, AutoCloseable {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Path directory;
    private final LuaScriptManifest manifest;
    private final String ownerId;
    private final List<Setting<?>> settings = new ArrayList<>();
    private final List<SettingGroup> settingGroups = new ArrayList<>();
    private final List<LuaModule> modules = new ArrayList<>();
    private final List<ModuleHolder.ExternalModuleRegistration> moduleRegistrations = new ArrayList<>();
    private final Map<String, Boolean> desiredEnabled = new LinkedHashMap<>();
    private final Map<String, Boolean> enabledSnapshot = new LinkedHashMap<>();
    private final LuaStorage packageStorage = new LuaStorage();
    private LuaRuntime settingsRuntime;
    private ConfigHolder.ExternalSettingHostRegistration settingHostRegistration;
    private final LuaTranslationCatalog translations;
    private final LuaTranslateComponent nameComponent;
    private final LuaTranslateComponent descriptionComponent;
    private boolean closed;
    private boolean prepared;
    private boolean registered;
    private boolean packageEnabled = true;

    public LuaScriptPackage(Path directory, LuaScriptManifest manifest) {
        this.directory = directory.toAbsolutePath().normalize();
        this.manifest = manifest;
        ownerId = "lua." + manifest.id();
        translations = new LuaTranslationCatalog(manifest.id(), this.directory.resolve("lang"));
        nameComponent = translations.create("", manifest.displayName());
        descriptionComponent = translations.create("description", manifest.description() == null ? "" : manifest.description());
    }

    public void load() throws IOException {
        prepare();
        register();
    }

    /** 执行所有可信脚本但不发布 Module/SettingHost，用于热重载候选构建。 */
    public void prepare() throws IOException {
        if (prepared) return;
        manifest.validate();
        Path libDirectory = directory.resolve("lib");
        Files.createDirectories(libDirectory);

        try {
            loadPackageState();
            loadGlobalSettings(libDirectory);
            initGlobalSettingI18n();
            ConfigHolder.INSTANCE.hydrateExternalSettingHost(ownerId, this);
            loadModules(libDirectory);
            prepared = true;
        } catch (Throwable failure) {
            close();
            if (failure instanceof IOException ioException) throw ioException;
            if (failure instanceof RuntimeException runtimeException) throw runtimeException;
            throw new RuntimeException("加载 Lua 脚本包失败: " + manifest.id(), failure);
        }
    }

    /** 将已完整构建的候选包发布到 Epsilon registry。 */
    public void register() {
        if (!prepared) throw new IllegalStateException("Lua package 尚未 prepare: " + manifest.id());
        if (registered) return;
        settingHostRegistration = ConfigHolder.INSTANCE.registerExternalSettingHost(ownerId, this);
        try {
            for (LuaModule module : modules) {
                moduleRegistrations.add(ModuleHolder.INSTANCE.registerExternal(ownerId, module,
                        translations.create("modules." + module.getModuleId(), module.getName())));
            }
            registered = true;
            applyPackageEnabledState();
        } catch (Throwable failure) {
            for (int index = moduleRegistrations.size() - 1; index >= 0; index--) moduleRegistrations.get(index).close();
            moduleRegistrations.clear();
            if (settingHostRegistration != null) {
                settingHostRegistration.close();
                settingHostRegistration = null;
            }
            throw failure;
        }
    }

    public void replaceRegistration(LuaScriptPackage previous) {
        if (!prepared) throw new IllegalStateException("Lua package 尚未 prepare: " + manifest.id());
        if (!ownerId.equals(previous.ownerId) || !previous.registered) {
            throw new IllegalArgumentException("只能替换同 owner 的已注册 Lua package");
        }
        settingHostRegistration = ConfigHolder.INSTANCE.replaceExternalSettingHost(ownerId, previous, this);
        moduleRegistrations.addAll(ModuleHolder.INSTANCE.replaceExternal(ownerId, modules,
                module -> translations.create("modules." + module.getModuleId(), module.getName())));
        registered = true;
        previous.registered = false;
        applyPackageEnabledState();
    }

    private void loadGlobalSettings(Path libDirectory) throws IOException {
        String entry = manifest.settingsEntry();
        if (entry == null || entry.isBlank()) return;
        settingsRuntime = new LuaRuntime(ownerId + ":settings", libDirectory);
        LuaTable epsilonApi = createPackageApi(settingsRuntime);
        settingsRuntime.set("epsilon", epsilonApi);
        settingsRuntime.set("addon", LuaSettingApi.create(settingsRuntime, this, true, ownerId));
        settingsRuntime.execute(resolveEntrypoint(entry));
        settingsRuntime.closeDeclarations();
    }

    private void loadModules(Path libDirectory) throws IOException {
        for (LuaScriptManifest.ModuleSpec spec : manifest.modules()) {
            LuaModule module = new LuaModule(spec.id(), spec.displayName(), spec.categoryValue(),
                    spec.defaultEnabled(), spec.defaultHidden());
            module.setAddonId(ownerId);
            LuaRuntime runtime = new LuaRuntime(ownerId + ":" + spec.id(), libDirectory);
            module.attachRuntime(runtime);
            runtime.set("epsilon", createPackageApi(runtime));
            runtime.set("addon", LuaSettingApi.create(runtime, this, false, ownerId));
            runtime.set("module", LuaModuleApi.create(runtime, module));
            runtime.execute(resolveEntrypoint(spec.entry()));
            runtime.closeDeclarations();

            modules.add(module);
            desiredEnabled.put(spec.id(), ConfigHolder.INSTANCE.hydrateModule(module, spec.defaultEnabled()));
        }
    }

    private LuaTable createPackageApi(LuaRuntime runtime) {
        LuaTable api = new LuaTable();
        api.set("id", LuaValue.valueOf(manifest.id()));
        api.set("version", LuaValue.valueOf(manifest.version() == null ? "" : manifest.version()));
        api.set("directory", LuaValue.valueOf(directory.toString()));
        api.set("packageStorage", packageStorage.createApi(runtime));
        return api;
    }

    private Path packageStateFile() {
        return ConfigHolder.INSTANCE.getActiveConfigStorageDir().resolve(ownerId).resolve("package-state.json");
    }

    static boolean readEnabledState(String packageId) {
        Path file = ConfigHolder.INSTANCE.getActiveConfigStorageDir()
                .resolve("lua." + packageId).resolve("package-state.json");
        if (!Files.isRegularFile(file)) return true;
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (parsed != null && parsed.isJsonObject()) {
                JsonElement enabled = parsed.getAsJsonObject().get("enabled");
                if (enabled != null && enabled.isJsonPrimitive()) return enabled.getAsBoolean();
            }
        } catch (Exception failure) {
            Constants.LOGGER.error("读取 Lua package 启用状态失败: {}", file, failure);
        }
        return true;
    }

    static void writeEnabledState(String packageId, boolean enabled) throws IOException {
        Path file = ConfigHolder.INSTANCE.getActiveConfigStorageDir()
                .resolve("lua." + packageId).resolve("package-state.json");
        JsonObject state = new JsonObject();
        if (Files.isRegularFile(file)) {
            try {
                JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                if (parsed != null && parsed.isJsonObject()) state = parsed.getAsJsonObject();
            } catch (RuntimeException failure) {
                Constants.LOGGER.warn("重建损坏的 Lua package state: {}", file, failure);
            }
        }
        state.addProperty("enabled", enabled);
        Files.createDirectories(file.getParent());
        Files.writeString(file, GSON.toJson(state), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    private void loadPackageState() {
        loadExternalState(packageStateFile().getParent());
    }

    @Override
    public synchronized void loadExternalState(Path ownerDirectory) {
        Path file = ownerDirectory.resolve("package-state.json");
        packageStorage.clear();
        enabledSnapshot.clear();
        packageEnabled = true;
        if (!Files.isRegularFile(file)) return;
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (parsed != null && parsed.isJsonObject()) {
                JsonElement storage = parsed.getAsJsonObject().get("storage");
                if (storage != null && storage.isJsonObject()) packageStorage.load(storage.getAsJsonObject());
                JsonElement enabled = parsed.getAsJsonObject().get("enabled");
                if (enabled != null && enabled.isJsonPrimitive()) packageEnabled = enabled.getAsBoolean();
                JsonElement snapshot = parsed.getAsJsonObject().get("enabledModules");
                if (snapshot != null && snapshot.isJsonObject()) {
                    for (var entry : snapshot.getAsJsonObject().entrySet()) {
                        if (entry.getValue().isJsonPrimitive()) enabledSnapshot.put(entry.getKey(), entry.getValue().getAsBoolean());
                    }
                }
                if (registered && !packageEnabled) {
                    for (LuaModule module : modules) module.setEnabled(false);
                }
            }
        } catch (Exception failure) {
            Constants.LOGGER.error("读取 Lua package state 失败: {}", file, failure);
        }
    }

    private void savePackageState() {
        saveExternalState(packageStateFile().getParent());
    }

    @Override
    public synchronized void saveExternalState(Path ownerDirectory) {
        Path file = ownerDirectory.resolve("package-state.json");
        try {
            Files.createDirectories(file.getParent());
            JsonObject state = new JsonObject();
            state.add("storage", packageStorage.toJson());
            state.addProperty("enabled", packageEnabled);
            JsonObject snapshot = new JsonObject();
            enabledSnapshot.forEach(snapshot::addProperty);
            state.add("enabledModules", snapshot);
            Files.writeString(file, GSON.toJson(state), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException failure) {
            Constants.LOGGER.error("写入 Lua package state 失败: {}", file, failure);
        }
    }

    private Path resolveEntrypoint(String entry) throws IOException {
        String normalizedEntry = LuaScriptManifest.normalizeEntry(entry);
        Path resolved = directory.resolve(normalizedEntry).normalize();
        if (!resolved.startsWith(directory)) throw new IOException("entrypoint 越过脚本包目录: " + entry);
        if (!Files.isRegularFile(resolved)) throw new IOException("entrypoint 不存在: " + resolved);
        return resolved;
    }

    private void initGlobalSettingI18n() {
        for (SettingGroup group : settingGroups) {
            group.initTranslateComponent(translations.create("groups." + group.getName().toLowerCase(), group.getName()));
        }
        for (Setting<?> setting : settings) {
            setting.initTranslateComponent(translations.create("settings." + setting.getName().toLowerCase(), setting.getName()));
        }
    }

    public String id() {
        return manifest.id();
    }

    public String ownerId() {
        return ownerId;
    }

    public String displayName() {
        return nameComponent.getTranslatedName();
    }

    public String description() {
        return descriptionComponent.getTranslatedName();
    }

    public String version() {
        return manifest.version() == null ? "" : manifest.version();
    }

    public List<String> authors() {
        return manifest.authors();
    }

    public List<LuaModule> modules() {
        return Collections.unmodifiableList(modules);
    }

    public boolean isEnabled() {
        return packageEnabled;
    }

    public synchronized void setEnabled(boolean enabled) {
        if (packageEnabled == enabled) return;
        if (!enabled) {
            enabledSnapshot.clear();
            for (LuaModule module : modules) enabledSnapshot.put(module.getModuleId(), module.isEnabled());
            ConfigHolder.INSTANCE.saveNow();
            packageEnabled = false;
            for (LuaModule module : modules) module.setEnabled(false);
        } else {
            packageEnabled = true;
            for (LuaModule module : modules) {
                module.setEnabled(enabledSnapshot.getOrDefault(module.getModuleId(),
                        desiredEnabled.getOrDefault(module.getModuleId(), false)));
            }
            enabledSnapshot.clear();
        }
        savePackageState();
    }

    public Path directory() {
        return directory;
    }

    public synchronized void persistState() {
        ConfigHolder.INSTANCE.saveNow();
        if (!packageEnabled) savePackageState();
    }

    private void applyPackageEnabledState() {
        for (LuaModule module : modules) {
            boolean enabled = desiredEnabled.getOrDefault(module.getModuleId(), false);
            module.setEnabled(packageEnabled && enabled);
        }
    }

    @Override
    public List<Setting<?>> mutableSettings() {
        return settings;
    }

    @Override
    public List<SettingGroup> mutableSettingGroups() {
        return settingGroups;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (registered && packageEnabled) ConfigHolder.INSTANCE.saveNow();
        savePackageState();
        for (int index = moduleRegistrations.size() - 1; index >= 0; index--) {
            try {
                moduleRegistrations.get(index).close();
            } catch (Throwable failure) {
                Constants.LOGGER.error("注销 Lua Module 失败: {}", ownerId, failure);
            }
        }
        moduleRegistrations.clear();
        for (LuaModule module : modules) module.close();
        modules.clear();
        if (settingHostRegistration != null) settingHostRegistration.close();
        if (settingsRuntime != null) settingsRuntime.close();
        translations.close();
    }
}
