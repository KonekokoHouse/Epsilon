package com.github.epsilon.graphics;

import com.github.epsilon.assets.holders.RenderTargetHolder;
import com.github.epsilon.assets.holders.RendererHolder;
import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.github.epsilon.graphics.buffer.LuminDynamicUniforms;
import com.github.epsilon.modules.impl.ClientSetting;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.*;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.DynamicUniformStorage;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import net.minecraft.client.renderer.state.WindowRenderState;
import net.minecraft.resources.Identifier;
import org.joml.*;

import javax.annotation.Nullable;
import java.lang.Math;
import java.util.OptionalDouble;

import static com.github.epsilon.Constants.mc;

public class LuminRenderSystem {

    public static final Projection guiOrthoProjection = new Projection();

    private static final ProjectionMatrixBuffer guiProjectionMatrixBuffer = new ProjectionMatrixBuffer("lumin-gui");

    @Nullable
    private static LuminRenderTarget activeTarget = null;

    public static void setActiveTarget(@Nullable LuminRenderTarget target) {
        activeTarget = target;
    }

    public static void destroyAll() {
        guiProjectionMatrixBuffer.close();
        ShaderUniforms.closeAll();
        RenderTargetHolder.INSTANCE.destroyAll();
        RendererHolder.INSTANCE.destroyAll();
    }

    public static <T extends DynamicUniformStorage.DynamicUniform> GpuBufferSlice writeDynamicUniform(
            String key,
            String label,
            int uniformSize,
            int initialCapacity,
            T uniform
    ) {
        return ShaderUniforms.write(key, label, uniformSize, initialCapacity, uniform);
    }

    public static void endDynamicUniformFrame() {
        ShaderUniforms.endFrame();
    }

    @Nullable
    public static LuminRenderTarget getActiveTarget() {
        return activeTarget;
    }

    public static double getGuiScale() {
        return ClientSetting.INSTANCE.getScale();
    }

    public static float getScaledWidth() {
        WindowRenderState windowState = mc.gameRenderer.getGameRenderState().windowRenderState;
        return (float) (windowState.width / getGuiScale());
    }

    public static float getScaledHeight() {
        WindowRenderState windowState = mc.gameRenderer.getGameRenderState().windowRenderState;
        return (float) (windowState.height / getGuiScale());
    }

    public static int getScaledWidthInt() {
        return (int) Math.ceil(getScaledWidth());
    }

    public static int getScaledHeightInt() {
        return (int) Math.ceil(getScaledHeight());
    }

    public static double toEpsilonMouseX(double mouseX) {
        return mouseX * mc.getWindow().getGuiScale() / getGuiScale();
    }

    public static double toEpsilonMouseY(double mouseY) {
        return mouseY * mc.getWindow().getGuiScale() / getGuiScale();
    }

    public static int toEpsilonMouseX(int mouseX) {
        return (int) Math.round(toEpsilonMouseX((double) mouseX));
    }

    public static int toEpsilonMouseY(int mouseY) {
        return (int) Math.round(toEpsilonMouseY((double) mouseY));
    }

    public static MouseButtonEvent toEpsilonMouseEvent(MouseButtonEvent event) {
        return new MouseButtonEvent(toEpsilonMouseX(event.x()), toEpsilonMouseY(event.y()), event.buttonInfo());
    }

    public static ScissorRect toFramebufferScissor(float x, float y, float width, float height) {
        double scale = getGuiScale();
        WindowRenderState windowState = mc.gameRenderer.getGameRenderState().windowRenderState;
        int sx = (int) Math.round(x * scale);
        int sy = (int) Math.round(windowState.height - (y + height) * scale);
        int sw = Math.max(0, (int) Math.round(width * scale));
        int sh = Math.max(0, (int) Math.round(height * scale));
        return clampFramebufferScissor(sx, sy, sw, sh);
    }

