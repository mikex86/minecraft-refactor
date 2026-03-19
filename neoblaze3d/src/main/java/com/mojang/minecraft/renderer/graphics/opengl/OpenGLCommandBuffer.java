package com.mojang.minecraft.renderer.graphics.opengl;

import com.mojang.minecraft.renderer.graphics.CommandBuffer;
import com.mojang.minecraft.renderer.graphics.DataType;
import com.mojang.minecraft.renderer.graphics.DescriptorSet;
import com.mojang.minecraft.renderer.graphics.DrawBatch;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.BlendFactor;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.CompareFunc;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.CullMode;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.FillMode;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.PrimitiveType;
import com.mojang.minecraft.renderer.graphics.IndexBuffer;
import com.mojang.minecraft.renderer.graphics.Pipeline;
import com.mojang.minecraft.renderer.graphics.PipelineLayout;
import com.mojang.minecraft.renderer.graphics.RenderPassAttachments;
import com.mojang.minecraft.renderer.graphics.ResourceState;
import com.mojang.minecraft.renderer.graphics.ShaderProgram;
import com.mojang.minecraft.renderer.graphics.Texture;
import com.mojang.minecraft.renderer.graphics.Uniform;
import com.mojang.minecraft.renderer.graphics.VertexBuffer;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL14.glMultiDrawArrays;
import static org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glBufferSubData;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.GL_HALF_FLOAT;
import static org.lwjgl.opengl.GL30.glBindBufferBase;
import static org.lwjgl.opengl.GL30.glVertexAttribIPointer;
import static org.lwjgl.opengl.GL31.GL_UNIFORM_BUFFER;
import static org.lwjgl.opengl.GL31.GL_UNIFORM_BUFFER_OFFSET_ALIGNMENT;
import static org.lwjgl.opengl.GL30.glBindBufferRange;
import static org.lwjgl.opengl.GL11.glGetInteger;
import static org.lwjgl.opengl.GL32.glMultiDrawElementsBaseVertex;

/**
 * OpenGL command buffer implementation.
 * <p>
 * Commands are recorded during frame building and replayed at submit time
 * ({@link #executeRecordedCommands()}) so backend behavior lines up better
 * with deferred APIs like Vulkan.
 */
final class OpenGLCommandBuffer implements CommandBuffer {
    private static final int COMMAND_BEGIN_RENDER_PASS = 1;
    private static final int COMMAND_END_RENDER_PASS = 2;
    private static final int COMMAND_DRAW = 3;
    private static final int COMMAND_SET_VIEWPORT = 4;
    private static final int COMMAND_MULTI_DRAW = 5;

    private static final int SNAPSHOT_KIND_TEXTURE = 1;
    private static final int SNAPSHOT_KIND_UNIFORM = 2;

    private static final int INITIAL_COMMAND_CAPACITY = 2048;
    private static final int INITIAL_SNAPSHOT_CAPACITY = 8192;
    private static final int INITIAL_UNIFORM_RING_CAPACITY = 1 << 20; // 1 MiB
    private static final int INITIAL_MULTI_DRAW_ENTRY_CAPACITY = 4096;

    private Pipeline currentPipeline;
    private DescriptorSet currentDescriptorSet;
    private boolean insideRenderPass;

    private int commandCount;
    private int[] commandKinds;
    private RenderPassAttachments[] commandRenderPassAttachments;
    private int[] commandX;
    private int[] commandY;
    private int[] commandWidth;
    private int[] commandHeight;

    private Pipeline[] drawPipelines;
    private PrimitiveType[] drawPrimitiveTypes;
    private VertexBuffer[] drawVertexBuffers;
    private IndexBuffer[] drawIndexBuffers;
    private int[] drawStarts;
    private int[] drawCounts;
    private int[] drawSnapshotStarts;
    private int[] drawSnapshotCounts;
    private int[] multiDrawEntryStartsByCommand;
    private int[] multiDrawEntryCountsByCommand;
    private int[] multiDrawSnapshotStartsByCommand;
    private int[] multiDrawSnapshotCountsByCommand;

    private int multiDrawEntryCount;
    private PrimitiveType[] multiDrawEntryPrimitiveTypes;
    private VertexBuffer[] multiDrawEntryVertexBuffers;
    private IndexBuffer[] multiDrawEntryIndexBuffers;
    private int[] multiDrawEntryStarts;
    private int[] multiDrawEntryCounts;

    private int snapshotEntryCount;
    private int[] snapshotKinds;
    private int[] snapshotBindings;
    private Texture[] snapshotTextures;
    private int[] snapshotUniformSizes;
    private int[] snapshotUniformOffsets;

    private ByteBuffer uniformRingBuffer;
    private int uniformRingWriteOffset;
    private int uniformRingGpuBufferId;
    private int uniformRingGpuCapacity;
    private int uniformBufferOffsetAlignment;
    private boolean uniformRingUploadDirty;
    private int frameSequence;
    private int[] boundUniformBufferIdsByBinding;
    private int[] boundUniformOffsetsByBinding;
    private int[] boundUniformSizesByBinding;

