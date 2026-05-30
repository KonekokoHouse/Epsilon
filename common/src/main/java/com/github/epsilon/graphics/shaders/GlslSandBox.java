package com.github.epsilon.graphics.shaders;

import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import static com.github.epsilon.Constants.mc;

public class GlslSandBox implements AutoCloseable {

    public static final GlslSandBox INSTANCE = new GlslSandBox();

    public static final Identifier BLACK_HOLE = ResourceLocationUtils.getIdentifier("menu/black_hole");
    public static final Identifier MINECRAFT = ResourceLocationUtils.getIdentifier("menu/minecraft");
    public static final Identifier PLANET = ResourceLocationUtils.getIdentifier("menu/planet");

    private static final Identifier FULLSCREEN_VERTEX = ResourceLocationUtils.getIdentifier("fullscreen");

    private static final int SANDBOX_INFO_SIZE = new Std140SizeCalculator()
            .putVec4()
            .putVec4()
            .get();

    private final Map<Identifier, RenderPipeline> pipelines = new HashMap<>();

    private GpuBuffer sandboxInfoUniformBuf;
    private long initTime = Util.getMillis();

    private RenderPipeline getOrCreatePipeline(Identifier fragmentShader) {
        return pipelines.computeIfAbsent(fragmentShader, shader -> RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(shader.getNamespace(), "pipelines/glsl_sandbox/" + shader.getPath().replace('/', '_')))
                .withVertexShader(FULLSCREEN_VERTEX)
                .withFragmentShader(shader)
                .withUniform("GlslSandboxInfo", UniformType.UNIFORM_BUFFER)
                .withCull(false)
                .build()
        );
    }

    private void ensureUniformBuffer() {
        if (sandboxInfoUniformBuf == null) {
            sandboxInfoUniformBuf = RenderSystem.getDevice().createBuffer(
                    () -> "Lumin GLSL Sandbox UBO",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE,
                    SANDBOX_INFO_SIZE
            );
        }
    }

    public void resetTime() {
        initTime = Util.getMillis();
    }

    public void render(Identifier fragmentShader, double mouseX, double mouseY) {
        render(fragmentShader, mouseX, mouseY, initTime);
    }

    public void render(Identifier fragmentShader, double mouseX, double mouseY, long startTimeMs) {
        GpuTextureView colorView = LuminRenderSystem.resolveColorView();
        if (colorView == null) return;

        ensureUniformBuffer();

        final var activeTarget = LuminRenderSystem.getActiveTarget();
        final int targetWidth = activeTarget != null ? activeTarget.width() : mc.getMainRenderTarget().width;
        final int targetHeight = activeTarget != null ? activeTarget.height() : mc.getMainRenderTarget().height;

        if (targetWidth <= 0 || targetHeight <= 0) return;

        float scaleX = targetWidth / LuminRenderSystem.getScaledWidth();
        float scaleY = targetHeight / LuminRenderSystem.getScaledHeight();

        float mousePxX = (float) mouseX * scaleX;
        float mousePxY = (float) mouseY * scaleY;
        float mouseUvX = mousePxX / targetWidth;
        float mouseUvY = (targetHeight - 1.0f - mousePxY) / targetHeight;
        float elapsedTime = (Util.getMillis() - startTimeMs) / 1000.0f;

        final var encoder = RenderSystem.getDevice().createCommandEncoder();
        try (GpuBuffer.MappedView mappedView = encoder.mapBuffer(sandboxInfoUniformBuf, false, true)) {
            Std140Builder.intoBuffer(mappedView.data())
                    .putVec4(targetWidth, targetHeight, elapsedTime, 0.0f)
                    .putVec4(mouseUvX, mouseUvY, mousePxX, mousePxY);
        }

        try (RenderPass pass = encoder.createRenderPass(
                () -> "Lumin GLSL Sandbox",
                colorView, OptionalInt.empty(),
                LuminRenderSystem.resolveDepthView(), OptionalDouble.empty())
        ) {
            pass.setPipeline(getOrCreatePipeline(fragmentShader));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("GlslSandboxInfo", sandboxInfoUniformBuf);
            pass.draw(0, 6);
        }
    }

    @Override
    public void close() {
        pipelines.clear();
        if (sandboxInfoUniformBuf != null) {
            sandboxInfoUniformBuf.close();
            sandboxInfoUniformBuf = null;
        }
    }

}
