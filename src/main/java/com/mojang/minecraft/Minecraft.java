package com.mojang.minecraft;

import com.mojang.minecraft.character.Zombie;
import com.mojang.minecraft.engine.GameEngine;
import com.mojang.minecraft.gui.Font;
import com.mojang.minecraft.input.GameInputHandler;
import com.mojang.minecraft.level.Level;
import com.mojang.minecraft.level.LevelRenderer;
import com.mojang.minecraft.particle.ParticleEngine;
import com.mojang.minecraft.renderer.GameRenderer;
import com.mojang.minecraft.renderer.Textures;

import javax.swing.*;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Main game class for Minecraft Classic 0.0.11a.
 * Handles game-specific logic and delegates engine functionality to GameEngine.
 */
public class Minecraft implements Runnable {
    // Constants
    public static final String VERSION_STRING = "0.0.11a";

    // Game engine
    private final GameEngine engine;

    // Game state
    public Level level;
    private LevelRenderer levelRenderer;
    private Player player;
    private ParticleEngine particleEngine;
    private final ArrayList<Entity> entities = new ArrayList<>();

    // Game input
    private GameInputHandler gameInputHandler;

    // Game flags
    public volatile boolean pause = false;
    private volatile boolean running = false;

    // Game resources
    public Textures textures;
    private Font font;
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

            // Create game objects
            this.level = new Level(256, 256, 64);
            this.levelRenderer = new LevelRenderer(this.level, this.textures);
            this.player = new Player(this.level);
            this.particleEngine = new ParticleEngine(this.level, this.textures);
            this.font = new Font("/default.gif", this.textures);

            // Create renderer
            this.renderer = new GameRenderer(
                    this,
                    this.levelRenderer,
                    this.particleEngine,
                    this.player,
                    this.entities,
                    this.textures,
                    this.font,
                    engine.getWidth(),
                    engine.getHeight()
            );

            // Add some zombies to the level
            for (int i = 0; i < 10; ++i) {
                Zombie zombie = new Zombie(this.level, this.textures, 128.0F, 0.0F, 128.0F);
                zombie.resetPos();
                this.entities.add(zombie);
            }

            // Initialize game input handler
            this.gameInputHandler = new GameInputHandler(
                    engine.getInputHandler(),
                    this.player,
                    this.level,
                    this.particleEngine,
                    this.entities,
                    engine.isFullscreen()
            );
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, e.toString(), "Failed to initialize game", JOptionPane.ERROR_MESSAGE);
            throw new IOException("Failed to initialize game", e);
        }
    }

    /**
     * Cleans up resources and saves the level before shutting down.
     */
    public void destroy() {
        try {
            this.level.save();
            engine.shutdown();
            if (textures != null) {
                textures.dispose();
            }
        } catch (Exception e) {
            e.printStackTrace();
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
            JOptionPane.showMessageDialog(null, e.toString(),
                    "Failed to start Minecraft", JOptionPane.ERROR_MESSAGE);
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

                    // Process input
                    gameInputHandler.processInput(this.hitResult);

                    // Get time information from engine
                    int ticksToProcess = engine.getTicksToProcess();
                    float partialTick = engine.getPartialTick();

                    // Process game ticks
                    for (int i = 0; i < ticksToProcess; ++i) {
                        this.tick();
                    }

                    // Handle mouse look
                    gameInputHandler.processMouseLook();

                    // Perform picking to detect which block the player is looking at
                    this.hitResult = this.renderer.pick(partialTick);

                    // Render the frame
                    this.renderer.render(
                            partialTick,
                            this.hitResult,
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
