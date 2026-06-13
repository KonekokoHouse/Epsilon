package com.github.epsilon.managers;

public class ShaderManager {

    public final ShaderManager INSTANCE = new ShaderManager();

    public enum Shader {
        Default,
        Smoke,
        Gradient,
        Snow,
        Fade
    }

}
