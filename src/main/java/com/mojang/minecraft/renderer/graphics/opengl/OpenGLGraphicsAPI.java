package com.mojang.minecraft.renderer.graphics.opengl;

import com.mojang.minecraft.renderer.graphics.GraphicsAPI;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.*;
import com.mojang.minecraft.renderer.graphics.MatrixStack;
import com.mojang.minecraft.renderer.graphics.Texture;
import com.mojang.minecraft.renderer.graphics.VertexBuffer;
import com.mojang.minecraft.renderer.shader.Shader;

import java.nio.ByteBuffer;
import java.util.Objects;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;

/**
 * OpenGL implementation of the GraphicsAPI interface.
 * This implementation uses LWJGL to interact with OpenGL.
 */
public class OpenGLGraphicsAPI implements GraphicsAPI {

    // Matrix stack for emulating OpenGL's matrix functionality
    private final MatrixStack matrixStack;

    // Current shader
    private Shader currentShader = null;

    /**
     * Creates a new OpenGL graphics API implementation.
     */
    public OpenGLGraphicsAPI() {
        this.matrixStack = new MatrixStack();
    }

    @Override
    public void initialize() {
        // Enable texture mapping
        glEnable(GL_TEXTURE_2D);

        // Set up blending
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // Set up depth testing
        glClearDepth(1.0f);
        glDepthFunc(GL_LEQUAL);

        // Enable alpha testing
        glEnable(GL_ALPHA_TEST);
        glAlphaFunc(GL_GREATER, 0.0f);

        // Set up smooth shading
        glShadeModel(GL_SMOOTH);

        setMatrixMode(MatrixMode.PROJECTION);
        loadIdentity();
        setMatrixMode(MatrixMode.MODELVIEW);
        loadIdentity();
    }

    @Override
    public void shutdown() {
        // Nothing to do for OpenGL shutdown
    }

    @Override
    public VertexBuffer createVertexBuffer(BufferUsage usage) {
        return new OpenGLVertexBuffer(translateBufferUsage(usage));
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
    public void drawPrimitives(VertexBuffer buffer, PrimitiveType type, int start, int count) {
        Objects.requireNonNull(currentShader, "Current shader must be set before drawing primitives");
        updateShaderMatrices(currentShader);
        if (buffer instanceof OpenGLVertexBuffer) {
            OpenGLVertexBuffer glBuffer = (OpenGLVertexBuffer) buffer;
            VertexBuffer.VertexFormat format = buffer.getFormat();

            // Bind VBO
            glBuffer.bind();

            // Set up vertex attribute pointers
            int stride = format.getStrideInBytes();
            int offset = 0;

            // Enable vertex arrays
            glEnableClientState(GL_VERTEX_ARRAY);

            // Set up texture coordinates if present
            if (format.hasTexCoords()) {
                glEnableClientState(GL_TEXTURE_COORD_ARRAY);
                glTexCoordPointer(2, GL_FLOAT, stride, offset);
                offset += 2 * 4; // 2 floats * 4 bytes
            }

            // Set up colors if present
            if (format.hasColors()) {
                glEnableClientState(GL_COLOR_ARRAY);
                glColorPointer(3, GL_FLOAT, stride, offset);
                offset += 3 * 4; // 3 floats * 4 bytes
            }

            // Set up normals if present
            if (format.hasNormals()) {
                glEnableClientState(GL_NORMAL_ARRAY);
                glNormalPointer(GL_FLOAT, stride, offset);
                offset += 3 * 4; // 3 floats * 4 bytes
            }

            // Set up positions (must be present)
            if (format.hasPositions()) {
                glVertexPointer(3, GL_FLOAT, stride, offset);
            }

            // Draw the primitives
            glDrawArrays(translatePrimitiveType(type), start, count);

            // Disable vertex arrays
            glDisableClientState(GL_VERTEX_ARRAY);
            if (format.hasTexCoords()) {
                glDisableClientState(GL_TEXTURE_COORD_ARRAY);
            }
            if (format.hasColors()) {
                glDisableClientState(GL_COLOR_ARRAY);
            }
            if (format.hasNormals()) {
                glDisableClientState(GL_NORMAL_ARRAY);
            }

            // Unbind VBO
            glBuffer.unbind();
        } else {
            throw new IllegalArgumentException("Buffer must be an OpenGLVertexBuffer");
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
     * 
     * @param shader The shader to update
     */
    private void updateShaderMatrices(Shader shader) {
        // Set modelview matrix uniform if the shader supports it
        try {
            shader.setUniformMatrix4fv("modelViewMatrix", matrixStack.getModelViewBuffer());
        } catch (IllegalArgumentException e) {
            // Ignore if uniform doesn't exist
        }
        
        // Set projection matrix uniform if the shader supports it
        try {
            shader.setUniformMatrix4fv("projectionMatrix", matrixStack.getProjectionBuffer());
        } catch (IllegalArgumentException e) {
            // Ignore if uniform doesn't exist
        }
        
        // Set modelview-projection matrix uniform if the shader supports it
        try {
            shader.setUniformMatrix4fv("modelViewProjectionMatrix", matrixStack.getModelViewProjectionBuffer());
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