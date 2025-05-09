package com.mojang.minecraft.renderer.graphics.opengl;

import com.mojang.minecraft.renderer.graphics.GraphicsAPI;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.*;
import com.mojang.minecraft.renderer.graphics.MatrixStack;
import com.mojang.minecraft.renderer.graphics.Texture;
import com.mojang.minecraft.renderer.graphics.VertexBuffer;
import com.mojang.minecraft.renderer.graphics.IndexBuffer;
import com.mojang.minecraft.renderer.shader.Shader;
import com.mojang.minecraft.renderer.graphics.VertexArrayObject;

import java.nio.ByteBuffer;
import java.util.Objects;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * OpenGL implementation of the GraphicsAPI interface.
 * This implementation uses LWJGL to interact with OpenGL.
 */
public class OpenGLGraphicsAPI implements GraphicsAPI {

    // Matrix stack for emulating OpenGL's matrix functionality
    private final MatrixStack matrixStack;

    // Current shader
    private Shader currentShader = null;
    
    // Default VAO (required for OpenGL core profile)
    private int defaultVaoId;

    /**
     * Creates a new OpenGL graphics API implementation.
     */
    public OpenGLGraphicsAPI() {
        this.matrixStack = new MatrixStack();
    }

    @Override
    public void initialize() {
        // Create and bind a default VAO
        // This is required when using an OpenGL core profile
        defaultVaoId = glGenVertexArrays();
        glBindVertexArray(defaultVaoId);
        
        // Set up blending
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // Set up depth testing
        glClearDepth(1.0f);
        glDepthFunc(GL_LEQUAL);

        setMatrixMode(MatrixMode.PROJECTION);
        loadIdentity();
        setMatrixMode(MatrixMode.MODELVIEW);
        loadIdentity();
    }

    @Override
    public void shutdown() {
        // Clean up the default VAO
        glDeleteVertexArrays(defaultVaoId);
    }

    @Override
    public VertexBuffer createVertexBuffer(BufferUsage usage) {
        return new OpenGLVertexBuffer(translateBufferUsage(usage));
    }

    @Override
    public IndexBuffer createIndexBuffer(BufferUsage usage) {
        return new OpenGLIndexBuffer(translateBufferUsage(usage));
    }

    @Override
    public VertexArrayObject createVertexArrayObject() {
        return new OpenGLVertexArrayObject();
    }

    @Override
    public Texture createTexture(int width, int height, TextureFormat format) {
        return new OpenGLTexture(width, height, format);
    }

    @Override
    public Texture createTexture(int width, int height, TextureFormat format, ByteBuffer data) {
        OpenGLTexture texture = new OpenGLTexture(width, height, format);
        texture.update(0, 0, width, height, data);
        return texture;
    }

    @Override
    public void setBlendState(boolean enabled, BlendFactor srcFactor, BlendFactor dstFactor) {
        if (enabled) {
            glEnable(GL_BLEND);
            glBlendFunc(translateBlendFactor(srcFactor), translateBlendFactor(dstFactor));
        } else {
            glDisable(GL_BLEND);
        }
    }

    @Override
    public void setDepthState(boolean depthTest, boolean depthMask, CompareFunc depthFunc) {
        if (depthTest) {
            glEnable(GL_DEPTH_TEST);
        } else {
            glDisable(GL_DEPTH_TEST);
        }
        glDepthFunc(translateCompareFunc(depthFunc));
        glDepthMask(depthMask);
    }