    OpenGLCommandBuffer() {
        this.commandKinds = new int[INITIAL_COMMAND_CAPACITY];
        this.commandRenderPassAttachments = new RenderPassAttachments[INITIAL_COMMAND_CAPACITY];
        this.commandX = new int[INITIAL_COMMAND_CAPACITY];
        this.commandY = new int[INITIAL_COMMAND_CAPACITY];
        this.commandWidth = new int[INITIAL_COMMAND_CAPACITY];
        this.commandHeight = new int[INITIAL_COMMAND_CAPACITY];

        this.drawPipelines = new Pipeline[INITIAL_COMMAND_CAPACITY];
        this.drawPrimitiveTypes = new PrimitiveType[INITIAL_COMMAND_CAPACITY];
        this.drawVertexBuffers = new VertexBuffer[INITIAL_COMMAND_CAPACITY];
        this.drawIndexBuffers = new IndexBuffer[INITIAL_COMMAND_CAPACITY];
        this.drawStarts = new int[INITIAL_COMMAND_CAPACITY];
        this.drawCounts = new int[INITIAL_COMMAND_CAPACITY];
        this.drawSnapshotStarts = new int[INITIAL_COMMAND_CAPACITY];
        this.drawSnapshotCounts = new int[INITIAL_COMMAND_CAPACITY];
        this.multiDrawEntryStartsByCommand = new int[INITIAL_COMMAND_CAPACITY];
        this.multiDrawEntryCountsByCommand = new int[INITIAL_COMMAND_CAPACITY];
        this.multiDrawSnapshotStartsByCommand = new int[INITIAL_COMMAND_CAPACITY];
        this.multiDrawSnapshotCountsByCommand = new int[INITIAL_COMMAND_CAPACITY];

        this.multiDrawEntryPrimitiveTypes = new PrimitiveType[INITIAL_MULTI_DRAW_ENTRY_CAPACITY];
        this.multiDrawEntryVertexBuffers = new VertexBuffer[INITIAL_MULTI_DRAW_ENTRY_CAPACITY];
        this.multiDrawEntryIndexBuffers = new IndexBuffer[INITIAL_MULTI_DRAW_ENTRY_CAPACITY];
        this.multiDrawEntryStarts = new int[INITIAL_MULTI_DRAW_ENTRY_CAPACITY];
        this.multiDrawEntryCounts = new int[INITIAL_MULTI_DRAW_ENTRY_CAPACITY];

        this.snapshotKinds = new int[INITIAL_SNAPSHOT_CAPACITY];
        this.snapshotBindings = new int[INITIAL_SNAPSHOT_CAPACITY];
        this.snapshotTextures = new Texture[INITIAL_SNAPSHOT_CAPACITY];
        this.snapshotUniformSizes = new int[INITIAL_SNAPSHOT_CAPACITY];
        this.snapshotUniformOffsets = new int[INITIAL_SNAPSHOT_CAPACITY];

        this.uniformRingBuffer = MemoryUtil.memAlloc(INITIAL_UNIFORM_RING_CAPACITY);
        this.uniformRingGpuBufferId = 0;
        this.uniformRingGpuCapacity = INITIAL_UNIFORM_RING_CAPACITY;
        this.uniformBufferOffsetAlignment = 16;
        this.uniformRingUploadDirty = false;
        this.frameSequence = 1;
        this.boundUniformBufferIdsByBinding = new int[8];
        this.boundUniformOffsetsByBinding = new int[8];
        this.boundUniformSizesByBinding = new int[8];
    }

    void initializeResources() {
        if (uniformRingGpuBufferId != 0) {
            return;
        }
        uniformBufferOffsetAlignment = Math.max(16, glGetInteger(GL_UNIFORM_BUFFER_OFFSET_ALIGNMENT));
        uniformRingGpuBufferId = glGenBuffers();
        glBindBuffer(GL_UNIFORM_BUFFER, uniformRingGpuBufferId);
        glBufferData(GL_UNIFORM_BUFFER, uniformRingGpuCapacity, GL_DYNAMIC_DRAW);
        glBindBuffer(GL_UNIFORM_BUFFER, 0);
    }

    void reset() {
        for (int i = 0; i < commandCount; i++) {
            commandRenderPassAttachments[i] = null;
            drawPipelines[i] = null;
            drawPrimitiveTypes[i] = null;
            drawVertexBuffers[i] = null;
            drawIndexBuffers[i] = null;
        }
        for (int i = 0; i < multiDrawEntryCount; i++) {
            multiDrawEntryPrimitiveTypes[i] = null;
            multiDrawEntryVertexBuffers[i] = null;
            multiDrawEntryIndexBuffers[i] = null;
        }
        for (int i = 0; i < snapshotEntryCount; i++) {
            snapshotTextures[i] = null;
            snapshotUniformSizes[i] = 0;
        }

        currentPipeline = null;
        currentDescriptorSet = null;
        insideRenderPass = false;

        commandCount = 0;
        multiDrawEntryCount = 0;
        snapshotEntryCount = 0;
        uniformRingWriteOffset = 0;
        uniformRingUploadDirty = false;
        frameSequence++;
        if (frameSequence <= 0) {
            frameSequence = 1;
        }
    }

    void dispose() {
        if (uniformRingGpuBufferId != 0) {
            glDeleteBuffers(uniformRingGpuBufferId);
            uniformRingGpuBufferId = 0;
        }
        if (uniformRingBuffer != null) {
            MemoryUtil.memFree(uniformRingBuffer);
            uniformRingBuffer = null;
        }
    }

