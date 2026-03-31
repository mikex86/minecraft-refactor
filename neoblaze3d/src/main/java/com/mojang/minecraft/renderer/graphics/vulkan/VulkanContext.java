package com.mojang.minecraft.renderer.graphics.vulkan;

import com.mojang.minecraft.renderer.graphics.GraphicsFactory;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

import static org.lwjgl.vulkan.KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME;

final class VulkanContext {
    private static final String VALIDATION_LAYER = "VK_LAYER_KHRONOS_validation";
    // Allow CPU/GPU overlap. One in-flight frame causes strict serialization
    // and significantly reduces throughput.
    private static final int FRAMES_IN_FLIGHT = 3;
    private static final int DEFAULT_UNIFORM_RING_SIZE = 4 * 1024 * 1024;
    private static final int DEFAULT_INDIRECT_RING_SIZE = 4 * 1024 * 1024;
    private static final int DEFAULT_IMMEDIATE_UPLOAD_BUFFER_SIZE = 4 * 1024 * 1024;
    private static final int DEFAULT_MAX_DESCRIPTOR_SETS_PER_FRAME = 8192;
    private static final int MAX_PENDING_BUFFER_DESTROYS_PER_DRAIN = 2048;

    private final long windowHandle;

    private VkInstance instance;
    private boolean validationLayersEnabled;
    private long debugMessenger;
    private VkDebugUtilsMessengerCallbackEXT debugMessengerCallback;
    private long surface;
    private VkPhysicalDevice physicalDevice;
    private VkDevice device;
    private VkQueue graphicsQueue;
    private VkQueue presentQueue;
    private int graphicsQueueFamily = -1;
    private int presentQueueFamily = -1;

    private long swapchain;
    private int swapchainImageFormat;
    private int swapchainWidth;
    private int swapchainHeight;
    private long[] swapchainImages;
    private boolean[] swapchainImageInitialized;
    private long[] swapchainRenderFinishedSemaphores;
    private long[] swapchainImageViews;
    private long[] framebuffers;

    private long renderPass;
    private int renderPassColorFormat = -1;
    private int renderPassDepthFormat = -1;

    private int depthFormat;
    private long depthImage;
    private long depthImageMemory;
    private long depthImageView;

    private long commandPool;
    private long pipelineCache;
    private VkCommandBuffer immediateCommandBuffer;
    private long immediateFence;
    private long immediateUploadBuffer;
    private long immediateUploadMemory;
    private long immediateUploadMappedAddress;
    private int immediateUploadCapacity;
    private final Object immediateSubmitLock = new Object();
    private final Object deferredFreeSignal = new Object();
    private Thread deferredFreeThread;
    private volatile boolean deferredFreeThreadRunning;

    private final Frame[] frames = new Frame[FRAMES_IN_FLIGHT];
    private final long[] submittedSerialByFrame = new long[FRAMES_IN_FLIGHT];
    private final AtomicLong completedSubmissionSerial = new AtomicLong(0L);
    private long nextSubmissionSerial = 1L;
    private int currentFrame;
    private int currentSwapchainImageIndex = -1;
    private int lastSubmittedFrameIndex = -1;
    private boolean frameActive;

    private int minUniformBufferOffsetAlignment = 16;
    private boolean multiDrawIndirectEnabled;

    private VulkanTexture fallbackTexture;
    private final Map<Long, Long> liveBufferMemory = new ConcurrentHashMap<>();
    private final Queue<PendingBufferDestroy> pendingBufferDestroys = new ConcurrentLinkedQueue<>();

    VulkanContext(long windowHandle) {
        if (windowHandle == 0L) {
            throw new IllegalArgumentException("windowHandle cannot be 0");
        }
        this.windowHandle = windowHandle;
    }

    void initialize() {
        createInstance();
        createSurface();
        pickPhysicalDevice();
        createLogicalDevice();
        queryDeviceLimits();
        createCommandPool();
        createImmediateResources();
        depthFormat = findDepthFormat();
        createSwapchainResources();
        createPipelineCache();
        createPerFrameResources();
        startDeferredFreeThread();
        this.fallbackTexture = createFallbackTexture();
    }

    void dispose() {
        if (device != null) {
            stopDeferredFreeThread();
            VK10.vkDeviceWaitIdle(device);
            markAllSubmittedAsCompleted();
            drainAllPendingBufferDestroys();

            if (fallbackTexture != null && !fallbackTexture.isDisposed()) {
                fallbackTexture.dispose();
                fallbackTexture = null;
            }

            destroyPerFrameResources();
            destroySwapchainResources();

            if (renderPass != VK10.VK_NULL_HANDLE) {
                VK10.vkDestroyRenderPass(device, renderPass, null);
                renderPass = VK10.VK_NULL_HANDLE;
                renderPassColorFormat = -1;
                renderPassDepthFormat = -1;
            }

            if (pipelineCache != VK10.VK_NULL_HANDLE) {
                VK10.vkDestroyPipelineCache(device, pipelineCache, null);
                pipelineCache = VK10.VK_NULL_HANDLE;
            }

            destroyImmediateResources();
            if (commandPool != VK10.VK_NULL_HANDLE) {
                VK10.vkDestroyCommandPool(device, commandPool, null);
                commandPool = VK10.VK_NULL_HANDLE;
            }

            drainAllPendingBufferDestroys();
            destroyTrackedBuffers();
            VK10.vkDestroyDevice(device, null);
            device = null;
        }

        if (instance != null && surface != VK10.VK_NULL_HANDLE) {
            KHRSurface.vkDestroySurfaceKHR(instance, surface, null);
            surface = VK10.VK_NULL_HANDLE;
        }

        if (instance != null && debugMessenger != VK10.VK_NULL_HANDLE) {
            EXTDebugUtils.vkDestroyDebugUtilsMessengerEXT(instance, debugMessenger, null);
            debugMessenger = VK10.VK_NULL_HANDLE;
        }
        if (debugMessengerCallback != null) {
            debugMessengerCallback.free();
            debugMessengerCallback = null;
        }

        if (instance != null) {
            VK10.vkDestroyInstance(instance, null);
            instance = null;
        }
    }

    VkDevice getDevice() {
        return device;
    }

    VkPhysicalDevice getPhysicalDevice() {
        return physicalDevice;
    }

    long getRenderPass() {
        return renderPass;
    }

    long getPipelineCache() {
        return pipelineCache;
    }

    int getSwapchainWidth() {
        return swapchainWidth;
    }

    int getSwapchainHeight() {
        return swapchainHeight;
    }

    int getMinUniformBufferOffsetAlignment() {
        return minUniformBufferOffsetAlignment;
    }

    boolean supportsMultiDrawIndirect() {
        return multiDrawIndirectEnabled;
    }

    VulkanTexture getFallbackTexture() {
        return fallbackTexture;
    }

    Frame beginFrame() {
        ensureSwapchainMatchesFramebuffer();

        Frame frame = frames[currentFrame];
        if (frame == null) {
            throw new IllegalStateException("Frame resources are not initialized");
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer fences = stack.longs(frame.inFlightFence);
            int waitResult = VK10.vkWaitForFences(device, fences, true, Long.MAX_VALUE);
            checkVk(waitResult, "vkWaitForFences");
            long completedSerial = submittedSerialByFrame[currentFrame];
            if (completedSerial > 0L) {
                completedSubmissionSerial.accumulateAndGet(completedSerial, Math::max);
            }

            // If capture data from this frame slot was not consumed before reuse, drop it now.
            if (frame.captureBuffer != VK10.VK_NULL_HANDLE) {
                releaseFrameCapture(frame);
            }

            IntBuffer pImageIndex = stack.mallocInt(1);
            checkVk(VK10.vkResetFences(device, stack.longs(frame.imageAcquiredFence)), "vkResetFences(acquire)");
            int acquireResult = KHRSwapchain.vkAcquireNextImageKHR(
                    device,
                    swapchain,
                    Long.MAX_VALUE,
                    VK10.VK_NULL_HANDLE,
                    frame.imageAcquiredFence,
                    pImageIndex
            );

            if (acquireResult == KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR) {
                recreateSwapchain();
                return beginFrame();
            }
            if (acquireResult != VK10.VK_SUCCESS && acquireResult != KHRSwapchain.VK_SUBOPTIMAL_KHR) {
                checkVk(acquireResult, "vkAcquireNextImageKHR");
            }
            LongBuffer acquireFence = stack.longs(frame.imageAcquiredFence);
            int acquireFenceWait = VK10.vkWaitForFences(device, acquireFence, true, 0L);
            if (acquireFenceWait == VK10.VK_TIMEOUT) {
                acquireFenceWait = VK10.vkWaitForFences(device, acquireFence, true, Long.MAX_VALUE);
            }
            checkVk(acquireFenceWait, "vkWaitForFences(acquire)");

            currentSwapchainImageIndex = pImageIndex.get(0);
            checkVk(VK10.vkResetFences(device, fences), "vkResetFences(submit)");

            checkVk(VK10.vkResetCommandBuffer(frame.commandBuffer, 0), "vkResetCommandBuffer");

            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                    .flags(VK10.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
            checkVk(VK10.vkBeginCommandBuffer(frame.commandBuffer, beginInfo), "vkBeginCommandBuffer");

            if (swapchainImageInitialized != null
                    && currentSwapchainImageIndex >= 0
                    && currentSwapchainImageIndex < swapchainImageInitialized.length) {
                boolean initialized = swapchainImageInitialized[currentSwapchainImageIndex];
                int oldLayout = initialized
                        ? KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR
                        : VK10.VK_IMAGE_LAYOUT_UNDEFINED;
                int srcStage = initialized
                        ? VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
                        : VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
                recordImageBarrier(
                        frame.commandBuffer,
                        swapchainImages[currentSwapchainImageIndex],
                        VK10.VK_IMAGE_ASPECT_COLOR_BIT,
                        oldLayout,
                        VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                        0,
                        VK10.VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT,
                        srcStage,
                        VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
                );
                swapchainImageInitialized[currentSwapchainImageIndex] = true;
            }

            checkVk(VK10.vkResetDescriptorPool(device, frame.descriptorPool, 0), "vkResetDescriptorPool");
            frame.uniformWriteOffset = 0;
            frame.indirectWriteOffset = 0;
            frame.submissionSerial = nextSubmissionSerial++;

            frameActive = true;
            return frame;
        }
    }

    void endFrame() {
        if (!frameActive) {
            throw new IllegalStateException("No active frame to end");
        }

        Frame frame = frames[currentFrame];
        long renderFinishedSemaphore = swapchainRenderFinishedSemaphores[currentSwapchainImageIndex];

        recordImageBarrier(
                frame.commandBuffer,
                swapchainImages[currentSwapchainImageIndex],
                VK10.VK_IMAGE_ASPECT_COLOR_BIT,
                VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
                VK10.VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT,
                0,
                VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                VK10.VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT
        );

        checkVk(VK10.vkEndCommandBuffer(frame.commandBuffer), "vkEndCommandBuffer");

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_SUBMIT_INFO)
                    .pCommandBuffers(stack.pointers(frame.commandBuffer.address()))
                    .pSignalSemaphores(stack.longs(renderFinishedSemaphore));

            checkVk(VK10.vkQueueSubmit(graphicsQueue, submitInfo, frame.inFlightFence), "vkQueueSubmit");
            submittedSerialByFrame[currentFrame] = frame.submissionSerial;

            VkPresentInfoKHR presentInfo = VkPresentInfoKHR.calloc(stack)
                    .sType(KHRSwapchain.VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
                    .pWaitSemaphores(stack.longs(renderFinishedSemaphore))
                    .swapchainCount(1)
                    .pSwapchains(stack.longs(swapchain))
                    .pImageIndices(stack.ints(currentSwapchainImageIndex));

            int presentResult = KHRSwapchain.vkQueuePresentKHR(presentQueue, presentInfo);
            if (presentResult == KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR || presentResult == KHRSwapchain.VK_SUBOPTIMAL_KHR) {
                recreateSwapchain();
            } else {
                checkVk(presentResult, "vkQueuePresentKHR");
            }
        }

        frameActive = false;
        currentSwapchainImageIndex = -1;
        lastSubmittedFrameIndex = currentFrame;
        currentFrame = (currentFrame + 1) % FRAMES_IN_FLIGHT;
        signalDeferredFreeThread();
    }

