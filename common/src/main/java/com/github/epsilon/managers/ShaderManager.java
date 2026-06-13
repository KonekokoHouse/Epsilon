package com.github.epsilon.managers;

import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.github.epsilon.modules.impl.render.Shaders;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import net.minecraft.client.renderer.RenderPipelines;

import java.awt.*;
import java.util.OptionalInt;

/**
 * @author ilove0329P
 * Thanks to furry client.
 */
public class ShaderManager {

    public static final ShaderManager INSTANCE = new ShaderManager();

    private static final int UNIFORMS_SIZE = new Std140SizeCalculator()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .get();

    private RenderPipeline defaultPipeline;
    private RenderPipeline smokePipeline;
    private RenderPipeline gradientPipeline;
    private RenderPipeline snowPipeline;
    private RenderPipeline fadePipeline;
    private RenderPipeline copyPipeline;
    private GpuBuffer uniforms;
    private RenderTarget shaderSwap;
    private RenderTarget handTarget;
    private boolean renderingHands;
    private boolean capturedHands;
    private float time;

    private ShaderManager() {
    }

    public void processEntityOutlineTarget(RenderTarget target, Shader shader) {
        if (target == null || target.width <= 0 || target.height <= 0 || target.getColorTextureView() == null) {
            return;
        }

        ensureProgram();
        ensureSwap(target.width, target.height);
        updateUniforms(target.width, target.height);

        renderPass("epsilon_shader_effect", target, shaderSwap, pipeline(shader), true);
        renderPass("epsilon_shader_copy", shaderSwap, target, copyPipeline, false);
    }

    public void beginHandOutlineCapture(int width, int height) {
        if (Shaders.INSTANCE.shouldRenderHands()) {
            ensureHandTarget(Math.max(1, width), Math.max(1, height));
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.clearColorAndDepthTextures(handTarget.getColorTexture(), 0, handTarget.getDepthTexture(), 1.0);
            renderingHands = true;
            capturedHands = true;
        }
    }

    public void endHandOutlineCapture() {
        renderingHands = false;
    }

    public boolean isRenderingHands() {
        return renderingHands;
    }

    public RenderTarget getHandOutlineTarget() {
        return renderingHands ? handTarget : null;
    }

    public void processHandOutlineTarget(RenderTarget mainTarget) {
        if (capturedHands) {
            capturedHands = false;

            if (!Shaders.INSTANCE.shouldRenderHands() || handTarget == null || mainTarget == null || mainTarget.getColorTextureView() == null) {
                return;
            }

            processEntityOutlineTarget(handTarget, Shaders.INSTANCE.handsMode.getValue());
            handTarget.blitAndBlendToTexture(mainTarget.getColorTextureView());
        }
    }

    private void renderPass(String name, RenderTarget input, RenderTarget output, RenderPipeline pipeline, boolean customUniforms) {
        if (input.getColorTextureView() == null || output.getColorTextureView() == null) {
            return;
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);

        try (RenderPass renderPass = encoder.createRenderPass(
                () -> name,
                output.getColorTextureView(),
                OptionalInt.empty()
        )) {
            renderPass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(renderPass);
            if (customUniforms) {
                renderPass.setUniform("ShaderConfig", uniforms);
            }
            renderPass.bindTexture("InputSampler", input.getColorTextureView(), sampler);
            renderPass.draw(0, 6);
        }
    }