    void executeRecordedCommands() {
        ensureGpuResourcesInitialized();

        Pipeline activePipeline = null;
        boolean replayInsideRenderPass = false;
        resetUniformBindingCache();

        uploadUniformRingIfNeeded();

        for (int i = 0; i < commandCount; i++) {
            int commandKind = commandKinds[i];
            switch (commandKind) {
                case COMMAND_SET_VIEWPORT:
                    executeSetViewport(commandX[i], commandY[i], commandWidth[i], commandHeight[i]);
                    break;
                case COMMAND_BEGIN_RENDER_PASS:
                    executeBeginRenderPass(commandRenderPassAttachments[i], commandX[i], commandY[i], commandWidth[i], commandHeight[i]);
                    replayInsideRenderPass = true;
                    break;
                case COMMAND_END_RENDER_PASS:
                    if (!replayInsideRenderPass) {
                        throw new IllegalStateException("Recorded endRenderPass without active render pass");
                    }
                    replayInsideRenderPass = false;
                    break;
                case COMMAND_DRAW:
                    if (!replayInsideRenderPass) {
                        throw new IllegalStateException("Recorded draw outside render pass");
                    }
                    Pipeline drawPipeline = drawPipelines[i];
                    if (drawPipeline == null) {
                        throw new IllegalStateException("Recorded draw has no pipeline");
                    }
                    if (drawPipeline != activePipeline) {
                        applyPipelineState(drawPipeline);
                        activePipeline = drawPipeline;
                    }

                    bindDescriptorSnapshot(drawPipeline.getLayout(), drawSnapshotStarts[i], drawSnapshotCounts[i]);
                    executeDraw(
                            drawPrimitiveTypes[i],
                            drawVertexBuffers[i],
                            drawIndexBuffers[i],
                            drawStarts[i],
                            drawCounts[i],
                            drawPipeline.getVertexFormat()
                    );
                    break;
                case COMMAND_MULTI_DRAW:
                    if (!replayInsideRenderPass) {
                        throw new IllegalStateException("Recorded drawBatch outside render pass");
                    }
                    Pipeline multiDrawPipeline = drawPipelines[i];
                    if (multiDrawPipeline == null) {
                        throw new IllegalStateException("Recorded drawBatch has no pipeline");
                    }
                    if (multiDrawPipeline != activePipeline) {
                        applyPipelineState(multiDrawPipeline);
                        activePipeline = multiDrawPipeline;
                    }
                    bindDescriptorSnapshot(
                            multiDrawPipeline.getLayout(),
                            multiDrawSnapshotStartsByCommand[i],
                            multiDrawSnapshotCountsByCommand[i]
                    );
                    executeMultiDraw(
                            multiDrawEntryStartsByCommand[i],
                            multiDrawEntryCountsByCommand[i],
                            multiDrawPipeline.getVertexFormat()
                    );
                    break;
                default:
                    throw new IllegalStateException("Unknown recorded command kind: " + commandKind);
            }
        }

        if (replayInsideRenderPass) {
            throw new IllegalStateException("Recorded command stream ended with an open render pass");
        }

        glUseProgram(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    @Override
    public void setPipeline(Pipeline pipeline) {
        if (pipeline == null) {
            currentPipeline = null;
            return;
        }
        validatePipelineForRecord(pipeline);
        currentPipeline = pipeline;
    }

    @Override
    public void setViewport(int x, int y, int width, int height) {
        int commandIndex = appendCommand(COMMAND_SET_VIEWPORT);
        commandX[commandIndex] = x;
        commandY[commandIndex] = y;
        commandWidth[commandIndex] = width;
        commandHeight[commandIndex] = height;
    }

    @Override
    public void beginRenderPass(RenderPassAttachments attachments, int x, int y, int width, int height) {
        Objects.requireNonNull(attachments, "attachments cannot be null");
        if (insideRenderPass) {
            throw new IllegalStateException("beginRenderPass called while another render pass is active");
        }

        int commandIndex = appendCommand(COMMAND_BEGIN_RENDER_PASS);
        commandRenderPassAttachments[commandIndex] = attachments;
        commandX[commandIndex] = x;
        commandY[commandIndex] = y;
        commandWidth[commandIndex] = width;
        commandHeight[commandIndex] = height;
        insideRenderPass = true;
    }

    @Override
    public void endRenderPass() {
        if (!insideRenderPass) {
            throw new IllegalStateException("endRenderPass called without an active render pass");
        }
        appendCommand(COMMAND_END_RENDER_PASS);
        insideRenderPass = false;
    }

    @Override
    public void draw(PrimitiveType type, VertexBuffer vertexBuffer, IndexBuffer indexBuffer, int start, int count) {
        Objects.requireNonNull(type, "Primitive type cannot be null");
        Objects.requireNonNull(vertexBuffer, "Vertex buffer cannot be null");
        Objects.requireNonNull(currentPipeline, "No pipeline set");
        if (!insideRenderPass) {
            throw new IllegalStateException("draw called without an active render pass");
        }
        OpenGLResourceTransitions.requireVertexBufferState(
                vertexBuffer,
                ResourceState.BufferAccess.VERTEX_READ,
                "draw"
        );
        if (indexBuffer != null) {
            OpenGLResourceTransitions.requireIndexBufferState(
                    indexBuffer,
                    ResourceState.BufferAccess.INDEX_READ,
                    "draw"
            );
        }

        int snapshotStart = snapshotEntryCount;
        int snapshotCount = captureCurrentDescriptorSnapshotForDraw();

        int commandIndex = appendCommand(COMMAND_DRAW);
        drawPipelines[commandIndex] = currentPipeline;
        drawPrimitiveTypes[commandIndex] = type;
        drawVertexBuffers[commandIndex] = vertexBuffer;
        drawIndexBuffers[commandIndex] = indexBuffer;
        drawStarts[commandIndex] = start;
        drawCounts[commandIndex] = count;
        drawSnapshotStarts[commandIndex] = snapshotStart;
        drawSnapshotCounts[commandIndex] = snapshotCount;
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

        int snapshotStart = snapshotEntryCount;
        int snapshotCount = captureCurrentDescriptorSnapshotForDraw();
        int entryStart = multiDrawEntryCount;
        ensureMultiDrawEntryCapacity(entryCount);

        for (int i = 0; i < entryCount; i++) {
            PrimitiveType type = drawBatch.getPrimitiveType(i);
            VertexBuffer vertexBuffer = drawBatch.getVertexBuffer(i);
            IndexBuffer indexBuffer = drawBatch.getIndexBuffer(i);
            int start = drawBatch.getStart(i);
            int count = drawBatch.getCount(i);

            OpenGLResourceTransitions.requireVertexBufferState(
                    vertexBuffer,
                    ResourceState.BufferAccess.VERTEX_READ,
                    "drawBatch"
            );
            if (indexBuffer != null) {
                OpenGLResourceTransitions.requireIndexBufferState(
                        indexBuffer,
                        ResourceState.BufferAccess.INDEX_READ,
                        "drawBatch"
                );
            }

            int writeIndex = multiDrawEntryCount++;
            multiDrawEntryPrimitiveTypes[writeIndex] = type;
            multiDrawEntryVertexBuffers[writeIndex] = vertexBuffer;
            multiDrawEntryIndexBuffers[writeIndex] = indexBuffer;
            multiDrawEntryStarts[writeIndex] = start;
            multiDrawEntryCounts[writeIndex] = count;
        }

        int commandIndex = appendCommand(COMMAND_MULTI_DRAW);
        drawPipelines[commandIndex] = currentPipeline;
        multiDrawEntryStartsByCommand[commandIndex] = entryStart;
        multiDrawEntryCountsByCommand[commandIndex] = entryCount;
        multiDrawSnapshotStartsByCommand[commandIndex] = snapshotStart;
        multiDrawSnapshotCountsByCommand[commandIndex] = snapshotCount;
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
        OpenGLResourceTransitions.transitionTexture(texture, expectedOldAccess, newAccess);
    }

    @Override
    public void transitionVertexBuffer(VertexBuffer vertexBuffer,
                                       ResourceState.BufferAccess expectedOldAccess,
                                       ResourceState.BufferAccess newAccess) {
        OpenGLResourceTransitions.transitionVertexBuffer(vertexBuffer, expectedOldAccess, newAccess);
    }

    @Override
    public void transitionIndexBuffer(IndexBuffer indexBuffer,
                                      ResourceState.BufferAccess expectedOldAccess,
                                      ResourceState.BufferAccess newAccess) {
        OpenGLResourceTransitions.transitionIndexBuffer(indexBuffer, expectedOldAccess, newAccess);
    }

    private int captureCurrentDescriptorSnapshotForDraw() {
        List<PipelineLayout.Binding> bindings = currentPipeline.getLayout().getBindings();
        if (bindings.isEmpty()) {
            return 0;
        }

        if (currentDescriptorSet == null) {
            throw new IllegalStateException(
                    "Draw for pipeline '" + currentPipeline.getDebugName() + "' requires descriptor bindings, but no descriptor set is bound"
            );
        }
        if (currentDescriptorSet.getLayout() != currentPipeline.getLayout()) {
            throw new IllegalStateException(
                    "Current descriptor set layout does not match pipeline layout for pipeline '" + currentPipeline.getDebugName() + "'"
            );
        }

        int snapshotStart = snapshotEntryCount;

        //noinspection ForLoopReplaceableByForEach
        for (int i = 0, n = bindings.size(); i < n; i++) {
            PipelineLayout.Binding declaredBinding = bindings.get(i);
            int binding = declaredBinding.getBinding();
            PipelineLayout.ResourceType resourceType = declaredBinding.getResourceType();
            if (isTextureResourceType(resourceType)) {
                Texture texture = currentDescriptorSet.getTexture(binding);
                if (texture != null) {
                    OpenGLResourceTransitions.requireTextureState(
                            texture,
                            ResourceState.TextureAccess.SHADER_READ,
                            "Descriptor binding " + binding
                    );
                }
                ensureSnapshotCapacity(1);
                snapshotKinds[snapshotEntryCount] = SNAPSHOT_KIND_TEXTURE;
                snapshotBindings[snapshotEntryCount] = binding;
                snapshotTextures[snapshotEntryCount] = texture;
                snapshotUniformSizes[snapshotEntryCount] = 0;
                snapshotUniformOffsets[snapshotEntryCount] = 0;
                snapshotEntryCount++;
                continue;
            }
            if (isUniformResourceType(resourceType)) {
                Uniform uniform = currentDescriptorSet.getUniform(binding);
                if (uniform == null) {
                    throw new IllegalStateException(
                            "Descriptor set '" + currentDescriptorSet.getLayout().getDebugName()
                                    + "' is missing required uniform binding " + binding
                    );
                }
                if (!(uniform instanceof OpenGLUniform)) {
                    throw new IllegalArgumentException("Uniform must be an OpenGL uniform");
                }

                OpenGLUniform glUniform = (OpenGLUniform) uniform;
                int dataOffset = appendUniformSnapshot(glUniform);

                ensureSnapshotCapacity(1);
                snapshotKinds[snapshotEntryCount] = SNAPSHOT_KIND_UNIFORM;
                snapshotBindings[snapshotEntryCount] = binding;
                snapshotTextures[snapshotEntryCount] = null;
                snapshotUniformSizes[snapshotEntryCount] = glUniform.getSizeInBytes();
                snapshotUniformOffsets[snapshotEntryCount] = dataOffset;
                snapshotEntryCount++;
            }
        }

        return snapshotEntryCount - snapshotStart;
    }

    private int appendUniformSnapshot(OpenGLUniform uniform) {
        int reusedOffset = uniform.getSnapshotOffsetForFrame(frameSequence);
        if (reusedOffset >= 0) {
            return reusedOffset;
        }

        int sizeInBytes = uniform.getSizeInBytes();
        int alignedOffset = align(uniformRingWriteOffset, uniformBufferOffsetAlignment);
        ensureUniformRingCapacity(alignedOffset + sizeInBytes);
        uniform.copyCurrentValueTo(uniformRingBuffer, alignedOffset);
        uniformRingWriteOffset = alignedOffset + sizeInBytes;
        uniformRingUploadDirty = true;
        uniform.setSnapshotOffsetForFrame(frameSequence, alignedOffset);
        return alignedOffset;
    }

    private void bindDescriptorSnapshot(PipelineLayout layout, int snapshotStart, int snapshotCount) {
        if (layout.getBindings().isEmpty()) {
            return;
        }
        for (int i = snapshotStart; i < snapshotStart + snapshotCount; i++) {
            int kind = snapshotKinds[i];
            int binding = snapshotBindings[i];

            if (kind == SNAPSHOT_KIND_TEXTURE) {
                bindTextureUnit(binding, snapshotTextures[i]);
                continue;
            }
            if (kind == SNAPSHOT_KIND_UNIFORM) {
                int sizeInBytes = snapshotUniformSizes[i];
                if (sizeInBytes <= 0) {
                    throw new IllegalStateException("Recorded uniform snapshot has invalid size for binding " + binding);
                }
                ensureUniformBindingCacheCapacity(binding + 1);
                int offset = snapshotUniformOffsets[i];
                if (boundUniformBufferIdsByBinding[binding] == uniformRingGpuBufferId
                        && boundUniformOffsetsByBinding[binding] == offset
                        && boundUniformSizesByBinding[binding] == sizeInBytes) {
                    continue;
                }

                glBindBufferRange(GL_UNIFORM_BUFFER, binding, uniformRingGpuBufferId, offset, sizeInBytes);
                boundUniformBufferIdsByBinding[binding] = uniformRingGpuBufferId;
                boundUniformOffsetsByBinding[binding] = offset;
                boundUniformSizesByBinding[binding] = sizeInBytes;
                continue;
            }
            throw new IllegalStateException("Unknown snapshot entry kind: " + kind);
        }
    }

    private void uploadUniformRingIfNeeded() {
        if (!uniformRingUploadDirty || uniformRingWriteOffset <= 0) {
            return;
        }
        ensureUniformRingGpuCapacity(uniformRingWriteOffset);

        glBindBuffer(GL_UNIFORM_BUFFER, uniformRingGpuBufferId);
        uniformRingBuffer.position(0);
        uniformRingBuffer.limit(uniformRingWriteOffset);
        glBufferSubData(GL_UNIFORM_BUFFER, 0, uniformRingBuffer);
        glBindBuffer(GL_UNIFORM_BUFFER, 0);
        uniformRingUploadDirty = false;
    }

    private void executeSetViewport(int x, int y, int width, int height) {
        glViewport(x, y, width, height);
    }

    private void executeBeginRenderPass(RenderPassAttachments attachments, int x, int y, int width, int height) {
        executeSetViewport(x, y, width, height);

        int clearBits = 0;
        RenderPassAttachments.ColorAttachment colorAttachment = attachments.getColorAttachment();
        if (colorAttachment != null && colorAttachment.getLoadOp() == RenderPassAttachments.LoadOp.CLEAR) {
            glClearColor(
                    colorAttachment.getClearR(),
                    colorAttachment.getClearG(),
                    colorAttachment.getClearB(),
                    colorAttachment.getClearA()
            );
            clearBits |= GL_COLOR_BUFFER_BIT;
        }

        RenderPassAttachments.DepthAttachment depthAttachment = attachments.getDepthAttachment();
        if (depthAttachment != null && depthAttachment.getLoadOp() == RenderPassAttachments.LoadOp.CLEAR) {
            glClearDepth(depthAttachment.getClearDepth());
            clearBits |= GL_DEPTH_BUFFER_BIT;
        }

        if (clearBits != 0) {
            glClear(clearBits);
        }
    }

    private void executeDraw(PrimitiveType type,
                             VertexBuffer vertexBuffer,
                             IndexBuffer indexBuffer,
                             int start,
                             int count,
                             VertexBuffer.VertexFormat vertexFormat) {
        setupVertexAttributes(vertexBuffer, vertexFormat);

        if (indexBuffer != null) {
            long indexOffset = resolveIndexOffsetBytes(indexBuffer) + start * 4L;
            bindIndexBuffer(indexBuffer);
            glDrawElements(translatePrimitiveType(type), count, GL_UNSIGNED_INT, indexOffset);
        } else {
            glDrawArrays(translatePrimitiveType(type), start, count);
        }

        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    private void executeMultiDraw(int entryStart, int entryCount, VertexBuffer.VertexFormat vertexFormat) {
        if (entryCount <= 0) {
            return;
        }
        int runStart = entryStart;
        int end = entryStart + entryCount;
        while (runStart < end) {
            int runEnd = runStart + 1;
            while (runEnd < end && canShareMultiDrawRun(runStart, runEnd)) {
                runEnd++;
            }
            executeMultiDrawRun(runStart, runEnd - runStart, vertexFormat);
            runStart = runEnd;
        }
    }

    private boolean canShareMultiDrawRun(int runAnchorEntry, int candidateEntry) {
        if (multiDrawEntryPrimitiveTypes[runAnchorEntry] != multiDrawEntryPrimitiveTypes[candidateEntry]) {
            return false;
        }
        IndexBuffer anchorIndexBuffer = multiDrawEntryIndexBuffers[runAnchorEntry];
        IndexBuffer candidateIndexBuffer = multiDrawEntryIndexBuffers[candidateEntry];
        boolean indexed = anchorIndexBuffer != null;
        if (indexed != (candidateIndexBuffer != null)) {
            return false;
        }
        if (resolveVertexBufferId(multiDrawEntryVertexBuffers[runAnchorEntry])
                != resolveVertexBufferId(multiDrawEntryVertexBuffers[candidateEntry])) {
            return false;
        }
        if (indexed && resolveIndexBufferId(anchorIndexBuffer) != resolveIndexBufferId(candidateIndexBuffer)) {
            return false;
        }
        return true;
    }

    private void executeMultiDrawRun(int runStart, int runCount, VertexBuffer.VertexFormat vertexFormat) {
        if (runCount <= 0) {
            return;
        }
        if (multiDrawEntryIndexBuffers[runStart] != null) {
            executeIndexedMultiDrawRun(runStart, runCount, vertexFormat);
            return;
        }
        executeArrayMultiDrawRun(runStart, runCount, vertexFormat);
    }

    private void executeIndexedMultiDrawRun(int runStart, int runCount, VertexBuffer.VertexFormat vertexFormat) {
        PrimitiveType primitiveType = multiDrawEntryPrimitiveTypes[runStart];
        int mode = translatePrimitiveType(primitiveType);
        int vertexBufferId = resolveVertexBufferId(multiDrawEntryVertexBuffers[runStart]);
        int indexBufferId = resolveIndexBufferId(multiDrawEntryIndexBuffers[runStart]);

        setupVertexAttributes(vertexBufferId, 0L, vertexFormat);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indexBufferId);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            java.nio.IntBuffer counts = stack.mallocInt(runCount);
            PointerBuffer indices = stack.mallocPointer(runCount);
            java.nio.IntBuffer baseVertices = stack.mallocInt(runCount);

            for (int i = 0; i < runCount; i++) {
                int entry = runStart + i;
                VertexBuffer vertexBuffer = multiDrawEntryVertexBuffers[entry];
                IndexBuffer indexBuffer = multiDrawEntryIndexBuffers[entry];
                if (resolveVertexBufferId(vertexBuffer) != vertexBufferId
                        || resolveIndexBufferId(indexBuffer) != indexBufferId) {
                    throw new IllegalStateException("drawBatch compatibility run changed unexpectedly");
                }

                counts.put(i, multiDrawEntryCounts[entry]);
                indices.put(i, resolveIndexOffsetBytes(indexBuffer) + ((long) multiDrawEntryStarts[entry] * 4L));
                baseVertices.put(i, resolveVertexBaseVertex(vertexBuffer, vertexFormat));
            }

            glMultiDrawElementsBaseVertex(mode, counts, GL_UNSIGNED_INT, indices, baseVertices);
        }
    }

    private void executeArrayMultiDrawRun(int runStart, int runCount, VertexBuffer.VertexFormat vertexFormat) {
        PrimitiveType primitiveType = multiDrawEntryPrimitiveTypes[runStart];
        int mode = translatePrimitiveType(primitiveType);
        int vertexBufferId = resolveVertexBufferId(multiDrawEntryVertexBuffers[runStart]);

        setupVertexAttributes(vertexBufferId, 0L, vertexFormat);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            java.nio.IntBuffer first = stack.mallocInt(runCount);
            java.nio.IntBuffer count = stack.mallocInt(runCount);

            for (int i = 0; i < runCount; i++) {
                int entry = runStart + i;
                VertexBuffer vertexBuffer = multiDrawEntryVertexBuffers[entry];
                if (resolveVertexBufferId(vertexBuffer) != vertexBufferId) {
                    throw new IllegalStateException("drawBatch compatibility run changed unexpectedly");
                }

                first.put(i, multiDrawEntryStarts[entry] + resolveVertexBaseVertex(vertexBuffer, vertexFormat));
                count.put(i, multiDrawEntryCounts[entry]);
            }

            glMultiDrawArrays(mode, first, count);
        }
    }

