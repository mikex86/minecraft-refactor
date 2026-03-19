package com.mojang.minecraft.renderer.swapchain.opengl;

import com.mojang.minecraft.renderer.swapchain.Swapchain;

import static org.lwjgl.glfw.GLFW.glfwSwapBuffers;

/**
 * OpenGL-backed swapchain implementation.
 * OpenGL effectively has a single presentable backbuffer, so image index is always 0.
 */
public class OpenGLSwapchain implements Swapchain {
    private final long window;

    private int width;
    private int height;
    private boolean outOfDate;
    private boolean disposed;

    public OpenGLSwapchain(long window, int width, int height) {
        this.window = window;
        this.width = width;
        this.height = height;
    }

    @Override
    public AcquireResult acquireNextImage() {
        ensureActive();

        if (outOfDate) {
            return new AcquireResult(AcquireStatus.OUT_OF_DATE, -1);
        }

        return new AcquireResult(AcquireStatus.SUCCESS, 0);
    }

    @Override
    public PresentStatus present() {
        ensureActive();

        if (outOfDate) {
            return PresentStatus.OUT_OF_DATE;
        }

        glfwSwapBuffers(window);
        return PresentStatus.SUCCESS;
    }

    @Override
    public void recreate(int width, int height) {
        ensureActive();
        this.width = width;
        this.height = height;
        this.outOfDate = false;
    }

    @Override
    public void markOutOfDate() {
        ensureActive();
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
        disposed = true;
    }

    private void ensureActive() {
        if (disposed) {
            throw new IllegalStateException("Swapchain has been disposed");
        }
    }
}
