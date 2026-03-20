package com.mojang.minecraft.renderer.graphics.vulkan;

import com.mojang.minecraft.renderer.graphics.IndexBuffer;
import com.mojang.minecraft.renderer.graphics.ResourceState;
import com.mojang.minecraft.renderer.graphics.Texture;
import com.mojang.minecraft.renderer.graphics.VertexBuffer;

final class VulkanResourceTransitions {
    private VulkanResourceTransitions() {
    }

    static void transitionTexture(Texture texture,
                                  ResourceState.TextureAccess expectedOldAccess,
                                  ResourceState.TextureAccess newAccess) {
        if (texture == null) {
            throw new IllegalArgumentException("texture cannot be null");
        }
        if (!(texture instanceof VulkanTexture)) {
            throw new IllegalArgumentException("Texture must be a VulkanTexture");
        }
        VulkanTexture vkTexture = (VulkanTexture) texture;
        vkTexture.transition(expectedOldAccess, newAccess);
    }

    static void transitionVertexBuffer(VertexBuffer vertexBuffer,
                                       ResourceState.BufferAccess expectedOldAccess,
                                       ResourceState.BufferAccess newAccess) {
        if (vertexBuffer == null) {
            throw new IllegalArgumentException("vertexBuffer cannot be null");
        }
        if (!(vertexBuffer instanceof VulkanBufferStateTracked)) {
            throw new IllegalArgumentException("VertexBuffer must be Vulkan state-tracked");
        }
        VulkanBufferStateTracked tracked = (VulkanBufferStateTracked) vertexBuffer;
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
        if (!(indexBuffer instanceof VulkanBufferStateTracked)) {
            throw new IllegalArgumentException("IndexBuffer must be Vulkan state-tracked");
        }
        VulkanBufferStateTracked tracked = (VulkanBufferStateTracked) indexBuffer;
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
        if (!(texture instanceof VulkanTextureStateTracked)) {
            throw new IllegalArgumentException("Texture must be Vulkan state-tracked");
        }
        VulkanTextureStateTracked tracked = (VulkanTextureStateTracked) texture;
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
        if (!(vertexBuffer instanceof VulkanBufferStateTracked)) {
            throw new IllegalArgumentException("VertexBuffer must be Vulkan state-tracked");
        }
        VulkanBufferStateTracked tracked = (VulkanBufferStateTracked) vertexBuffer;
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
        if (!(indexBuffer instanceof VulkanBufferStateTracked)) {
            throw new IllegalArgumentException("IndexBuffer must be Vulkan state-tracked");
        }
        VulkanBufferStateTracked tracked = (VulkanBufferStateTracked) indexBuffer;
        ResourceState.BufferAccess current = tracked.getBufferAccess();
        if (current != requiredAccess) {
            throw new IllegalStateException(
                    usageLabel + " requires index buffer state " + requiredAccess + " but was " + current
            );
        }
    }
}
