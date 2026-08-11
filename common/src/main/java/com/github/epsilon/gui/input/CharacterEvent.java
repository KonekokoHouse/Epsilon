package com.github.epsilon.gui.input;

public record CharacterEvent(int codepoint, int modifiers) {
    public String codepointAsString() {
        return Character.isValidCodePoint(codepoint) ? Character.toString(codepoint) : "";
    }
}