    private void applyPipelineState(Pipeline pipeline) {
        validatePipelineForRecord(pipeline);
        bindProgram(pipeline.getProgram());
        applyBlendState(pipeline.getBlendState());
        applyDepthState(pipeline.getDepthState());
        applyRasterizerState(pipeline.getRasterizerState());
    }

    private void validatePipelineForRecord(Pipeline pipeline) {
        if (!(pipeline instanceof OpenGLPipeline)) {
            throw new IllegalArgumentException("Not an OpenGL pipeline");
        }
        if (pipeline.isDisposed()) {
            throw new IllegalStateException("Cannot bind a disposed pipeline");
        }
    }

    private int appendCommand(int commandKind) {
        ensureCommandCapacity(1);
        int commandIndex = commandCount++;
        commandKinds[commandIndex] = commandKind;
        return commandIndex;
    }

    private void ensureCommandCapacity(int additional) {
        int required = commandCount + additional;
        if (required <= commandKinds.length) {
            return;
        }
        int newCapacity = Math.max(commandKinds.length * 2, required);

        commandKinds = Arrays.copyOf(commandKinds, newCapacity);
        commandRenderPassAttachments = Arrays.copyOf(commandRenderPassAttachments, newCapacity);
        commandX = Arrays.copyOf(commandX, newCapacity);
        commandY = Arrays.copyOf(commandY, newCapacity);
        commandWidth = Arrays.copyOf(commandWidth, newCapacity);
        commandHeight = Arrays.copyOf(commandHeight, newCapacity);

        drawPipelines = Arrays.copyOf(drawPipelines, newCapacity);
        drawPrimitiveTypes = Arrays.copyOf(drawPrimitiveTypes, newCapacity);
        drawVertexBuffers = Arrays.copyOf(drawVertexBuffers, newCapacity);
        drawIndexBuffers = Arrays.copyOf(drawIndexBuffers, newCapacity);
        drawStarts = Arrays.copyOf(drawStarts, newCapacity);
        drawCounts = Arrays.copyOf(drawCounts, newCapacity);
        drawSnapshotStarts = Arrays.copyOf(drawSnapshotStarts, newCapacity);
        drawSnapshotCounts = Arrays.copyOf(drawSnapshotCounts, newCapacity);
        multiDrawEntryStartsByCommand = Arrays.copyOf(multiDrawEntryStartsByCommand, newCapacity);
        multiDrawEntryCountsByCommand = Arrays.copyOf(multiDrawEntryCountsByCommand, newCapacity);
        multiDrawSnapshotStartsByCommand = Arrays.copyOf(multiDrawSnapshotStartsByCommand, newCapacity);
        multiDrawSnapshotCountsByCommand = Arrays.copyOf(multiDrawSnapshotCountsByCommand, newCapacity);
    }

