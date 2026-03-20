package com.mojang.minecraft.renderer.graphics.vulkan;

import com.mojang.minecraft.renderer.graphics.CommandBuffer;
import com.mojang.minecraft.renderer.graphics.DescriptorSet;
import com.mojang.minecraft.renderer.graphics.DrawBatch;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums;
import com.mojang.minecraft.renderer.graphics.IndexBuffer;
import com.mojang.minecraft.renderer.graphics.Pipeline;
import com.mojang.minecraft.renderer.graphics.PipelineLayout;
import com.mojang.minecraft.renderer.graphics.RenderPassAttachments;
import com.mojang.minecraft.renderer.graphics.ResourceState;
import com.mojang.minecraft.renderer.graphics.Texture;
import com.mojang.minecraft.renderer.graphics.Uniform;
import com.mojang.minecraft.renderer.graphics.VertexBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkClearAttachment;
import org.lwjgl.vulkan.VkClearRect;
import org.lwjgl.vulkan.VkClearValue;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkRenderPassBeginInfo;
import org.lwjgl.vulkan.VkViewport;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

import java.nio.LongBuffer;
import java.util.List;
import java.util.Objects;

final class VulkanCommandBuffer implements CommandBuffer {
    private final VulkanContext context;

    private Pipeline currentPipeline;
    private DescriptorSet currentDescriptorSet;
    private boolean insideRenderPass;

    VulkanCommandBuffer(VulkanContext context) {
        this.context = context;
    }

    void resetForFrame() {
        currentPipeline = null;
        currentDescriptorSet = null;
        insideRenderPass = false;
    }

    @Override
    public void setPipeline(Pipeline pipeline) {
        if (pipeline == null) {
            currentPipeline = null;
            return;
        }
        if (!(pipeline instanceof VulkanPipeline)) {
            throw new IllegalArgumentException("Not a Vulkan pipeline");
        }
        if (pipeline.isDisposed()) {
            throw new IllegalStateException("Cannot bind a disposed pipeline");
        }
        currentPipeline = pipeline;
    }

