package com.github.epsilon.gui.addon;

import com.github.epsilon.addon.EpsilonAddon;
import com.github.epsilon.holders.AddonHolder;
import com.github.epsilon.scripting.lua.LuaScriptManager;
import com.github.epsilon.scripting.lua.LuaScriptPackage;
import com.github.epsilon.settings.Setting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class AddonPanelEntryRegistry {
    public static final AddonPanelEntryRegistry INSTANCE = new AddonPanelEntryRegistry();

    private AddonPanelEntryRegistry() {
    }

    public List<AddonPanelEntry> entries() {
        List<AddonPanelEntry> result = new ArrayList<>();
        for (EpsilonAddon addon : AddonHolder.INSTANCE.getAddons()) result.add(new JavaEntry(addon));

        Map<String, LuaScriptPackage> loaded = LuaScriptManager.INSTANCE.packages().stream()
                .collect(java.util.stream.Collectors.toMap(LuaScriptPackage::id, value -> value));
        for (LuaScriptManager.ScriptDescriptor descriptor : LuaScriptManager.INSTANCE.descriptors()) {
            result.add(new LuaEntry(descriptor, loaded.get(descriptor.manifest().id())));
        }
        for (var error : LuaScriptManager.INSTANCE.errors().entrySet()) {
            boolean represented = result.stream().anyMatch(entry -> entry.getDisplayId().equals(error.getKey()));
            if (!represented) result.add(new LuaErrorEntry(error.getKey(), error.getValue()));
        }
        return List.copyOf(result);
    }

    private record JavaEntry(EpsilonAddon addon) implements AddonPanelEntry {
        @Override public String getAddonId() { return "java:" + addon.getAddonId(); }
        @Override public String getDisplayId() { return addon.getAddonId(); }
        @Override public String getDisplayName() { return addon.getDisplayName(); }
        @Override public String getDescription() { return addon.getDescription(); }
        @Override public String getVersion() { return addon.getVersion(); }
        @Override public List<String> getAuthors() { return addon.getAuthors(); }
        @Override public List<Setting<?>> getSettings() { return addon.getSettings(); }
        @Override public int getModuleCount() { return addon.getRegisteredModules().size(); }
        @Override public Kind getKind() { return Kind.JAVA_ADDON; }
    }

    private record LuaEntry(LuaScriptManager.ScriptDescriptor descriptor,
                            LuaScriptPackage scriptPackage) implements AddonPanelEntry {
        @Override public String getAddonId() { return "lua:" + descriptor.manifest().id(); }
        @Override public String getDisplayId() { return descriptor.manifest().id(); }
        @Override public String getDisplayName() {
            return scriptPackage != null ? scriptPackage.displayName() : descriptor.manifest().displayName();
        }
        @Override public String getDescription() {
            if (scriptPackage != null) return scriptPackage.description();
            return descriptor.manifest().description() == null ? "" : descriptor.manifest().description();
        }
        @Override public String getVersion() {
            return descriptor.manifest().version() == null ? "" : descriptor.manifest().version();
        }
        @Override public List<String> getAuthors() { return descriptor.manifest().authors(); }
        @Override public List<Setting<?>> getSettings() {
            return scriptPackage == null ? List.of() : List.copyOf(scriptPackage.mutableSettings());
        }
        @Override public int getModuleCount() { return descriptor.manifest().modules().size(); }
        @Override public Kind getKind() { return Kind.LUA_SCRIPT; }
        @Override public boolean canToggle() { return LuaScriptManager.INSTANCE.isEnabled(); }
        @Override public boolean isEnabled() { return descriptor.enabled(); }
        @Override public void toggle() {
            if (canToggle()) LuaScriptManager.INSTANCE.setPackageEnabled(descriptor.manifest().id(), !isEnabled());
        }
        @Override public boolean canReload() { return LuaScriptManager.INSTANCE.isEnabled() && descriptor.enabled(); }
        @Override public void reload() { LuaScriptManager.INSTANCE.reload(descriptor.manifest().id()); }
        @Override public String getError() {
            return LuaScriptManager.INSTANCE.errors().getOrDefault(descriptor.manifest().id(), "");
        }
    }

    private record LuaErrorEntry(String id, String error) implements AddonPanelEntry {
        @Override public String getAddonId() { return "lua:error:" + id; }
        @Override public String getDisplayId() { return id; }
        @Override public String getDisplayName() { return id; }
        @Override public String getDescription() { return error; }
        @Override public String getVersion() { return ""; }
        @Override public List<String> getAuthors() { return List.of(); }
        @Override public List<Setting<?>> getSettings() { return List.of(); }
        @Override public int getModuleCount() { return 0; }
        @Override public Kind getKind() { return Kind.LUA_ERROR; }
        @Override public boolean canReload() { return LuaScriptManager.INSTANCE.isEnabled(); }
        @Override public void reload() { LuaScriptManager.INSTANCE.reloadAll(); }
        @Override public String getError() { return error; }
    }
}
