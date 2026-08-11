package com.github.epsilon.scripting.lua;

import com.github.epsilon.Constants;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class LuaScriptManager implements AutoCloseable {
    public static final LuaScriptManager INSTANCE = new LuaScriptManager();

    private static final Gson GSON = new Gson();
    private final Path scriptsDirectory = Path.of(System.getProperty("user.home"), ".epsilon", "scripts");
    private final Map<String, LuaScriptPackage> packages = new LinkedHashMap<>();
    private final Map<String, String> errors = new LinkedHashMap<>();
    private final Map<String, ScriptDescriptor> descriptors = new LinkedHashMap<>();
    private boolean initialized;
    private boolean enabled;

    private LuaScriptManager() {
    }

    public synchronized void init(boolean enabled) {
        if (initialized) return;
        initialized = true;
        try {
            Files.createDirectories(scriptsDirectory);
        } catch (IOException failure) {
            Constants.LOGGER.error("创建 Lua scripts 目录失败: {}", scriptsDirectory, failure);
            return;
        }
        refreshDescriptors();
        setEnabled(enabled);
    }

    public void setEnabled(boolean enabled) {
        runOnClient(() -> setEnabledOnClient(enabled));
    }

    private synchronized void setEnabledOnClient(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        if (!initialized) return;
        if (enabled) reloadAllOnClient();
        else unloadAll();
    }

    /** 重新扫描 manifest，并仅加载当前 profile 中独立开关为开的包。 */
    public void reloadAll() {
        runOnClient(this::reloadAllOnClient);
    }

    private synchronized void reloadAllOnClient() {
        if (!initialized || !enabled) return;
        Map<String, ManifestSource> discovered = new LinkedHashMap<>();
        Map<String, String> nextErrors = new LinkedHashMap<>();
        for (Path manifestFile : discoverManifests()) {
            String errorKey = manifestFile.getParent().getFileName().toString();
            try {
                LuaScriptManifest manifest = readManifest(manifestFile);
                if (discovered.putIfAbsent(manifest.id(), new ManifestSource(manifestFile, manifest)) != null) {
                    throw new IllegalArgumentException("重复脚本包 ID: " + manifest.id());
                }
            } catch (Throwable failure) {
                nextErrors.put(errorKey, failure.toString());
                Constants.LOGGER.error("Lua manifest 读取失败: {}", manifestFile, failure);
            }
        }

        descriptors.clear();
        discovered.forEach((id, source) -> descriptors.put(id, new ScriptDescriptor(
                source.manifestFile().getParent(), source.manifest(), LuaScriptPackage.readEnabledState(id))));

        for (String loadedId : new ArrayList<>(packages.keySet())) {
            ScriptDescriptor descriptor = descriptors.get(loadedId);
            if (descriptor == null || !descriptor.enabled()) packages.remove(loadedId).close();
        }
        for (ManifestSource source : discovered.values()) {
            ScriptDescriptor descriptor = descriptors.get(source.manifest().id());
            if (descriptor == null || !descriptor.enabled()) continue;
            LuaScriptPackage previous = packages.get(source.manifest().id());
            if (previous == null) loadManifest(source.manifestFile(), source.manifest(), nextErrors);
            else reloadPackageOnClient(previous, source.manifestFile(), source.manifest(), nextErrors);
        }
        errors.clear();
        errors.putAll(nextErrors);
    }

    public void reload(String packageId) {
        runOnClient(() -> reloadOnClient(packageId));
    }

    private synchronized void reloadOnClient(String packageId) {
        if (!initialized || !enabled) return;
        ScriptDescriptor descriptor = descriptors.get(packageId);
        if (descriptor == null || !descriptor.enabled()) return;

        Path manifestFile = descriptor.directory().resolve("script.json");
        try {
            LuaScriptManifest manifest = readManifest(manifestFile);
            if (!packageId.equals(manifest.id())) {
                throw new IllegalArgumentException("Reload 不能修改脚本包 ID: " + packageId + " -> " + manifest.id());
            }
            descriptors.put(packageId, new ScriptDescriptor(manifestFile.getParent(), manifest, true));
            LuaScriptPackage previous = packages.get(packageId);
            if (previous == null) loadManifest(manifestFile, manifest, errors);
            else reloadPackageOnClient(previous, manifestFile, manifest, errors);
        } catch (Throwable failure) {
            errors.put(packageId, failure.toString());
            Constants.LOGGER.error("Lua 脚本包重载失败，保留当前状态: {}", packageId, failure);
        }
    }

    public void setPackageEnabled(String packageId, boolean enabled) {
        runOnClient(() -> setPackageEnabledOnClient(packageId, enabled));
    }

    private synchronized void setPackageEnabledOnClient(String packageId, boolean enabled) {
        if (!initialized || !this.enabled) return;
        ScriptDescriptor descriptor = descriptors.get(packageId);
        if (descriptor == null) return;

        if (!enabled) {
            try {
                LuaScriptPackage scriptPackage = packages.remove(packageId);
                if (scriptPackage != null) {
                    scriptPackage.setEnabled(false);
                    scriptPackage.close();
                } else {
                    LuaScriptPackage.writeEnabledState(packageId, false);
                }
                descriptors.put(packageId, new ScriptDescriptor(descriptor.directory(), descriptor.manifest(), false));
                errors.remove(packageId);
                Constants.LOGGER.info("Lua 脚本包已卸载: {}", packageId);
            } catch (Throwable failure) {
                errors.put(packageId, failure.toString());
                Constants.LOGGER.error("Lua 脚本包卸载失败: {}", packageId, failure);
            }
            return;
        }

        if (packages.containsKey(packageId)) return;
        Path manifestFile = descriptor.directory().resolve("script.json");
        LuaScriptPackage candidate = null;
        try {
            LuaScriptManifest manifest = readManifest(manifestFile);
            if (!packageId.equals(manifest.id())) {
                throw new IllegalArgumentException("开启脚本包时不能修改 ID: " + packageId + " -> " + manifest.id());
            }
            candidate = new LuaScriptPackage(manifestFile.getParent(), manifest);
            candidate.load();
            if (!candidate.isEnabled()) candidate.setEnabled(true);
            packages.put(packageId, candidate);
            descriptors.put(packageId, new ScriptDescriptor(manifestFile.getParent(), manifest, true));
            errors.remove(packageId);
            Constants.LOGGER.info("Lua 脚本包已加载并注册: {} ({} modules)", packageId, candidate.modules().size());
        } catch (Throwable failure) {
            if (candidate != null) candidate.close();
            errors.put(packageId, failure.toString());
            Constants.LOGGER.error("Lua 脚本包开启失败: {}", packageId, failure);
        }
    }

    public synchronized List<LuaScriptPackage> packages() {
        return Collections.unmodifiableList(new ArrayList<>(packages.values()));
    }

    public synchronized Map<String, String> errors() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(errors));
    }

    public synchronized List<ScriptDescriptor> descriptors() {
        return List.copyOf(descriptors.values());
    }

    public Path scriptsDirectory() {
        return scriptsDirectory;
    }

    public synchronized boolean isEnabled() {
        return enabled;
    }

    /** 配置 profile 切换后，重新协调包级启用状态与已注册 runtime。 */
    public void onActiveConfigChanged() {
        runOnClient(() -> {
            synchronized (LuaScriptManager.this) {
                if (!initialized) return;
                if (enabled) reloadAllOnClient();
                else refreshDescriptors();
            }
        });
    }

    private List<Path> discoverManifests() {
        try (Stream<Path> children = Files.list(scriptsDirectory)) {
            return children.filter(Files::isDirectory)
                    .map(directory -> directory.resolve("script.json"))
                    .filter(Files::isRegularFile)
                    .sorted()
                    .toList();
        } catch (IOException failure) {
            Constants.LOGGER.error("扫描 Lua scripts 失败: {}", scriptsDirectory, failure);
            return List.of();
        }
    }

    private synchronized void refreshDescriptors() {
        descriptors.clear();
        errors.clear();
        for (Path manifestFile : discoverManifests()) {
            try {
                LuaScriptManifest manifest = readManifest(manifestFile);
                if (descriptors.putIfAbsent(manifest.id(), new ScriptDescriptor(
                        manifestFile.getParent(), manifest, LuaScriptPackage.readEnabledState(manifest.id()))) != null) {
                    throw new IllegalArgumentException("重复脚本包 ID: " + manifest.id());
                }
            } catch (Throwable failure) {
                String key = manifestFile.getParent().getFileName().toString();
                errors.put(key, failure.toString());
            }
        }
    }

    private LuaScriptManifest readManifest(Path manifestFile) throws IOException {
        try (Reader reader = Files.newBufferedReader(manifestFile, StandardCharsets.UTF_8)) {
            LuaScriptManifest manifest = GSON.fromJson(reader, LuaScriptManifest.class);
            if (manifest == null) throw new JsonParseException("manifest 为空");
            manifest.validate();
            return manifest;
        }
    }

    private void loadManifest(Path manifestFile, LuaScriptManifest manifest, Map<String, String> errorTarget) {
        LuaScriptPackage scriptPackage = null;
        try {
            if (packages.containsKey(manifest.id())) throw new IllegalArgumentException("重复脚本包 ID: " + manifest.id());
            scriptPackage = new LuaScriptPackage(manifestFile.getParent(), manifest);
            scriptPackage.load();
            if (!scriptPackage.isEnabled()) scriptPackage.setEnabled(true);
            packages.put(manifest.id(), scriptPackage);
            errorTarget.remove(manifest.id());
            Constants.LOGGER.info("Lua 脚本包加载完成: {} ({} modules)", manifest.id(), scriptPackage.modules().size());
        } catch (Throwable failure) {
            if (scriptPackage != null) scriptPackage.close();
            errorTarget.put(manifest.id(), failure.toString());
            Constants.LOGGER.error("Lua 脚本包加载失败: {}", manifestFile, failure);
        }
    }

    private void reloadPackageOnClient(LuaScriptPackage previous, Path manifestFile, LuaScriptManifest manifest,
                                       Map<String, String> errorTarget) {
        previous.persistState();
        LuaScriptPackage candidate = new LuaScriptPackage(manifestFile.getParent(), manifest);
        try {
            candidate.prepare();
        } catch (Throwable failure) {
            candidate.close();
            errorTarget.put(manifest.id(), failure.toString());
            Constants.LOGGER.error("Lua 脚本包 staging 失败，保留旧 runtime: {}", manifest.id(), failure);
            return;
        }

        try {
            candidate.replaceRegistration(previous);
            if (!candidate.isEnabled()) candidate.setEnabled(true);
            previous.close();
            packages.remove(previous.id());
            packages.put(manifest.id(), candidate);
            errorTarget.remove(manifest.id());
            Constants.LOGGER.info("Lua 脚本包重载完成: {}", manifest.id());
        } catch (Throwable failure) {
            candidate.close();
            packages.remove(previous.id());
            errorTarget.put(manifest.id(), failure.toString());
            Constants.LOGGER.error("Lua 脚本包提交失败: {}", manifest.id(), failure);
        }
    }

    private void unloadAll() {
        List<LuaScriptPackage> current = new ArrayList<>(packages.values());
        Collections.reverse(current);
        for (LuaScriptPackage scriptPackage : current) scriptPackage.close();
        packages.clear();
    }

    private void runOnClient(Runnable action) {
        if (Constants.mc == null || Constants.mc.isSameThread()) action.run();
        else Constants.mc.execute(action);
    }

    @Override
    public synchronized void close() {
        unloadAll();
        enabled = false;
    }

    private record ManifestSource(Path manifestFile, LuaScriptManifest manifest) {
    }

    public record ScriptDescriptor(Path directory, LuaScriptManifest manifest, boolean enabled) {
    }
}
