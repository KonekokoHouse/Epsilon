package com.github.epsilon.graphics.vulkan;

import com.mojang.blaze3d.systems.GpuDeviceBackend;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanQueue;
import com.mojang.blaze3d.vulkan.VulkanUtils;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkDevice;

import javax.annotation.Nullable;

import static org.lwjgl.vulkan.VK10.*;

/**
 * Vulkan 上下文封装。
 * <p>
 * 负责从 Blaze3D 后端提取 Vulkan 设备、VMA 分配器、队列与命令池句柄。
 * 当当前后端不是 Vulkan 时，本上下文会保持未初始化状态。
 */
public class LuminVulkanContext {

    private @Nullable VulkanDevice blz3dDevice;
    private @Nullable VkDevice device;
    private long vma;

    // Queues
    private VulkanQueue graphicsQueue;
    private VulkanQueue computeQueue;
    private VulkanQueue transferQueue;

    private long cmdPool;

    public LuminVulkanContext() {
        GpuDeviceBackend backend = getBackend();
        if (!(backend instanceof VulkanDevice)) {
            return;
        }

        VulkanDevice blz3dDevice = (VulkanDevice) backend;

        this.blz3dDevice = blz3dDevice;
        this.device = blz3dDevice.vkDevice();
        this.vma = blz3dDevice.vma();

        this.graphicsQueue = blz3dDevice.graphicsQueue();
        this.computeQueue = blz3dDevice.computeQueue();
        this.transferQueue = blz3dDevice.transferQueue();

        try (MemoryStack stack = MemoryStack.stackPush()) {

            VkCommandPoolCreateInfo poolInfo = VkCommandPoolCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
                    .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
                    .queueFamilyIndex(graphicsQueue.queueFamilyIndex());

            final var cmdPool = stack.callocLong(1);

            VulkanUtils.crashIfFailure(blz3dDevice,
                    vkCreateCommandPool(device, poolInfo, null, cmdPool),
                    "Failed to create command pool"
            );

            this.cmdPool = cmdPool.get();

        }
    }

    /**
     * 获取 Vulkan 逻辑设备。
     *
     * @return Vulkan 设备句柄包装
     * @throws IllegalStateException 当当前后端不是 Vulkan 或尚未初始化时抛出
     */
    public VkDevice device() {
        if (this.device == null) {
            throw new IllegalStateException("Vulkan device is not initialized. Make sure to initialize the Vulkan context properly.");
        }
        return this.device;
    }

    /**
     * 获取 Blaze3D VulkanDevice 引用。
     */
    public VulkanDevice blz3dDevice() {
        if (this.blz3dDevice == null) {
            throw new IllegalStateException("VulkanDevice is not initialized. Make sure to initialize the Vulkan context properly.");
        }
        return this.blz3dDevice;
    }

    /**
     * 获取 VMA 分配器原生句柄。
     */
    public long vma() {
        return this.vma;
    }

    /**
     * 获取用于分配命令缓冲区的命令池句柄。
     */
    public long cmdPool() {
        return this.cmdPool;
    }

    /**
     * 判断当前是否处于可用的 Vulkan 后端。
     */
    public boolean isAvailable() {
        return this.device != null;
    }

    /**
     * 获取图形队列。
     */
    public VulkanQueue graphicsQueue() {
        return this.graphicsQueue;
    }

    /**
     * 获取计算队列。
     */
    public VulkanQueue computeQueue() {
        return this.computeQueue;
    }

    /**
     * 获取传输队列。
     */
    public VulkanQueue transferQueue() {
        return this.transferQueue;
    }

    public void destroy() {
        if (device == null || cmdPool == 0L) {
            return;
        }

        vkDestroyCommandPool(device, cmdPool, null);
        cmdPool = 0L;
    }

    private static GpuDeviceBackend getBackend() {
        return RenderSystem.getDevice().backend;
    }

}
