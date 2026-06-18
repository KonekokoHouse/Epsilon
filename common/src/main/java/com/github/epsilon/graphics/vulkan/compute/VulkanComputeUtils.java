package com.github.epsilon.graphics.vulkan.compute;

import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.vulkan.buffer.VulkanOutputBuffer;
import com.github.epsilon.graphics.vulkan.buffer.VulkanStd430Buffer;
import com.mojang.blaze3d.vulkan.VulkanUtils;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.lwjgl.vulkan.KHRSynchronization2.*;
import static org.lwjgl.vulkan.VK12.*;

public final class VulkanComputeUtils implements AutoCloseable {

    private final VkDevice device;
    private final long cmdPool;
    private final VkQueue queue;
    private final VkCommandBuffer cmdBuf;
    private final long fence;

    public VulkanComputeUtils() {
        this(
                LuminRenderSystem.vulkanContext.device(),
                LuminRenderSystem.vulkanContext.cmdPool(),
                LuminRenderSystem.vulkanContext.graphicsQueue().vkQueue()
        );
    }

    public VulkanComputeUtils(VkDevice device, long cmdPool, VkQueue queue) {
        this.device = device;
        this.cmdPool = cmdPool;
        this.queue = queue;
        this.cmdBuf = allocateCommandBuffer();
        this.fence = createFence();
    }

    /**
     * 录制并提交一次完整的「上传 → compute → 回读」流程，阻塞等待完成。
     *
     * @param inputs        需要上传到 GPU 的输入缓冲（staging → gpu copy）
     * @param output        计算结果输出缓冲（gpu → readback copy）
     * @param pipeline      已创建的 compute pipeline
     * @param descriptorSet 已绑定所有 buffer 的 descriptor set 句柄
     * @param groupCountX   dispatch X 轴工作组数
     * @param groupCountY   dispatch Y 轴工作组数
     * @param groupCountZ   dispatch Z 轴工作组数
     * @param readbackBytes 需要回读的字节数（从 output gpu buffer offset 0 起）
     */
    public void dispatchAndWait(
            VulkanStd430Buffer[] inputs,
            VulkanOutputBuffer output,
            VulkanComputePipeline pipeline,
            long descriptorSet,
            int groupCountX,
            int groupCountY,
            int groupCountZ,
            long readbackBytes
    ) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            vkResetCommandBuffer(cmdBuf, 0);
            vkResetFences(device, stack.longs(fence));

            var beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                    .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
            VulkanUtils.crashIfFailure(
                    LuminRenderSystem.vulkanContext.blz3dDevice(),
                    vkBeginCommandBuffer(cmdBuf, beginInfo),
                    "Failed to begin command buffer"
            );

            for (VulkanStd430Buffer input : inputs) {
                input.map(cmdBuf);
            }

            var barriers = VkBufferMemoryBarrier2KHR.calloc(inputs.length, stack);

            for (int i = 0; i < inputs.length; i++) {
                barriers.get(i)
                        .sType(VK_STRUCTURE_TYPE_BUFFER_MEMORY_BARRIER_2_KHR)
                        .srcStageMask(VK_PIPELINE_STAGE_2_TRANSFER_BIT_KHR)
                        .srcAccessMask(VK_ACCESS_2_TRANSFER_WRITE_BIT_KHR)
                        .dstStageMask(VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT_KHR)
                        .dstAccessMask(VK_ACCESS_2_SHADER_STORAGE_READ_BIT_KHR)
                        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .buffer(inputs[i].gpuBuffer())
                        .offset(0)
                        .size(inputs[i].size());
            }

