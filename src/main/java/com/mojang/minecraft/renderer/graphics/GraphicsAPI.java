package com.mojang.minecraft.renderer.graphics;

import com.mojang.minecraft.renderer.graphics.GraphicsEnums.*;
import com.mojang.minecraft.renderer.shader.Shader;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

/**
 * Main interface for the graphics API abstraction layer.
 * This is the entry point for all graphics operations.
 * It provides methods to create and manage graphics resources.
 */
public interface GraphicsAPI {
    /**
     * Initialize the graphics API.
     * This should be called once at the start of the application.
     */
    void initialize();
    
    /**
     * Shutdown the graphics API.
     * This should be called once at the end of the application.
     */
    void shutdown();
    
    /**
     * Creates a vertex buffer.
     * 
     * @param usage The intended usage pattern of the buffer
     * @return A new vertex buffer
     */
    VertexBuffer createVertexBuffer(BufferUsage usage);
    
    /**
     * Creates a texture.
     * 
     * @param width  The texture width
     * @param height The texture height
     * @param format The texture format
     * @return A new texture
     */
    Texture createTexture(int width, int height, TextureFormat format);
    
    /**
     * Creates a texture from raw image data.
     * 
     * @param width      The texture width
     * @param height     The texture height
     * @param format     The texture format
     * @param data       The raw image data
     * @return A new texture
     */
    Texture createTexture(int width, int height, TextureFormat format, ByteBuffer data);
    
    /**
     * Sets the blend state.
     * 
     * @param enabled   Whether blending is enabled
     * @param srcFactor The source blend factor
     * @param dstFactor The destination blend factor
     */
    void setBlendState(boolean enabled, BlendFactor srcFactor, BlendFactor dstFactor);
    
    /**
     * Sets the depth state.
     * 
     * @param depthTest Whether depth testing is enabled
     * @param depthMask Whether depth writing is enabled
     * @param depthFunc The depth comparison function
     */
    void setDepthState(boolean depthTest, boolean depthMask, CompareFunc depthFunc);
    
    /**
     * Sets the rasterizer state.
     * 
     * @param cullMode  The cull mode
     * @param fillMode  The fill mode
     */
    void setRasterizerState(CullMode cullMode, FillMode fillMode);
    
    /**
     * Sets the viewport dimensions.
     * 
     * @param x      The viewport x position
     * @param y      The viewport y position
     * @param width  The viewport width
     * @param height The viewport height
     */
    void setViewport(int x, int y, int width, int height);
    
    /**
     * Clears the current render target.
     * 
     * @param clearColor  Whether to clear the color buffer
     * @param clearDepth  Whether to clear the depth buffer
     * @param r           Red component of clear color (0.0-1.0)
     * @param g           Green component of clear color (0.0-1.0)
     * @param b           Blue component of clear color (0.0-1.0)
     * @param a           Alpha component of clear color (0.0-1.0)
     */
    void clear(boolean clearColor, boolean clearDepth, float r, float g, float b, float a);
    
    /**
     * Sets up a perspective projection matrix.
     * 
     * @param fov       Field of view angle in degrees
     * @param aspect    Aspect ratio (width / height)
     * @param nearPlane Distance to near clipping plane
     * @param farPlane  Distance to far clipping plane
     */
    void setPerspectiveProjection(float fov, float aspect, float nearPlane, float farPlane);
    
    /**
     * Sets up an orthographic projection matrix.
     * 
     * @param left    Left coordinate
     * @param right   Right coordinate
     * @param bottom  Bottom coordinate
     * @param top     Top coordinate
     * @param near    Near clipping plane
     * @param far     Far clipping plane
     */
    void setOrthographicProjection(float left, float right, float bottom, float top, float near, float far);
    
    /**
     * Pushes the current matrix onto the stack.
     */
    void pushMatrix();
    
    /**
     * Pops the top matrix from the stack and makes it the current matrix.
     */
    void popMatrix();
    
    /**
     * Loads an identity matrix onto the current matrix stack.
     */
    void loadIdentity();
    
    /**
     * Translates the current matrix.
     * 
     * @param x X translation
     * @param y Y translation
     * @param z Z translation
     */
    void translate(float x, float y, float z);
    
    /**
     * Rotates the current matrix around the X axis.
     * 
     * @param angle Angle in degrees
     */
    void rotateX(float angle);
    
    /**
     * Rotates the current matrix around the Y axis.
     * 
     * @param angle Angle in degrees
     */
    void rotateY(float angle);
    
    /**
     * Rotates the current matrix around the Z axis.
     * 
     * @param angle Angle in degrees
     */
    void rotateZ(float angle);
    
    /**
     * Scales the current matrix.
     * 
     * @param x X scale factor
     * @param y Y scale factor
     * @param z Z scale factor
     */
    void scale(float x, float y, float z);
    
    /**
     * Sets the current matrix mode.
     * 
     * @param mode The matrix mode
     */
    void setMatrixMode(MatrixMode mode);
    
    /**
     * Renders primitives from the given vertex buffer.
     * 
     * @param buffer The vertex buffer
     * @param type   The primitive type
     * @param start  The starting vertex
     * @param count  The number of vertices
     */
    void drawPrimitives(VertexBuffer buffer, PrimitiveType type, int start, int count);
    
    /**
     * Sets the current texture.
     * 
     * @param texture The texture to bind
     */
    void setTexture(Texture texture);

    /**
     * Sets the current shader.
     * 
     * @param shader The shader to use, or null to use the fixed-function pipeline
     */
    void setShader(Shader shader);
    
    /**
     * Gets the matrix stack used by this graphics API.
     * This provides direct access to the matrix operations and state.
     * 
     * @return The matrix stack
     */
    MatrixStack getMatrixStack();
    
    /**
     * Matrix modes for the graphics API.
     */
    enum MatrixMode {
        MODELVIEW,
        PROJECTION
    }
    
    /**
     * Fog modes for the graphics API.
     */
    enum FogMode {
        LINEAR,
        EXP,
        EXP2
    }
} 