    private void ensureSnapshotCapacity(int additional) {
        int required = snapshotEntryCount + additional;
        if (required <= snapshotKinds.length) {
            return;
        }
        int newCapacity = Math.max(snapshotKinds.length * 2, required);
        snapshotKinds = Arrays.copyOf(snapshotKinds, newCapacity);
        snapshotBindings = Arrays.copyOf(snapshotBindings, newCapacity);
        snapshotTextures = Arrays.copyOf(snapshotTextures, newCapacity);
        snapshotUniformSizes = Arrays.copyOf(snapshotUniformSizes, newCapacity);
        snapshotUniformOffsets = Arrays.copyOf(snapshotUniformOffsets, newCapacity);
    }

    private void ensureMultiDrawEntryCapacity(int additional) {
        int required = multiDrawEntryCount + additional;
        if (required <= multiDrawEntryPrimitiveTypes.length) {
            return;
        }
        int newCapacity = Math.max(multiDrawEntryPrimitiveTypes.length * 2, required);
        multiDrawEntryPrimitiveTypes = Arrays.copyOf(multiDrawEntryPrimitiveTypes, newCapacity);
        multiDrawEntryVertexBuffers = Arrays.copyOf(multiDrawEntryVertexBuffers, newCapacity);
        multiDrawEntryIndexBuffers = Arrays.copyOf(multiDrawEntryIndexBuffers, newCapacity);
        multiDrawEntryStarts = Arrays.copyOf(multiDrawEntryStarts, newCapacity);
        multiDrawEntryCounts = Arrays.copyOf(multiDrawEntryCounts, newCapacity);
    }