    public static ScissorRect toFramebufferScissor(float x, float y, float width, float height, float guiHeight) {
        double scale = getGuiScale();
        int sx = (int) Math.round(x * scale);
        int sy = (int) Math.round((guiHeight - y - height) * scale);
        int sw = Math.max(0, (int) Math.round(width * scale));
        int sh = Math.max(0, (int) Math.round(height * scale));
        return clampFramebufferScissor(sx, sy, sw, sh);
    }

    public static ScissorRect toFramebufferScissor(float x, float y, float width, float height, int guiHeight) {
        return toFramebufferScissor(x, y, width, height, (float) guiHeight);
    }

    public static ScissorRect toFramebufferScissor(float x, float y, float width, float height, double guiHeight) {
        return toFramebufferScissor(x, y, width, height, (float) guiHeight);
    }

    public static void applyOrthoProjection() {
        guiOrthoProjection
                .setupOrtho(-1000.0F, 1000.0F,
                        getScaledWidth(),
                        getScaledHeight(),
                        true
                );
        RenderSystem.setProjectionMatrix(
                guiProjectionMatrixBuffer.getBuffer(guiOrthoProjection), ProjectionType.ORTHOGRAPHIC);
    }

    /**
     * 获取当前活动目标的 colorTextureView 和 depthTextureView。
     * 如果设置了 activeTarget，则使用 activeTarget；否则使用主 RenderTarget。
     */
    public static GpuTextureView resolveColorView() {
        if (activeTarget != null) return activeTarget.colorView();
        return mc.getMainRenderTarget().getColorTextureView();
    }

    @Nullable
    public static GpuTextureView resolveDepthView() {
        if (activeTarget != null) return activeTarget.depthView();
        return mc.getMainRenderTarget().getDepthTextureView();
    }

    public static QuadRenderingInfo prepareQuadRendering(int vertexCount) {
        LuminRenderSystem.applyOrthoProjection();

        GpuTextureView colorView = resolveColorView();
        GpuTextureView depthView = resolveDepthView();
        if (colorView == null) return null;

        final var indexCount = vertexCount / 4 * 6;
        GpuBuffer ibo = getQuadIndexBuffer(indexCount);

        GpuBufferSlice dynamicUniforms = writeTransform(
                RenderSystem.getModelViewMatrix(),
                new Vector4f(1, 1, 1, 1),
                new Vector3f(0, 0, 0),
                TextureTransform.DEFAULT_TEXTURING.getMatrix()
        );

        return new QuadRenderingInfo(colorView, depthView, getQuadIndexType(), ibo, indexCount, dynamicUniforms);
    }

    public static GpuBuffer getQuadIndexBuffer(int indexCount) {
        RenderSystem.AutoStorageIndexBuffer autoIndices =
                RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        return autoIndices.getBuffer(indexCount);
    }

    public static VertexFormat.IndexType getQuadIndexType() {
        RenderSystem.AutoStorageIndexBuffer autoIndices =
                RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        return autoIndices.type();
    }

    public static GpuBufferSlice writeTransform(Matrix4fc modelView, Vector4fc colorModulator, Vector3fc modelOffset, Matrix4fc textureMatrix) {
        return RenderSystem.getDynamicUniforms().writeTransform(
                modelView, colorModulator, modelOffset, textureMatrix
        );
    }

