package com.github.epsilon.scripting.lua;

import com.github.epsilon.Constants;
import com.github.epsilon.modules.impl.ClientSetting;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.concurrent.TimeUnit;

public final class LuaScriptManager implements AutoCloseable {
    public static final LuaScriptManager INSTANCE = new LuaScriptManager();

    private static final Gson GSON = new Gson();
    private final Path scriptsDirectory = Path.of(System.getProperty("user.home"), ".epsilon", "scripts");
    private final Map<String, LuaScriptPackage> packages = new LinkedHashMap<>();
    private final Map<String, String> errors = new LinkedHashMap<>();
    private final Map<String, ScriptDescriptor> descriptors = new LinkedHashMap<>();
    private boolean initialized;
    private boolean enabled;
    private WatchService watchService;
    private Thread watchThread;
    private volatile boolean watcherEnabled;

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
        this.enabled = enabled;
        if (!initialized) return;
        if (enabled) {
            reloadAllOnClient();
            setWatcherEnabledOnClient(ClientSetting.INSTANCE.luaScriptWatcher.getValue());
        } else {
            stopWatcher();
            unloadAll();
        }
    }

    public void setWatcherEnabled(boolean enabled) {
        runOnClient(() -> setWatcherEnabledOnClient(enabled));
    }

    private synchronized void setWatcherEnabledOnClient(boolean enabled) {
        watcherEnabled = enabled;
        if (!initialized || !this.enabled || !enabled) {
            stopWatcher();
            return;
        }
        if (watchThread != null && watchThread.isAlive()) return;
        startWatcher();
    }

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
        discovered.forEach((id, source) -> descriptors.put(id,
                new ScriptDescriptor(source.manifestFile().getParent(), source.manifest())));

        for (String loadedId : new ArrayList<>(packages.keySet())) {
            if (!discovered.containsKey(loadedId)) {
                packages.remove(loadedId).close();
            }
        }
        for (ManifestSource source : discovered.values()) {
            LuaScriptPackage previous = packages.get(source.manifest().id());
            if (previous == null) loadManifest(source.manifestFile(), source.manifest(), nextErrors);
            else reloadPackageOnClient(previous, source.manifestFile(), source.manifest(), nextErrors);
        }
        errors.clear();
        errors.putAll(nextErrors);
        if (watcherEnabled) restartWatcherRegistrations();
    }

    public void reload(String packageId) {
        runOnClient(() -> reloadOnClient(packageId));
    }

    private synchronized void reloadOnClient(String packageId) {
        if (!initialized || !enabled) return;
        LuaScriptPackage previous = packages.get(packageId);
        if (previous == null) {
            reloadAllOnClient();
            return;
        }
        Path manifestFile = previous.directory().resolve("script.json");
        try {
            LuaScriptManifest manifest = readManifest(manifestFile);
            reloadPackageOnClient(previous, manifestFile, manifest, errors);
        } catch (Throwable failure) {
            errors.put(packageId, failure.toString());
            Constants.LOGGER.error("Lua 脚本包重载失败，保留旧 runtime: {}", packageId, failure);
        }
    }

    public void setPackageEnabled(String packageId, boolean enabled) {
        runOnClient(() -> {
            synchronized (LuaScriptManager.this) {
                LuaScriptPackage scriptPackage = packages.get(packageId);
                if (scriptPackage != null) scriptPackage.setEnabled(enabled);
            }
        });
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
                if (descriptors.putIfAbsent(manifest.id(),
                        new ScriptDescriptor(manifestFile.getParent(), manifest)) != null) {
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
        String errorKey = manifestFile.getParent().getFileName().toString();
        try {
            if (packages.containsKey(manifest.id())) throw new IllegalArgumentException("重复脚本包 ID: " + manifest.id());
            LuaScriptPackage scriptPackage = new LuaScriptPackage(manifestFile.getParent(), manifest);
            scriptPackage.load();
            packages.put(manifest.id(), scriptPackage);
            Constants.LOGGER.info("Lua 脚本包加载完成: {} ({} modules)", manifest.id(), scriptPackage.modules().size());
        } catch (Throwable failure) {
            errorTarget.put(errorKey, failure.toString());
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

    private synchronized void startWatcher() {
        try {
            watchService = FileSystems.getDefault().newWatchService();
            registerTree(scriptsDirectory);
            watchThread = new Thread(this::watchLoop, "Epsilon-Lua-Watcher");
            watchThread.setDaemon(true);
            watchThread.start();
        } catch (IOException failure) {
            stopWatcher();
            Constants.LOGGER.error("启动 Lua watcher 失败", failure);
        }
    }

    private void registerTree(Path root) throws IOException {
        if (!Files.isDirectory(root) || watchService == null) return;
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                dir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void watchLoop() {
        long lastChange = 0L;
        boolean pending = false;
        while (watcherEnabled && enabled && watchService != null) {
            try {
                WatchKey key = watchService.poll(100, TimeUnit.MILLISECONDS);
                if (key != null) {
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.kind() == StandardWatchEventKinds.OVERFLOW) continue;
                        pending = true;
                        lastChange = System.nanoTime();
                    }
                    key.reset();
                }
                long debounceNanos = TimeUnit.MILLISECONDS.toNanos(
                        ClientSetting.INSTANCE.luaScriptReloadDebounce.getValue());
                if (pending && System.nanoTime() - lastChange >= debounceNanos) {
                    pending = false;
                    reloadAll();
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            } catch (Throwable failure) {
                if (watcherEnabled && enabled) Constants.LOGGER.error("Lua watcher 失败", failure);
                return;
            }
        }
    }

    private synchronized void restartWatcherRegistrations() {
        if (!watcherEnabled || !enabled) return;
        stopWatcher();
        startWatcher();
    }

    private synchronized void stopWatcher() {
        WatchService service = watchService;
        watchService = null;
        Thread thread = watchThread;
        watchThread = null;
        if (service != null) {
            try {
                service.close();
            } catch (IOException failure) {
                Constants.LOGGER.warn("关闭 Lua watcher 失败", failure);
            }
        }
        if (thread != null && thread != Thread.currentThread()) thread.interrupt();
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
        stopWatcher();
        unloadAll();
        enabled = false;
    }

    private record ManifestSource(Path manifestFile, LuaScriptManifest manifest) {
    }

    public record ScriptDescriptor(Path directory, LuaScriptManifest manifest) {
    }
}