    private void ensureUniformRingCapacity(int requiredBytes) {
        if (requiredBytes <= uniformRingBuffer.capacity()) {
            return;
        }
        int newCapacity = Math.max(uniformRingBuffer.capacity() * 2, requiredBytes);
        uniformRingBuffer = MemoryUtil.memRealloc(uniformRingBuffer, newCapacity);
        ensureUniformRingGpuCapacity(newCapacity);
    }

    private void ensureUniformRingGpuCapacity(int requiredBytes) {
        if (requiredBytes <= uniformRingGpuCapacity) {
            return;
        }
        int newCapacity = Math.max(uniformRingGpuCapacity * 2, requiredBytes);
        if (uniformRingGpuBufferId == 0) {
            uniformRingGpuCapacity = newCapacity;
            return;
        }
        glBindBuffer(GL_UNIFORM_BUFFER, uniformRingGpuBufferId);
        glBufferData(GL_UNIFORM_BUFFER, newCapacity, GL_DYNAMIC_DRAW);
        glBindBuffer(GL_UNIFORM_BUFFER, 0);
        uniformRingGpuCapacity = newCapacity;
    }

    private void resetUniformBindingCache() {
        Arrays.fill(boundUniformBufferIdsByBinding, -1);
        Arrays.fill(boundUniformOffsetsByBinding, -1);
        Arrays.fill(boundUniformSizesByBinding, -1);
    }

