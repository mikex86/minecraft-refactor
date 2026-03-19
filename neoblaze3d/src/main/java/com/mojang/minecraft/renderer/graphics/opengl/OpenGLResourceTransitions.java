package com.mojang.minecraft.renderer.graphics.opengl;

import com.mojang.minecraft.renderer.graphics.IndexBuffer;
import com.mojang.minecraft.renderer.graphics.ResourceState;
import com.mojang.minecraft.renderer.graphics.Texture;
import com.mojang.minecraft.renderer.graphics.VertexBuffer;

final class OpenGLResourceTransitions {
    private OpenGLResourceTransitions() {
    }

    static void transitionTexture(Texture texture,
                                  ResourceState.TextureAccess expectedOldAccess,
                                  ResourceState.TextureAccess newAccess) {
        if (texture == null) {
            throw new IllegalArgumentException("texture cannot be null");
        }
        if (!(texture instanceof OpenGLTextureStateTracked)) {
            throw new IllegalArgumentException("Texture must be OpenGL state-tracked");
        }
        if (texture.isDisposed()) {
            throw new IllegalStateException("Cannot transition disposed texture");
        }

        OpenGLTextureStateTracked tracked = (OpenGLTextureStateTracked) texture;
        ResourceState.TextureAccess current = tracked.getTextureAccess();
        if (current != expectedOldAccess) {
            throw new IllegalStateException(
                    "Texture transition mismatch: expected " + expectedOldAccess + " but was " + current
            );
        }
        tracked.setTextureAccess(newAccess);
    }

    static void transitionVertexBuffer(VertexBuffer vertexBuffer,
                                       ResourceState.BufferAccess expectedOldAccess,
                                       ResourceState.BufferAccess newAccess) {
        if (vertexBuffer == null) {
            throw new IllegalArgumentException("vertexBuffer cannot be null");
        }
        if (!(vertexBuffer instanceof OpenGLBufferStateTracked)) {
            throw new IllegalArgumentException("VertexBuffer must be OpenGL state-tracked");
        }
        if (vertexBuffer.isDisposed()) {
            throw new IllegalStateException("Cannot transition disposed vertex buffer");
        }

        OpenGLBufferStateTracked tracked = (OpenGLBufferStateTracked) vertexBuffer;
        ResourceState.BufferAccess current = tracked.getBufferAccess();
        if (current != expectedOldAccess) {
            throw new IllegalStateException(
                    "VertexBuffer transition mismatch: expected " + expectedOldAccess + " but was " + current
            );
        }
        tracked.setBufferAccess(newAccess);
    }

    static void transitionIndexBuffer(IndexBuffer indexBuffer,
                                      ResourceState.BufferAccess expectedOldAccess,
                                      ResourceState.BufferAccess newAccess) {
        if (indexBuffer == null) {
            throw new IllegalArgumentException("indexBuffer cannot be null");
        }
        if (!(indexBuffer instanceof OpenGLBufferStateTracked)) {
            throw new IllegalArgumentException("IndexBuffer must be OpenGL state-tracked");
        }
        if (indexBuffer.isDisposed()) {
            throw new IllegalStateException("Cannot transition disposed index buffer");
        }

        OpenGLBufferStateTracked tracked = (OpenGLBufferStateTracked) indexBuffer;
        ResourceState.BufferAccess current = tracked.getBufferAccess();
        if (current != expectedOldAccess) {
            throw new IllegalStateException(
                    "IndexBuffer transition mismatch: expected " + expectedOldAccess + " but was " + current
            );
        }
        tracked.setBufferAccess(newAccess);
    }

    static void requireTextureState(Texture texture, ResourceState.TextureAccess requiredAccess, String usageLabel) {
        if (texture == null) {
            return;
        }
        if (!(texture instanceof OpenGLTextureStateTracked)) {
            throw new IllegalArgumentException("Texture must be OpenGL state-tracked");
        }
        OpenGLTextureStateTracked tracked = (OpenGLTextureStateTracked) texture;
        ResourceState.TextureAccess current = tracked.getTextureAccess();
        if (current != requiredAccess) {
            throw new IllegalStateException(
                    usageLabel + " requires texture state " + requiredAccess + " but was " + current
            );
        }
    }

    static void requireVertexBufferState(VertexBuffer vertexBuffer,
                                         ResourceState.BufferAccess requiredAccess,
                                         String usageLabel) {
        if (!(vertexBuffer instanceof OpenGLBufferStateTracked)) {
            throw new IllegalArgumentException("VertexBuffer must be OpenGL state-tracked");
        }
        OpenGLBufferStateTracked tracked = (OpenGLBufferStateTracked) vertexBuffer;
        ResourceState.BufferAccess current = tracked.getBufferAccess();
        if (current != requiredAccess) {
            throw new IllegalStateException(
                    usageLabel + " requires vertex buffer state " + requiredAccess + " but was " + current
            );
        }
    }

    static void requireIndexBufferState(IndexBuffer indexBuffer,
                                        ResourceState.BufferAccess requiredAccess,
                                        String usageLabel) {
        if (!(indexBuffer instanceof OpenGLBufferStateTracked)) {
            throw new IllegalArgumentException("IndexBuffer must be OpenGL state-tracked");
        }
        OpenGLBufferStateTracked tracked = (OpenGLBufferStateTracked) indexBuffer;
        ResourceState.BufferAccess current = tracked.getBufferAccess();
        if (current != requiredAccess) {
            throw new IllegalStateException(
                    usageLabel + " requires index buffer state " + requiredAccess + " but was " + current
            );
        }
    }
}
