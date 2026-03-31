package com.mojang.minecraft.renderer.graphics.vulkan;

import com.mojang.minecraft.renderer.graphics.CommandBuffer;
import com.mojang.minecraft.renderer.graphics.DataType;
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

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

final class VulkanCommandBuffer implements CommandBuffer {
    private static final int DRAW_INDEXED_INDIRECT_COMMAND_SIZE = 5 * Integer.BYTES;
    private static final int DRAW_INDIRECT_COMMAND_SIZE = 4 * Integer.BYTES;
    private static final int INITIAL_BATCH_CAPACITY = 2048;

    private final VulkanContext context;

    private Pipeline currentPipeline;
    private DescriptorSet currentDescriptorSet;
    private boolean insideRenderPass;
    private DescriptorSet cachedSnapshotSource;
    private VulkanPipeline cachedSnapshotPipeline;
    private long cachedSnapshotHandle;
    private int cachedSnapshotStateHash;
    private long lastBoundPipelineHandle = VK10.VK_NULL_HANDLE;
    private long lastBoundPipelineLayoutHandle = VK10.VK_NULL_HANDLE;
    private long lastBoundDescriptorSetHandle = VK10.VK_NULL_HANDLE;
    private long lastBoundVertexBufferHandle = VK10.VK_NULL_HANDLE;
    private long lastBoundVertexBufferOffset = Long.MIN_VALUE;
    private long lastBoundIndexBufferHandle = VK10.VK_NULL_HANDLE;
    private long lastBoundIndexBufferOffset = Long.MIN_VALUE;

    private GraphicsEnums.PrimitiveType[] batchPrimitiveTypes = new GraphicsEnums.PrimitiveType[INITIAL_BATCH_CAPACITY];
    private long[] batchVertexBufferHandles = new long[INITIAL_BATCH_CAPACITY];
    private long[] batchVertexBindOffsets = new long[INITIAL_BATCH_CAPACITY];
    private long[] batchIndexBufferHandles = new long[INITIAL_BATCH_CAPACITY];
    private long[] batchIndexBindOffsets = new long[INITIAL_BATCH_CAPACITY];
    private int[] batchFirstElements = new int[INITIAL_BATCH_CAPACITY];
    private int[] batchElementCounts = new int[INITIAL_BATCH_CAPACITY];
    private int[] batchVertexDrawOffsets = new int[INITIAL_BATCH_CAPACITY];
    private boolean[] batchIndexed = new boolean[INITIAL_BATCH_CAPACITY];

    private final VertexDrawBinding scratchVertexBinding = new VertexDrawBinding();
    private final IndexDrawBinding scratchIndexBinding = new IndexDrawBinding();

    VulkanCommandBuffer(VulkanContext context) {
        this.context = context;
    }

    void resetForFrame() {
        currentPipeline = null;
        currentDescriptorSet = null;
        insideRenderPass = false;
        invalidateDescriptorSnapshotCache();
        invalidateBoundStateCache();
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
        invalidateBoundStateCache();
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
        try (MemoryStack stack = MemoryStack.stackPush()) {
            drawInternal(type, vertexBuffer, indexBuffer, start, count, stack);
        }
    }

