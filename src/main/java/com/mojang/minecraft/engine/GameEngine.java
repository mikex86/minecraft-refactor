package com.mojang.minecraft.engine;

import com.mojang.minecraft.Timer;
import com.mojang.minecraft.crash.CrashReporter;
import com.mojang.minecraft.level.Chunk;
import com.mojang.minecraft.renderer.GameWindow;
import com.mojang.minecraft.renderer.InputHandler;

import java.io.IOException;

/**
 * Core engine class that handles window management, timing,
 * input handling, and other engine-level operations.
 * This class separates engine concerns from game-specific logic.
 */
public class GameEngine {
    // Engine configuration
    private final int initialWidth;
    private final int initialHeight;
    private final boolean fullscreen;
    private final String windowTitle;

    // Engine state
    private int width;
    private int height;
    private boolean resized = false;

    // Window and input
    private GameWindow window;
    private InputHandler inputHandler;

    // Timing
    private final Timer timer = new Timer(20.0F); // 20 ticks per second
    private String fpsString = "";
    private long lastFpsUpdateTime = 0;
    private int framesCounter = 0;

    /**
     * Creates a new GameEngine with the specified configuration.
     *
     * @param width       Initial window width
     * @param height      Initial window height
     * @param fullscreen  Whether to run in fullscreen mode
     * @param windowTitle Window title
     */
    public GameEngine(int width, int height, boolean fullscreen, String windowTitle) {
        this.initialWidth = width;
        this.initialHeight = height;
        this.fullscreen = fullscreen;
        this.windowTitle = windowTitle;
        this.width = width;
        this.height = height;
    }

    /**
     * Initializes the engine, creating the window and input handler.
     *
     * @throws IOException If initialization fails
     */
    public void initialize() throws IOException {
        try {
            // Create window
            window = new GameWindow(initialWidth, initialHeight, windowTitle, fullscreen);
            inputHandler = new InputHandler(window);

            // Get the actual window size (may have changed for fullscreen)
            width = window.getWidth();
            height = window.getHeight();

            System.out.println("Initialized window with dimensions: " + width + "x" + height);

            // Initialize OpenGL
            window.initOpenGL();

            // Initialize timing
            lastFpsUpdateTime = System.currentTimeMillis();
            framesCounter = 0;

        } catch (Exception e) {
            CrashReporter.handleCrash("Failed to create window", e);
            throw new IOException("Failed to create window", e);
        }
    }

    /**
     * Updates the engine state for one frame.
     * This handles window events, input updates, and timing.
     *
     * @return true if the engine should continue running, false if it should stop
     */
    public boolean update() {
        // Update window (swaps buffers and processes events)
        boolean shouldContinue = window.update();

        // Update input state
        inputHandler.update();

        // Update timer
        timer.advanceTime();

        // Update FPS counter
        ++framesCounter;
        long currentTime = System.currentTimeMillis();

        // Update the FPS string once per second
        if (currentTime >= lastFpsUpdateTime + 1000L) {
            this.fpsString = framesCounter + " fps, " + Chunk.updates + " chunk updates";
            Chunk.updates = 0;
            lastFpsUpdateTime += 1000L;
            framesCounter = 0;
        }

        // Check if window dimensions have changed
        int newWidth = window.getWidth();
        int newHeight = window.getHeight();

        if (newWidth != width || newHeight != height) {
            width = newWidth;
            height = newHeight;
            resized = true;
            System.out.println("Window resized: " + width + "x" + height);
        } else {
            resized = false;
        }

        return shouldContinue;
    }

    /**
     * Shuts down the engine, releasing all resources.
     */
    public void shutdown() {
        if (window != null) {
            window.destroy();
            window = null;
        }
    }

    /**
     * Gets the number of ticks that should be processed this frame.
     *
     * @return Number of ticks to process
     */
    public int getTicksToProcess() {
        return timer.ticks;
    }

    /**
     * Gets the partial tick value for smooth rendering between ticks.
     *
     * @return Partial tick value (0.0-1.0)
     */
    public float getPartialTick() {
        return timer.partialTick;
    }

    /**
     * Gets the current window width.
     *
     * @return Window width in pixels
     */
    public int getWidth() {
        return width;
    }

    /**
     * Gets the current window height.
     *
     * @return Window height in pixels
     */
    public int getHeight() {
        return height;
    }

    /**
     * Checks if the window was resized this frame.
     *
     * @return true if the window was resized
     */
    public boolean hasResized() {
        return resized;
    }

    /**
     * Gets the input handler.
     *
     * @return The input handler
     */
    public InputHandler getInputHandler() {
        return inputHandler;
    }

    /**
     * Checks if the window has focus.
     *
     * @return true if the window has focus
     */
    public boolean hasFocus() {
        return window != null && window.hasFocus();
    }

    /**
     * Gets the FPS string for display.
     *
     * @return Current FPS string
     */
    public String getFpsString() {
        return fpsString;
    }

    /**
     * Checks if the game is running in fullscreen mode.
     *
     * @return true if fullscreen
     */
    public boolean isFullscreen() {
        return fullscreen;
    }
} 