            var preComputeDep = VkDependencyInfoKHR.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DEPENDENCY_INFO_KHR)
                    .pBufferMemoryBarriers(barriers);
            vkCmdPipelineBarrier2KHR(cmdBuf, preComputeDep);

            vkCmdBindPipeline(cmdBuf, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline.pipeline());
            vkCmdBindDescriptorSets(cmdBuf, VK_PIPELINE_BIND_POINT_COMPUTE,
                    pipeline.pipelineLayout(), 0,
                    stack.longs(descriptorSet), null);
            vkCmdDispatch(cmdBuf, groupCountX, groupCountY, groupCountZ);

            var postCompute = VkBufferMemoryBarrier2KHR.calloc(1, stack)
                    .sType(VK_STRUCTURE_TYPE_BUFFER_MEMORY_BARRIER_2_KHR)
                    .srcStageMask(VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT_KHR)
                    .srcAccessMask(VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT_KHR)
                    .dstStageMask(VK_PIPELINE_STAGE_2_TRANSFER_BIT_KHR)
                    .dstAccessMask(VK_ACCESS_2_TRANSFER_READ_BIT_KHR)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .buffer(output.gpuBuffer())
                    .offset(0)
                    .size(readbackBytes);

            var postComputeDep = VkDependencyInfoKHR.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DEPENDENCY_INFO_KHR)
                    .pBufferMemoryBarriers(postCompute);
            vkCmdPipelineBarrier2KHR(cmdBuf, postComputeDep);

            output.copyToReadback(cmdBuf, readbackBytes);

            VulkanUtils.crashIfFailure(
                    LuminRenderSystem.vulkanContext.blz3dDevice(),
                    vkEndCommandBuffer(cmdBuf),
                    "Failed to end command buffer"
            );

            var submitInfo = VkSubmitInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                    .pCommandBuffers(stack.pointers(cmdBuf));
            VulkanUtils.crashIfFailure(
                    LuminRenderSystem.vulkanContext.blz3dDevice(),
                    vkQueueSubmit(queue, submitInfo, fence),
                    "Failed to submit compute queue"
            );
            VulkanUtils.crashIfFailure(
                    LuminRenderSystem.vulkanContext.blz3dDevice(),
                    vkWaitForFences(device, stack.longs(fence), true, Long.MAX_VALUE),
                    "Failed to wait for compute fence"
            );
        }
    }

    /**
     * 读取 output buffer 中 [offset, offset+byteCount) 区间的数据。
     * <p>
     * 必须在 {@link #dispatchAndWait} 返回之后调用，此时 fence 已确保
     * GPU → readback copy 完成，{@code readMapped} 内部会 invalidate
     * host cache 以保证 CPU 看到最新数据。
     */
    public ByteBuffer readOutput(VulkanOutputBuffer output, long offset, long byteCount) {
        return output.readMapped(offset, byteCount);
    }

    /**
     * 读取 output buffer 从 offset 0 起的 byteCount 字节。
     */
    public ByteBuffer readOutput(VulkanOutputBuffer output, long byteCount) {
        return output.readMapped(0, byteCount);
    }

    /**
     * 读取 output buffer 中单个 float。
     */
    public float readFloat(VulkanOutputBuffer output, int index) {
        ByteBuffer buf = output.readMapped((long) index * Float.BYTES, Float.BYTES);
        return buf.order(ByteOrder.LITTLE_ENDIAN).getFloat(0);
    }

    /**
     * 批量读取 output buffer 中 count 个 float。
     */
    public float[] readFloats(VulkanOutputBuffer output, int count) {
        if (count <= 0) return new float[0];
        ByteBuffer buf = output.readMapped(0, (long) count * Float.BYTES);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        float[] result = new float[count];
        for (int i = 0; i < count; i++) {
            result[i] = buf.getFloat(i * Float.BYTES);
        }
        return result;
    }

    // ─── 内部 ───────────────────────────────────────────────────────────────

    private VkCommandBuffer allocateCommandBuffer() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var pCmd = stack.mallocPointer(1);
            var allocInfo = VkCommandBufferAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                    .commandPool(cmdPool)
                    .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandBufferCount(1);
            VulkanUtils.crashIfFailure(
                    LuminRenderSystem.vulkanContext.blz3dDevice(),
                    vkAllocateCommandBuffers(device, allocInfo, pCmd),
                    "Failed to allocate compute command buffer"
            );
            return new VkCommandBuffer(pCmd.get(0), device);
        }
    }

    private long createFence() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var pFence = stack.mallocLong(1);
            var info = VkFenceCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO);
            VulkanUtils.crashIfFailure(
                    LuminRenderSystem.vulkanContext.blz3dDevice(),
                    vkCreateFence(device, info, null, pFence),
                    "Failed to create compute fence"
            );
            return pFence.get(0);
        }
    }

    @Override
    public void close() {
        vkDestroyFence(device, fence, null);
        vkFreeCommandBuffers(device, cmdPool, cmdBuf);
    }
}

