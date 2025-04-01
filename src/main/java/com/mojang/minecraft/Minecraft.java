package com.mojang.minecraft;

import com.mojang.minecraft.crash.CrashReporter;
import com.mojang.minecraft.engine.GameEngine;
import com.mojang.minecraft.gui.Font;
import com.mojang.minecraft.input.GameInputHandler;
import com.mojang.minecraft.renderer.GameRenderer;
import com.mojang.minecraft.renderer.Textures;
import com.mojang.minecraft.world.HitResult;

import java.io.IOException;

/**
 * Main game class for Minecraft Classic 0.0.11a.
 * Coordinates the different components of the game.
 */
public class Minecraft implements Runnable {
    // Constants
    public static final String VERSION_STRING = "0.0.11a";

    // Core systems
    private final GameEngine engine;
    private GameInputHandler gameInputHandler;
    private GameRenderer renderer;

    // Game state
    private GameState gameState;

    // Game resources
    private final Textures textures;
    private Font font;

    // Game flags
    private volatile boolean running = false;
    public volatile boolean pause = false;

    /**
     * Creates a new Minecraft game instance.
     *
     * @param width      Width of the rendering area
     * @param height     Height of the rendering area
     * @param fullscreen Whether to run in fullscreen mode
     */
    public Minecraft(int width, int height, boolean fullscreen) {
        this.engine = new GameEngine(width, height, fullscreen, "Minecraft " + VERSION_STRING);
        this.textures = new Textures();
    }

    /**
     * Initializes the game, setting up the display, OpenGL, and game objects.
     *
     * @throws IOException If resource loading fails
     */
    public void init() throws IOException {
        try {
            // Initialize the engine
            engine.initialize();

            // Create game resources
            this.font = new Font("/default.gif", this.textures);

            // Create game state (manages level, entities, player)
            this.gameState = new GameState(this.textures);
            this.gameState.initialize();

            // Create renderer
            this.renderer = new GameRenderer(
                    this.gameState.getLevel(),
                    gameState.getLevelRenderer(),
                    gameState.getParticleEngine(),
                    gameState.getPlayer(),
                    gameState.getEntities(),
                    this.textures,
                    this.font,
                    engine.getWidth(),
                    engine.getHeight()
            );

            // Initialize game input handler
            this.gameInputHandler = new GameInputHandler(
                    engine.getInputHandler(),
                    gameState.getPlayer(),
                    gameState.getLevel(),
                    gameState.getParticleEngine(),
                    gameState.getEntities(),
                    engine.isFullscreen()
            );
        } catch (Exception e) {
            CrashReporter.handleCrash("Failed to initialize game", e);
            throw new IOException("Failed to initialize game", e);
        }
    }

    /**
     * Cleans up resources and saves the level before shutting down.
     */
    public void destroy() {
        try {
            if (gameState != null) {
                gameState.save();
            }
            engine.shutdown();
            textures.dispose();
        } catch (Exception e) {
            CrashReporter.handleError("Failed to clean up resources during shutdown", e);
        }
    }

    /**
     * Main game loop. Initializes the game and handles game state updates.
     */
    public void run() {
        this.running = true;

        try {
            // Initialize the game
            this.init();
        } catch (Exception e) {
            CrashReporter.handleCrash("Failed to start Minecraft", e);
            return;
        }

        try {
            // Main game loop
            while (this.running) {
                if (this.pause) {
                    // When paused, sleep to avoid using CPU resources
                    Thread.sleep(100L);
                } else {
                    // Check if window is closed
                    if (!engine.update()) {
                        this.stop();
                    }

                    // Get time information from engine
                    int ticksToProcess = engine.getTicksToProcess();
                    float partialTick = engine.getPartialTick();

                    // Process input
                    HitResult hitResult = this.renderer.pick(partialTick);
                    gameInputHandler.processInput(hitResult);

                    // Process game ticks
                    for (int i = 0; i < ticksToProcess; ++i) {
                        gameState.tick();
                    }

                    // Handle mouse look
                    gameInputHandler.processMouseLook();

                    // Render the frame
                    this.renderer.render(
                            partialTick,
                            hitResult,
                            gameInputHandler.getEditMode(),
                            gameInputHandler.getPaintTexture(),
                            engine.getFpsString()
                    );

                    // Check for window size changes
                    if (engine.hasResized()) {
                        int newWidth = engine.getWidth();
                        int newHeight = engine.getHeight();
                        renderer.setDimensions(newWidth, newHeight);
                    }

                    // Handle window focus change
                    if (!engine.hasFocus()) {
                        gameInputHandler.handleFocusChange(false);
                    }
                }
            }
        } catch (Exception e) {
            CrashReporter.handleCrash("Uncaught exception in main game loop", e);
        } finally {
            // Clean up resources
            this.destroy();
        }
    }

    /**
     * Stops the game loop.
     */
    public void stop() {
        this.running = false;
    }

    /**
     * Main entry point for standalone game.
     */
    public static void main(String[] args) {
        try {
            Minecraft minecraft = new Minecraft(854, 480, false);
            // Run directly on the main thread instead of creating a new thread
            minecraft.run();
        } catch (Exception e) {
            CrashReporter.handleCrash("Failed to start Minecraft", e);
        }
    }
}
