package com.github.epsilon.graphics.vulkan.compute;

import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.vulkan.descriptor.DescriptorLayout;
import com.github.epsilon.graphics.vulkan.descriptor.DescriptorLayoutSpec;
import com.mojang.blaze3d.vulkan.VulkanUtils;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.util.Objects;

import static org.lwjgl.vulkan.VK12.*;

/**
 * Vulkan 计算管线封装。
 * <p>
 * 内部负责创建 shader module、descriptor set layout、pipeline layout 与 compute pipeline。
 */
public final class VulkanComputePipeline implements AutoCloseable {

    private final VkDevice device;
    private final DescriptorLayout descriptorLayout;
    private final long shaderModule;
    private final long pipelineLayout;
    private final long pipeline;

    /**
     * 使用默认入口点 main 创建计算管线。
     */
    public VulkanComputePipeline(VkDevice device, ByteBuffer computeShaderSpirv, DescriptorLayoutSpec descriptorLayoutSpec) {
        this(device, computeShaderSpirv, "main", descriptorLayoutSpec);
    }

    /**
     * 创建计算管线。
     *
     * @param entryPoint shader 入口点名称
     */
    public VulkanComputePipeline(VkDevice device, ByteBuffer computeShaderSpirv, String entryPoint, DescriptorLayoutSpec descriptorLayoutSpec) {
        this.device = Objects.requireNonNull(device, "device");
        Objects.requireNonNull(computeShaderSpirv, "computeShaderSpirv");
        Objects.requireNonNull(entryPoint, "entryPoint");
        Objects.requireNonNull(descriptorLayoutSpec, "descriptorLayoutSpec");

        this.descriptorLayout = DescriptorLayout.create(device, descriptorLayoutSpec);
        this.shaderModule = createShaderModule(device, computeShaderSpirv);
        this.pipelineLayout = createPipelineLayout(device, this.descriptorLayout.handle());
        this.pipeline = createComputePipeline(device, this.shaderModule, this.pipelineLayout, entryPoint);
    }

    /**
     * 返回 VkPipeline 句柄。
     */
    public long pipeline() {
        return pipeline;
    }

    /**
     * 返回 VkPipelineLayout 句柄。
     */
    public long pipelineLayout() {
        return pipelineLayout;
    }

    /**
     * 返回 VkDescriptorSetLayout 句柄。
     */
    public long descriptorSetLayout() {
        return descriptorLayout.handle();
    }

    /**
     * 返回 DescriptorLayout 封装对象。
     */
    public DescriptorLayout descriptorLayout() {
        return descriptorLayout;
    }

    @Override
    /**
     * 销毁计算管线相关 Vulkan 资源。
     */
    public void close() {
        vkDestroyPipeline(device, pipeline, null);
        vkDestroyPipelineLayout(device, pipelineLayout, null);
        vkDestroyShaderModule(device, shaderModule, null);
        descriptorLayout.close();
    }

    private long createShaderModule(VkDevice device, ByteBuffer shaderSpirv) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var createInfo = VkShaderModuleCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
                    .pCode(shaderSpirv);

            var pShaderModule = stack.mallocLong(1);
            VulkanUtils.crashIfFailure(
                    LuminRenderSystem.vulkanContext.blz3dDevice(),
                    vkCreateShaderModule(device, createInfo, null, pShaderModule),
                    "Can't create compute shader module"
            );
            return pShaderModule.get(0);
        }
    }

    private long createPipelineLayout(VkDevice device, long descriptorSetLayout) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var descriptorLayouts = stack.mallocLong(1).put(0, descriptorSetLayout);

            var createInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                    .pSetLayouts(descriptorLayouts);

            var pPipelineLayout = stack.mallocLong(1);
            VulkanUtils.crashIfFailure(
                    LuminRenderSystem.vulkanContext.blz3dDevice(),
                    vkCreatePipelineLayout(device, createInfo, null, pPipelineLayout),
                    "Can't create compute pipeline layout"
            );
            return pPipelineLayout.get(0);
        }
    }

    private long createComputePipeline(VkDevice device, long shaderModule, long pipelineLayout, String entryPoint) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var stage = VkPipelineShaderStageCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK_SHADER_STAGE_COMPUTE_BIT)
                    .module(shaderModule)
                    .pName(stack.UTF8(entryPoint));

            var pipelineInfo = VkComputePipelineCreateInfo.calloc(1, stack)
                    .sType(VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO)
                    .stage(stage)
                    .layout(pipelineLayout);

            var pPipeline = stack.mallocLong(1);
            VulkanUtils.crashIfFailure(
                    LuminRenderSystem.vulkanContext.blz3dDevice(),
                    vkCreateComputePipelines(device, VK_NULL_HANDLE, pipelineInfo, null, pPipeline),
                    "Can't create compute pipeline"
            );
            return pPipeline.get(0);
        }
    }

}