    private void drawInternal(GraphicsEnums.PrimitiveType type,
                              VertexBuffer vertexBuffer,
                              IndexBuffer indexBuffer,
                              int start,
                              int count,
                              MemoryStack stack) {
        Objects.requireNonNull(type, "Primitive type cannot be null");
        Objects.requireNonNull(vertexBuffer, "Vertex buffer cannot be null");
        Objects.requireNonNull(currentPipeline, "No pipeline set");

        if (!insideRenderPass) {
            throw new IllegalStateException("draw called without an active render pass");
        }

        VulkanPipeline pipeline = (VulkanPipeline) currentPipeline;
        validateDrawResources(vertexBuffer, indexBuffer, "draw");
        bindPipelineIfNeeded(pipeline, type);

        resolveVertexBinding(pipeline, vertexBuffer, scratchVertexBinding);
        bindVertexBufferIfNeeded(scratchVertexBinding.bufferHandle, scratchVertexBinding.bindOffsetBytes, stack);

        bindDescriptorSetSnapshot(pipeline, stack);

        if (indexBuffer != null) {
            resolveIndexBinding(indexBuffer, start, scratchIndexBinding);
            bindIndexBufferIfNeeded(scratchIndexBinding.bufferHandle, scratchIndexBinding.bindOffsetBytes);
            VK10.vkCmdDrawIndexed(
                    context.getCurrentFrame().commandBuffer,
                    count,
                    1,
                    scratchIndexBinding.firstIndex,
                    scratchVertexBinding.drawVertexOffset,
                    0
            );
        } else {
            int firstVertex = addOrThrow(start, scratchVertexBinding.drawVertexOffset, "Vertex start overflows int when applying sub-allocation base");
            VK10.vkCmdDraw(context.getCurrentFrame().commandBuffer, count, 1, firstVertex, 0);
        }
    }

    @Override
    public void drawBatch(DrawBatch drawBatch) {
        Objects.requireNonNull(drawBatch, "drawBatch cannot be null");
        Objects.requireNonNull(currentPipeline, "No pipeline set");
        if (!insideRenderPass) {
            throw new IllegalStateException("drawBatch called without an active render pass");
        }

        int entryCount = drawBatch.size();
        if (entryCount == 0) {
            return;
        }

        VulkanPipeline pipeline = (VulkanPipeline) currentPipeline;
        ensureBatchCapacity(entryCount);

        for (int i = 0; i < entryCount; i++) {
            GraphicsEnums.PrimitiveType primitiveType = drawBatch.getPrimitiveType(i);
            VertexBuffer vertexBuffer = drawBatch.getVertexBuffer(i);
            IndexBuffer indexBuffer = drawBatch.getIndexBuffer(i);
            int start = drawBatch.getStart(i);
            int count = drawBatch.getCount(i);

            Objects.requireNonNull(primitiveType, "Primitive type cannot be null");
            Objects.requireNonNull(vertexBuffer, "Vertex buffer cannot be null");
            validateDrawResources(vertexBuffer, indexBuffer, "drawBatch");

            resolveVertexBinding(pipeline, vertexBuffer, scratchVertexBinding);
            boolean indexed = indexBuffer != null;
            int firstElement;
            int drawVertexOffsetForIndexed = 0;
            long indexHandle = VK10.VK_NULL_HANDLE;
            long indexBindOffset = 0L;
            if (indexed) {
                resolveIndexBinding(indexBuffer, start, scratchIndexBinding);
                firstElement = scratchIndexBinding.firstIndex;
                drawVertexOffsetForIndexed = scratchVertexBinding.drawVertexOffset;
                indexHandle = scratchIndexBinding.bufferHandle;
                indexBindOffset = scratchIndexBinding.bindOffsetBytes;
            } else {
                firstElement = addOrThrow(start, scratchVertexBinding.drawVertexOffset, "Vertex start overflows int when applying sub-allocation base");
            }

            batchPrimitiveTypes[i] = primitiveType;
            batchVertexBufferHandles[i] = scratchVertexBinding.bufferHandle;
            batchVertexBindOffsets[i] = scratchVertexBinding.bindOffsetBytes;
            batchIndexBufferHandles[i] = indexHandle;
            batchIndexBindOffsets[i] = indexBindOffset;
            batchFirstElements[i] = firstElement;
            batchElementCounts[i] = count;
            batchVertexDrawOffsets[i] = drawVertexOffsetForIndexed;
            batchIndexed[i] = indexed;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            int runStart = 0;
            while (runStart < entryCount) {
                GraphicsEnums.PrimitiveType runType = batchPrimitiveTypes[runStart];
                boolean indexedRun = batchIndexed[runStart];
                long runVertexBufferHandle = batchVertexBufferHandles[runStart];
                long runVertexBindOffset = batchVertexBindOffsets[runStart];
                long runIndexBufferHandle = batchIndexBufferHandles[runStart];
                long runIndexBindOffset = batchIndexBindOffsets[runStart];

                int runEnd = runStart + 1;
                while (runEnd < entryCount) {
                    if (batchPrimitiveTypes[runEnd] != runType) {
                        break;
                    }
                    if (batchIndexed[runEnd] != indexedRun) {
                        break;
                    }
                    if (batchVertexBufferHandles[runEnd] != runVertexBufferHandle
                            || batchVertexBindOffsets[runEnd] != runVertexBindOffset) {
                        break;
                    }
                    if (indexedRun) {
                        if (batchIndexBufferHandles[runEnd] != runIndexBufferHandle
                                || batchIndexBindOffsets[runEnd] != runIndexBindOffset) {
                            break;
                        }
                    }
                    runEnd++;
                }

                int runCount = runEnd - runStart;
                boolean issuedIndirect = false;
                if (runCount > 1 && context.supportsMultiDrawIndirect()) {
                    if (indexedRun) {
                        issuedIndirect = recordIndexedIndirectRun(
                                runStart,
                                runEnd,
                                pipeline,
                                runType,
                                runVertexBufferHandle,
                                runVertexBindOffset,
                                runIndexBufferHandle,
                                runIndexBindOffset,
                                stack
                        );
                    } else {
                        issuedIndirect = recordArrayIndirectRun(
                                runStart,
                                runEnd,
                                pipeline,
                                runType,
                                runVertexBufferHandle,
                                runVertexBindOffset,
                                stack
                        );
                    }
                }

                if (!issuedIndirect) {
                    bindPipelineIfNeeded(pipeline, runType);
                    bindVertexBufferIfNeeded(runVertexBufferHandle, runVertexBindOffset, stack);
                    bindDescriptorSetSnapshot(pipeline, stack);
                    if (indexedRun) {
                        bindIndexBufferIfNeeded(runIndexBufferHandle, runIndexBindOffset);
                    }
                    for (int i = runStart; i < runEnd; i++) {
                        if (indexedRun) {
                            VK10.vkCmdDrawIndexed(
                                    context.getCurrentFrame().commandBuffer,
                                    batchElementCounts[i],
                                    1,
                                    batchFirstElements[i],
                                    batchVertexDrawOffsets[i],
                                    0
                            );
                        } else {
                            VK10.vkCmdDraw(
                                    context.getCurrentFrame().commandBuffer,
                                    batchElementCounts[i],
                                    1,
                                    batchFirstElements[i],
                                    0
                            );
                        }
                    }
                }

                runStart = runEnd;
            }
        }
    }

