package com.mojang.minecraft.renderer.graphics;

/**
 * Explicit resource usage states used for backend-neutral transition validation.
 */
public final class ResourceState {
    private ResourceState() {
    }

    public enum TextureAccess {
        UNDEFINED,
        TRANSFER_DST,
        SHADER_READ,
        COLOR_ATTACHMENT,
        DEPTH_ATTACHMENT,
        PRESENT
    }

    public enum BufferAccess {
        UNDEFINED,
        TRANSFER_DST,
        VERTEX_READ,
        INDEX_READ,
        UNIFORM_READ
    }
}
