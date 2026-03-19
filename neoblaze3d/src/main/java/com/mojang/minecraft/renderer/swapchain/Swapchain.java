package com.mojang.minecraft.renderer.swapchain;

import com.mojang.minecraft.renderer.Disposable;

/**
 * Backend-neutral swapchain abstraction.
 * A backend implementation may expose one or more presentable images.
 */
public interface Swapchain extends Disposable {
    /**
     * Initializes backend swapchain state.
     * Must be called exactly once before beginFrame/endFrame usage.
     */
    void initialize();

    /**
     * Begins a frame by synchronizing with prior work and acquiring a presentable image.
     *
     * @return acquisition result
     */
    AcquireResult beginFrame();

    /**
     * Ends the current frame by presenting the acquired image.
     *
     * @return present result
     */
    PresentStatus endFrame();

    /**
     * Waits for all in-flight swapchain work to finish.
     */
    void waitForIdle();

    /**
     * Recreates swapchain resources for the provided framebuffer dimensions.
     *
     * @param width  framebuffer width
     * @param height framebuffer height
     */
    void recreate(int width, int height);

    /**
     * Marks this swapchain as out-of-date.
     * The next acquire/present should report OUT_OF_DATE until recreated.
     */
    void markOutOfDate();

    /**
     * @return true if this swapchain currently requires recreation
     */
    boolean isOutOfDate();

    /**
     * @return framebuffer width tracked by this swapchain
     */
    int getWidth();

    /**
     * @return framebuffer height tracked by this swapchain
     */
    int getHeight();

    /**
     * Result for an image acquisition call.
     */
    final class AcquireResult {
        private final AcquireStatus status;
        private final int imageIndex;
        private final int frameInFlightIndex;

        public AcquireResult(AcquireStatus status, int imageIndex, int frameInFlightIndex) {
            this.status = status;
            this.imageIndex = imageIndex;
            this.frameInFlightIndex = frameInFlightIndex;
        }

        public AcquireStatus getStatus() {
            return status;
        }

        public int getImageIndex() {
            return imageIndex;
        }

        public int getFrameInFlightIndex() {
            return frameInFlightIndex;
        }
    }

    /**
     * Acquire status values aligned with Vulkan-style flow.
     */
    enum AcquireStatus {
        SUCCESS,
        SUBOPTIMAL,
        OUT_OF_DATE
    }

    /**
     * Present status values aligned with Vulkan-style flow.
     */
    enum PresentStatus {
        SUCCESS,
        SUBOPTIMAL,
        OUT_OF_DATE
    }
}