    private void validateDrawResources(VertexBuffer vertexBuffer, IndexBuffer indexBuffer, String opName) {
        VulkanResourceTransitions.requireVertexBufferState(vertexBuffer, ResourceState.BufferAccess.VERTEX_READ, opName);
        if (indexBuffer != null) {
            VulkanResourceTransitions.requireIndexBufferState(indexBuffer, ResourceState.BufferAccess.INDEX_READ, opName);
        }
    }

    private void ensureBatchCapacity(int required) {
        if (required <= batchPrimitiveTypes.length) {
            return;
        }
        int newCapacity = Math.max(batchPrimitiveTypes.length * 2, required);
        batchPrimitiveTypes = Arrays.copyOf(batchPrimitiveTypes, newCapacity);
        batchVertexBufferHandles = Arrays.copyOf(batchVertexBufferHandles, newCapacity);
        batchVertexBindOffsets = Arrays.copyOf(batchVertexBindOffsets, newCapacity);
        batchIndexBufferHandles = Arrays.copyOf(batchIndexBufferHandles, newCapacity);
        batchIndexBindOffsets = Arrays.copyOf(batchIndexBindOffsets, newCapacity);
        batchFirstElements = Arrays.copyOf(batchFirstElements, newCapacity);
        batchElementCounts = Arrays.copyOf(batchElementCounts, newCapacity);
        batchVertexDrawOffsets = Arrays.copyOf(batchVertexDrawOffsets, newCapacity);
        batchIndexed = Arrays.copyOf(batchIndexed, newCapacity);
    }