    void waitIdle() {
        if (device != null) {
            VK10.vkDeviceWaitIdle(device);
            markAllSubmittedAsCompleted();
            drainAllPendingBufferDestroys();
        }
    }

    Frame getCurrentFrame() {
        return frames[currentFrame];
    }

    int getCurrentSwapchainImageIndex() {
        if (currentSwapchainImageIndex < 0) {
            throw new IllegalStateException("No active swapchain image");
        }
        return currentSwapchainImageIndex;
    }

    long getCurrentFramebuffer() {
        return framebuffers[getCurrentSwapchainImageIndex()];
    }

    boolean isFrameActive() {
        return frameActive;
    }

    VkQueue getGraphicsQueue() {
        return graphicsQueue;
    }

    int getGraphicsQueueFamily() {
        return graphicsQueueFamily;
    }

    long createBuffer(long sizeInBytes, int usageFlags, int memoryPropertyFlags, LongBuffer pBuffer, LongBuffer pMemory) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo createInfo = VkBufferCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                    .size(sizeInBytes)
                    .usage(usageFlags)
                    .sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);

            checkVk(VK10.vkCreateBuffer(device, createInfo, null, pBuffer), "vkCreateBuffer");

            VkMemoryRequirements requirements = VkMemoryRequirements.calloc(stack);
            VK10.vkGetBufferMemoryRequirements(device, pBuffer.get(0), requirements);

            VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                    .allocationSize(requirements.size())
                    .memoryTypeIndex(findMemoryType(requirements.memoryTypeBits(), memoryPropertyFlags));

            checkVk(VK10.vkAllocateMemory(device, allocInfo, null, pMemory), "vkAllocateMemory");
            checkVk(VK10.vkBindBufferMemory(device, pBuffer.get(0), pMemory.get(0), 0), "vkBindBufferMemory");
            liveBufferMemory.put(pBuffer.get(0), pMemory.get(0));
            return requirements.size();
        }
    }

    void destroyBufferWithMemory(long buffer, long memory) {
        if (buffer != VK10.VK_NULL_HANDLE) {
            liveBufferMemory.remove(buffer);
        }
        if (device == null) {
            return;
        }
        if (buffer == VK10.VK_NULL_HANDLE && memory == VK10.VK_NULL_HANDLE) {
            return;
        }
        long completedSerial = completedSubmissionSerial.get();
        long retireSubmissionSerial;
        if (frameActive) {
            retireSubmissionSerial = frames[currentFrame].submissionSerial;
        } else if (lastSubmittedFrameIndex >= 0) {
            retireSubmissionSerial = submittedSerialByFrame[lastSubmittedFrameIndex];
        } else {
            retireSubmissionSerial = completedSerial;
        }
        if (retireSubmissionSerial <= completedSerial) {
            destroyBufferWithMemoryNow(buffer, memory);
            return;
        }
        pendingBufferDestroys.add(new PendingBufferDestroy(buffer, memory, retireSubmissionSerial));
    }

    /**
     * Destroys a buffer/memory pair immediately.
     * Use only when the caller has already synchronized GPU usage (for example,
     * after an immediate submission path that waits on a fence before returning).
     */
    void destroyBufferWithMemoryImmediate(long buffer, long memory) {
        if (buffer != VK10.VK_NULL_HANDLE) {
            liveBufferMemory.remove(buffer);
        }
        if (device == null) {
            return;
        }
        destroyBufferWithMemoryNow(buffer, memory);
    }

    private void drainPendingBufferDestroys() {
        if (device == null) {
            pendingBufferDestroys.clear();
            return;
        }
        if (pendingBufferDestroys.isEmpty()) {
            return;
        }
        long completedSerial = completedSubmissionSerial.get();
        int queued = pendingBufferDestroys.size();
        int destroyedThisFrame = 0;
        for (int i = 0; i < queued && destroyedThisFrame < MAX_PENDING_BUFFER_DESTROYS_PER_DRAIN; i++) {
            PendingBufferDestroy pending = pendingBufferDestroys.poll();
            if (pending == null) {
                break;
            }
            if (pending.retireSubmissionSerial <= completedSerial) {
                destroyPendingBuffer(pending);
                destroyedThisFrame++;
            } else {
                pendingBufferDestroys.add(pending);
            }
        }
    }

    private void drainAllPendingBufferDestroys() {
        while (!pendingBufferDestroys.isEmpty()) {
            int before = pendingBufferDestroys.size();
            drainPendingBufferDestroys();
            if (pendingBufferDestroys.size() >= before) {
                // No forward progress; avoid a potential busy loop.
                break;
            }
        }
    }

    private void markAllSubmittedAsCompleted() {
        long lastSubmittedSerial = nextSubmissionSerial - 1L;
        completedSubmissionSerial.accumulateAndGet(lastSubmittedSerial, Math::max);
    }

    private void destroyPendingBuffer(PendingBufferDestroy pending) {
        destroyBufferWithMemoryNow(pending.buffer, pending.memory);
    }

    private void destroyBufferWithMemoryNow(long buffer, long memory) {
        if (buffer != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyBuffer(device, buffer, null);
        }
        if (memory != VK10.VK_NULL_HANDLE) {
            VK10.vkFreeMemory(device, memory, null);
        }
    }

    private void destroyTrackedBuffers() {
        if (device == null) {
            liveBufferMemory.clear();
            pendingBufferDestroys.clear();
            return;
        }
        drainPendingBufferDestroys();
        for (Map.Entry<Long, Long> entry : liveBufferMemory.entrySet()) {
            long buffer = entry.getKey() == null ? VK10.VK_NULL_HANDLE : entry.getKey();
            long memory = entry.getValue() == null ? VK10.VK_NULL_HANDLE : entry.getValue();
            if (buffer != VK10.VK_NULL_HANDLE) {
                VK10.vkDestroyBuffer(device, buffer, null);
            }
            if (memory != VK10.VK_NULL_HANDLE) {
                VK10.vkFreeMemory(device, memory, null);
            }
        }
        liveBufferMemory.clear();
    }

    long createImage(int width,
                     int height,
                     int format,
                     int tiling,
                     int usage,
                     int memoryProperties,
                     LongBuffer pImage,
                     LongBuffer pMemory) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
                    .imageType(VK10.VK_IMAGE_TYPE_2D)
                    .extent(extent -> extent.width(width).height(height).depth(1))
                    .mipLevels(1)
                    .arrayLayers(1)
                    .format(format)
                    .tiling(tiling)
                    .initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED)
                    .usage(usage)
                    .samples(VK10.VK_SAMPLE_COUNT_1_BIT)
                    .sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);

            checkVk(VK10.vkCreateImage(device, imageInfo, null, pImage), "vkCreateImage");

            VkMemoryRequirements requirements = VkMemoryRequirements.calloc(stack);
            VK10.vkGetImageMemoryRequirements(device, pImage.get(0), requirements);

            VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                    .allocationSize(requirements.size())
                    .memoryTypeIndex(findMemoryType(requirements.memoryTypeBits(), memoryProperties));

            checkVk(VK10.vkAllocateMemory(device, allocInfo, null, pMemory), "vkAllocateMemory(image)");
            checkVk(VK10.vkBindImageMemory(device, pImage.get(0), pMemory.get(0), 0), "vkBindImageMemory");
            return requirements.size();
        }
    }

    long createImageView(long image, int format, int aspectMask) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageViewCreateInfo createInfo = VkImageViewCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                    .image(image)
                    .viewType(VK10.VK_IMAGE_VIEW_TYPE_2D)
                    .format(format);
            VkImageSubresourceRange range = createInfo.subresourceRange();
            range.aspectMask(aspectMask);
            range.baseMipLevel(0);
            range.levelCount(1);
            range.baseArrayLayer(0);
            range.layerCount(1);

            LongBuffer pView = stack.mallocLong(1);
            checkVk(VK10.vkCreateImageView(device, createInfo, null, pView), "vkCreateImageView");
            return pView.get(0);
        }
    }

    void transitionImageLayoutImmediate(long image,
                                        int aspectMask,
                                        int oldLayout,
                                        int newLayout,
                                        int srcAccessMask,
                                        int dstAccessMask,
                                        int srcStageMask,
                                        int dstStageMask) {
        synchronized (immediateSubmitLock) {
            VkCommandBuffer commandBuffer = beginImmediateCommands();
            try {
                VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1)
                        .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                        .oldLayout(oldLayout)
                        .newLayout(newLayout)
                        .srcQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
                        .dstQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
                        .image(image)
                        .srcAccessMask(srcAccessMask)
                        .dstAccessMask(dstAccessMask);
                VkImageSubresourceRange range = barrier.subresourceRange();
                range.aspectMask(aspectMask);
                range.baseMipLevel(0);
                range.levelCount(1);
                range.baseArrayLayer(0);
                range.layerCount(1);

                VK10.vkCmdPipelineBarrier(
                        commandBuffer,
                        srcStageMask,
                        dstStageMask,
                        0,
                        null,
                        null,
                        barrier
                );
                barrier.free();
            } finally {
                endImmediateCommands(commandBuffer);
            }
        }
    }

    void copyBufferToImageImmediate(long buffer, long image, int width, int height) {
        synchronized (immediateSubmitLock) {
            VkCommandBuffer commandBuffer = beginImmediateCommands();
            try {
                VkBufferImageCopy.Buffer region = VkBufferImageCopy.calloc(1);
                region.bufferOffset(0);
                region.bufferRowLength(0);
                region.bufferImageHeight(0);
                region.imageSubresource().aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT);
                region.imageSubresource().mipLevel(0);
                region.imageSubresource().baseArrayLayer(0);
                region.imageSubresource().layerCount(1);
                region.imageOffset().set(0, 0, 0);
                region.imageExtent().set(width, height, 1);

                VK10.vkCmdCopyBufferToImage(
                        commandBuffer,
                        buffer,
                        image,
                        VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                        region
                );
                region.free();
            } finally {
                endImmediateCommands(commandBuffer);
            }
        }
    }

    void copyBufferImmediate(long srcBuffer, long dstBuffer, long sizeInBytes, long srcOffset, long dstOffset) {
        if (sizeInBytes <= 0L) {
            return;
        }
        synchronized (immediateSubmitLock) {
            VkCommandBuffer commandBuffer = beginImmediateCommands();
            try {
                VkBufferCopy.Buffer region = VkBufferCopy.calloc(1);
                region.srcOffset(srcOffset);
                region.dstOffset(dstOffset);
                region.size(sizeInBytes);
                VK10.vkCmdCopyBuffer(commandBuffer, srcBuffer, dstBuffer, region);
                region.free();
            } finally {
                endImmediateCommands(commandBuffer);
            }
        }
    }

    void uploadToBufferImmediate(ByteBuffer src, int sizeInBytes, long dstBuffer, long dstOffset) {
        if (sizeInBytes <= 0) {
            return;
        }
        synchronized (immediateSubmitLock) {
            ensureImmediateUploadBufferCapacity(sizeInBytes);
            VulkanMemoryCopies.copyByteBuffer(src, immediateUploadMappedAddress, sizeInBytes);
            recordImmediateUploadCopy(dstBuffer, dstOffset, sizeInBytes);
        }
    }

    void uploadToBufferImmediate(IntBuffer src, int sizeInBytes, long dstBuffer, long dstOffset) {
        if (sizeInBytes <= 0) {
            return;
        }
        synchronized (immediateSubmitLock) {
            ensureImmediateUploadBufferCapacity(sizeInBytes);
            VulkanMemoryCopies.copyIntBuffer(src, immediateUploadMappedAddress, sizeInBytes);
            recordImmediateUploadCopy(dstBuffer, dstOffset, sizeInBytes);
        }
    }

    private void recordImmediateUploadCopy(long dstBuffer, long dstOffset, long sizeInBytes) {
        VkCommandBuffer commandBuffer = beginImmediateCommands();
        try {
            VkBufferCopy.Buffer region = VkBufferCopy.calloc(1);
            region.srcOffset(0L);
            region.dstOffset(dstOffset);
            region.size(sizeInBytes);
            VK10.vkCmdCopyBuffer(commandBuffer, immediateUploadBuffer, dstBuffer, region);
            region.free();
        } finally {
            endImmediateCommands(commandBuffer);
        }
    }

    void requestCurrentFrameCaptureRgba() {
        if (!frameActive) {
            throw new IllegalStateException("No active frame to capture");
        }

        Frame frame = frames[currentFrame];
        if (frame == null) {
            throw new IllegalStateException("Frame resources are not initialized");
        }
        if (frame.captureBuffer != VK10.VK_NULL_HANDLE) {
            releaseFrameCapture(frame);
        }

        int captureWidth = swapchainWidth;
        int captureHeight = swapchainHeight;
        if (captureWidth <= 0 || captureHeight <= 0) {
            throw new IllegalStateException("Swapchain has invalid extent for capture: " + captureWidth + "x" + captureHeight);
        }
        int captureSize = captureWidth * captureHeight * 4;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer pBuffer = stack.mallocLong(1);
            LongBuffer pMemory = stack.mallocLong(1);
            createBuffer(
                    captureSize,
                    VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                    VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                    pBuffer,
                    pMemory
            );
            frame.captureBuffer = pBuffer.get(0);
            frame.captureBufferMemory = pMemory.get(0);
            frame.captureSize = captureSize;
            frame.captureWidth = captureWidth;
            frame.captureHeight = captureHeight;
        }

        long image = swapchainImages[getCurrentSwapchainImageIndex()];
        recordImageBarrier(
                frame.commandBuffer,
                image,
                VK10.VK_IMAGE_ASPECT_COLOR_BIT,
                KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
                VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT,
                VK10.VK_ACCESS_TRANSFER_READ_BIT,
                VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                VK10.VK_PIPELINE_STAGE_TRANSFER_BIT
        );

        VkBufferImageCopy.Buffer region = VkBufferImageCopy.calloc(1);
        region.bufferOffset(0);
        region.bufferRowLength(0);
        region.bufferImageHeight(0);
        region.imageSubresource().aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT);
        region.imageSubresource().mipLevel(0);
        region.imageSubresource().baseArrayLayer(0);
        region.imageSubresource().layerCount(1);
        region.imageOffset().set(0, 0, 0);
        region.imageExtent().set(captureWidth, captureHeight, 1);
        VK10.vkCmdCopyImageToBuffer(
                frame.commandBuffer,
                image,
                VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                frame.captureBuffer,
                region
        );
        region.free();

        recordImageBarrier(
                frame.commandBuffer,
                image,
                VK10.VK_IMAGE_ASPECT_COLOR_BIT,
                VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
                VK10.VK_ACCESS_TRANSFER_READ_BIT,
                0,
                VK10.VK_PIPELINE_STAGE_TRANSFER_BIT,
                VK10.VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT
        );
    }

    // Warning: slow
    ByteBuffer consumeLastFrameCaptureRgba() {
        if (lastSubmittedFrameIndex < 0) {
            throw new IllegalStateException("No submitted frame capture to consume");
        }

        Frame frame = frames[lastSubmittedFrameIndex];
        if (frame == null || frame.captureBuffer == VK10.VK_NULL_HANDLE || frame.captureBufferMemory == VK10.VK_NULL_HANDLE) {
            throw new IllegalStateException("Last submitted frame does not have a pending capture");
        }

        final int width = frame.captureWidth;
        final int height = frame.captureHeight;
        final int byteSize = frame.captureSize;

        ByteBuffer out = ByteBuffer.allocateDirect(byteSize);
        boolean mapped = false;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            checkVk(VK10.vkWaitForFences(device, stack.longs(frame.inFlightFence), true, Long.MAX_VALUE), "vkWaitForFences(capture)");

            PointerBuffer pMapped = stack.mallocPointer(1);
            checkVk(VK10.vkMapMemory(device, frame.captureBufferMemory, 0, byteSize, 0, pMapped), "vkMapMemory(capture)");
            mapped = true;
            ByteBuffer mappedData = MemoryUtil.memByteBuffer(pMapped.get(0), byteSize);

            boolean bgraSource = swapchainImageFormat == VK10.VK_FORMAT_B8G8R8A8_UNORM
                    || swapchainImageFormat == VK10.VK_FORMAT_B8G8R8A8_SRGB;

            // Convert to RGBA and flip to bottom-up row order to match GL readback semantics.
            for (int y = 0; y < height; y++) {
                int srcY = height - 1 - y;
                int srcRowBase = srcY * width * 4;
                int dstRowBase = y * width * 4;
                for (int x = 0; x < width; x++) {
                    int src = srcRowBase + (x * 4);
                    int dst = dstRowBase + (x * 4);
                    byte c0 = mappedData.get(src);
                    byte c1 = mappedData.get(src + 1);
                    byte c2 = mappedData.get(src + 2);
                    byte c3 = mappedData.get(src + 3);
                    if (bgraSource) {
                        out.put(dst, c2);     // R
                        out.put(dst + 1, c1); // G
                        out.put(dst + 2, c0); // B
                        out.put(dst + 3, c3); // A
                    } else {
                        out.put(dst, c0);
                        out.put(dst + 1, c1);
                        out.put(dst + 2, c2);
                        out.put(dst + 3, c3);
                    }
                }
            }
        } finally {
            if (mapped) {
                VK10.vkUnmapMemory(device, frame.captureBufferMemory);
            }
            releaseFrameCapture(frame);
            lastSubmittedFrameIndex = -1;
        }

        return out;
    }

    private VkCommandBuffer beginImmediateCommands() {
        if (commandPool == VK10.VK_NULL_HANDLE || immediateCommandBuffer == null || immediateFence == VK10.VK_NULL_HANDLE) {
            throw new IllegalStateException("Cannot begin immediate commands before command pool creation");
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            checkVk(VK10.vkResetCommandBuffer(immediateCommandBuffer, 0), "vkResetCommandBuffer(immediate)");

            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                    .flags(VK10.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
            checkVk(VK10.vkBeginCommandBuffer(immediateCommandBuffer, beginInfo), "vkBeginCommandBuffer(immediate)");
            return immediateCommandBuffer;
        }
    }

    private void endImmediateCommands(VkCommandBuffer commandBuffer) {
        if (commandBuffer == null || immediateCommandBuffer == null || commandBuffer.address() != immediateCommandBuffer.address()) {
            throw new IllegalStateException("Unexpected immediate command buffer");
        }
        checkVk(VK10.vkEndCommandBuffer(commandBuffer), "vkEndCommandBuffer(immediate)");

        try (MemoryStack stack = MemoryStack.stackPush()) {
            checkVk(VK10.vkResetFences(device, stack.longs(immediateFence)), "vkResetFences(immediate)");

            VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_SUBMIT_INFO)
                    .pCommandBuffers(stack.pointers(commandBuffer.address()));
            checkVk(VK10.vkQueueSubmit(graphicsQueue, submitInfo, immediateFence), "vkQueueSubmit(immediate)");
            checkVk(VK10.vkWaitForFences(device, stack.longs(immediateFence), true, Long.MAX_VALUE), "vkWaitForFences(immediate submit)");
        }
    }

    private void ensureImmediateUploadBufferCapacity(int requiredSizeInBytes) {
        if (requiredSizeInBytes <= 0) {
            throw new IllegalArgumentException("requiredSizeInBytes must be > 0");
        }
        if (immediateUploadBuffer != VK10.VK_NULL_HANDLE && immediateUploadCapacity >= requiredSizeInBytes) {
            return;
        }

        int newCapacity = Math.max(DEFAULT_IMMEDIATE_UPLOAD_BUFFER_SIZE, immediateUploadCapacity);
        while (newCapacity < requiredSizeInBytes) {
            newCapacity = Math.max(newCapacity * 2, requiredSizeInBytes);
        }

        destroyImmediateUploadBuffer();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer pBuffer = stack.mallocLong(1);
            LongBuffer pMemory = stack.mallocLong(1);
            createBuffer(
                    newCapacity,
                    VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                    VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                    pBuffer,
                    pMemory
            );
            immediateUploadBuffer = pBuffer.get(0);
            immediateUploadMemory = pMemory.get(0);

            PointerBuffer pMapped = stack.mallocPointer(1);
            checkVk(VK10.vkMapMemory(device, immediateUploadMemory, 0, newCapacity, 0, pMapped), "vkMapMemory(immediate upload)");
            immediateUploadMappedAddress = pMapped.get(0);
            immediateUploadCapacity = newCapacity;
        }
    }

    private void destroyImmediateUploadBuffer() {
        if (device == null) {
            immediateUploadBuffer = VK10.VK_NULL_HANDLE;
            immediateUploadMemory = VK10.VK_NULL_HANDLE;
            immediateUploadMappedAddress = 0L;
            immediateUploadCapacity = 0;
            return;
        }
        if (immediateUploadMemory != VK10.VK_NULL_HANDLE && immediateUploadMappedAddress != 0L) {
            VK10.vkUnmapMemory(device, immediateUploadMemory);
        }
        immediateUploadMappedAddress = 0L;
        immediateUploadCapacity = 0;
        destroyBufferWithMemoryImmediate(immediateUploadBuffer, immediateUploadMemory);
        immediateUploadBuffer = VK10.VK_NULL_HANDLE;
        immediateUploadMemory = VK10.VK_NULL_HANDLE;
    }

    int findMemoryType(int typeFilter, int properties) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceMemoryProperties memProperties = VkPhysicalDeviceMemoryProperties.calloc(stack);
            VK10.vkGetPhysicalDeviceMemoryProperties(physicalDevice, memProperties);

            for (int i = 0; i < memProperties.memoryTypeCount(); i++) {
                boolean typeMatches = (typeFilter & (1 << i)) != 0;
                boolean propsMatch = (memProperties.memoryTypes(i).propertyFlags() & properties) == properties;
                if (typeMatches && propsMatch) {
                    return i;
                }
            }
        }
        throw new IllegalStateException("Failed to find suitable Vulkan memory type");
    }

    private void createInstance() {
        if (!VulkanLoader.ensureGlfwVulkanLoaded()) {
            throw new IllegalStateException(
                    "GLFW reports Vulkan is not supported (missing loader/driver/runtime integration). Loader diagnostics:\n"
                            + VulkanLoader.getDiagnostics()
            );
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkApplicationInfo appInfo = VkApplicationInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_APPLICATION_INFO)
                    .pApplicationName(stack.UTF8("neoblaze3d"))
                    .applicationVersion(VK10.VK_MAKE_VERSION(1, 0, 0))
                    .pEngineName(stack.UTF8("neoblaze3d"))
                    .engineVersion(VK10.VK_MAKE_VERSION(1, 0, 0))
                    .apiVersion(VK10.VK_API_VERSION_1_0);

            PointerBuffer requiredExtensions = GLFWVulkan.glfwGetRequiredInstanceExtensions();
            if (requiredExtensions == null) {
                throw new IllegalStateException("glfwGetRequiredInstanceExtensions returned null (Vulkan loader/extensions unavailable)");
            }

            boolean debugRequested = GraphicsFactory.isDebugModeHintEnabled();
            if (debugRequested && Boolean.getBoolean("neoblaze3d.vulkan.disableValidation")) {
                debugRequested = false;
                System.out.println("[Vulkan] Validation disabled by system property: -Dneoblaze3d.vulkan.disableValidation=true");
            }
            validationLayersEnabled = debugRequested && isValidationLayerSupported();
            boolean debugUtilsSupported = debugRequested && isInstanceExtensionSupported(EXTDebugUtils.VK_EXT_DEBUG_UTILS_EXTENSION_NAME);
            if (debugRequested && !validationLayersEnabled) {
                System.err.println("[Vulkan] Validation layer requested, but VK_LAYER_KHRONOS_validation is not available.");
            }
            if (debugRequested && !debugUtilsSupported) {
                System.err.println("[Vulkan] Debug utils extension requested, but VK_EXT_debug_utils is not available.");
            }
            if (validationLayersEnabled && debugUtilsSupported) {
                System.out.println("[Vulkan] Validation layers enabled (DEBUG mode).");
            }

            int extensionCount = requiredExtensions.remaining() + (validationLayersEnabled && debugUtilsSupported ? 1 : 0);
            PointerBuffer enabledExtensions = stack.mallocPointer(extensionCount);
            for (int i = 0; i < requiredExtensions.remaining(); i++) {
                enabledExtensions.put(requiredExtensions.get(i));
            }
            if (validationLayersEnabled && debugUtilsSupported) {
                enabledExtensions.put(stack.UTF8(EXTDebugUtils.VK_EXT_DEBUG_UTILS_EXTENSION_NAME));
            }
            enabledExtensions.flip();

            VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
                    .pApplicationInfo(appInfo)
                    .ppEnabledExtensionNames(enabledExtensions);

            if (validationLayersEnabled) {
                createInfo.ppEnabledLayerNames(stack.pointers(stack.UTF8(VALIDATION_LAYER)));
            }

            PointerBuffer pInstance = stack.mallocPointer(1);
            checkVk(VK10.vkCreateInstance(createInfo, null, pInstance), "vkCreateInstance");
            instance = new VkInstance(pInstance.get(0), createInfo);

            if (validationLayersEnabled && debugUtilsSupported) {
                createDebugMessenger();
            }
        }
    }

    private void createDebugMessenger() {
        if (instance == null || validationLayersEnabled == false) {
            return;
        }

        debugMessengerCallback = VkDebugUtilsMessengerCallbackEXT.create(
                (messageSeverity, messageTypes, pCallbackData, pUserData) -> {
                    String severity = vulkanMessageSeverity(messageSeverity);
                    String message = pCallbackData != MemoryUtil.NULL
                            ? VkDebugUtilsMessengerCallbackDataEXT.create(pCallbackData).pMessageString()
                            : "<no message>";
                    String line = "[Vulkan][" + severity + "] " + message;

                    if ((messageSeverity & EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT) != 0) {
                        System.err.println(line);
                    } else {
                        System.out.println(line);
                    }

                    return VK10.VK_FALSE;
                }
        );

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDebugUtilsMessengerCreateInfoEXT createInfo = VkDebugUtilsMessengerCreateInfoEXT.calloc(stack)
                    .sType(EXTDebugUtils.VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT)
                    .messageSeverity(
                            EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT
                                    | EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT
                    )
                    .messageType(
                            EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT
                                    | EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT
                                    | EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT
                    )
                    .pfnUserCallback(debugMessengerCallback);

            LongBuffer pMessenger = stack.mallocLong(1);
            checkVk(EXTDebugUtils.vkCreateDebugUtilsMessengerEXT(instance, createInfo, null, pMessenger), "vkCreateDebugUtilsMessengerEXT");
            debugMessenger = pMessenger.get(0);
        }
    }

    private boolean isValidationLayerSupported() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer pLayerCount = stack.ints(0);
            int countResult = VK10.vkEnumerateInstanceLayerProperties(pLayerCount, null);
            if (countResult != VK10.VK_SUCCESS && countResult != VK10.VK_INCOMPLETE) {
                checkVk(countResult, "vkEnumerateInstanceLayerProperties(count)");
            }
            if (pLayerCount.get(0) <= 0) {
                return false;
            }

            VkLayerProperties.Buffer layers = VkLayerProperties.calloc(pLayerCount.get(0), stack);
            int listResult = VK10.vkEnumerateInstanceLayerProperties(pLayerCount, layers);
            if (listResult != VK10.VK_SUCCESS && listResult != VK10.VK_INCOMPLETE) {
                checkVk(listResult, "vkEnumerateInstanceLayerProperties(list)");
            }
            for (int i = 0; i < layers.remaining(); i++) {
                if (VALIDATION_LAYER.equals(layers.get(i).layerNameString())) {
                    return true;
                }
            }
            return false;
        }
    }

    private boolean isInstanceExtensionSupported(String extensionName) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer pExtensionCount = stack.ints(0);
            int countResult = VK10.vkEnumerateInstanceExtensionProperties((ByteBuffer) null, pExtensionCount, null);
            if (countResult != VK10.VK_SUCCESS && countResult != VK10.VK_INCOMPLETE) {
                checkVk(countResult, "vkEnumerateInstanceExtensionProperties(count)");
            }
            if (pExtensionCount.get(0) <= 0) {
                return false;
            }

            VkExtensionProperties.Buffer extensions = VkExtensionProperties.calloc(pExtensionCount.get(0), stack);
            int listResult = VK10.vkEnumerateInstanceExtensionProperties((ByteBuffer) null, pExtensionCount, extensions);
            if (listResult != VK10.VK_SUCCESS && listResult != VK10.VK_INCOMPLETE) {
                checkVk(listResult, "vkEnumerateInstanceExtensionProperties(list)");
            }
            for (int i = 0; i < extensions.remaining(); i++) {
                if (extensionName.equals(extensions.get(i).extensionNameString())) {
                    return true;
                }
            }
            return false;
        }
    }

    private static String vulkanMessageSeverity(int messageSeverity) {
        if ((messageSeverity & EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT) != 0) {
            return "ERROR";
        }
        if ((messageSeverity & EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT) != 0) {
            return "WARN";
        }
        if ((messageSeverity & EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT) != 0) {
            return "INFO";
        }
        return "VERBOSE";
    }

    private void createSurface() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer pSurface = stack.mallocLong(1);
            int result = GLFWVulkan.glfwCreateWindowSurface(instance, windowHandle, null, pSurface);
            checkVk(result, "glfwCreateWindowSurface");
            surface = pSurface.get(0);
        }
    }

    private void pickPhysicalDevice() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer pDeviceCount = stack.ints(0);
            checkVk(VK10.vkEnumeratePhysicalDevices(instance, pDeviceCount, null), "vkEnumeratePhysicalDevices(count)");
            int deviceCount = pDeviceCount.get(0);
            if (deviceCount == 0) {
                throw new IllegalStateException("No Vulkan physical devices found");
            }

            PointerBuffer devices = stack.mallocPointer(deviceCount);
            checkVk(VK10.vkEnumeratePhysicalDevices(instance, pDeviceCount, devices), "vkEnumeratePhysicalDevices(list)");

            VkPhysicalDevice bestDevice = null;
            int bestScore = Integer.MIN_VALUE;
            int bestGraphicsFamily = -1;
            int bestPresentFamily = -1;

            for (int i = 0; i < deviceCount; i++) {
                VkPhysicalDevice candidate = new VkPhysicalDevice(devices.get(i), instance);
                try (MemoryStack candidateStack = MemoryStack.stackPush()) {
                    QueueFamilyIndices indices = findQueueFamilies(candidate, candidateStack);
                    int score = scorePhysicalDevice(candidate, indices, candidateStack);
                    if (score > bestScore) {
                        bestScore = score;
                        bestDevice = candidate;
                        bestGraphicsFamily = indices.graphicsFamily;
                        bestPresentFamily = indices.presentFamily;
                    }
                }
            }

            if (bestDevice != null && bestScore >= 0) {
                physicalDevice = bestDevice;
                graphicsQueueFamily = bestGraphicsFamily;
                presentQueueFamily = bestPresentFamily;

                VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.calloc(stack);
                VK10.vkGetPhysicalDeviceProperties(physicalDevice, properties);
                System.out.println(
                        "[Vulkan] Selected GPU: "
                                + properties.deviceNameString()
                                + " ("
                                + physicalDeviceTypeName(properties.deviceType())
                                + "), graphicsQueueFamily="
                                + graphicsQueueFamily
                                + ", presentQueueFamily="
                                + presentQueueFamily
                );
                return;
            }
        }

        throw new IllegalStateException("No suitable Vulkan physical device found");
    }

    private QueueFamilyIndices findQueueFamilies(VkPhysicalDevice candidate, MemoryStack stack) {
        QueueFamilyIndices indices = new QueueFamilyIndices();

        IntBuffer pQueueFamilyCount = stack.ints(0);
        VK10.vkGetPhysicalDeviceQueueFamilyProperties(candidate, pQueueFamilyCount, null);
        int queueFamilyCount = pQueueFamilyCount.get(0);
        if (queueFamilyCount <= 0) {
            return indices;
        }

        VkQueueFamilyProperties.Buffer queueFamilies = VkQueueFamilyProperties.calloc(queueFamilyCount, stack);
        VK10.vkGetPhysicalDeviceQueueFamilyProperties(candidate, pQueueFamilyCount, queueFamilies);

        for (int i = 0; i < queueFamilyCount; i++) {
            if (indices.graphicsFamily < 0 && (queueFamilies.get(i).queueFlags() & VK10.VK_QUEUE_GRAPHICS_BIT) != 0) {
                indices.graphicsFamily = i;
            }

            IntBuffer pPresentSupport = stack.ints(VK10.VK_FALSE);
            checkVk(KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR(candidate, i, surface, pPresentSupport), "vkGetPhysicalDeviceSurfaceSupportKHR");
            if (indices.presentFamily < 0 && pPresentSupport.get(0) == VK10.VK_TRUE) {
                indices.presentFamily = i;
            }

            if (indices.isComplete()) {
                break;
            }
        }
        return indices;
    }

    private boolean hasSwapchainExtension(VkPhysicalDevice candidate, MemoryStack stack) {
        IntBuffer pExtensionCount = stack.ints(0);
        checkVk(VK10.vkEnumerateDeviceExtensionProperties(candidate, (ByteBuffer) null, pExtensionCount, null), "vkEnumerateDeviceExtensionProperties(count)");
        int extensionCount = pExtensionCount.get(0);
        if (extensionCount <= 0) {
            return false;
        }
        if (extensionCount > 16384) {
            throw new IllegalStateException("Unreasonable Vulkan extension count reported: " + extensionCount);
        }

        // Avoid variable-size stack allocation here. Some drivers/platforms can report
        // larger extension arrays and exhaust MemoryStack during device scoring.
        VkExtensionProperties.Buffer extensions = VkExtensionProperties.calloc(extensionCount);
        try {
            checkVk(VK10.vkEnumerateDeviceExtensionProperties(candidate, (ByteBuffer) null, pExtensionCount, extensions), "vkEnumerateDeviceExtensionProperties(list)");
            for (int i = 0; i < extensionCount; i++) {
                if (VK_KHR_SWAPCHAIN_EXTENSION_NAME.equals(extensions.get(i).extensionNameString())) {
                    return true;
                }
            }
            return false;
        } finally {
            extensions.free();
        }
    }

    private int scorePhysicalDevice(VkPhysicalDevice candidate, QueueFamilyIndices indices, MemoryStack stack) {
        if (!indices.isComplete()) {
            return -1;
        }
        if (!hasSwapchainExtension(candidate, stack)) {
            return -1;
        }

        SwapchainSupport support = querySwapchainSupport(candidate, stack);
        if (support.formats == null || !support.formats.hasRemaining()) {
            return -1;
        }
        if (support.presentModes == null || !support.presentModes.hasRemaining()) {
            return -1;
        }

        VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.calloc(stack);
        VK10.vkGetPhysicalDeviceProperties(candidate, properties);

        int score = 0;
        switch (properties.deviceType()) {
            case VK10.VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU:
                score += 100_000;
                break;
            case VK10.VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU:
                score += 50_000;
                break;
            case VK10.VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU:
                score += 30_000;
                break;
            case VK10.VK_PHYSICAL_DEVICE_TYPE_CPU:
                score += 10_000;
                break;
            default:
                score += 1_000;
                break;
        }

        if (indices.graphicsFamily == indices.presentFamily) {
            score += 5_000;
        }
        if (hasPresentMode(support.presentModes, KHRSurface.VK_PRESENT_MODE_IMMEDIATE_KHR)) {
            score += 2_000;
        } else if (hasPresentMode(support.presentModes, KHRSurface.VK_PRESENT_MODE_MAILBOX_KHR)) {
            score += 1_000;
        }

        score += properties.limits().maxImageDimension2D();
        return score;
    }

    private static boolean hasPresentMode(IntBuffer presentModes, int requiredMode) {
        if (presentModes == null) {
            return false;
        }
        for (int i = 0; i < presentModes.remaining(); i++) {
            if (presentModes.get(i) == requiredMode) {
                return true;
            }
        }
        return false;
    }

    private static String physicalDeviceTypeName(int type) {
        switch (type) {
            case VK10.VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU:
                return "DISCRETE_GPU";
            case VK10.VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU:
                return "INTEGRATED_GPU";
            case VK10.VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU:
                return "VIRTUAL_GPU";
            case VK10.VK_PHYSICAL_DEVICE_TYPE_CPU:
                return "CPU";
            default:
                return "OTHER";
        }
    }

    private static final class QueueFamilyIndices {
        int graphicsFamily = -1;
        int presentFamily = -1;

        boolean isComplete() {
            return graphicsFamily >= 0 && presentFamily >= 0;
        }
    }

    private void createLogicalDevice() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer uniqueFamilies;
            if (graphicsQueueFamily == presentQueueFamily) {
                uniqueFamilies = stack.ints(graphicsQueueFamily);
            } else {
                uniqueFamilies = stack.ints(graphicsQueueFamily, presentQueueFamily);
            }

            VkDeviceQueueCreateInfo.Buffer queueCreateInfos = VkDeviceQueueCreateInfo.calloc(uniqueFamilies.remaining(), stack);
            FloatBuffer queuePriority = stack.floats(1.0f);
            for (int i = 0; i < uniqueFamilies.remaining(); i++) {
                queueCreateInfos.get(i)
                        .sType(VK10.VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                        .queueFamilyIndex(uniqueFamilies.get(i))
                        .pQueuePriorities(queuePriority);
            }

            PointerBuffer extensions = stack.mallocPointer(1);
            extensions.put(stack.UTF8(VK_KHR_SWAPCHAIN_EXTENSION_NAME));
            extensions.flip();

            VkDeviceCreateInfo createInfo = VkDeviceCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
                    .pQueueCreateInfos(queueCreateInfos)
                    .ppEnabledExtensionNames(extensions);

            VkPhysicalDeviceFeatures supportedFeatures = VkPhysicalDeviceFeatures.calloc(stack);
            VK10.vkGetPhysicalDeviceFeatures(physicalDevice, supportedFeatures);
            VkPhysicalDeviceFeatures enabledFeatures = VkPhysicalDeviceFeatures.calloc(stack);
            multiDrawIndirectEnabled = supportedFeatures.multiDrawIndirect();
            if (multiDrawIndirectEnabled) {
                enabledFeatures.multiDrawIndirect(true);
            }
            createInfo.pEnabledFeatures(enabledFeatures);

            PointerBuffer pDevice = stack.mallocPointer(1);
            checkVk(VK10.vkCreateDevice(physicalDevice, createInfo, null, pDevice), "vkCreateDevice");
            device = new VkDevice(pDevice.get(0), physicalDevice, createInfo);

            PointerBuffer pQueue = stack.mallocPointer(1);
            VK10.vkGetDeviceQueue(device, graphicsQueueFamily, 0, pQueue);
            graphicsQueue = new VkQueue(pQueue.get(0), device);
            VK10.vkGetDeviceQueue(device, presentQueueFamily, 0, pQueue);
            presentQueue = new VkQueue(pQueue.get(0), device);
        }
    }

    private void queryDeviceLimits() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.calloc(stack);
            VK10.vkGetPhysicalDeviceProperties(physicalDevice, properties);
            long alignment = properties.limits().minUniformBufferOffsetAlignment();
            if (alignment > Integer.MAX_VALUE) {
                throw new IllegalStateException("minUniformBufferOffsetAlignment exceeds int range");
            }
            minUniformBufferOffsetAlignment = Math.max(16, (int) alignment);
        }
    }

    private void createCommandPool() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandPoolCreateInfo createInfo = VkCommandPoolCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
                    .queueFamilyIndex(graphicsQueueFamily)
                    .flags(VK10.VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);

            LongBuffer pCommandPool = stack.mallocLong(1);
            checkVk(VK10.vkCreateCommandPool(device, createInfo, null, pCommandPool), "vkCreateCommandPool");
            commandPool = pCommandPool.get(0);
        }
    }

    private void createImmediateResources() {
        if (commandPool == VK10.VK_NULL_HANDLE) {
            throw new IllegalStateException("Cannot create immediate resources before command pool creation");
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                    .commandPool(commandPool)
                    .level(VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandBufferCount(1);
            PointerBuffer pCommandBuffer = stack.mallocPointer(1);
            checkVk(VK10.vkAllocateCommandBuffers(device, allocInfo, pCommandBuffer), "vkAllocateCommandBuffers(immediate persistent)");
            immediateCommandBuffer = new VkCommandBuffer(pCommandBuffer.get(0), device);

            VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_FENCE_CREATE_INFO);
            LongBuffer pFence = stack.mallocLong(1);
            checkVk(VK10.vkCreateFence(device, fenceInfo, null, pFence), "vkCreateFence(immediate persistent)");
            immediateFence = pFence.get(0);
        }
        ensureImmediateUploadBufferCapacity(DEFAULT_IMMEDIATE_UPLOAD_BUFFER_SIZE);
    }

    private void destroyImmediateResources() {
        if (device == null) {
            immediateCommandBuffer = null;
            immediateFence = VK10.VK_NULL_HANDLE;
            immediateUploadBuffer = VK10.VK_NULL_HANDLE;
            immediateUploadMemory = VK10.VK_NULL_HANDLE;
            immediateUploadMappedAddress = 0L;
            immediateUploadCapacity = 0;
            return;
        }
        destroyImmediateUploadBuffer();
        if (immediateFence != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyFence(device, immediateFence, null);
            immediateFence = VK10.VK_NULL_HANDLE;
        }
        if (immediateCommandBuffer != null && commandPool != VK10.VK_NULL_HANDLE) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VK10.vkFreeCommandBuffers(device, commandPool, stack.pointers(immediateCommandBuffer.address()));
            }
            immediateCommandBuffer = null;
        }
    }

    private void startDeferredFreeThread() {
        if (deferredFreeThreadRunning) {
            return;
        }
        deferredFreeThreadRunning = true;
        deferredFreeThread = new Thread(new Runnable() {
            @Override
            public void run() {
                runDeferredFreeLoop();
            }
        }, "VulkanDeferredFree");
        deferredFreeThread.setDaemon(true);
        deferredFreeThread.start();
    }

    private void stopDeferredFreeThread() {
        deferredFreeThreadRunning = false;
        signalDeferredFreeThread();
        Thread thread = deferredFreeThread;
        if (thread == null) {
            return;
        }
        try {
            thread.join(2000L);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        deferredFreeThread = null;
    }

    private void runDeferredFreeLoop() {
        while (deferredFreeThreadRunning) {
            synchronized (deferredFreeSignal) {
                if (!deferredFreeThreadRunning) {
                    break;
                }
                try {
                    deferredFreeSignal.wait();
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            if (!deferredFreeThreadRunning) {
                break;
            }
            try {
                drainPendingBufferDestroys();
            } catch (Throwable t) {
                System.err.println("[Vulkan] Deferred free worker error: " + t.getMessage());
            }
        }
    }

    private void signalDeferredFreeThread() {
        synchronized (deferredFreeSignal) {
            deferredFreeSignal.notifyAll();
        }
    }

    private void createSwapchainResources() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            SwapchainSupport support = querySwapchainSupport(physicalDevice, stack);

            VkSurfaceFormatKHR surfaceFormat = chooseSurfaceFormat(support.formats);
            int presentMode = choosePresentMode(support.presentModes);
            VkExtent2D extent = chooseSwapExtent(support.capabilities, stack);
            System.out.println("[Vulkan] Using present mode: " + presentModeName(presentMode));
            if (presentMode == KHRSurface.VK_PRESENT_MODE_FIFO_KHR
                    || presentMode == KHRSurface.VK_PRESENT_MODE_FIFO_RELAXED_KHR) {
                System.out.println("[Vulkan] Present mode is v-sync limited by display/compositor.");
            }

            int imageCount = Math.max(support.capabilities.minImageCount() + 1, FRAMES_IN_FLIGHT + 1);
            if (support.capabilities.maxImageCount() > 0 && imageCount > support.capabilities.maxImageCount()) {
                imageCount = support.capabilities.maxImageCount();
            }

            VkSwapchainCreateInfoKHR createInfo = VkSwapchainCreateInfoKHR.calloc(stack)
                    .sType(KHRSwapchain.VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR)
                    .surface(surface)
                    .minImageCount(imageCount)
                    .imageFormat(surfaceFormat.format())
                    .imageColorSpace(surfaceFormat.colorSpace())
                    .imageExtent(extent)
                    .imageArrayLayers(1)
                    .imageUsage(VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
                    .preTransform(support.capabilities.currentTransform())
                    .compositeAlpha(KHRSurface.VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
                    .presentMode(presentMode)
                    .clipped(true)
                    .oldSwapchain(VK10.VK_NULL_HANDLE);

            if (graphicsQueueFamily != presentQueueFamily) {
                createInfo.imageSharingMode(VK10.VK_SHARING_MODE_CONCURRENT);
                createInfo.pQueueFamilyIndices(stack.ints(graphicsQueueFamily, presentQueueFamily));
            } else {
                createInfo.imageSharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);
            }

            LongBuffer pSwapchain = stack.mallocLong(1);
            checkVk(KHRSwapchain.vkCreateSwapchainKHR(device, createInfo, null, pSwapchain), "vkCreateSwapchainKHR");
            swapchain = pSwapchain.get(0);

            IntBuffer pImageCount = stack.ints(0);
            checkVk(KHRSwapchain.vkGetSwapchainImagesKHR(device, swapchain, pImageCount, null), "vkGetSwapchainImagesKHR(count)");
            LongBuffer imageHandles = stack.mallocLong(pImageCount.get(0));
            checkVk(KHRSwapchain.vkGetSwapchainImagesKHR(device, swapchain, pImageCount, imageHandles), "vkGetSwapchainImagesKHR(list)");

            swapchainImages = new long[imageHandles.remaining()];
            for (int i = 0; i < swapchainImages.length; i++) {
                swapchainImages[i] = imageHandles.get(i);
            }
            swapchainImageInitialized = new boolean[swapchainImages.length];

            int selectedColorFormat = surfaceFormat.format();
            int selectedDepthFormat = depthFormat;
            if (renderPass != VK10.VK_NULL_HANDLE) {
                if (selectedColorFormat != renderPassColorFormat || selectedDepthFormat != renderPassDepthFormat) {
                    throw new IllegalStateException(
                            "Swapchain format changed across resize (color " + selectedColorFormat
                                    + ", depth " + selectedDepthFormat
                                    + ") but existing render pass/pipelines were created for (color "
                                    + renderPassColorFormat + ", depth " + renderPassDepthFormat + ")."
                    );
                }
            }

            swapchainImageFormat = selectedColorFormat;
            swapchainWidth = extent.width();
            swapchainHeight = extent.height();

            swapchainImageViews = new long[swapchainImages.length];
            for (int i = 0; i < swapchainImages.length; i++) {
                swapchainImageViews[i] = createImageView(swapchainImages[i], swapchainImageFormat, VK10.VK_IMAGE_ASPECT_COLOR_BIT);
            }

            swapchainRenderFinishedSemaphores = new long[swapchainImages.length];
            VkSemaphoreCreateInfo semaphoreInfo = VkSemaphoreCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);
            LongBuffer pSemaphore = stack.mallocLong(1);
            for (int i = 0; i < swapchainRenderFinishedSemaphores.length; i++) {
                checkVk(VK10.vkCreateSemaphore(device, semaphoreInfo, null, pSemaphore), "vkCreateSemaphore(renderFinishedByImage)");
                swapchainRenderFinishedSemaphores[i] = pSemaphore.get(0);
            }

            if (renderPass == VK10.VK_NULL_HANDLE) {
                createRenderPass();
            }
            createDepthResources();
            createFramebuffers();
        }
    }

    private void destroySwapchainResources() {
        if (framebuffers != null) {
            for (long framebuffer : framebuffers) {
                if (framebuffer != VK10.VK_NULL_HANDLE) {
                    VK10.vkDestroyFramebuffer(device, framebuffer, null);
                }
            }
            framebuffers = null;
        }

        if (depthImageView != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyImageView(device, depthImageView, null);
            depthImageView = VK10.VK_NULL_HANDLE;
        }
        if (depthImage != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyImage(device, depthImage, null);
            depthImage = VK10.VK_NULL_HANDLE;
        }
        if (depthImageMemory != VK10.VK_NULL_HANDLE) {
            VK10.vkFreeMemory(device, depthImageMemory, null);
            depthImageMemory = VK10.VK_NULL_HANDLE;
        }

        if (swapchainImageViews != null) {
            for (long imageView : swapchainImageViews) {
                if (imageView != VK10.VK_NULL_HANDLE) {
                    VK10.vkDestroyImageView(device, imageView, null);
                }
            }
            swapchainImageViews = null;
        }

        if (swapchain != VK10.VK_NULL_HANDLE) {
            KHRSwapchain.vkDestroySwapchainKHR(device, swapchain, null);
            swapchain = VK10.VK_NULL_HANDLE;
        }

        if (swapchainRenderFinishedSemaphores != null) {
            for (long semaphore : swapchainRenderFinishedSemaphores) {
                if (semaphore != VK10.VK_NULL_HANDLE) {
                    VK10.vkDestroySemaphore(device, semaphore, null);
                }
            }
            swapchainRenderFinishedSemaphores = null;
        }

        swapchainImages = null;
        swapchainImageInitialized = null;
    }

    private void recreateSwapchain() {
        int[] fbSize = framebufferSize();
        while (fbSize[0] == 0 || fbSize[1] == 0) {
            GLFW.glfwWaitEvents();
            fbSize = framebufferSize();
        }

        VK10.vkDeviceWaitIdle(device);
        markAllSubmittedAsCompleted();
        drainAllPendingBufferDestroys();
        destroySwapchainResources();
        createSwapchainResources();
    }

    private void ensureSwapchainMatchesFramebuffer() {
        int[] fbSize = framebufferSize();
        int fbWidth = fbSize[0];
        int fbHeight = fbSize[1];
        if (fbWidth <= 0 || fbHeight <= 0) {
            return;
        }
        if (fbWidth != swapchainWidth || fbHeight != swapchainHeight) {
            recreateSwapchain();
        }
    }

    private int[] framebufferSize() {
        int[] w = new int[1];
        int[] h = new int[1];
        GLFW.glfwGetFramebufferSize(windowHandle, w, h);
        return new int[]{w[0], h[0]};
    }

    private void createRenderPass() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            org.lwjgl.vulkan.VkAttachmentDescription.Buffer attachments = org.lwjgl.vulkan.VkAttachmentDescription.calloc(2, stack);

            attachments.get(0)
                    .format(swapchainImageFormat)
                    .samples(VK10.VK_SAMPLE_COUNT_1_BIT)
                    .loadOp(VK10.VK_ATTACHMENT_LOAD_OP_LOAD)
                    .storeOp(VK10.VK_ATTACHMENT_STORE_OP_STORE)
                    .stencilLoadOp(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                    .stencilStoreOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .initialLayout(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                    .finalLayout(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

            attachments.get(1)
                    .format(depthFormat)
                    .samples(VK10.VK_SAMPLE_COUNT_1_BIT)
                    .loadOp(VK10.VK_ATTACHMENT_LOAD_OP_LOAD)
                    .storeOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .stencilLoadOp(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                    .stencilStoreOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .initialLayout(VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
                    .finalLayout(VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);

            org.lwjgl.vulkan.VkAttachmentReference.Buffer colorAttachmentRef = org.lwjgl.vulkan.VkAttachmentReference.calloc(1, stack)
                    .attachment(0)
                    .layout(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

            org.lwjgl.vulkan.VkAttachmentReference depthAttachmentRef = org.lwjgl.vulkan.VkAttachmentReference.calloc(stack)
                    .attachment(1)
                    .layout(VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);

            VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1, stack)
                    .pipelineBindPoint(VK10.VK_PIPELINE_BIND_POINT_GRAPHICS)
                    .colorAttachmentCount(1)
                    .pColorAttachments(colorAttachmentRef)
                    .pDepthStencilAttachment(depthAttachmentRef);

            VkSubpassDependency.Buffer dependencies = VkSubpassDependency.calloc(1, stack)
                    .srcSubpass(VK10.VK_SUBPASS_EXTERNAL)
                    .dstSubpass(0)
                    .srcStageMask(VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                    .dstStageMask(VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                    .srcAccessMask(0)
                    .dstAccessMask(VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT | VK10.VK_ACCESS_COLOR_ATTACHMENT_READ_BIT);

            VkRenderPassCreateInfo renderPassInfo = VkRenderPassCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
                    .pAttachments(attachments)
                    .pSubpasses(subpass)
                    .pDependencies(dependencies);

            LongBuffer pRenderPass = stack.mallocLong(1);
            checkVk(VK10.vkCreateRenderPass(device, renderPassInfo, null, pRenderPass), "vkCreateRenderPass");
            renderPass = pRenderPass.get(0);
            renderPassColorFormat = swapchainImageFormat;
            renderPassDepthFormat = depthFormat;
        }
    }

    private void createDepthResources() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer pImage = stack.mallocLong(1);
            LongBuffer pMemory = stack.mallocLong(1);
            createImage(
                    swapchainWidth,
                    swapchainHeight,
                    depthFormat,
                    VK10.VK_IMAGE_TILING_OPTIMAL,
                    VK10.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT,
                    VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                    pImage,
                    pMemory
            );
            depthImage = pImage.get(0);
            depthImageMemory = pMemory.get(0);
            depthImageView = createImageView(depthImage, depthFormat, VK10.VK_IMAGE_ASPECT_DEPTH_BIT);

            transitionImageLayoutImmediate(
                    depthImage,
                    VK10.VK_IMAGE_ASPECT_DEPTH_BIT,
                    VK10.VK_IMAGE_LAYOUT_UNDEFINED,
                    VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL,
                    0,
                    VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT | VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT,
                    VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                    VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT
            );
        }
    }

    private void createFramebuffers() {
        framebuffers = new long[swapchainImageViews.length];
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer attachments = stack.mallocLong(2);
            VkFramebufferCreateInfo framebufferInfo = VkFramebufferCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO)
                    .renderPass(renderPass)
                    .width(swapchainWidth)
                    .height(swapchainHeight)
                    .layers(1);

            LongBuffer pFramebuffer = stack.mallocLong(1);

            for (int i = 0; i < swapchainImageViews.length; i++) {
                attachments.put(0, swapchainImageViews[i]);
                attachments.put(1, depthImageView);
                framebufferInfo.pAttachments(attachments);

                checkVk(VK10.vkCreateFramebuffer(device, framebufferInfo, null, pFramebuffer), "vkCreateFramebuffer");
                framebuffers[i] = pFramebuffer.get(0);
            }
        }
    }

    private void createPipelineCache() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPipelineCacheCreateInfo createInfo = VkPipelineCacheCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_PIPELINE_CACHE_CREATE_INFO);
            LongBuffer pPipelineCache = stack.mallocLong(1);
            checkVk(VK10.vkCreatePipelineCache(device, createInfo, null, pPipelineCache), "vkCreatePipelineCache");
            pipelineCache = pPipelineCache.get(0);
        }
    }

    private void createPerFrameResources() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                    .commandPool(commandPool)
                    .level(VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandBufferCount(FRAMES_IN_FLIGHT);

            PointerBuffer pCommandBuffers = stack.mallocPointer(FRAMES_IN_FLIGHT);
            checkVk(VK10.vkAllocateCommandBuffers(device, allocInfo, pCommandBuffers), "vkAllocateCommandBuffers");

            for (int i = 0; i < FRAMES_IN_FLIGHT; i++) {
                Frame frame = new Frame();
                frame.commandBuffer = new VkCommandBuffer(pCommandBuffers.get(i), device);

                LongBuffer pFence = stack.mallocLong(1);
                VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.calloc(stack)
                        .sType(VK10.VK_STRUCTURE_TYPE_FENCE_CREATE_INFO)
                        .flags(VK10.VK_FENCE_CREATE_SIGNALED_BIT);
                checkVk(VK10.vkCreateFence(device, fenceInfo, null, pFence), "vkCreateFence");
                frame.inFlightFence = pFence.get(0);
                checkVk(VK10.vkCreateFence(device, fenceInfo, null, pFence), "vkCreateFence(acquire)");
                frame.imageAcquiredFence = pFence.get(0);

                VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(2, stack);
                poolSizes.get(0)
                        .type(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                        .descriptorCount(DEFAULT_MAX_DESCRIPTOR_SETS_PER_FRAME * 8);
                poolSizes.get(1)
                        .type(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                        .descriptorCount(DEFAULT_MAX_DESCRIPTOR_SETS_PER_FRAME * 4);

                VkDescriptorPoolCreateInfo descriptorPoolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                        .sType(VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                        .maxSets(DEFAULT_MAX_DESCRIPTOR_SETS_PER_FRAME)
                        .pPoolSizes(poolSizes);
                LongBuffer pDescriptorPool = stack.mallocLong(1);
                checkVk(VK10.vkCreateDescriptorPool(device, descriptorPoolInfo, null, pDescriptorPool), "vkCreateDescriptorPool");
                frame.descriptorPool = pDescriptorPool.get(0);

                LongBuffer pBuffer = stack.mallocLong(1);
                LongBuffer pMemory = stack.mallocLong(1);
                frame.uniformCapacity = DEFAULT_UNIFORM_RING_SIZE;
                createBuffer(
                        frame.uniformCapacity,
                        VK10.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                        VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                        pBuffer,
                        pMemory
                );
                frame.uniformBuffer = pBuffer.get(0);
                frame.uniformBufferMemory = pMemory.get(0);

                PointerBuffer pMapped = stack.mallocPointer(1);
                checkVk(VK10.vkMapMemory(device, frame.uniformBufferMemory, 0, frame.uniformCapacity, 0, pMapped), "vkMapMemory(uniform ring)");
                frame.uniformMappedAddress = pMapped.get(0);
                frame.uniformMapped = MemoryUtil.memByteBuffer(frame.uniformMappedAddress, frame.uniformCapacity);

                frame.indirectCapacity = DEFAULT_INDIRECT_RING_SIZE;
                createBuffer(
                        frame.indirectCapacity,
                        VK10.VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT,
                        VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                        pBuffer,
                        pMemory
                );
                frame.indirectBuffer = pBuffer.get(0);
                frame.indirectBufferMemory = pMemory.get(0);
                checkVk(VK10.vkMapMemory(device, frame.indirectBufferMemory, 0, frame.indirectCapacity, 0, pMapped), "vkMapMemory(indirect ring)");
                frame.indirectMappedAddress = pMapped.get(0);
                frame.indirectMapped = MemoryUtil.memByteBuffer(frame.indirectMappedAddress, frame.indirectCapacity);

                frames[i] = frame;
            }
        }
    }

    private void releaseFrameCapture(Frame frame) {
        if (frame == null) {
            return;
        }
        destroyBufferWithMemory(frame.captureBuffer, frame.captureBufferMemory);
        frame.captureBuffer = VK10.VK_NULL_HANDLE;
        frame.captureBufferMemory = VK10.VK_NULL_HANDLE;
        frame.captureSize = 0;
        frame.captureWidth = 0;
        frame.captureHeight = 0;
    }

    private void recordImageBarrier(VkCommandBuffer commandBuffer,
                                    long image,
                                    int aspectMask,
                                    int oldLayout,
                                    int newLayout,
                                    int srcAccessMask,
                                    int dstAccessMask,
                                    int srcStageMask,
                                    int dstStageMask) {
        VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1)
                .sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                .oldLayout(oldLayout)
                .newLayout(newLayout)
                .srcQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
                .image(image)
                .srcAccessMask(srcAccessMask)
                .dstAccessMask(dstAccessMask);
        VkImageSubresourceRange range = barrier.subresourceRange();
        range.aspectMask(aspectMask);
        range.baseMipLevel(0);
        range.levelCount(1);
        range.baseArrayLayer(0);
        range.layerCount(1);

        VK10.vkCmdPipelineBarrier(
                commandBuffer,
                srcStageMask,
                dstStageMask,
                0,
                null,
                null,
                barrier
        );
        barrier.free();
    }

    private void destroyPerFrameResources() {
        for (int i = 0; i < frames.length; i++) {
            Frame frame = frames[i];
            if (frame == null) {
                continue;
            }

            releaseFrameCapture(frame);

            if (frame.uniformBufferMemory != VK10.VK_NULL_HANDLE) {
                VK10.vkUnmapMemory(device, frame.uniformBufferMemory);
            }
            destroyBufferWithMemory(frame.uniformBuffer, frame.uniformBufferMemory);
            frame.uniformBuffer = VK10.VK_NULL_HANDLE;
            frame.uniformBufferMemory = VK10.VK_NULL_HANDLE;
            if (frame.indirectBufferMemory != VK10.VK_NULL_HANDLE) {
                VK10.vkUnmapMemory(device, frame.indirectBufferMemory);
            }
            destroyBufferWithMemory(frame.indirectBuffer, frame.indirectBufferMemory);
            frame.indirectBuffer = VK10.VK_NULL_HANDLE;
            frame.indirectBufferMemory = VK10.VK_NULL_HANDLE;
            if (frame.descriptorPool != VK10.VK_NULL_HANDLE) {
                VK10.vkDestroyDescriptorPool(device, frame.descriptorPool, null);
            }
            if (frame.inFlightFence != VK10.VK_NULL_HANDLE) {
                VK10.vkDestroyFence(device, frame.inFlightFence, null);
            }
            if (frame.imageAcquiredFence != VK10.VK_NULL_HANDLE) {
                VK10.vkDestroyFence(device, frame.imageAcquiredFence, null);
            }

            frames[i] = null;
        }
    }

    private SwapchainSupport querySwapchainSupport(VkPhysicalDevice candidate, MemoryStack stack) {
        SwapchainSupport support = new SwapchainSupport();

        support.capabilities = VkSurfaceCapabilitiesKHR.calloc(stack);
        checkVk(KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR(candidate, surface, support.capabilities), "vkGetPhysicalDeviceSurfaceCapabilitiesKHR");

        IntBuffer pFormatCount = stack.ints(0);
        checkVk(KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(candidate, surface, pFormatCount, null), "vkGetPhysicalDeviceSurfaceFormatsKHR(count)");
        if (pFormatCount.get(0) > 0) {
            support.formats = VkSurfaceFormatKHR.calloc(pFormatCount.get(0), stack);
            checkVk(KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(candidate, surface, pFormatCount, support.formats), "vkGetPhysicalDeviceSurfaceFormatsKHR(list)");
        }

        IntBuffer pPresentModeCount = stack.ints(0);
        checkVk(KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR(candidate, surface, pPresentModeCount, null), "vkGetPhysicalDeviceSurfacePresentModesKHR(count)");
        if (pPresentModeCount.get(0) > 0) {
            support.presentModes = stack.mallocInt(pPresentModeCount.get(0));
            checkVk(KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR(candidate, surface, pPresentModeCount, support.presentModes), "vkGetPhysicalDeviceSurfacePresentModesKHR(list)");
        }

        return support;
    }

    private VkSurfaceFormatKHR chooseSurfaceFormat(VkSurfaceFormatKHR.Buffer formats) {
        if (formats == null || formats.remaining() == 0) {
            throw new IllegalStateException("No Vulkan surface formats available");
        }

        for (int i = 0; i < formats.remaining(); i++) {
            VkSurfaceFormatKHR format = formats.get(i);
            if (format.format() == VK10.VK_FORMAT_B8G8R8A8_UNORM
                    && format.colorSpace() == KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
                return format;
            }
        }
        for (int i = 0; i < formats.remaining(); i++) {
            VkSurfaceFormatKHR format = formats.get(i);
            if (format.format() == VK10.VK_FORMAT_R8G8B8A8_UNORM
                    && format.colorSpace() == KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
                return format;
            }
        }
        for (int i = 0; i < formats.remaining(); i++) {
            VkSurfaceFormatKHR format = formats.get(i);
            if (format.format() == VK10.VK_FORMAT_B8G8R8A8_SRGB
                    && format.colorSpace() == KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
                return format;
            }
        }
        return formats.get(0);
    }

    private int choosePresentMode(IntBuffer presentModes) {
        if (presentModes == null) {
            return KHRSurface.VK_PRESENT_MODE_FIFO_KHR;
        }
        for (int i = 0; i < presentModes.remaining(); i++) {
            int mode = presentModes.get(i);
            if (mode == KHRSurface.VK_PRESENT_MODE_IMMEDIATE_KHR) {
                return mode;
            }
        }
        for (int i = 0; i < presentModes.remaining(); i++) {
            int mode = presentModes.get(i);
            if (mode == KHRSurface.VK_PRESENT_MODE_MAILBOX_KHR) {
                return mode;
            }
        }
        return KHRSurface.VK_PRESENT_MODE_FIFO_KHR;
    }

    private static String presentModeName(int mode) {
        if (mode == KHRSurface.VK_PRESENT_MODE_IMMEDIATE_KHR) {
            return "IMMEDIATE";
        }
        if (mode == KHRSurface.VK_PRESENT_MODE_MAILBOX_KHR) {
            return "MAILBOX";
        }
        if (mode == KHRSurface.VK_PRESENT_MODE_FIFO_RELAXED_KHR) {
            return "FIFO_RELAXED";
        }
        if (mode == KHRSurface.VK_PRESENT_MODE_FIFO_KHR) {
            return "FIFO";
        }
        return "UNKNOWN(" + mode + ")";
    }

    private VkExtent2D chooseSwapExtent(VkSurfaceCapabilitiesKHR capabilities, MemoryStack stack) {
        if (capabilities.currentExtent().width() != 0xFFFFFFFF) {
            return VkExtent2D.calloc(stack).set(capabilities.currentExtent());
        }

        int[] fb = framebufferSize();
        int width = Math.max(capabilities.minImageExtent().width(), Math.min(capabilities.maxImageExtent().width(), fb[0]));
        int height = Math.max(capabilities.minImageExtent().height(), Math.min(capabilities.maxImageExtent().height(), fb[1]));
        return VkExtent2D.calloc(stack).set(width, height);
    }

    private int findDepthFormat() {
        int[] candidates = new int[]{
                VK10.VK_FORMAT_D32_SFLOAT,
                VK10.VK_FORMAT_D32_SFLOAT_S8_UINT,
                VK10.VK_FORMAT_D24_UNORM_S8_UINT
        };
        try (MemoryStack stack = MemoryStack.stackPush()) {
            org.lwjgl.vulkan.VkFormatProperties props = org.lwjgl.vulkan.VkFormatProperties.calloc(stack);
            for (int format : candidates) {
                VK10.vkGetPhysicalDeviceFormatProperties(physicalDevice, format, props);
                if ((props.optimalTilingFeatures() & VK10.VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT) != 0) {
                    return format;
                }
            }
        }
        throw new IllegalStateException("No supported Vulkan depth format found");
    }

    private VulkanTexture createFallbackTexture() {
        ByteBuffer data = MemoryUtil.memAlloc(4);
        data.put(0, (byte) 0xFF);
        data.put(1, (byte) 0xFF);
        data.put(2, (byte) 0xFF);
        data.put(3, (byte) 0xFF);

        try {
            VulkanTexture texture = new VulkanTexture(this, 1, 1, com.mojang.minecraft.renderer.graphics.GraphicsEnums.TextureFormat.RGBA8);
            texture.transition(
                    com.mojang.minecraft.renderer.graphics.ResourceState.TextureAccess.UNDEFINED,
                    com.mojang.minecraft.renderer.graphics.ResourceState.TextureAccess.TRANSFER_DST
            );
            texture.update(0, 0, 1, 1, data);
            texture.transition(
                    com.mojang.minecraft.renderer.graphics.ResourceState.TextureAccess.TRANSFER_DST,
                    com.mojang.minecraft.renderer.graphics.ResourceState.TextureAccess.SHADER_READ
            );
            return texture;
        } finally {
            MemoryUtil.memFree(data);
        }
    }

    void checkVk(int result, String operation) {
        if (result != VK10.VK_SUCCESS) {
            throw new IllegalStateException(operation + " failed with VkResult=" + result);
        }
    }

    static final class Frame {
        VkCommandBuffer commandBuffer;
        long imageAcquiredFence;
        long inFlightFence;
        long descriptorPool;

        long uniformBuffer;
        long uniformBufferMemory;
        long uniformMappedAddress;
        ByteBuffer uniformMapped;
        int uniformCapacity;
        int uniformWriteOffset;

        long indirectBuffer;
        long indirectBufferMemory;
        long indirectMappedAddress;
        ByteBuffer indirectMapped;
        int indirectCapacity;
        int indirectWriteOffset;
        long submissionSerial;

        long captureBuffer;
        long captureBufferMemory;
        int captureSize;
        int captureWidth;
        int captureHeight;
    }

    private static final class SwapchainSupport {
        VkSurfaceCapabilitiesKHR capabilities;
        VkSurfaceFormatKHR.Buffer formats;
        IntBuffer presentModes;
    }

    private static final class PendingBufferDestroy {
        final long buffer;
        final long memory;
        final long retireSubmissionSerial;

        private PendingBufferDestroy(long buffer, long memory, long retireSubmissionSerial) {
            this.buffer = buffer;
            this.memory = memory;
            this.retireSubmissionSerial = retireSubmissionSerial;
        }
    }
}
