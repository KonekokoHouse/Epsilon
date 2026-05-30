package com.github.epsilon.graphics.shaders;

import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.awt.*;
import java.util.OptionalInt;

import static com.github.epsilon.Constants.mc;

public class BlurShader {

    public static final BlurShader INSTANCE = new BlurShader();

    private static final Identifier BLUR_IDENTIFIER = ResourceLocationUtils.getIdentifier("blur");
    private static final int UNIFORM_POOL_SIZE = 64;

    private static final int UNIFORMS_SIZE = new Std140SizeCalculator()
            .putVec3()
            .putVec4()
            .putVec4()
            .putVec4()
            .get();

    private RenderPipeline pipeline;
    private final GpuBuffer[] uniformsPool = new GpuBuffer[UNIFORM_POOL_SIZE];
    private int uniformIndex = 0;
    private RenderTarget snapshot;
    private boolean snapshotCaptured = false;

    private void ensureProgram() {
        if (this.pipeline == null) {
            this.pipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                    .withLocation(ResourceLocationUtils.getIdentifier("pipeline/blur"))
                    .withVertexShader(BLUR_IDENTIFIER)
                    .withFragmentShader(BLUR_IDENTIFIER)
                    .withUniform("BlurUniforms", UniformType.UNIFORM_BUFFER)
                    .withSampler("InputSampler")
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withCull(false)
                    .build();
        }
    }

    public void beginFrame() {
        snapshotCaptured = false;
        uniformIndex = 0;
    }

    public void render(float x, float y, float width, float height, float rTL, float rTR, float rBR, float rBL, Color color, float blurStrength) {
        this.ensureProgram();

        RenderTarget fb = mc.getMainRenderTarget();
        LuminRenderSystem.LuminRenderTarget activeTarget = LuminRenderSystem.getActiveTarget();

        GpuTexture sourceTexture;
        GpuTextureView targetColorView;
        int sourceWidth;
        int sourceHeight;

        if (activeTarget != null) {
            sourceTexture = fb.getColorTexture();
            targetColorView = activeTarget.colorView();
            sourceWidth = fb.width;
            sourceHeight = fb.height;
        } else {
            sourceTexture = fb.getColorTexture();
            targetColorView = fb.getColorTextureView();
            sourceWidth = fb.width;
            sourceHeight = fb.height;
        }

        if (sourceTexture == null || targetColorView == null) {
            return;
        }

        GpuBuffer uniforms = getUniformBuffer(nextUniformSlot());
        RenderTarget snapshot = getSnapshotTarget(sourceWidth, sourceHeight);

        if (uniforms == null || snapshot == null || snapshot.getColorTexture() == null || snapshot.getColorTextureView() == null) {
            return;
        }

        LuminRenderSystem.ScissorRect blurRect = LuminRenderSystem.toFramebufferScissor(x, y, width, height);
        float scale = (float) LuminRenderSystem.getGuiScale();
        float pxX = blurRect.x();
        float pxY = blurRect.y();
        float pxW = blurRect.width();
        float pxH = blurRect.height();

        float rTLPx = Math.max(0.0f, rTL * scale);
        float rTRPx = Math.max(0.0f, rTR * scale);
        float rBRPx = Math.max(0.0f, rBR * scale);
        float rBLPx = Math.max(0.0f, rBL * scale);

        float quality = Math.max(0.0f, blurStrength);

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        captureSnapshotIfNeeded(encoder, sourceTexture, sourceWidth, sourceHeight);

        try (GpuBuffer.MappedView view = encoder.mapBuffer(uniforms, false, true)) {
            Std140Builder builder = Std140Builder.intoBuffer(view.data());
            builder.putVec3(sourceWidth, sourceHeight, quality);
            builder.putVec4(pxW, pxH, pxX, pxY);
            builder.putVec4(color.getRed() / 255.0f, color.getGreen() / 255.0f, color.getBlue() / 255.0f, 1.0f);
            builder.putVec4(rTLPx, rTRPx, rBRPx, rBLPx);
        }

        try (RenderPass renderPass = encoder.createRenderPass(
                () -> "Lumin Blur",
                targetColorView,
                OptionalInt.empty()
        )) {
            renderPass.setPipeline(pipeline);
            renderPass.enableScissor((int) pxX, (int) pxY, Math.max(0, (int) pxW), Math.max(0, (int) pxH));
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("BlurUniforms", uniforms);
            renderPass.bindTexture("InputSampler", snapshot.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            renderPass.draw(0, 3);
        }
    }

    private int nextUniformSlot() {
        int slot = uniformIndex;
        uniformIndex = (uniformIndex + 1) % UNIFORM_POOL_SIZE;
        return slot;
    }

    private GpuBuffer getUniformBuffer(int slot) {
        GpuBuffer buffer = uniformsPool[slot];
        if (buffer == null) {
            buffer = RenderSystem.getDevice().createBuffer(() -> "Lumin-Blur-Uniforms[" + slot + "]", GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_UNIFORM, UNIFORMS_SIZE);
            uniformsPool[slot] = buffer;
        }
        return buffer;
    }

    private RenderTarget getSnapshotTarget(int width, int height) {
        if (snapshot == null) {
            snapshot = new TextureTarget("Lumin Blur Snapshot", width, height, false);
        } else if (snapshot.width != width || snapshot.height != height) {
            snapshot.resize(width, height);
        }
        return snapshot;
    }

    private void captureSnapshotIfNeeded(CommandEncoder encoder, GpuTexture sourceTexture, int sourceWidth, int sourceHeight) {
        if (snapshotCaptured || snapshot == null || snapshot.getColorTexture() == null) {
            return;
        }

        encoder.copyTextureToTexture(
                sourceTexture,
                snapshot.getColorTexture(),
                0, 0, 0, 0, 0,
                sourceWidth, sourceHeight
        );
        snapshotCaptured = true;
    }

    public void render(float x, float y, float width, float height, float radius, float blurStrength) {
        render(x, y, width, height, radius, radius, radius, radius, new Color(0, 0, 0, 0), blurStrength);
    }

    public void render(float x, float y, float width, float height, float radius, Color color, float blurStrength) {
        render(x, y, width, height, radius, radius, radius, radius, color, blurStrength);
    }

}
