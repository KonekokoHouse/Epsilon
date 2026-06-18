package com.github.epsilon.graphics.text;

import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.github.epsilon.graphics.text.ttf.TtfFontLoader;

public class StaticFontLoader {

    public static final TtfFontLoader DEFAULT = new TtfFontLoader(ResourceLocationUtils.getIdentifier("fonts/font.ttf"));

    public static final TtfFontLoader ICONS = new TtfFontLoader(ResourceLocationUtils.getIdentifier("fonts/icon.ttf"));

    public static final TtfFontLoader JURA_LIGHT = new TtfFontLoader(ResourceLocationUtils.getIdentifier("fonts/jura-light.ttf"));

    public static final TtfFontLoader OSAKA_CHIPS = new TtfFontLoader(ResourceLocationUtils.getIdentifier("fonts/osakachips.ttf"));

    public static void destroyAll() {
        DEFAULT.destroy();
        ICONS.destroy();
        JURA_LIGHT.destroy();
        OSAKA_CHIPS.destroy();
    }

}