    @Override
    public void setRasterizerState(CullMode cullMode, FillMode fillMode) {
        // Set face culling
        if (cullMode == CullMode.NONE) {
            glDisable(GL_CULL_FACE);
        } else {
            glEnable(GL_CULL_FACE);
            glCullFace(translateCullMode(cullMode));
        }

        // Set fill mode
        glPolygonMode(GL_FRONT_AND_BACK, translateFillMode(fillMode));
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
    public void setPerspectiveProjection(float fov, float aspect, float nearPlane, float farPlane) {
        matrixStack.setMatrixMode(MatrixMode.PROJECTION);
        matrixStack.loadIdentity();
        matrixStack.setPerspective(fov, aspect, nearPlane, farPlane);
    }

    @Override
    public void setOrthographicProjection(float left, float right, float bottom, float top, float near, float far) {
        matrixStack.setMatrixMode(MatrixMode.PROJECTION);
        matrixStack.loadIdentity();
        matrixStack.setOrthographic(left, right, bottom, top, near, far);
    }

    @Override
    public void pushMatrix() {
        matrixStack.pushMatrix();
    }

    @Override
    public void popMatrix() {
        matrixStack.popMatrix();
    }

    @Override
    public void loadIdentity() {
        matrixStack.loadIdentity();
    }

    @Override
    public void translate(float x, float y, float z) {
        matrixStack.translate(x, y, z);
    }

    @Override
    public void rotateX(float angle) {
        matrixStack.rotateX(angle);
    }

    @Override
    public void rotateY(float angle) {
        matrixStack.rotateY(angle);
    }

    @Override
    public void rotateZ(float angle) {
        matrixStack.rotateZ(angle);
    }

    @Override
    public void scale(float x, float y, float z) {
        matrixStack.scale(x, y, z);
    }

    @Override
    public void setMatrixMode(MatrixMode mode) {
        matrixStack.setMatrixMode(mode);
    }

    @Override
    public void drawIndexedPrimitives(VertexBuffer vertexBuffer, IndexBuffer indexBuffer, PrimitiveType type, int start, int count) {
        if (vertexBuffer instanceof OpenGLVertexBuffer && indexBuffer instanceof OpenGLIndexBuffer) {
            OpenGLVertexBuffer glVertexBuffer = (OpenGLVertexBuffer) vertexBuffer;
            OpenGLIndexBuffer glIndexBuffer = (OpenGLIndexBuffer) indexBuffer;
            VertexBuffer.VertexFormat format = vertexBuffer.getFormat();

            // In core profile, we need to use a VAO for all vertex attribute state
            // Create a temporary VAO
            int tempVao = glGenVertexArrays();
            glBindVertexArray(tempVao);
            
            // Bind VBO and IBO
            glVertexBuffer.bind();
            glIndexBuffer.bind();

            // Set up vertex attribute pointers
            int stride = format.getStrideInBytes();
            int offset = 0;

            // Note: The attribute locations must match the 'in' declarations in our shaders
            // In our updated shaders, they're:
            // position = 0, color = 1, texCoord0 = 2, normal = 3
            
            // Texture coordinates (attribute location 2)
            if (format.hasTexCoords()) {
                glEnableVertexAttribArray(2);
                glVertexAttribPointer(2, 2, GL_FLOAT, false, stride, offset);
                offset += 2 * 4; // 2 floats * 4 bytes
            }
            
            // Colors (attribute location 1)
            if (format.hasColors()) {
                glEnableVertexAttribArray(1);
                glVertexAttribPointer(1, 3, GL_FLOAT, false, stride, offset);
                offset += 3 * 4; // 3 floats * 4 bytes
            }
            
            // Normals (attribute location 3)
            if (format.hasNormals()) {
                glEnableVertexAttribArray(3);
                glVertexAttribPointer(3, 3, GL_FLOAT, false, stride, offset);
                offset += 3 * 4; // 3 floats * 4 bytes
            }
            
            // Positions (attribute location 0)
            if (format.hasPositions()) {
                glEnableVertexAttribArray(0);
                glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, offset);
                // No need to update offset as this is the last attribute
            }

            // Draw the indexed primitives
            glDrawElements(translatePrimitiveType(type), count, GL_UNSIGNED_INT, start * 4L); // 4 bytes per int

            // Disable vertex attribute arrays
            if (format.hasPositions()) {
                glDisableVertexAttribArray(0);
            }
            if (format.hasColors()) {
                glDisableVertexAttribArray(1);
            }
            if (format.hasTexCoords()) {
                glDisableVertexAttribArray(2);
            }
            if (format.hasNormals()) {
                glDisableVertexAttribArray(3);
            }

            // Unbind VAO, IBO, and VBO
            glBindVertexArray(0);
            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
            
            // Delete the temporary VAO
            glDeleteVertexArrays(tempVao);
        } else {
            throw new IllegalArgumentException("Buffers must be OpenGL buffers");
        }
    }

    @Override
    public void drawPrimitives(VertexArrayObject vao, PrimitiveType type, int start, int count) {
        if (vao instanceof OpenGLVertexArrayObject) {
            OpenGLVertexArrayObject glVao = (OpenGLVertexArrayObject) vao;
            
            // Bind the VAO
            glVao.bind();
            
            // Draw the indexed primitives
            glDrawElements(translatePrimitiveType(type), count, GL_UNSIGNED_INT, start * 4); // 4 bytes per int
            
            // Unbind the VAO
            glVao.unbind();
        } else {
            throw new IllegalArgumentException("VAO must be an OpenGL VAO");
        }
    }

    @Override
    public void setTexture(Texture texture) {
        if (texture == null) {
            glBindTexture(GL_TEXTURE_2D, 0);
        } else if (texture instanceof OpenGLTexture) {
            ((OpenGLTexture) texture).bind();
        } else {
            throw new IllegalArgumentException("Not an OpenGL texture");
        }
    }

    @Override
    public void setShader(Shader shader) {
        if (shader != null) {
            shader.use();
            currentShader = shader;
        } else {
            if (currentShader != null) {
                currentShader.detach();
                currentShader = null;
            }
        }
    }

    /**
     * Updates the shader uniforms with the current matrices.
     * This is used to provide the matrices to the shader program.
     */
    @Override
    public void updateShaderMatrices() {
        Objects.requireNonNull(currentShader, "No shader set");

        // Set modelview matrix uniform if the shader supports it
        try {
            currentShader.setUniformMatrix4fv("modelViewMatrix", matrixStack.getModelViewBuffer());
        } catch (IllegalArgumentException e) {
            // Ignore if uniform doesn't exist
        }

        // Set projection matrix uniform if the shader supports it
        try {
            currentShader.setUniformMatrix4fv("projectionMatrix", matrixStack.getProjectionBuffer());
        } catch (IllegalArgumentException e) {
            // Ignore if uniform doesn't exist
        }
    }

    @Override
    public MatrixStack getMatrixStack() {
        return matrixStack;
    }

    //--------------------------------------------------
    // Helper methods to translate enums to OpenGL constants
    //--------------------------------------------------

    private int translateBufferUsage(BufferUsage usage) {
        switch (usage) {
            case STATIC:
                return GL_STATIC_DRAW;
            case DYNAMIC:
                return GL_DYNAMIC_DRAW;
            case STREAM:
                return GL_STREAM_DRAW;
            default:
                return GL_STATIC_DRAW;
        }
    }

    private int translateBlendFactor(BlendFactor factor) {
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

    private int translateCompareFunc(CompareFunc func) {
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

    private int translateCullMode(CullMode mode) {
        switch (mode) {
            case FRONT:
                return GL_FRONT;
            case BACK:
                return GL_BACK;
            default:
                return GL_BACK;
        }
    }

    private int translateFillMode(FillMode mode) {
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

    private int translatePrimitiveType(PrimitiveType type) {
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
            case QUADS:
                return GL_QUADS;
            default:
                return GL_TRIANGLES;
        }
    }

}