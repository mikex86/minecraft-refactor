package com.mojang.minecraft.renderer.graphics;

import com.mojang.minecraft.renderer.graphics.GraphicsEnums.*;
import java.nio.ByteBuffer;

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
     * Creates a pooled vertex buffer using the default vertex buffer pool.
     *
     * @param sizeInBytes The size of the buffer in bytes
     * @return A new pooled vertex buffer, or null if the allocation failed
     */
    VertexBuffer createPooledVertexBuffer(int sizeInBytes);

    /**
     * Creates an index buffer.
     *
     * @param usage The intended usage pattern of the buffer
     * @return A new index buffer
     */
    IndexBuffer createIndexBuffer(BufferUsage usage);

    /**
     * Creates a pooled index buffer using the default index buffer pool.
     *
     * @param sizeInBytes The size of the buffer in bytes
     * @return A new pooled index buffer, or null if the allocation failed
     */
    IndexBuffer createPooledIndexBuffer(int sizeInBytes);

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
     * @param width  The texture width
     * @param height The texture height
     * @param format The texture format
     * @param data   The raw image data
     * @return A new texture
     */
    Texture createTexture(int width, int height, TextureFormat format, ByteBuffer data);

    /**
     * Creates a pipeline layout.
     *
     * @param descriptor Layout descriptor
     * @return A new pipeline layout
     */
    PipelineLayout createPipelineLayout(PipelineLayout.Descriptor descriptor);

    /**
     * Creates a graphics pipeline.
     *
     * @param descriptor Pipeline descriptor
     * @return A new pipeline
     */
    Pipeline createPipeline(Pipeline.Descriptor descriptor);

    /**
     * Binds a graphics pipeline.
     *
     * @param pipeline Pipeline to bind, or null to unbind
     */
    void setPipeline(Pipeline pipeline);

    /**
     * Sets the viewport.
     *
     * @param x      The viewport left
     * @param y      The viewport top
     * @param width  The viewport width
     * @param height The viewport height
     */
    void setViewport(int x, int y, int width, int height);

    /**
     * Clears the color and/or depth buffer.
     *
     * @param clearColor Whether to clear the color buffer
     * @param clearDepth Whether to clear the depth buffer
     * @param r          The red component
     * @param g          The green component
     * @param b          The blue component
     * @param a          The alpha component
     */
    void clear(boolean clearColor, boolean clearDepth, float r, float g, float b, float a);

    /**
     * Sets up a perspective projection matrix.
     *
     * @param fov       The field of view
     * @param aspect    The aspect ratio
     * @param nearPlane The near plane
     * @param farPlane  The far plane
     */
    void setPerspectiveProjection(float fov, float aspect, float nearPlane, float farPlane);

    /**
     * Sets up an orthographic projection matrix.
     *
     * @param left   The left plane
     * @param right  The right plane
     * @param bottom The bottom plane
     * @param top    The top plane
     * @param near   The near plane
     * @param far    The far plane
     */
    void setOrthographicProjection(float left, float right, float bottom, float top, float near, float far);

    /**
     * Pushes the current matrix onto the stack.
     */
    void pushMatrix();

    /**
     * Pops the current matrix from the stack.
     */
    void popMatrix();

    /**
     * Loads the identity matrix.
     */
    void loadIdentity();

    /**
     * Translates the current matrix.
     *
     * @param x The x translation
     * @param y The y translation
     * @param z The z translation
     */
    void translate(float x, float y, float z);

    /**
     * Rotates the current matrix around the x axis.
     *
     * @param angle The angle in degrees
     */
    void rotateX(float angle);

    /**
     * Rotates the current matrix around the y axis.
     *
     * @param angle The angle in degrees
     */
    void rotateY(float angle);

    /**
     * Rotates the current matrix around the z axis.
     *
     * @param angle The angle in degrees
     */
    void rotateZ(float angle);

    /**
     * Scales the current matrix.
     *
     * @param x The x scale
     * @param y The y scale
     * @param z The z scale
     */
    void scale(float x, float y, float z);

    /**
     * Sets the matrix mode.
     *
     * @param mode The matrix mode
     */
    void setMatrixMode(MatrixMode mode);

    /**
     * Draws primitives using explicit vertex/index buffers.
     *
     * @param type        The primitive type
     * @param vertexBuffer The vertex buffer to source vertex attributes from
     * @param indexBuffer  Optional index buffer, or null for non-indexed drawing
     * @param start       Vertex or index start offset
     * @param count       Number of vertices or indices to draw
     */
    void draw(PrimitiveType type, VertexBuffer vertexBuffer, IndexBuffer indexBuffer, int start, int count);

    /**
     * Sets the active texture.
     *
     * @param texture The texture to bind
     */
    void setTexture(Texture texture);

    /**
     * Gets the matrix stack.
     *
     * @return The matrix stack
     */
    MatrixStack getMatrixStack();

    /**
     * Called to update modelview and projection matrices in the shader.
     */
    void updateShaderMatrices();

    /**
     * Matrix modes for the graphics API.
     */
    enum MatrixMode {
        MODELVIEW,
        PROJECTION
    }
}