    private void ensureUniformBindingCacheCapacity(int required) {
        if (required <= boundUniformBufferIdsByBinding.length) {
            return;
        }
        int oldCapacity = boundUniformBufferIdsByBinding.length;
        int newCapacity = Math.max(oldCapacity * 2, required);
        boundUniformBufferIdsByBinding = Arrays.copyOf(boundUniformBufferIdsByBinding, newCapacity);
        boundUniformOffsetsByBinding = Arrays.copyOf(boundUniformOffsetsByBinding, newCapacity);
        boundUniformSizesByBinding = Arrays.copyOf(boundUniformSizesByBinding, newCapacity);
        Arrays.fill(boundUniformBufferIdsByBinding, oldCapacity, newCapacity, -1);
        Arrays.fill(boundUniformOffsetsByBinding, oldCapacity, newCapacity, -1);
        Arrays.fill(boundUniformSizesByBinding, oldCapacity, newCapacity, -1);
    }

    private void ensureGpuResourcesInitialized() {
        if (uniformRingGpuBufferId == 0) {
            initializeResources();
        }
    }

    private static int align(int value, int alignment) {
        int mask = alignment - 1;
        return (value + mask) & ~mask;
    }

    private void bindProgram(ShaderProgram program) {
        if (!(program instanceof OpenGLShaderProgram)) {
            throw new IllegalArgumentException("Program must be an OpenGL shader program");
        }
        OpenGLShaderProgram shader = (OpenGLShaderProgram) program;
        glUseProgram(shader.getProgramId());
    }

    private static void bindTextureUnit(int binding, Texture texture) {
        if (binding < 0) {
            throw new IllegalArgumentException("Texture binding must be >= 0");
        }
        glActiveTexture(GL_TEXTURE0 + binding);
        if (texture == null) {
            glBindTexture(GL_TEXTURE_2D, 0);
            return;
        }
        if (!(texture instanceof OpenGLTexture)) {
            throw new IllegalArgumentException("Texture must be an OpenGL texture");
        }
        ((OpenGLTexture) texture).bind();
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

    private void applyBlendState(Pipeline.BlendState state) {
        if (state.isEnabled()) {
            glEnable(GL_BLEND);
            glBlendFunc(translateBlendFactor(state.getSrcFactor()), translateBlendFactor(state.getDstFactor()));
        } else {
            glDisable(GL_BLEND);
        }
    }

    private void applyDepthState(Pipeline.DepthState state) {
        if (state.isDepthTest()) {
            glEnable(GL_DEPTH_TEST);
        } else {
            glDisable(GL_DEPTH_TEST);
        }
        glDepthFunc(translateCompareFunc(state.getCompareFunc()));
        glDepthMask(state.isDepthMask());
    }

    private void applyRasterizerState(Pipeline.RasterizerState state) {
        if (state.getCullMode() == CullMode.NONE) {
            glDisable(GL_CULL_FACE);
        } else {
            glEnable(GL_CULL_FACE);
            glCullFace(translateCullMode(state.getCullMode()));
        }
        glPolygonMode(GL_FRONT_AND_BACK, translateFillMode(state.getFillMode()));
    }

    private void setupVertexAttributes(VertexBuffer vertexBuffer, VertexBuffer.VertexFormat format) {
        setupVertexAttributes(resolveVertexBufferId(vertexBuffer), resolveVertexOffsetBytes(vertexBuffer), format);
    }

    private void setupVertexAttributes(int arrayBufferId, long baseOffset, VertexBuffer.VertexFormat format) {
        Objects.requireNonNull(format, "Current pipeline vertex format must be set before drawing");
        glBindBuffer(GL_ARRAY_BUFFER, arrayBufferId);

        disableVertexAttributes();

        int stride = format.getStrideInBytes();
        long offset = baseOffset;

        if (format.hasTexCoords()) {
            glEnableVertexAttribArray(2);
            glVertexAttribPointer(2, 2, mapDataType(format.getTexCoordDataType()), false, stride, offset);
            offset += 2L * format.getTexCoordDataType().getSize();
        }

        if (format.hasColors()) {
            glEnableVertexAttribArray(1);
            glVertexAttribPointer(1, 3, mapDataType(format.getColorDataType()), false, stride, offset);
            offset += 3L * format.getColorDataType().getSize();
        } else if (format.hasGrayScale()) {
            glEnableVertexAttribArray(1);
            glVertexAttribIPointer(1, 1, mapDataType(format.getGrayScaleDataType()), stride, offset);
            offset += format.getGrayScaleDataType().getSize();
        }

        if (format.hasNormals()) {
            glEnableVertexAttribArray(3);
            glVertexAttribPointer(3, 3, mapDataType(format.getNormalDataType()), false, stride, offset);
            offset += 3L * format.getNormalDataType().getSize();
        }

        if (format.hasPositions()) {
            glEnableVertexAttribArray(0);
            glVertexAttribPointer(0, 3, mapDataType(format.getPositionDataType()), false, stride, offset);
        }
    }

    private void bindIndexBuffer(IndexBuffer indexBuffer) {
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, resolveIndexBufferId(indexBuffer));
    }

