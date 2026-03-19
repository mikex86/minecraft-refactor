package com.mojang.minecraft.renderer.swapchain.opengl;

import com.mojang.minecraft.renderer.swapchain.Swapchain;

import static org.lwjgl.glfw.GLFW.glfwSwapBuffers;
import static org.lwjgl.opengl.GL11.glFinish;
import static org.lwjgl.opengl.GL32.GL_ALREADY_SIGNALED;
import static org.lwjgl.opengl.GL32.GL_CONDITION_SATISFIED;
import static org.lwjgl.opengl.GL32.GL_SYNC_FLUSH_COMMANDS_BIT;
import static org.lwjgl.opengl.GL32.GL_SYNC_GPU_COMMANDS_COMPLETE;
import static org.lwjgl.opengl.GL32.GL_TIMEOUT_IGNORED;
import static org.lwjgl.opengl.GL32.GL_WAIT_FAILED;
import static org.lwjgl.opengl.GL32.glClientWaitSync;
import static org.lwjgl.opengl.GL32.glDeleteSync;
import static org.lwjgl.opengl.GL32.glFenceSync;

/**
 * OpenGL-backed swapchain implementation.
 * OpenGL effectively has a single presentable backbuffer, so image index is always 0.
 */
public class OpenGLSwapchain implements Swapchain {
    private static final int DEFAULT_FRAMES_IN_FLIGHT = 2;

    private final long window;
    private final int framesInFlight;
    private final long[] inFlightFences;

    private int width;
    private int height;
    private boolean outOfDate;
    private boolean initialized;
    private boolean disposed;
    private int currentFrameInFlightIndex = -1;
    private boolean frameActive;

    public OpenGLSwapchain(long window, int width, int height) {
        this(window, width, height, DEFAULT_FRAMES_IN_FLIGHT);
    }

    public OpenGLSwapchain(long window, int width, int height, int framesInFlight) {
        if (framesInFlight <= 0) {
            throw new IllegalArgumentException("framesInFlight must be > 0");
        }
        this.window = window;
        this.width = width;
        this.height = height;
        this.framesInFlight = framesInFlight;
        this.inFlightFences = new long[framesInFlight];
    }

    @Override
    public void initialize() {
        ensureActive();
        if (initialized) {
            throw new IllegalStateException("Swapchain already initialized");
        }
        this.outOfDate = false;
        this.initialized = true;
    }

    @Override
    public AcquireResult beginFrame() {
        ensureInitialized();

        if (outOfDate) {
            frameActive = false;
            currentFrameInFlightIndex = -1;
            return new AcquireResult(AcquireStatus.OUT_OF_DATE, -1, -1);
        }

        int nextFrameInFlightIndex = (currentFrameInFlightIndex + 1) % framesInFlight;
        waitOnFrameFence(nextFrameInFlightIndex);
        currentFrameInFlightIndex = nextFrameInFlightIndex;
        frameActive = true;
        return new AcquireResult(AcquireStatus.SUCCESS, 0, currentFrameInFlightIndex);
    }

    @Override
    public PresentStatus endFrame() {
        ensureInitialized();

        if (!frameActive || currentFrameInFlightIndex < 0) {
            throw new IllegalStateException("endFrame called without an active frame");
        }

        if (outOfDate) {
            frameActive = false;
            return PresentStatus.OUT_OF_DATE;
        }

        insertFrameFence(currentFrameInFlightIndex);
        glfwSwapBuffers(window);
        frameActive = false;
        return PresentStatus.SUCCESS;
    }

    @Override
    public void waitForIdle() {
        ensureInitialized();
        glFinish();
        deleteAllFences();
    }

    @Override
    public void recreate(int width, int height) {
        ensureInitialized();
        waitForIdle();
        this.width = width;
        this.height = height;
        this.outOfDate = false;
        this.frameActive = false;
        this.currentFrameInFlightIndex = -1;
    }

    @Override
    public void markOutOfDate() {
        ensureInitialized();
        this.outOfDate = true;
    }

    @Override
    public boolean isOutOfDate() {
        return outOfDate;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public void dispose() {
        if (!disposed && initialized) {
            waitForIdle();
        }
        disposed = true;
    }

    private void ensureActive() {
        if (disposed) {
            throw new IllegalStateException("Swapchain has been disposed");
        }
    }

    private void ensureInitialized() {
        ensureActive();
        if (!initialized) {
            throw new IllegalStateException("Swapchain has not been initialized");
        }
    }

    private void waitOnFrameFence(int frameInFlightIndex) {
        long fence = inFlightFences[frameInFlightIndex];
        if (fence == 0L) {
            return;
        }

        while (true) {
            int waitResult = glClientWaitSync(fence, GL_SYNC_FLUSH_COMMANDS_BIT, GL_TIMEOUT_IGNORED);
            if (waitResult == GL_ALREADY_SIGNALED || waitResult == GL_CONDITION_SATISFIED) {
                break;
            }
            if (waitResult == GL_WAIT_FAILED) {
                throw new IllegalStateException("glClientWaitSync failed for frame index " + frameInFlightIndex);
            }
        }

        glDeleteSync(fence);
        inFlightFences[frameInFlightIndex] = 0L;
    }

    private void insertFrameFence(int frameInFlightIndex) {
        long oldFence = inFlightFences[frameInFlightIndex];
        if (oldFence != 0L) {
            glDeleteSync(oldFence);
            inFlightFences[frameInFlightIndex] = 0L;
        }

        long newFence = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        if (newFence == 0L) {
            throw new IllegalStateException("glFenceSync returned null for frame index " + frameInFlightIndex);
        }
        inFlightFences[frameInFlightIndex] = newFence;
    }

    private void deleteAllFences() {
        for (int i = 0; i < inFlightFences.length; i++) {
            long fence = inFlightFences[i];
            if (fence != 0L) {
                glDeleteSync(fence);
                inFlightFences[i] = 0L;
            }
        }
    }
}