    private void updateUniforms(int screenWidth, int screenHeight) {
        Shaders shaders = Shaders.INSTANCE;
        float width = Math.max(1.0f, screenWidth);
        float height = Math.max(1.0f, screenHeight);
        Color outline = shaders.outlineColor.getValue();
        Color smokeOutline1 = shaders.smokeOutlineColor1.getValue();
        Color smokeOutline2 = shaders.smokeOutlineColor2.getValue();
        Color fill = shaders.fillColor1.getValue();
        Color smokeFill1 = shaders.fillColor2.getValue();
        Color smokeFill2 = shaders.fillColor3.getValue();

        try (GpuBuffer.MappedView view = RenderSystem.getDevice().createCommandEncoder().mapBuffer(uniforms, false, true)) {
            Std140Builder.intoBuffer(view.data())
                    .putVec4(width, height, 1.0f / width, 1.0f / height)
                    .putVec4(shaders.quality.getValue(), shaders.lineWidth.getValue(), shaders.glow.getValue() ? -1.0f : alpha(outline), shaders.fillAlpha.getValue() / 255.0f)
                    .putVec4(shaders.alpha2.getValue() / 255.0f, time, shaders.factor.getValue().floatValue(), shaders.gradient.getValue().floatValue())
                    .putVec4(shaders.octaves.getValue(), 0.0f, 0.0f, 0.0f)
                    .putVec4(red(outline), green(outline), blue(outline), alpha(outline))
                    .putVec4(red(smokeOutline1), green(smokeOutline1), blue(smokeOutline1), alpha(smokeOutline1))
                    .putVec4(red(smokeOutline2), green(smokeOutline2), blue(smokeOutline2), alpha(smokeOutline2))
                    .putVec4(red(fill), green(fill), blue(fill), alpha(fill))
                    .putVec4(red(smokeFill1), green(smokeFill1), blue(smokeFill1), alpha(smokeFill1))
                    .putVec4(red(smokeFill2), green(smokeFill2), blue(smokeFill2), alpha(smokeFill2));
        }

        time += 0.008f;
        if (time > 1000.0f) {
            time = 0.0f;
        }
    }

    private void ensureProgram() {
        if (uniforms == null) {
            uniforms = RenderSystem.getDevice().createBuffer(() -> "EpsilonShaderUniforms", GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_UNIFORM, UNIFORMS_SIZE);
        }
        if (defaultPipeline == null) {
            defaultPipeline = pipeline("outline");
            smokePipeline = pipeline("smoke");
            gradientPipeline = pipeline("gradient");
            snowPipeline = pipeline("snow");
            fadePipeline = pipeline("fade");
            copyPipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                    .withLocation(ResourceLocationUtils.getIdentifier("pipelines/shader_copy"))
                    .withVertexShader(ResourceLocationUtils.getIdentifier("fullscreen"))
                    .withFragmentShader(ResourceLocationUtils.getIdentifier("shader_copy"))
                    .withSampler("InputSampler")
                    .withCull(false)
                    .build();
        }
    }

    private void ensureSwap(int width, int height) {
        if (shaderSwap == null) {
            shaderSwap = new TextureTarget("Epsilon Shader Swap", width, height, false);
        }

        if (shaderSwap.width != width || shaderSwap.height != height) {
            shaderSwap.resize(width, height);
        }
    }

    private void ensureHandTarget(int width, int height) {
        if (handTarget == null) {
            handTarget = new TextureTarget("Epsilon Shader Hands", width, height, true);
        }

        if (handTarget.width != width || handTarget.height != height) {
            handTarget.resize(width, height);
        }
    }

    private RenderPipeline pipeline(String shader) {
        return RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                .withLocation(ResourceLocationUtils.getIdentifier("pipelines/shader_" + shader))
                .withVertexShader(ResourceLocationUtils.getIdentifier("fullscreen"))
                .withFragmentShader(ResourceLocationUtils.getIdentifier("shader_" + shader))
                .withUniform("ShaderConfig", UniformType.UNIFORM_BUFFER)
                .withSampler("InputSampler")
                .withCull(false)
                .build();
    }

    private RenderPipeline pipeline(Shader shader) {
        return switch (shader) {
            case Smoke -> smokePipeline;
            case Gradient -> gradientPipeline;
            case Snow -> snowPipeline;
            case Fade -> fadePipeline;
            default -> defaultPipeline;
        };
    }

    private static float red(Color color) {
        return color.getRed() / 255.0f;
    }

    private static float green(Color color) {
        return color.getGreen() / 255.0f;
    }

    private static float blue(Color color) {
        return color.getBlue() / 255.0f;
    }

    private static float alpha(Color color) {
        return color.getAlpha() / 255.0f;
    }

    public enum Shader {
        Default,
        Smoke,
        Gradient,
        Snow,
        Fade
    }

}
