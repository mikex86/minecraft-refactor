package com.mojang.minecraft;

import com.mojang.minecraft.character.Zombie;
import com.mojang.minecraft.gui.Font;
import com.mojang.minecraft.input.GameInputHandler;
import com.mojang.minecraft.level.Chunk;
import com.mojang.minecraft.level.Level;
import com.mojang.minecraft.level.LevelRenderer;
import com.mojang.minecraft.particle.ParticleEngine;
import com.mojang.minecraft.renderer.GameRenderer;
import com.mojang.minecraft.renderer.GameWindow;
import com.mojang.minecraft.renderer.InputHandler;
import com.mojang.minecraft.renderer.Textures;

import java.io.IOException;
import java.util.ArrayList;
import javax.swing.JOptionPane;

/**
 * Main game class for Minecraft Classic 0.0.11a.
 * Handles initialization, game loop, and input.
 */
public class Minecraft implements Runnable {
    // Constants
    public static final String VERSION_STRING = "0.0.11a";

    // Game configuration
    private boolean fullscreen = false;
    private int width;
    private int height;

    // Game state
    private final Timer timer = new Timer(20.0F);
    public Level level;
    private LevelRenderer levelRenderer;
    private Player player;
    private ParticleEngine particleEngine;
    private final ArrayList<Entity> entities = new ArrayList<>();

    // Window and input handling
    private GameWindow window;
    private InputHandler inputHandler;
    private GameInputHandler gameInputHandler;

    // Game flags
    public volatile boolean pause = false;
    private volatile boolean running = false;

    // UI state
    private String fpsString = "";
    private Font font;

    // Rendering resources
    public Textures textures;
    private GameRenderer renderer;
    private HitResult hitResult = null;

    /**
     * Creates a new Minecraft game instance.
     *
     * @param width      Width of the rendering area
     * @param height     Height of the rendering area
     * @param fullscreen Whether to run in fullscreen mode
     */
    public Minecraft(int width, int height, boolean fullscreen) {
        this.width = width;
        this.height = height;
        this.fullscreen = fullscreen;
        this.textures = new Textures();
    }

    /**
     * Initializes the game, setting up the display, OpenGL, and game objects.
     *
     * @throws IOException If resource loading fails
     */
    public void init() throws IOException {
        try {
            // Create window and initialize input
            window = new GameWindow(width, height, "Minecraft " + VERSION_STRING, fullscreen);
            inputHandler = new InputHandler(window);

            // Get the updated window size (may have changed for fullscreen)
            width = window.getWidth();
            height = window.getHeight();

            System.out.println("Initialized window with dimensions: " + width + "x" + height);

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, e.toString(), "Failed to create window", JOptionPane.ERROR_MESSAGE);
            throw new IOException("Failed to create window", e);
        }

        // Initialize OpenGL state
        window.initOpenGL();

        // Create game objects
        this.level = new Level(256, 256, 64);
        this.levelRenderer = new LevelRenderer(this.level, this.textures);
        this.player = new Player(this.level);
        this.particleEngine = new ParticleEngine(this.level, this.textures);
        this.font = new Font("/default.gif", this.textures);
        this.renderer = new GameRenderer(
                this,
                this.levelRenderer,
                this.particleEngine,
                this.player,
                this.entities,
                this.textures,
                this.font,
                this.width,
                this.height
        );

        // Add some zombies to the level
        for (int i = 0; i < 10; ++i) {
            Zombie zombie = new Zombie(this.level, this.textures, 128.0F, 0.0F, 128.0F);
            zombie.resetPos();
            this.entities.add(zombie);
        }

        // Initialize game input handler
        this.gameInputHandler = new GameInputHandler(
                this.inputHandler,
                this.player,
                this.level,
                this.particleEngine,
                this.entities,
                this.fullscreen
        );
    }

    /**
     * Cleans up resources and saves the level before shutting down.
     */
    public void destroy() {
        try {
            this.level.save();
            if (window != null) {
                window.destroy();
            }
            if (textures != null) {
                textures.dispose();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Main game loop. Initializes the game and handles rendering,
     * updates, and resources.
     */
    public void run() {
        this.running = true;

        try {
            // Initialize the game
            this.init();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, e.toString(),
                    "Failed to start Minecraft", JOptionPane.ERROR_MESSAGE);
            return;
        }

        long lastFpsUpdateTime = System.currentTimeMillis();
        int framesCounter = 0;

        try {
            // Main game loop
            while (this.running) {
                if (this.pause) {
                    // When paused, sleep to avoid using CPU resources
                    Thread.sleep(100L);
                } else {
                    // Check if window is closed
                    if (!window.update()) {
                        this.stop();
                    }

                    // Update input state
                    inputHandler.update();

                    // Process input
                    gameInputHandler.processInput(this.hitResult);

                    // Update timer and calculate ticks
                    this.timer.advanceTime();

                    // Process game ticks
                    for (int i = 0; i < this.timer.ticks; ++i) {
                        this.tick();
                    }

                    // Handle mouse look
                    gameInputHandler.processMouseLook();

                    // Perform picking to detect which block the player is looking at
                    this.hitResult = this.renderer.pick(this.timer.partialTick);

                    // Render the frame
                    this.renderer.render(
                            this.timer.partialTick,
                            this.hitResult,
                            gameInputHandler.getEditMode(),
                            gameInputHandler.getPaintTexture(),
                            this.fpsString
                    );

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

                    // Handle window focus change
                    if (!window.hasFocus()) {
                        gameInputHandler.handleFocusChange(false);
                    }

                    // Update window dimensions if they've changed
                    if (window != null) {
                        int newWidth = window.getWidth();
                        int newHeight = window.getHeight();

                        if (newWidth != width || newHeight != height) {
                            width = newWidth;
                            height = newHeight;
                            renderer.setDimensions(width, height);
                            System.out.println("Window resized: " + width + "x" + height);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
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
     * Updates the game state for one tick.
     * Updates entities and the level.
     */
    public void tick() {
        // Update all game entities
        for (int i = 0; i < this.entities.size(); ++i) {
            Entity entity = this.entities.get(i);
            entity.tick();
            if (entity.removed) {
                this.entities.remove(i--);
            }
        }

        // Update the player
        this.player.tick();

        // Update particle engine
        this.particleEngine.tick();

        // Update level
        this.level.tick();
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
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, e.toString(), "Failed to start Minecraft", JOptionPane.ERROR_MESSAGE);
        }
    }
}
