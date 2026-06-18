package com.github.epsilon.graphics;

import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.BindGroupLayouts;

import java.util.Optional;


public class LuminRenderPipelines {

    private final static RenderPipeline.Snippet NO_BLEND_DEPTH_SNIPPET = RenderPipeline.builder()
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .buildSnippet();

    // 26.2 的 GUI 字体提交会直接使用 TextRenderable.guiPipeline()。
    // 自定义 TTF shader 也读取 dynamictransforms/projection，因此这里必须和原版 GUI_TEXT 一样声明
    // GLOBALS、MATRICES_PROJECTION、SAMPLER0 和 GUI 深度状态；否则 Vulkan 后端会按不完整的 bind group 渲染。
    private final static RenderPipeline.Snippet GUI_TTF_FONT_SNIPPET = RenderPipeline.builder()
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(Optional.<DepthStencilState>empty())
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withShaderDefine("IS_GUI")
            .buildSnippet();

    public final static RenderPipeline RECTANGLE = RenderPipeline.builder(NO_BLEND_DEPTH_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier("pipelines/rectangle"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withVertexShader(ResourceLocationUtils.getIdentifier("rectangle"))
            .withFragmentShader(ResourceLocationUtils.getIdentifier("rectangle"))
            .withCull(false)
            .build();

    public final static RenderPipeline TTF_FONT_AA = RenderPipeline.builder(GUI_TTF_FONT_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier("pipelines/ttf_font_aa"))
            .withVertexShader(ResourceLocationUtils.getIdentifier("ttf_font"))
            .withFragmentShader(ResourceLocationUtils.getIdentifier("ttf_font_aa"))
            .withCull(false)
            .build();

    public final static RenderPipeline TTF_FONT_NO_AA = RenderPipeline.builder(GUI_TTF_FONT_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier("pipelines/ttf_font_no_aa"))
            .withVertexShader(ResourceLocationUtils.getIdentifier("ttf_font"))
            .withFragmentShader(ResourceLocationUtils.getIdentifier("ttf_font_no_aa"))
            .withCull(false)
            .build();

    public final static RenderPipeline ROUND_RECT = RenderPipeline.builder(NO_BLEND_DEPTH_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier("pipelines/round_rectangle"))
            .withVertexBinding(0, LuminVertexFormats.ROUND_RECT)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withVertexShader(ResourceLocationUtils.getIdentifier("round_rectangle"))
            .withFragmentShader(ResourceLocationUtils.getIdentifier("round_rectangle"))
            .withCull(false)
            .build();

    public final static RenderPipeline ROUND_RECT_OUTLINE = RenderPipeline.builder(NO_BLEND_DEPTH_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier("pipelines/round_rectangle_outline"))
            .withVertexBinding(0, LuminVertexFormats.ROUND_RECT_OUTLINE)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withVertexShader(ResourceLocationUtils.getIdentifier("round_rectangle_outline"))
            .withFragmentShader(ResourceLocationUtils.getIdentifier("round_rectangle_outline"))
            .withCull(false)
            .build();

    public final static RenderPipeline SHADOW = RenderPipeline.builder(NO_BLEND_DEPTH_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier("pipelines/shadow"))
            .withVertexBinding(0, LuminVertexFormats.ROUND_RECT)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withVertexShader(ResourceLocationUtils.getIdentifier("shadow"))
            .withFragmentShader(ResourceLocationUtils.getIdentifier("shadow"))
            .withCull(false)
            .build();

    public final static RenderPipeline TEXTURE = RenderPipeline.builder(NO_BLEND_DEPTH_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier("pipelines/texture"))
            .withVertexBinding(0, LuminVertexFormats.TEXTURE)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withVertexShader(ResourceLocationUtils.getIdentifier("texture"))
            .withFragmentShader(ResourceLocationUtils.getIdentifier("texture"))
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withCull(false)
            .build();

    public final static RenderPipeline TRIANGLE = RenderPipeline.builder(NO_BLEND_DEPTH_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier("pipelines/triangle"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withVertexShader(ResourceLocationUtils.getIdentifier("triangle"))
            .withFragmentShader(ResourceLocationUtils.getIdentifier("triangle"))
            .withCull(false)
            .build();

}
