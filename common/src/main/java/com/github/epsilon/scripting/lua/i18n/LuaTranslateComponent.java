package com.github.epsilon.scripting.lua.i18n;

import com.github.epsilon.assets.i18n.TranslateComponent;

public final class LuaTranslateComponent implements TranslateComponent {
    private final LuaTranslationCatalog catalog;
    private final String relativeKey;
    private final String fallback;
    private String cached;

    LuaTranslateComponent(LuaTranslationCatalog catalog, String relativeKey, String fallback) {
        this.catalog = catalog;
        this.relativeKey = relativeKey;
        this.fallback = fallback;
    }

    @Override
    public String getFullKey() {
        return catalog.fullKey(relativeKey);
    }

    @Override
    public String getTranslatedName() {
        if (cached == null) cached = catalog.resolve(relativeKey, fallback);
        return cached;
    }

    @Override
    public void refresh() {
        clearCache();
        getTranslatedName();
    }

    void clearCache() {
        cached = null;
    }

    @Override
    public TranslateComponent createChild(String suffix) {
        String child = relativeKey == null || relativeKey.isBlank() ? suffix : relativeKey + "." + suffix;
        return catalog.create(child, suffix);
    }
}
