package com.mojang.minecraft.renderer.graphics.opengl;

import com.mojang.minecraft.renderer.graphics.IndexBuffer;
import com.mojang.minecraft.renderer.graphics.VertexArrayObject;
import com.mojang.minecraft.renderer.graphics.VertexBuffer;
import com.mojang.minecraft.renderer.graphics.VertexBuffer.VertexFormat;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * OpenGL implementation of the VertexArrayObject interface.
 * Represents a VAO (Vertex Array Object) in OpenGL.
 */
public class OpenGLVertexArrayObject implements VertexArrayObject {
    // OpenGL VAO ID
    private final int vaoId;
    
    // Bound buffers
    private VertexBuffer vertexBuffer;
    private IndexBuffer indexBuffer;
    
    // State tracking
    private boolean disposed = false;
    
    /**
     * Creates a new OpenGL vertex array object.
     */
    public OpenGLVertexArrayObject() {
        this.vaoId = glGenVertexArrays();
    }
    
    /**
     * Binds this vertex array object.
     */
    public void bind() {
        if (isDisposed()) {
            throw new IllegalStateException("Cannot use a disposed vertex array object");
        }
        
        glBindVertexArray(vaoId);
    }
    
    /**
     * Unbinds this vertex array object.
     */
    public void unbind() {
        glBindVertexArray(0);
    }
    
    @Override
    public void setVertexBuffer(VertexBuffer vertexBuffer) {
        if (isDisposed()) {
            throw new IllegalStateException("Cannot use a disposed vertex array object");
        }
        
        if (!(vertexBuffer instanceof OpenGLVertexBuffer)) {
            throw new IllegalArgumentException("VertexBuffer must be an OpenGLVertexBuffer");
        }
        
        // Store the vertex buffer
        this.vertexBuffer = vertexBuffer;
        VertexFormat format = vertexBuffer.getFormat();
        
        // Bind this VAO
        bind();
        
        // Bind the vertex buffer
        ((OpenGLVertexBuffer) vertexBuffer).bind();
        
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
        
        // Unbind this VAO
        unbind();
    }
    
    @Override
    public void setIndexBuffer(IndexBuffer indexBuffer) {
        if (isDisposed()) {
            throw new IllegalStateException("Cannot use a disposed vertex array object");
        }
        
        if (!(indexBuffer instanceof OpenGLIndexBuffer)) {
            throw new IllegalArgumentException("IndexBuffer must be an OpenGLIndexBuffer");
        }
        
        // Store the index buffer
        this.indexBuffer = indexBuffer;
        
        // Bind this VAO
        bind();
        
        // Bind the index buffer
        ((OpenGLIndexBuffer) indexBuffer).bind();
        
        // Unbind this VAO
        unbind();
    }
    
    @Override
    public VertexBuffer getVertexBuffer() {
        return vertexBuffer;
    }
    
    @Override
    public IndexBuffer getIndexBuffer() {
        return indexBuffer;
    }
    
    @Override
    public int getVertexCount() {
        return vertexBuffer != null ? vertexBuffer.getVertexCount() : 0;
    }
    
    @Override
    public int getIndexCount() {
        return indexBuffer != null ? indexBuffer.getIndexCount() : 0;
    }
    
    @Override
    public boolean isDisposed() {
        return disposed;
    }
    
    @Override
    public void dispose() {
        if (!disposed) {
            glDeleteVertexArrays(vaoId);
            disposed = true;
        }
    }
} 