package com.mojang.minecraft.renderer.graphics.opengl;

import com.mojang.minecraft.renderer.graphics.CommandBuffer;
import com.mojang.minecraft.renderer.graphics.DataType;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.BlendFactor;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.CompareFunc;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.CullMode;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.FillMode;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.PrimitiveType;
import com.mojang.minecraft.renderer.graphics.IndexBuffer;
import com.mojang.minecraft.renderer.graphics.Pipeline;
import com.mojang.minecraft.renderer.graphics.Texture;
import com.mojang.minecraft.renderer.graphics.Uniform;
import com.mojang.minecraft.renderer.graphics.VertexBuffer;
import com.mojang.minecraft.renderer.shader.IShader;

import java.util.Objects;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.GL_HALF_FLOAT;
import static org.lwjgl.opengl.GL30.glVertexAttribIPointer;

/**
 * OpenGL command buffer implementation.
 * OpenGL executes commands immediately, but this object owns per-frame command state.
 */
final class OpenGLCommandBuffer implements CommandBuffer {

    private IShader currentShader;
    private Pipeline currentPipeline;

    void reset() {
        bindShader(null);
        bindTexture(0, null);
        currentPipeline = null;
    }

    @Override
    public void setPipeline(Pipeline pipeline) {
        if (pipeline == null) {
            bindShader(null);
            currentPipeline = null;
            return;
        }
        if (!(pipeline instanceof OpenGLPipeline)) {
            throw new IllegalArgumentException("Not an OpenGL pipeline");
        }
        if (pipeline.isDisposed()) {
            throw new IllegalStateException("Cannot bind a disposed pipeline");
        }

        bindShader(pipeline.getShader());
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

        setupVertexAttributes(vertexBuffer);

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
    public void bindTexture(int binding, Texture texture) {
        if (binding < 0) {
            throw new IllegalArgumentException("Texture binding must be >= 0");
        }
        glActiveTexture(GL_TEXTURE0 + binding);
        if (texture == null) {
            glBindTexture(GL_TEXTURE_2D, 0);
        } else if (texture instanceof OpenGLTexture) {
            ((OpenGLTexture) texture).bind();
        } else {
            throw new IllegalArgumentException("Not an OpenGL texture");
        }
    }

    @Override
    public void bindUniform(Uniform uniform) {
        Objects.requireNonNull(uniform, "uniform cannot be null");
        Objects.requireNonNull(currentShader, "No shader set");
        Objects.requireNonNull(currentPipeline, "No pipeline set");

        if (!(uniform instanceof OpenGLUniform)) {
            throw new IllegalArgumentException("Uniform must be an OpenGL uniform");
        }

        OpenGLUniform glUniform = (OpenGLUniform) uniform;
        int binding = glUniform.getBinding();
        if (!currentPipeline.getLayout().hasBinding(binding)) {
            throw new IllegalStateException("Uniform binding " + binding + " is not declared by pipeline layout " + currentPipeline.getDebugName());
        }

        switch (glUniform.getType()) {
            case INT1:
                glUniform1i(binding, glUniform.intValue());
                break;
            case FLOAT1: {
                float[] values = glUniform.floatValues();
                glUniform1f(binding, values[0]);
                break;
            }
            case FLOAT2: {
                float[] values = glUniform.floatValues();
                glUniform2f(binding, values[0], values[1]);
                break;
            }
            case FLOAT3: {
                float[] values = glUniform.floatValues();
                glUniform3f(binding, values[0], values[1], values[2]);
                break;
            }
            case FLOAT4: {
                float[] values = glUniform.floatValues();
                glUniform4f(binding, values[0], values[1], values[2], values[3]);
                break;
            }
            case MAT3:
                glUniformMatrix3fv(binding, false, glUniform.floatValues());
                break;
            case MAT4:
                glUniformMatrix4fv(binding, false, glUniform.floatValues());
                break;
            default:
                throw new IllegalArgumentException("Unsupported uniform type: " + glUniform.getType());
        }
    }

    private void bindShader(IShader shader) {
        if (shader != null) {
            shader.use();
            currentShader = shader;
        } else {
            if (currentShader != null) {
                currentShader.detach();
                currentShader = null;
            }
        }
        currentPipeline = null;
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

    private void setupVertexAttributes(VertexBuffer vertexBuffer) {
        if (!(vertexBuffer instanceof OpenGLVertexBuffer) && !(vertexBuffer instanceof OpenGLPooledVertexBuffer)) {
            throw new IllegalArgumentException("VertexBuffer must be an OpenGL buffer");
        }

        VertexBuffer.VertexFormat format = vertexBuffer.getFormat();
        Objects.requireNonNull(format, "Vertex buffer format must be set before drawing");

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