    private void bindPipelineIfNeeded(VulkanPipeline pipeline, GraphicsEnums.PrimitiveType primitiveType) {
        long vkPipeline = pipeline.getVkPipeline(primitiveType);
        if (lastBoundPipelineHandle == vkPipeline) {
            return;
        }
        VK10.vkCmdBindPipeline(
                context.getCurrentFrame().commandBuffer,
                VK10.VK_PIPELINE_BIND_POINT_GRAPHICS,
                vkPipeline
        );
        lastBoundPipelineHandle = vkPipeline;

        long currentLayout = pipeline.getVkPipelineLayoutHandle();
        if (lastBoundPipelineLayoutHandle != currentLayout) {
            lastBoundPipelineLayoutHandle = currentLayout;
            lastBoundDescriptorSetHandle = VK10.VK_NULL_HANDLE;
        }
    }

    private void bindVertexBufferIfNeeded(long vertexBufferHandle, long vertexBindOffsetBytes, MemoryStack stack) {
        if (lastBoundVertexBufferHandle == vertexBufferHandle
                && lastBoundVertexBufferOffset == vertexBindOffsetBytes) {
            return;
        }
        LongBuffer vertexBuffers = stack.longs(vertexBufferHandle);
        LongBuffer vertexOffsets = stack.longs(vertexBindOffsetBytes);
        VK10.vkCmdBindVertexBuffers(context.getCurrentFrame().commandBuffer, 0, vertexBuffers, vertexOffsets);
        lastBoundVertexBufferHandle = vertexBufferHandle;
        lastBoundVertexBufferOffset = vertexBindOffsetBytes;
    }

    private void bindIndexBufferIfNeeded(long indexBufferHandle, long indexBindOffsetBytes) {
        if (lastBoundIndexBufferHandle == indexBufferHandle
                && lastBoundIndexBufferOffset == indexBindOffsetBytes) {
            return;
        }
        VK10.vkCmdBindIndexBuffer(
                context.getCurrentFrame().commandBuffer,
                indexBufferHandle,
                indexBindOffsetBytes,
                VK10.VK_INDEX_TYPE_UINT32
        );
        lastBoundIndexBufferHandle = indexBufferHandle;
        lastBoundIndexBufferOffset = indexBindOffsetBytes;
    }

    private boolean recordIndexedIndirectRun(int runStart,
                                             int runEnd,
                                             VulkanPipeline pipeline,
                                             GraphicsEnums.PrimitiveType runType,
                                             long runVertexBufferHandle,
                                             long runVertexBindOffset,
                                             long runIndexBufferHandle,
                                             long runIndexBindOffset,
                                             MemoryStack stack) {
        int runCount = runEnd - runStart;
        VulkanContext.Frame frame = context.getCurrentFrame();
        int alignedOffset = align(frame.indirectWriteOffset, 4);
        int requiredBytes = runCount * DRAW_INDEXED_INDIRECT_COMMAND_SIZE;
        int endOffset = alignedOffset + requiredBytes;
        if (endOffset > frame.indirectCapacity) {
            return false;
        }

        ByteBuffer mapped = frame.indirectMapped;
        int writeOffset = alignedOffset;
        for (int i = runStart; i < runEnd; i++) {
            mapped.putInt(writeOffset, batchElementCounts[i]);
            mapped.putInt(writeOffset + 4, 1);
            mapped.putInt(writeOffset + 8, batchFirstElements[i]);
            mapped.putInt(writeOffset + 12, batchVertexDrawOffsets[i]);
            mapped.putInt(writeOffset + 16, 0);
            writeOffset += DRAW_INDEXED_INDIRECT_COMMAND_SIZE;
        }
        frame.indirectWriteOffset = endOffset;

        bindPipelineIfNeeded(pipeline, runType);
        bindVertexBufferIfNeeded(runVertexBufferHandle, runVertexBindOffset, stack);
        bindDescriptorSetSnapshot(pipeline, stack);
        bindIndexBufferIfNeeded(runIndexBufferHandle, runIndexBindOffset);
        VK10.vkCmdDrawIndexedIndirect(
                frame.commandBuffer,
                frame.indirectBuffer,
                alignedOffset,
                runCount,
                DRAW_INDEXED_INDIRECT_COMMAND_SIZE
        );
        return true;
    }

