package com.mojang.minecraft.renderer.graphics.opengl;

import com.mojang.minecraft.renderer.graphics.CommandBuffer;
import com.mojang.minecraft.renderer.graphics.DataType;
import com.mojang.minecraft.renderer.graphics.DescriptorSet;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.BlendFactor;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.CompareFunc;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.CullMode;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.FillMode;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.PrimitiveType;
import com.mojang.minecraft.renderer.graphics.IndexBuffer;
import com.mojang.minecraft.renderer.graphics.Pipeline;
import com.mojang.minecraft.renderer.graphics.PipelineLayout;
import com.mojang.minecraft.renderer.graphics.ShaderProgram;
import com.mojang.minecraft.renderer.graphics.Texture;
import com.mojang.minecraft.renderer.graphics.Uniform;
import com.mojang.minecraft.renderer.graphics.VertexBuffer;

import java.util.Objects;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL30.GL_HALF_FLOAT;
import static org.lwjgl.opengl.GL30.glBindBufferBase;
import static org.lwjgl.opengl.GL30.glVertexAttribIPointer;
import static org.lwjgl.opengl.GL31.GL_UNIFORM_BUFFER;
import static org.lwjgl.opengl.GL20.*;

/**
 * OpenGL command buffer implementation.
 * OpenGL executes commands immediately, but this object owns per-frame command state.
 */
final class OpenGLCommandBuffer implements CommandBuffer {

    private Pipeline currentPipeline;

    void reset() {
        glUseProgram(0);
        currentPipeline = null;
    }

    @Override
    public void setPipeline(Pipeline pipeline) {
        if (pipeline == null) {
            glUseProgram(0);
            currentPipeline = null;
            return;
        }
        if (!(pipeline instanceof OpenGLPipeline)) {
            throw new IllegalArgumentException("Not an OpenGL pipeline");
        }
        if (pipeline.isDisposed()) {
            throw new IllegalStateException("Cannot bind a disposed pipeline");
        }

        bindProgram(pipeline.getProgram());
        applyBlendState(pipeline.getBlendState());
        applyDepthState(pipeline.getDepthState());
        applyRasterizerState(pipeline.getRasterizerState());
        currentPipeline = pipeline;
    }

    @Override
    public void setViewport(int x, int y, int width, int height) {
        glViewport(x, y, width, height);
    }

    @Override
    public void clear(boolean clearColor, boolean clearDepth, float r, float g, float b, float a) {
        int bits = 0;

        if (clearColor) {
            bits |= GL_COLOR_BUFFER_BIT;
            glClearColor(r, g, b, a);
        }

        if (clearDepth) {
            bits |= GL_DEPTH_BUFFER_BIT;
        }

        glClear(bits);
    }

    @Override
    public void draw(PrimitiveType type, VertexBuffer vertexBuffer, IndexBuffer indexBuffer, int start, int count) {
        Objects.requireNonNull(vertexBuffer, "Vertex buffer cannot be null");
        Objects.requireNonNull(currentPipeline, "No pipeline set");

        setupVertexAttributes(vertexBuffer, currentPipeline.getVertexFormat());

        if (indexBuffer != null) {
            long indexOffset = start * 4L; // 4 bytes per int
            bindIndexBuffer(indexBuffer);
            if (indexBuffer instanceof OpenGLPooledIndexBuffer) {
                indexOffset += ((OpenGLPooledIndexBuffer) indexBuffer).getOffset();
            }
            glDrawElements(translatePrimitiveType(type), count, GL_UNSIGNED_INT, indexOffset);
        } else {
            glDrawArrays(translatePrimitiveType(type), start, count);
        }

        glBindBuffer(GL_ARRAY_BUFFER, 0);
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

        for (PipelineLayout.Binding declaredBinding : descriptorSet.getLayout().getBindings()) {
            int binding = declaredBinding.getBinding();
            if (isTextureResourceType(declaredBinding.getResourceType())) {
                Texture texture = descriptorSet.getTexture(binding);
                bindTextureUnit(binding, texture);
                continue;
            }
            if (isUniformResourceType(declaredBinding.getResourceType())) {
                Uniform uniform = descriptorSet.getUniform(binding);
                if (uniform == null) {
                    throw new IllegalStateException(
                            "Descriptor set '" + descriptorSet.getLayout().getDebugName()
                                    + "' is missing required uniform binding " + binding
                    );
                }
                bindUniformBuffer(binding, uniform);
            }
        }
    }

    private void bindProgram(ShaderProgram program) {
        if (!(program instanceof OpenGLShaderProgram)) {
            throw new IllegalArgumentException("Program must be an OpenGL shader program");
        }
        OpenGLShaderProgram shader = (OpenGLShaderProgram) program;
        glUseProgram(shader.getProgramId());
    }

    private static void bindUniformBuffer(int binding, Uniform uniform) {
        if (!(uniform instanceof OpenGLUniform)) {
            throw new IllegalArgumentException("Uniform must be an OpenGL uniform");
        }
        OpenGLUniform glUniform = (OpenGLUniform) uniform;
        if (glUniform.getBinding() != binding) {
            throw new IllegalStateException(
                    "Descriptor binding " + binding + " does not match uniform binding " + glUniform.getBinding()
            );
        }
        glUniform.uploadIfDirty();
        glBindBufferBase(GL_UNIFORM_BUFFER, binding, glUniform.getBufferId());
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
        if (!(vertexBuffer instanceof OpenGLVertexBuffer) && !(vertexBuffer instanceof OpenGLPooledVertexBuffer)) {
            throw new IllegalArgumentException("VertexBuffer must be an OpenGL buffer");
        }
        Objects.requireNonNull(format, "Current pipeline vertex format must be set before drawing");

        long bufferOffset = 0L;
        if (vertexBuffer instanceof OpenGLVertexBuffer) {
            ((OpenGLVertexBuffer) vertexBuffer).bind();
        } else {
            OpenGLPooledVertexBuffer pooledVertexBuffer = (OpenGLPooledVertexBuffer) vertexBuffer;
            pooledVertexBuffer.bind();
            bufferOffset = pooledVertexBuffer.getOffset();
        }

        disableVertexAttributes();

        int stride = format.getStrideInBytes();
        long offset = bufferOffset;

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
        if (indexBuffer instanceof OpenGLIndexBuffer) {
            ((OpenGLIndexBuffer) indexBuffer).bind();
            return;
        }
        if (indexBuffer instanceof OpenGLPooledIndexBuffer) {
            ((OpenGLPooledIndexBuffer) indexBuffer).bind();
            return;
        }
        throw new IllegalArgumentException("IndexBuffer must be an OpenGL buffer");
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