    private static int resolveVertexBufferId(VertexBuffer vertexBuffer) {
        if (vertexBuffer instanceof OpenGLVertexBuffer) {
            return ((OpenGLVertexBuffer) vertexBuffer).getBufferId();
        }
        if (vertexBuffer instanceof OpenGLPooledVertexBuffer) {
            return ((OpenGLPooledVertexBuffer) vertexBuffer).getBufferId();
        }
        throw new IllegalArgumentException("VertexBuffer must be an OpenGL buffer");
    }

    private static long resolveVertexOffsetBytes(VertexBuffer vertexBuffer) {
        if (vertexBuffer instanceof OpenGLVertexBuffer) {
            return 0L;
        }
        if (vertexBuffer instanceof OpenGLPooledVertexBuffer) {
            return ((OpenGLPooledVertexBuffer) vertexBuffer).getOffset();
        }
        throw new IllegalArgumentException("VertexBuffer must be an OpenGL buffer");
    }

    private static int resolveIndexBufferId(IndexBuffer indexBuffer) {
        if (indexBuffer instanceof OpenGLIndexBuffer) {
            return ((OpenGLIndexBuffer) indexBuffer).getBufferId();
        }
        if (indexBuffer instanceof OpenGLPooledIndexBuffer) {
            return ((OpenGLPooledIndexBuffer) indexBuffer).getBufferId();
        }
        throw new IllegalArgumentException("IndexBuffer must be an OpenGL buffer");
    }

    private static long resolveIndexOffsetBytes(IndexBuffer indexBuffer) {
        if (indexBuffer instanceof OpenGLIndexBuffer) {
            return 0L;
        }
        if (indexBuffer instanceof OpenGLPooledIndexBuffer) {
            return ((OpenGLPooledIndexBuffer) indexBuffer).getOffset();
        }
        throw new IllegalArgumentException("IndexBuffer must be an OpenGL buffer");
    }

    private static int resolveVertexBaseVertex(VertexBuffer vertexBuffer, VertexBuffer.VertexFormat vertexFormat) {
        long offsetBytes = resolveVertexOffsetBytes(vertexBuffer);
        int stride = vertexFormat.getStrideInBytes();
        if (stride <= 0) {
            throw new IllegalStateException("Vertex stride must be > 0");
        }
        if (offsetBytes % stride != 0L) {
            throw new IllegalStateException(
                    "Vertex buffer offset " + offsetBytes + " is not aligned to stride " + stride
            );
        }
        return (int) (offsetBytes / stride);
    }

    private void disableVertexAttributes() {
        glDisableVertexAttribArray(0);
        glDisableVertexAttribArray(1);
        glDisableVertexAttribArray(2);
        glDisableVertexAttribArray(3);
    }

    private static int mapDataType(DataType type) {
        switch (type) {
            case UNSIGNED_BYTE:
                return GL_UNSIGNED_BYTE;
            case BYTE:
                return GL_BYTE;
            case UNSIGNED_SHORT:
                return GL_UNSIGNED_SHORT;
            case SHORT:
                return GL_SHORT;
            case FLOAT:
                return GL_FLOAT;
            case HALF_FLOAT:
                return GL_HALF_FLOAT;
            case INT:
                return GL_INT;
            default:
                throw new IllegalArgumentException("Unsupported data type: " + type);
        }
    }

    private static int translateBlendFactor(BlendFactor factor) {
        switch (factor) {
            case ZERO:
                return GL_ZERO;
            case ONE:
                return GL_ONE;
            case SRC_COLOR:
                return GL_SRC_COLOR;
            case ONE_MINUS_SRC_COLOR:
                return GL_ONE_MINUS_SRC_COLOR;
            case DST_COLOR:
                return GL_DST_COLOR;
            case ONE_MINUS_DST_COLOR:
                return GL_ONE_MINUS_DST_COLOR;
            case SRC_ALPHA:
                return GL_SRC_ALPHA;
            case ONE_MINUS_SRC_ALPHA:
                return GL_ONE_MINUS_SRC_ALPHA;
            case DST_ALPHA:
                return GL_DST_ALPHA;
            case ONE_MINUS_DST_ALPHA:
                return GL_ONE_MINUS_DST_ALPHA;
            case CONSTANT_COLOR:
                return GL_CONSTANT_COLOR;
            case ONE_MINUS_CONSTANT_COLOR:
                return GL_ONE_MINUS_CONSTANT_COLOR;
            case CONSTANT_ALPHA:
                return GL_CONSTANT_ALPHA;
            case ONE_MINUS_CONSTANT_ALPHA:
                return GL_ONE_MINUS_CONSTANT_ALPHA;
            case SRC_ALPHA_SATURATE:
                return GL_SRC_ALPHA_SATURATE;
            default:
                return GL_ONE;
        }
    }

    private static int translateCompareFunc(CompareFunc func) {
        switch (func) {
            case NEVER:
                return GL_NEVER;
            case LESS:
                return GL_LESS;
            case EQUAL:
                return GL_EQUAL;
            case LESS_EQUAL:
                return GL_LEQUAL;
            case GREATER:
                return GL_GREATER;
            case NOT_EQUAL:
                return GL_NOTEQUAL;
            case GREATER_EQUAL:
                return GL_GEQUAL;
            case ALWAYS:
                return GL_ALWAYS;
            default:
                return GL_LESS;
        }
    }

    private static int translateCullMode(CullMode mode) {
        switch (mode) {
            case FRONT:
                return GL_FRONT;
            case BACK:
                return GL_BACK;
            default:
                return GL_BACK;
        }
    }

    private static int translateFillMode(FillMode mode) {
        switch (mode) {
            case POINT:
                return GL_POINT;
            case WIREFRAME:
                return GL_LINE;
            case SOLID:
                return GL_FILL;
            default:
                return GL_FILL;
        }
    }

    private static int translatePrimitiveType(PrimitiveType type) {
        switch (type) {
            case POINTS:
                return GL_POINTS;
            case LINES:
                return GL_LINES;
            case LINE_STRIP:
                return GL_LINE_STRIP;
            case TRIANGLES:
                return GL_TRIANGLES;
            case TRIANGLE_STRIP:
                return GL_TRIANGLE_STRIP;
            case TRIANGLE_FAN:
                return GL_TRIANGLE_FAN;
            default:
                return GL_TRIANGLES;
        }
    }
}
