package com.github.epsilon.graphics.shaders;

import com.github.epsilon.Constants;
import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.github.epsilon.Constants.mc;

/** 使用 1.21.1 OpenGL context 绘制主菜单的全屏 GLSL 背景。 */
public final class GlslSandBox implements AutoCloseable {

    public static final GlslSandBox INSTANCE = new GlslSandBox();

    public static final ResourceLocation SEA_LEVEL = shader("sea_level");
    public static final ResourceLocation PLANET = shader("planet");
    public static final ResourceLocation BLACK_HOLE = shader("black_hole");
    public static final ResourceLocation MINECRAFT = shader("minecraft");

    private static final String FULLSCREEN_VERTEX = """
            #version 410 core
            void main() {
                vec2 position = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                gl_Position = vec4(position * 2.0 - 1.0, 0.0, 1.0);
            }
            """;
    private static final int UNIFORM_BINDING = 0;
    private static final int UNIFORM_SIZE = Float.BYTES * 8;

    private final Map<ResourceLocation, Integer> programs = new HashMap<>();
    private final Set<ResourceLocation> failedShaders = new HashSet<>();
    private int vertexArray;
    private int uniformBuffer;
    private long initTime = Util.getMillis();

    private GlslSandBox() {
    }

    public void resetTime() {
        initTime = Util.getMillis();
    }

    public boolean render(ResourceLocation fragmentShader, double mouseX, double mouseY) {
        RenderSystem.assertOnRenderThread();
        if (failedShaders.contains(fragmentShader)) return false;

        int program;
        try {
            program = programs.computeIfAbsent(fragmentShader, this::createProgram);
        } catch (RuntimeException failure) {
            failedShaders.add(fragmentShader);
            Constants.LOGGER.error("Failed to compile main menu background {}", fragmentShader, failure);
            return false;
        }

        int width = mc.getMainRenderTarget().width;
        int height = mc.getMainRenderTarget().height;
        if (width <= 0 || height <= 0) return false;
        ensureBuffers();

        float mousePxX = (float) (mouseX * width / mc.getWindow().getGuiScaledWidth());
        float mousePxY = (float) (mouseY * height / mc.getWindow().getGuiScaledHeight());
        float elapsedTime = (Util.getMillis() - initTime) / 1000.0f;

        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousVertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int previousUniformBuffer = GL11.glGetInteger(GL31.GL_UNIFORM_BUFFER_BINDING);
        int previousIndexedUniformBuffer = GL30.glGetIntegeri(GL31.GL_UNIFORM_BUFFER_BINDING, UNIFORM_BINDING);
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer uniforms = stack.malloc(UNIFORM_SIZE);
            uniforms.putFloat(width).putFloat(height).putFloat(elapsedTime).putFloat(0.0f);
            uniforms.putFloat(mousePxX / width).putFloat((height - 1.0f - mousePxY) / height);
            uniforms.putFloat(mousePxX).putFloat(mousePxY).flip();

            RenderSystem.disableDepthTest();
            RenderSystem.disableBlend();
            RenderSystem.disableCull();
            RenderSystem.depthMask(false);
            GlStateManager._glUseProgram(program);
            GlStateManager._glBindVertexArray(vertexArray);
            GlStateManager._glBindBuffer(GL31.GL_UNIFORM_BUFFER, uniformBuffer);
            GL15.glBufferSubData(GL31.GL_UNIFORM_BUFFER, 0L, uniforms);
            GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, UNIFORM_BINDING, uniformBuffer);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        } finally {
            GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, UNIFORM_BINDING, previousIndexedUniformBuffer);
            GlStateManager._glBindBuffer(GL31.GL_UNIFORM_BUFFER, previousUniformBuffer);
            GlStateManager._glBindVertexArray(previousVertexArray);
            GlStateManager._glUseProgram(previousProgram);
            setDepthMask(depthMask);
            setDepthTest(depthTest);
            setBlend(blend);
            setCull(cull);
        }
        return true;
    }

    private int createProgram(ResourceLocation fragmentShader) {
        int vertex = compileShader(GL20.GL_VERTEX_SHADER, FULLSCREEN_VERTEX, "main menu fullscreen vertex");
        int fragment = 0;
        int program = 0;
        try {
            fragment = compileShader(GL20.GL_FRAGMENT_SHADER, readShader(fragmentShader), fragmentShader.toString());
            program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vertex);
            GL20.glAttachShader(program, fragment);
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                throw new IllegalStateException("Failed to link " + fragmentShader + ": " + GL20.glGetProgramInfoLog(program));
            }
            int uniformBlock = GL31.glGetUniformBlockIndex(program, "GlslSandboxInfo");
            if (uniformBlock == GL31.GL_INVALID_INDEX) {
                throw new IllegalStateException(fragmentShader + " does not declare GlslSandboxInfo");
            }
            GL31.glUniformBlockBinding(program, uniformBlock, UNIFORM_BINDING);
            return program;
        } finally {
            GL20.glDeleteShader(vertex);
            if (fragment != 0) GL20.glDeleteShader(fragment);
            if (program != 0 && GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                GL20.glDeleteProgram(program);
            }
        }
    }

    private static int compileShader(int type, String source, String name) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException("Failed to compile " + name + ": " + log);
        }
        return shader;
    }

    private static String readShader(ResourceLocation location) {
        var resource = mc.getResourceManager().getResource(location)
                .orElseThrow(() -> new IllegalStateException("Missing shader resource " + location));
        try (var reader = resource.openAsReader()) {
            StringBuilder source = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) source.append(line).append('\n');
            return source.toString();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read shader resource " + location, exception);
        }
    }

    private void ensureBuffers() {
        if (vertexArray == 0) vertexArray = GlStateManager._glGenVertexArrays();
        if (uniformBuffer != 0) return;
        uniformBuffer = GlStateManager._glGenBuffers();
        int previous = GL11.glGetInteger(GL31.GL_UNIFORM_BUFFER_BINDING);
        GlStateManager._glBindBuffer(GL31.GL_UNIFORM_BUFFER, uniformBuffer);
        GL15.glBufferData(GL31.GL_UNIFORM_BUFFER, UNIFORM_SIZE, GL15.GL_DYNAMIC_DRAW);
        GlStateManager._glBindBuffer(GL31.GL_UNIFORM_BUFFER, previous);
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        programs.values().forEach(GL20::glDeleteProgram);
        programs.clear();
        failedShaders.clear();
        if (uniformBuffer != 0) {
            GlStateManager._glDeleteBuffers(uniformBuffer);
            uniformBuffer = 0;
        }
        if (vertexArray != 0) {
            GlStateManager._glDeleteVertexArrays(vertexArray);
            vertexArray = 0;
        }
    }

    private static ResourceLocation shader(String name) {
        return ResourceLocationUtils.getIdentifier("shaders/menu/" + name + ".fsh");
    }

    private static void setDepthMask(boolean enabled) {
        RenderSystem.depthMask(enabled);
    }

    private static void setDepthTest(boolean enabled) {
        if (enabled) RenderSystem.enableDepthTest();
        else RenderSystem.disableDepthTest();
    }

    private static void setBlend(boolean enabled) {
        if (enabled) RenderSystem.enableBlend();
        else RenderSystem.disableBlend();
    }

    private static void setCull(boolean enabled) {
        if (enabled) RenderSystem.enableCull();
        else RenderSystem.disableCull();
    }
}