    private static ScissorRect clampFramebufferScissor(int x, int y, int width, int height) {
        int areaWidth;
        int areaHeight;
        if (activeTarget != null) {
            areaWidth = activeTarget.width();
            areaHeight = activeTarget.height();
        } else {
            WindowRenderState windowState = mc.gameRenderer.getGameRenderState().windowRenderState;
            areaWidth = windowState.width;
            areaHeight = windowState.height;
        }

        int left = Math.clamp(x, 0, areaWidth);
        int top = Math.clamp(y, 0, areaHeight);
        int right = Math.clamp(x + width, 0, areaWidth);
        int bottom = Math.clamp(y + height, 0, areaHeight);
        return new ScissorRect(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
    }

    public record ScissorRect(int x, int y, int width, int height) {
    }

    public record QuadRenderingInfo(
            GpuTextureView colorView,
            @Nullable GpuTextureView depthView,
            VertexFormat.IndexType indexType,
            GpuBuffer ibo,
            int indexCount,
            GpuBufferSlice dynamicUniforms
    ) {
    }

    private static final class ShaderUniforms {

        private static final java.util.Map<String, LuminDynamicUniforms<DynamicUniformStorage.DynamicUniform>> UNIFORMS = new java.util.HashMap<>();

        private ShaderUniforms() {
        }

        @SuppressWarnings("unchecked")
        private static <T extends DynamicUniformStorage.DynamicUniform> GpuBufferSlice write(
                String key,
                String label,
                int uniformSize,
                int initialCapacity,
                T uniform
        ) {
            LuminDynamicUniforms<T> storage = (LuminDynamicUniforms<T>) UNIFORMS.computeIfAbsent(key, ignored ->
                    new LuminDynamicUniforms<>(label, uniformSize, initialCapacity));
            return storage.write(uniform);
        }

        private static void endFrame() {
            UNIFORMS.values().forEach(LuminDynamicUniforms::endFrame);
        }

        private static void closeAll() {
            UNIFORMS.values().forEach(LuminDynamicUniforms::close);
            UNIFORMS.clear();
        }

    }

    public static final class LuminRenderTarget implements AutoCloseable {

        private LuminTexture colorTexture;
        private GpuTexture depthTexture;
        private GpuTextureView depthView;
        private final Identifier identifier;
        private int width;
        private int height;

        private LuminRenderTarget(String name, int width, int height) {
            this.width = width;
            this.height = height;
            this.identifier = ResourceLocationUtils.getIdentifier("lumin-rt" + name);
            createTextures();
        }

        public static LuminRenderTarget create(String name, int width, int height) {
            return RenderTargetHolder.INSTANCE.register(new LuminRenderTarget(name, width, height));
        }

        private void createTextures() {
            var device = RenderSystem.getDevice();

            final var colorTexture = device.createTexture(
                    "lumin-rt-color",
                    GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC,
                    TextureFormat.RGBA8,
                    width, height, 1, 1
            );
            final var colorView = device.createTextureView(colorTexture);

            depthTexture = device.createTexture(
                    "lumin-rt-depth",
                    GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC,
                    TextureFormat.DEPTH32,
                    width, height, 1, 1
            );
            depthView = device.createTextureView(depthTexture);

            final var sampler = RenderSystem.getDevice().createSampler(
                    AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                    FilterMode.NEAREST, FilterMode.NEAREST,
                    1, OptionalDouble.empty()
            );

            this.colorTexture = new LuminTexture(colorTexture, colorView, sampler);

            mc.getTextureManager().register(identifier, getColorTexture());
        }

        public void resize(int newWidth, int newHeight) {
            if (newWidth == width && newHeight == height) return;
            destroyTextures();
            width = newWidth;
            height = newHeight;
            createTextures();
        }

        public Identifier getIdentifier() {
            return identifier;
        }

        public void clear() {
            var encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.clearColorAndDepthTextures(colorTexture.getTexture(), 0, depthTexture, 1.0);
        }

        public GpuTextureView colorView() {
            return colorTexture.getTextureView();
        }

        public GpuTextureView depthView() {
            return depthView;
        }

        public GpuTexture colorTexture() {
            return colorTexture.getTexture();
        }

        public GpuSampler sampler() {
            return colorTexture.getSampler();
        }

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }

        public LuminTexture getColorTexture() {
            return colorTexture;
        }

        private void destroyTextures() {
            mc.getTextureManager().release(identifier);
            if (depthView != null) depthView.close();
            if (depthTexture != null) depthTexture.close();
        }

        @Override
        public void close() {
            destroyTextures();
        }
    }

}