    private boolean recordArrayIndirectRun(int runStart,
                                           int runEnd,
                                           VulkanPipeline pipeline,
                                           GraphicsEnums.PrimitiveType runType,
                                           long runVertexBufferHandle,
                                           long runVertexBindOffset,
                                           MemoryStack stack) {
        int runCount = runEnd - runStart;
        VulkanContext.Frame frame = context.getCurrentFrame();
        int alignedOffset = align(frame.indirectWriteOffset, 4);
        int requiredBytes = runCount * DRAW_INDIRECT_COMMAND_SIZE;
        int endOffset = alignedOffset + requiredBytes;
        if (endOffset > frame.indirectCapacity) {
            return false;
        }

        ByteBuffer mapped = frame.indirectMapped;
        int writeOffset = alignedOffset;
        for (int i = runStart; i < runEnd; i++) {
            mapped.putInt(writeOffset, batchElementCounts[i]);
            mapped.putInt(writeOffset + 4, 1);
            mapped.putInt(writeOffset + 8, batchFirstElements[i]);
            mapped.putInt(writeOffset + 12, 0);
            writeOffset += DRAW_INDIRECT_COMMAND_SIZE;
        }
        frame.indirectWriteOffset = endOffset;

        bindPipelineIfNeeded(pipeline, runType);
        bindVertexBufferIfNeeded(runVertexBufferHandle, runVertexBindOffset, stack);
        bindDescriptorSetSnapshot(pipeline, stack);
        VK10.vkCmdDrawIndirect(
                frame.commandBuffer,
                frame.indirectBuffer,
                alignedOffset,
                runCount,
                DRAW_INDIRECT_COMMAND_SIZE
        );
        return true;
    }

    private static void resolveVertexBinding(VulkanPipeline pipeline, VertexBuffer vertexBuffer, VertexDrawBinding out) {
        VulkanVertexBufferHandle vertexHandle = asVertexHandle(vertexBuffer);
        long vertexOffsetBytes = vertexHandle.getVkBufferOffset();
        long vertexBindOffsetBytes = vertexOffsetBytes;
        int drawVertexOffset = 0;
        int strideInBytes = pipeline.getVertexFormat().getStrideInBytes();
        if (strideInBytes > 0 && vertexOffsetBytes >= 0) {
            long baseVertexLong = vertexOffsetBytes / strideInBytes;
            if (baseVertexLong <= Integer.MAX_VALUE) {
                long remainder = vertexOffsetBytes - (baseVertexLong * strideInBytes);
                int requiredAlignment = maxVertexAttributeAlignment(pipeline.getVertexFormat());
                if (requiredAlignment <= 0) {
                    requiredAlignment = 1;
                }
                if (isBindOffsetCompatibleWithVertexFormat(pipeline.getVertexFormat(), remainder)
                        && (strideInBytes % requiredAlignment) == 0) {
                    vertexBindOffsetBytes = remainder;
                    drawVertexOffset = (int) baseVertexLong;
                }
            }
        }
        out.set(vertexHandle.getVkBufferHandle(), vertexBindOffsetBytes, drawVertexOffset);
    }