    @Override
    public void setViewport(int x, int y, int width, int height) {
        if (!context.isFrameActive()) {
            throw new IllegalStateException("setViewport called without an active frame");
        }
        RenderArea safeArea = sanitizeRenderArea(x, y, width, height);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkViewport.Buffer viewport = VkViewport.calloc(1, stack)
                    .x(safeArea.x)
                    .y(safeArea.y)
                    .width(safeArea.width)
                    .height(safeArea.height)
                    .minDepth(0.0f)
                    .maxDepth(1.0f);
            VK10.vkCmdSetViewport(context.getCurrentFrame().commandBuffer, 0, viewport);

            VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);
            scissor.offset().set(safeArea.x, safeArea.y);
            scissor.extent().set(safeArea.width, safeArea.height);
            VK10.vkCmdSetScissor(context.getCurrentFrame().commandBuffer, 0, scissor);
        }
    }

    @Override
    public void beginRenderPass(RenderPassAttachments attachments, int x, int y, int width, int height) {
        Objects.requireNonNull(attachments, "attachments cannot be null");
        if (!context.isFrameActive()) {
            throw new IllegalStateException("beginRenderPass called without an active frame");
        }
        if (insideRenderPass) {
            throw new IllegalStateException("beginRenderPass called while another render pass is active");
        }
        RenderArea safeArea = sanitizeRenderArea(x, y, width, height);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkClearValue.Buffer clearValues = VkClearValue.calloc(2, stack);
            clearValues.get(0).color().float32(0, 0.0f).float32(1, 0.0f).float32(2, 0.0f).float32(3, 1.0f);
            clearValues.get(1).depthStencil().set(1.0f, 0);

            VkRenderPassBeginInfo beginInfo = VkRenderPassBeginInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                    .renderPass(context.getRenderPass())
                    .framebuffer(context.getCurrentFramebuffer())
                    .pClearValues(clearValues);
            beginInfo.renderArea().offset().set(safeArea.x, safeArea.y);
            beginInfo.renderArea().extent().set(safeArea.width, safeArea.height);

            VK10.vkCmdBeginRenderPass(
                    context.getCurrentFrame().commandBuffer,
                    beginInfo,
                    VK10.VK_SUBPASS_CONTENTS_INLINE
            );

            VkViewport.Buffer viewport = VkViewport.calloc(1, stack)
                    .x(safeArea.x)
                    .y(safeArea.y)
                    .width(safeArea.width)
                    .height(safeArea.height)
                    .minDepth(0.0f)
                    .maxDepth(1.0f);
            VK10.vkCmdSetViewport(context.getCurrentFrame().commandBuffer, 0, viewport);

            VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);
            scissor.offset().set(safeArea.x, safeArea.y);
            scissor.extent().set(safeArea.width, safeArea.height);
            VK10.vkCmdSetScissor(context.getCurrentFrame().commandBuffer, 0, scissor);

            issueClearIfRequested(attachments, safeArea.x, safeArea.y, safeArea.width, safeArea.height, stack);
        }

        insideRenderPass = true;
    }

    @Override
    public void endRenderPass() {
        if (!insideRenderPass) {
            throw new IllegalStateException("endRenderPass called without an active render pass");
        }
        VK10.vkCmdEndRenderPass(context.getCurrentFrame().commandBuffer);
        insideRenderPass = false;
    }

    @Override
    public void draw(GraphicsEnums.PrimitiveType type, VertexBuffer vertexBuffer, IndexBuffer indexBuffer, int start, int count) {
        Objects.requireNonNull(type, "Primitive type cannot be null");
        Objects.requireNonNull(vertexBuffer, "Vertex buffer cannot be null");
        Objects.requireNonNull(currentPipeline, "No pipeline set");

        if (!insideRenderPass) {
            throw new IllegalStateException("draw called without an active render pass");
        }

        VulkanResourceTransitions.requireVertexBufferState(vertexBuffer, ResourceState.BufferAccess.VERTEX_READ, "draw");
        if (indexBuffer != null) {
            VulkanResourceTransitions.requireIndexBufferState(indexBuffer, ResourceState.BufferAccess.INDEX_READ, "draw");
        }

        VulkanPipeline pipeline = (VulkanPipeline) currentPipeline;
        long vkPipeline = pipeline.getVkPipeline(type);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VK10.vkCmdBindPipeline(
                    context.getCurrentFrame().commandBuffer,
                    VK10.VK_PIPELINE_BIND_POINT_GRAPHICS,
                    vkPipeline
            );

            VulkanVertexBufferHandle vertexHandle = asVertexHandle(vertexBuffer);
            LongBuffer vertexBuffers = stack.longs(vertexHandle.getVkBufferHandle());
            LongBuffer vertexOffsets = stack.longs(vertexHandle.getVkBufferOffset());
            VK10.vkCmdBindVertexBuffers(context.getCurrentFrame().commandBuffer, 0, vertexBuffers, vertexOffsets);

            bindDescriptorSetSnapshot(pipeline, stack);

            if (indexBuffer != null) {
                VulkanIndexBufferHandle indexHandle = asIndexHandle(indexBuffer);
                long indexOffsetBytes = indexHandle.getVkBufferOffset() + (start * 4L);
                VK10.vkCmdBindIndexBuffer(
                        context.getCurrentFrame().commandBuffer,
                        indexHandle.getVkBufferHandle(),
                        indexOffsetBytes,
                        VK10.VK_INDEX_TYPE_UINT32
                );
                VK10.vkCmdDrawIndexed(context.getCurrentFrame().commandBuffer, count, 1, 0, 0, 0);
            } else {
                VK10.vkCmdDraw(context.getCurrentFrame().commandBuffer, count, 1, start, 0);
            }
        }
    }

    @Override
    public void drawBatch(DrawBatch drawBatch) {
        Objects.requireNonNull(drawBatch, "drawBatch cannot be null");
        int entryCount = drawBatch.size();
        for (int i = 0; i < entryCount; i++) {
            draw(
                    drawBatch.getPrimitiveType(i),
                    drawBatch.getVertexBuffer(i),
                    drawBatch.getIndexBuffer(i),
                    drawBatch.getStart(i),
                    drawBatch.getCount(i)
            );
        }
    }

    @Override
    public void bindDescriptorSet(DescriptorSet descriptorSet) {
        Objects.requireNonNull(descriptorSet, "descriptorSet cannot be null");
        Objects.requireNonNull(currentPipeline, "No pipeline set");

        if (descriptorSet.isDisposed()) {
            throw new IllegalStateException("Cannot bind a disposed descriptor set");
        }
        if (descriptorSet.getLayout() != currentPipeline.getLayout()) {
            throw new IllegalStateException(
                    "Descriptor set layout '" + descriptorSet.getLayout().getDebugName()
                            + "' does not match current pipeline layout '" + currentPipeline.getLayout().getDebugName() + "'"
            );
        }

        currentDescriptorSet = descriptorSet;
    }

    @Override
    public void transitionTexture(Texture texture,
                                  ResourceState.TextureAccess expectedOldAccess,
                                  ResourceState.TextureAccess newAccess) {
        VulkanResourceTransitions.transitionTexture(texture, expectedOldAccess, newAccess);
    }

    @Override
    public void transitionVertexBuffer(VertexBuffer vertexBuffer,
                                       ResourceState.BufferAccess expectedOldAccess,
                                       ResourceState.BufferAccess newAccess) {
        VulkanResourceTransitions.transitionVertexBuffer(vertexBuffer, expectedOldAccess, newAccess);
    }

    @Override
    public void transitionIndexBuffer(IndexBuffer indexBuffer,
                                      ResourceState.BufferAccess expectedOldAccess,
                                      ResourceState.BufferAccess newAccess) {
        VulkanResourceTransitions.transitionIndexBuffer(indexBuffer, expectedOldAccess, newAccess);
    }

    private void bindDescriptorSetSnapshot(VulkanPipeline pipeline, MemoryStack stack) {
        VulkanPipelineLayout layout = (VulkanPipelineLayout) pipeline.getLayout();
        List<PipelineLayout.Binding> bindings = layout.getBindings();
        if (bindings.isEmpty()) {
            return;
        }

        if (currentDescriptorSet == null) {
            throw new IllegalStateException(
                    "Draw for pipeline '" + pipeline.getDebugName() + "' requires descriptor bindings, but no descriptor set is bound"
            );
        }

        VkDescriptorSetAllocateInfo allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                .sType(VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                .descriptorPool(context.getCurrentFrame().descriptorPool)
                .pSetLayouts(stack.longs(layout.getVkDescriptorSetLayout()));

        LongBuffer pDescriptorSet = stack.mallocLong(1);
        context.checkVk(VK10.vkAllocateDescriptorSets(context.getDevice(), allocInfo, pDescriptorSet), "vkAllocateDescriptorSets");
        long descriptorSetHandle = pDescriptorSet.get(0);

        VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(bindings.size(), stack);
        int writeCount = 0;

        for (int i = 0; i < bindings.size(); i++) {
            PipelineLayout.Binding binding = bindings.get(i);
            int bindingIndex = binding.getBinding();

            if (isTextureResourceType(binding.getResourceType())) {
                Texture texture = currentDescriptorSet.getTexture(bindingIndex);
                if (texture == null) {
                    texture = context.getFallbackTexture();
                }
                VulkanResourceTransitions.requireTextureState(
                        texture,
                        ResourceState.TextureAccess.SHADER_READ,
                        "Descriptor binding " + bindingIndex
                );
                VulkanTexture vkTexture = (VulkanTexture) texture;

                VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo.calloc(1, stack)
                        .sampler(vkTexture.getSampler())
                        .imageView(vkTexture.getImageView())
                        .imageLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);

                writes.get(writeCount)
                        .sType(VK10.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                        .dstSet(descriptorSetHandle)
                        .dstBinding(bindingIndex)
                        .dstArrayElement(0)
                        .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                        .descriptorCount(1)
                        .pImageInfo(imageInfo);
                writeCount++;
                continue;
            }

            if (isUniformResourceType(binding.getResourceType())) {
                Uniform uniform = currentDescriptorSet.getUniform(bindingIndex);
                if (uniform == null) {
                    throw new IllegalStateException(
                            "Descriptor set '" + currentDescriptorSet.getLayout().getDebugName()
                                    + "' is missing required uniform binding " + bindingIndex
                    );
                }
                if (!(uniform instanceof VulkanUniform)) {
                    throw new IllegalArgumentException("Uniform must be a Vulkan uniform");
                }

                VulkanUniform vkUniform = (VulkanUniform) uniform;
                int uniformSize = vkUniform.getSizeInBytes();

                int alignedOffset = align(
                        context.getCurrentFrame().uniformWriteOffset,
                        context.getMinUniformBufferOffsetAlignment()
                );
                int endOffset = alignedOffset + uniformSize;
                if (endOffset > context.getCurrentFrame().uniformCapacity) {
                    throw new IllegalStateException(
                            "Uniform ring buffer exhausted for current frame (required end offset " + endOffset
                                    + ", capacity " + context.getCurrentFrame().uniformCapacity + ")"
                    );
                }

                vkUniform.copyCurrentValueTo(context.getCurrentFrame().uniformMapped, alignedOffset);
                context.getCurrentFrame().uniformWriteOffset = endOffset;

                VkDescriptorBufferInfo.Buffer bufferInfo = VkDescriptorBufferInfo.calloc(1, stack)
                        .buffer(context.getCurrentFrame().uniformBuffer)
                        .offset(alignedOffset)
                        .range(uniformSize);

                writes.get(writeCount)
                        .sType(VK10.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                        .dstSet(descriptorSetHandle)
                        .dstBinding(bindingIndex)
                        .dstArrayElement(0)
                        .descriptorType(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                        .descriptorCount(1)
                        .pBufferInfo(bufferInfo);
                writeCount++;
            }
        }

        writes.limit(writeCount);
        VK10.vkUpdateDescriptorSets(context.getDevice(), writes, null);

        VK10.vkCmdBindDescriptorSets(
                context.getCurrentFrame().commandBuffer,
                VK10.VK_PIPELINE_BIND_POINT_GRAPHICS,
                pipeline.getVkPipelineLayoutHandle(),
                0,
                stack.longs(descriptorSetHandle),
                null
        );
    }

    private void issueClearIfRequested(RenderPassAttachments attachments,
                                       int x,
                                       int y,
                                       int width,
                                       int height,
                                       MemoryStack stack) {
        int clearAttachmentCount = 0;
        RenderPassAttachments.ColorAttachment colorAttachment = attachments.getColorAttachment();
        RenderPassAttachments.DepthAttachment depthAttachment = attachments.getDepthAttachment();
        if (colorAttachment != null && colorAttachment.getLoadOp() == RenderPassAttachments.LoadOp.CLEAR) {
            clearAttachmentCount++;
        }
        if (depthAttachment != null && depthAttachment.getLoadOp() == RenderPassAttachments.LoadOp.CLEAR) {
            clearAttachmentCount++;
        }
        if (clearAttachmentCount == 0) {
            return;
        }

        VkClearAttachment.Buffer clearAttachments = VkClearAttachment.calloc(clearAttachmentCount, stack);
        int idx = 0;
        if (colorAttachment != null && colorAttachment.getLoadOp() == RenderPassAttachments.LoadOp.CLEAR) {
            clearAttachments.get(idx)
                    .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                    .colorAttachment(0);
            clearAttachments.get(idx).clearValue().color()
                    .float32(0, colorAttachment.getClearR())
                    .float32(1, colorAttachment.getClearG())
                    .float32(2, colorAttachment.getClearB())
                    .float32(3, colorAttachment.getClearA());
            idx++;
        }
        if (depthAttachment != null && depthAttachment.getLoadOp() == RenderPassAttachments.LoadOp.CLEAR) {
            clearAttachments.get(idx)
                    .aspectMask(VK10.VK_IMAGE_ASPECT_DEPTH_BIT)
                    .colorAttachment(0);
            clearAttachments.get(idx).clearValue().depthStencil().set(depthAttachment.getClearDepth(), 0);
        }

        VkClearRect.Buffer clearRects = VkClearRect.calloc(1, stack);
        clearRects.get(0).rect().offset().set(x, y);
        clearRects.get(0).rect().extent().set(width, height);
        clearRects.get(0).baseArrayLayer(0);
        clearRects.get(0).layerCount(1);

        VK10.vkCmdClearAttachments(
                context.getCurrentFrame().commandBuffer,
                clearAttachments,
                clearRects
        );
    }

    private static boolean isTextureResourceType(PipelineLayout.ResourceType resourceType) {
        return resourceType == PipelineLayout.ResourceType.SAMPLED_TEXTURE
                || resourceType == PipelineLayout.ResourceType.SAMPLER
                || resourceType == PipelineLayout.ResourceType.COMBINED_IMAGE_SAMPLER;
    }

    private static boolean isUniformResourceType(PipelineLayout.ResourceType resourceType) {
        return resourceType == PipelineLayout.ResourceType.UNIFORM_BUFFER
                || resourceType == PipelineLayout.ResourceType.STORAGE_BUFFER;
    }

    private static VulkanVertexBufferHandle asVertexHandle(VertexBuffer vertexBuffer) {
        if (!(vertexBuffer instanceof VulkanVertexBufferHandle)) {
            throw new IllegalArgumentException("VertexBuffer must be Vulkan-backed");
        }
        return (VulkanVertexBufferHandle) vertexBuffer;
    }

    private static VulkanIndexBufferHandle asIndexHandle(IndexBuffer indexBuffer) {
        if (!(indexBuffer instanceof VulkanIndexBufferHandle)) {
            throw new IllegalArgumentException("IndexBuffer must be Vulkan-backed");
        }
        return (VulkanIndexBufferHandle) indexBuffer;
    }

    private static int align(int value, int alignment) {
        int mask = alignment - 1;
        return (value + mask) & ~mask;
    }

    private RenderArea sanitizeRenderArea(int x, int y, int width, int height) {
        int framebufferWidth = context.getSwapchainWidth();
        int framebufferHeight = context.getSwapchainHeight();
        if (framebufferWidth <= 0 || framebufferHeight <= 0) {
            throw new IllegalStateException(
                    "Invalid swapchain extent for render commands: " + framebufferWidth + "x" + framebufferHeight
            );
        }

        int safeX = Math.max(0, Math.min(x, framebufferWidth - 1));
        int safeY = Math.max(0, Math.min(y, framebufferHeight - 1));
        int maxWidth = Math.max(1, framebufferWidth - safeX);
        int maxHeight = Math.max(1, framebufferHeight - safeY);
        int safeWidth = Math.max(1, Math.min(width, maxWidth));
        int safeHeight = Math.max(1, Math.min(height, maxHeight));
        return new RenderArea(safeX, safeY, safeWidth, safeHeight);
    }

    private static final class RenderArea {
        private final int x;
        private final int y;
        private final int width;
        private final int height;

        private RenderArea(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }
}
