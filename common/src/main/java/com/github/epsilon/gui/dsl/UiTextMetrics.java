package com.github.epsilon.gui.dsl;

import com.github.epsilon.graphics.text.ttf.TtfFontLoader;

public interface UiTextMetrics {

    float textWidth(String text, float scale);

    float textWidth(String text, float scale, TtfFontLoader fontLoader);

    float textHeight(float scale);

    float textHeight(float scale, TtfFontLoader fontLoader);
}