    private static void resolveIndexBinding(IndexBuffer indexBuffer, int start, IndexDrawBinding out) {
        VulkanIndexBufferHandle indexHandle = asIndexHandle(indexBuffer);
        long indexBufferOffsetBytes = indexHandle.getVkBufferOffset();
        long indexBindOffsetBytes = indexBufferOffsetBytes;
        int firstIndex = start;
        if (indexBufferOffsetBytes >= 0 && (indexBufferOffsetBytes & 3L) == 0L) {
            long baseFirstIndexLong = indexBufferOffsetBytes >>> 2;
            if (baseFirstIndexLong <= Integer.MAX_VALUE) {
                firstIndex = addOrThrow(start, (int) baseFirstIndexLong, "Index start overflows int when applying sub-allocation base");
                indexBindOffsetBytes = 0L;
            }
        }
        out.set(indexHandle.getVkBufferHandle(), indexBindOffsetBytes, firstIndex);
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

        int snapshotStateHash = computeDescriptorStateHash(currentDescriptorSet, layout);

        if (cachedSnapshotHandle != VK10.VK_NULL_HANDLE
                && cachedSnapshotSource == currentDescriptorSet
                && cachedSnapshotPipeline == pipeline
                && cachedSnapshotStateHash == snapshotStateHash) {
            long layoutHandle = pipeline.getVkPipelineLayoutHandle();
            if (lastBoundDescriptorSetHandle != cachedSnapshotHandle
                    || lastBoundPipelineLayoutHandle != layoutHandle) {
                VK10.vkCmdBindDescriptorSets(
                        context.getCurrentFrame().commandBuffer,
                        VK10.VK_PIPELINE_BIND_POINT_GRAPHICS,
                        layoutHandle,
                        0,
                        stack.longs(cachedSnapshotHandle),
                        null
                );
                lastBoundDescriptorSetHandle = cachedSnapshotHandle;
                lastBoundPipelineLayoutHandle = layoutHandle;
            }
            return;
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

        long layoutHandle = pipeline.getVkPipelineLayoutHandle();
        VK10.vkCmdBindDescriptorSets(
                context.getCurrentFrame().commandBuffer,
                VK10.VK_PIPELINE_BIND_POINT_GRAPHICS,
                layoutHandle,
                0,
                stack.longs(descriptorSetHandle),
                null
        );
        cachedSnapshotSource = currentDescriptorSet;
        cachedSnapshotPipeline = pipeline;
        cachedSnapshotHandle = descriptorSetHandle;
        cachedSnapshotStateHash = snapshotStateHash;
        lastBoundDescriptorSetHandle = descriptorSetHandle;
        lastBoundPipelineLayoutHandle = layoutHandle;
    }

    private void invalidateDescriptorSnapshotCache() {
        cachedSnapshotSource = null;
        cachedSnapshotPipeline = null;
        cachedSnapshotHandle = VK10.VK_NULL_HANDLE;
        cachedSnapshotStateHash = 0;
    }

    private int computeDescriptorStateHash(DescriptorSet descriptorSet, VulkanPipelineLayout layout) {
        int hash = 1;
        List<PipelineLayout.Binding> bindings = layout.getBindings();
        for (int i = 0; i < bindings.size(); i++) {
            PipelineLayout.Binding binding = bindings.get(i);
            int bindingIndex = binding.getBinding();
            hash = 31 * hash + bindingIndex;
            PipelineLayout.ResourceType resourceType = binding.getResourceType();
            hash = 31 * hash + resourceType.ordinal();

            if (isTextureResourceType(resourceType)) {
                Texture texture = descriptorSet.getTexture(bindingIndex);
                if (texture == null) {
                    texture = context.getFallbackTexture();
                }
                if (texture instanceof VulkanTexture) {
                    VulkanTexture vkTexture = (VulkanTexture) texture;
                    long sampler = vkTexture.getSampler();
                    long imageView = vkTexture.getImageView();
                    hash = 31 * hash + (int) (sampler ^ (sampler >>> 32));
                    hash = 31 * hash + (int) (imageView ^ (imageView >>> 32));
                } else {
                    hash = 31 * hash + System.identityHashCode(texture);
                }
                continue;
            }

            if (isUniformResourceType(resourceType)) {
                Uniform uniform = descriptorSet.getUniform(bindingIndex);
                if (uniform instanceof VulkanUniform) {
                    hash = 31 * hash + ((VulkanUniform) uniform).getMutationVersion();
                } else {
                    hash = 31 * hash + System.identityHashCode(uniform);
                }
            }
        }
        return hash;
    }

    private void invalidateBoundStateCache() {
        lastBoundPipelineHandle = VK10.VK_NULL_HANDLE;
        lastBoundPipelineLayoutHandle = VK10.VK_NULL_HANDLE;
        lastBoundDescriptorSetHandle = VK10.VK_NULL_HANDLE;
        lastBoundVertexBufferHandle = VK10.VK_NULL_HANDLE;
        lastBoundVertexBufferOffset = Long.MIN_VALUE;
        lastBoundIndexBufferHandle = VK10.VK_NULL_HANDLE;
        lastBoundIndexBufferOffset = Long.MIN_VALUE;
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

    private static int addOrThrow(int left, int right, String overflowMessage) {
        if (right > 0 && left > Integer.MAX_VALUE - right) {
            throw new IllegalStateException(overflowMessage);
        }
        if (right < 0 && left < Integer.MIN_VALUE - right) {
            throw new IllegalStateException(overflowMessage);
        }
        return left + right;
    }

    private static int maxVertexAttributeAlignment(VertexBuffer.VertexFormat format) {
        int alignment = 1;
        if (format.hasTexCoords()) {
            alignment = Math.max(alignment, alignmentForDataType(format.getTexCoordDataType()));
        }
        if (format.hasColors()) {
            alignment = Math.max(alignment, alignmentForDataType(format.getColorDataType()));
        } else if (format.hasGrayScale()) {
            alignment = Math.max(alignment, alignmentForDataType(format.getGrayScaleDataType()));
        }
        if (format.hasNormals()) {
            alignment = Math.max(alignment, alignmentForDataType(format.getNormalDataType()));
        }
        if (format.hasPositions()) {
            alignment = Math.max(alignment, alignmentForDataType(format.getPositionDataType()));
        }
        return alignment;
    }

    private static boolean isBindOffsetCompatibleWithVertexFormat(VertexBuffer.VertexFormat format, long bindOffset) {
        long attributeOffset = 0L;

        if (format.hasTexCoords()) {
            if (!isAligned(bindOffset + attributeOffset, alignmentForDataType(format.getTexCoordDataType()))) {
                return false;
            }
            attributeOffset += 2L * format.getTexCoordDataType().getSize();
        }
        if (format.hasColors()) {
            if (!isAligned(bindOffset + attributeOffset, alignmentForDataType(format.getColorDataType()))) {
                return false;
            }
            attributeOffset += 3L * format.getColorDataType().getSize();
        } else if (format.hasGrayScale()) {
            if (!isAligned(bindOffset + attributeOffset, alignmentForDataType(format.getGrayScaleDataType()))) {
                return false;
            }
            attributeOffset += format.getGrayScaleDataType().getSize();
        }
        if (format.hasNormals()) {
            if (!isAligned(bindOffset + attributeOffset, alignmentForDataType(format.getNormalDataType()))) {
                return false;
            }
            attributeOffset += 3L * format.getNormalDataType().getSize();
        }
        if (format.hasPositions()) {
            return isAligned(bindOffset + attributeOffset, alignmentForDataType(format.getPositionDataType()));
        }
        return true;
    }

    private static boolean isAligned(long value, int alignment) {
        if (alignment <= 1) {
            return true;
        }
        return (value % alignment) == 0L;
    }

    private static int alignmentForDataType(DataType dataType) {
        if (dataType == null) {
            return 1;
        }
        int size = dataType.getSize();
        if (size <= 0) {
            return 1;
        }
        return Math.min(size, 4);
    }

    private static final class VertexDrawBinding {
        private long bufferHandle;
        private long bindOffsetBytes;
        private int drawVertexOffset;

        private void set(long bufferHandle, long bindOffsetBytes, int drawVertexOffset) {
            this.bufferHandle = bufferHandle;
            this.bindOffsetBytes = bindOffsetBytes;
            this.drawVertexOffset = drawVertexOffset;
        }
    }

    private static final class IndexDrawBinding {
        private long bufferHandle;
        private long bindOffsetBytes;
        private int firstIndex;

        private void set(long bufferHandle, long bindOffsetBytes, int firstIndex) {
            this.bufferHandle = bufferHandle;
            this.bindOffsetBytes = bindOffsetBytes;
            this.firstIndex = firstIndex;
        }
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
