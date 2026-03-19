package com.mojang.minecraft.renderer.graphics;

/**
 * Attachment load/store behavior for a render pass.
 * This models Vulkan-style intent while remaining backend-neutral.
 */
public final class RenderPassAttachments {

    public enum LoadOp {
        LOAD,
        CLEAR,
        DONT_CARE
    }

    public enum StoreOp {
        STORE,
        DONT_CARE
    }

    public static final class ColorAttachment {
        private final LoadOp loadOp;
        private final StoreOp storeOp;
        private final float clearR;
        private final float clearG;
        private final float clearB;
        private final float clearA;

        public ColorAttachment(LoadOp loadOp, StoreOp storeOp, float clearR, float clearG, float clearB, float clearA) {
            if (loadOp == null) {
                throw new IllegalArgumentException("loadOp cannot be null");
            }
            if (storeOp == null) {
                throw new IllegalArgumentException("storeOp cannot be null");
            }
            this.loadOp = loadOp;
            this.storeOp = storeOp;
            this.clearR = clearR;
            this.clearG = clearG;
            this.clearB = clearB;
            this.clearA = clearA;
        }

        public LoadOp getLoadOp() {
            return loadOp;
        }

        public StoreOp getStoreOp() {
            return storeOp;
        }

        public float getClearR() {
            return clearR;
        }

        public float getClearG() {
            return clearG;
        }

        public float getClearB() {
            return clearB;
        }

        public float getClearA() {
            return clearA;
        }
    }

    public static final class DepthAttachment {
        private final LoadOp loadOp;
        private final StoreOp storeOp;
        private final float clearDepth;

        public DepthAttachment(LoadOp loadOp, StoreOp storeOp, float clearDepth) {
            if (loadOp == null) {
                throw new IllegalArgumentException("loadOp cannot be null");
            }
            if (storeOp == null) {
                throw new IllegalArgumentException("storeOp cannot be null");
            }
            this.loadOp = loadOp;
            this.storeOp = storeOp;
            this.clearDepth = clearDepth;
        }

        public LoadOp getLoadOp() {
            return loadOp;
        }

        public StoreOp getStoreOp() {
            return storeOp;
        }

        public float getClearDepth() {
            return clearDepth;
        }
    }

    private final ColorAttachment colorAttachment;
    private final DepthAttachment depthAttachment;

    public RenderPassAttachments(ColorAttachment colorAttachment, DepthAttachment depthAttachment) {
        this.colorAttachment = colorAttachment;
        this.depthAttachment = depthAttachment;
    }

    public ColorAttachment getColorAttachment() {
        return colorAttachment;
    }

    public DepthAttachment getDepthAttachment() {
        return depthAttachment;
    }
}
