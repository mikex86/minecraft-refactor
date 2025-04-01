package com.mojang.minecraft;

import com.mojang.minecraft.character.Zombie;
import com.mojang.minecraft.gui.Font;
import com.mojang.minecraft.level.Chunk;
import com.mojang.minecraft.level.Level;
import com.mojang.minecraft.level.LevelRenderer;
import com.mojang.minecraft.level.tile.Tile;
import com.mojang.minecraft.particle.ParticleEngine;
import com.mojang.minecraft.phys.AABB;
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
    
    // Game flags
    public volatile boolean pause = false;
    private volatile boolean running = false;
    private boolean mouseGrabbed = false;
    
    // UI and input state
    private int paintTexture = 1;
    private int yMouseAxis = 1;  // Controls if mouse Y axis is inverted
    private int editMode = 0;    // 0 = destroy blocks, 1 = place blocks
    private String fpsString = "";
    private Font font;
    
    // Rendering resources
    public Textures textures;
    private GameRenderer renderer;
    private HitResult hitResult = null;

    /**
     * Creates a new Minecraft game instance.
     *
     * @param width Width of the rendering area
     * @param height Height of the rendering area
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
        
        // Initially grab the mouse
        this.grabMouse();
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

                    // Update timer and calculate ticks
                    this.timer.advanceTime();

                    // Process game ticks
                    for (int i = 0; i < this.timer.ticks; ++i) {
                        this.tick();
                    }

                    // Handle mouse look if mouse is grabbed
                    if (this.mouseGrabbed) {
                        float mouseX = (float) inputHandler.getMouseDX();
                        float mouseY = (float) inputHandler.getMouseDY();

                        // Apply mouse movement to player rotation ;
                        // This must be performed every frame, as tick() is too slow
                        this.player.turn(mouseX, mouseY * (float) this.yMouseAxis);
                    }

                    // Perform picking to detect which block the player is looking at
                    this.hitResult = this.renderer.pick(this.timer.partialTick);
                    
                    // Render the frame
                    this.renderer.render(
                        this.timer.partialTick,
                        this.hitResult,
                        this.editMode,
                        this.paintTexture,
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
     * Grabs the mouse cursor, hiding it and enabling mouse look.
     */
    public void grabMouse() {
        if (!this.mouseGrabbed) {
            this.mouseGrabbed = true;
            inputHandler.setCursorVisible(false);
        }
    }

    /**
     * Releases the mouse cursor, showing it and disabling mouse look.
     */
    public void releaseMouse() {
        if (this.mouseGrabbed) {
            this.mouseGrabbed = false;
            inputHandler.setCursorVisible(true);
        }
    }

    /**
     * Handles mouse click actions in the world, either destroying or placing blocks.
     */
    private void handleMouseClick() {
        if (this.editMode == 0) {
            // Destroy mode
            if (this.hitResult != null) {
                Tile oldTile = Tile.tiles[this.level.getTile(this.hitResult.x, this.hitResult.y, this.hitResult.z)];
                boolean changed = this.level.setTile(this.hitResult.x, this.hitResult.y, this.hitResult.z, 0);
                if (oldTile != null && changed) {
                    oldTile.destroy(this.level, this.hitResult.x, this.hitResult.y, this.hitResult.z, this.particleEngine);
                }
            }
        } else if (this.hitResult != null) {
            // Build mode
            int x = this.hitResult.x;
            int y = this.hitResult.y;
            int z = this.hitResult.z;
            
            // Adjust coordinates based on which face was hit
            if (this.hitResult.face == 0) {
                --y; // Bottom face
            } else if (this.hitResult.face == 1) {
                ++y; // Top face
            } else if (this.hitResult.face == 2) {
                --z; // North face
            } else if (this.hitResult.face == 3) {
                ++z; // South face
            } else if (this.hitResult.face == 4) {
                --x; // West face
            } else if (this.hitResult.face == 5) {
                ++x; // East face
            }

            // Check if we can place a block here
            AABB aabb = Tile.tiles[this.paintTexture].getAABB(x, y, z);
            if (aabb == null || this.isFree(aabb)) {
                this.level.setTile(x, y, z, this.paintTexture);
            }
        }
    }

    /**
     * Updates the game state for one tick.
     * Processes input, updates entities, and updates the level.
     */
    public void tick() {
        // Process all keyboard events
        while (inputHandler.hasNextKeyEvent()) {
            InputHandler.KeyEvent event = inputHandler.getNextKeyEvent();
            int key = event.getKey();
            boolean pressed = event.isPressed();
            
            if (pressed) {
                // Escape key - release mouse in windowed mode
                if (key == InputHandler.Keys.KEY_ESCAPE && !this.fullscreen) {
                    this.releaseMouse();
                }

                // Enter key - save level
                if (key == InputHandler.Keys.KEY_RETURN) {
                    this.level.save();
                }

                // Block selection keys
                if (key == InputHandler.Keys.KEY_1) {
                    this.paintTexture = 1;  // Stone
                }
                if (key == InputHandler.Keys.KEY_2) {
                    this.paintTexture = 3;  // Dirt
                }
                if (key == InputHandler.Keys.KEY_3) {
                    this.paintTexture = 4;  // Cobblestone
                }
                if (key == InputHandler.Keys.KEY_4) {
                    this.paintTexture = 5;  // Wooden planks
                }
                if (key == InputHandler.Keys.KEY_5) {
                    this.paintTexture = 6;  // Sapling
                }
            }
        }

        // Process all mouse button events
        while (inputHandler.hasNextMouseButtonEvent()) {
            InputHandler.MouseButtonEvent event = inputHandler.getNextMouseButtonEvent();
            int button = event.getButton();
            boolean pressed = event.isPressed();
            
            if (!this.mouseGrabbed && pressed) {
                // Auto-grab mouse on click when not grabbed
                this.grabMouse();
            } else {
                // Handle left mouse button (destroy/place blocks)
                if (button == InputHandler.MouseButtons.BUTTON_LEFT && pressed) {
                    this.handleMouseClick();
                }

                // Handle right mouse button (toggle edit mode)
                if (button == InputHandler.MouseButtons.BUTTON_RIGHT && pressed) {
                    this.editMode = (this.editMode + 1) % 2;
                }
            }
        }

        // Update player movement based on keyboard input
        boolean forward = inputHandler.isKeyDown(InputHandler.Keys.KEY_W);
        boolean back = inputHandler.isKeyDown(InputHandler.Keys.KEY_S);
        boolean left = inputHandler.isKeyDown(InputHandler.Keys.KEY_A);
        boolean right = inputHandler.isKeyDown(InputHandler.Keys.KEY_D);
        boolean jump = inputHandler.isKeyDown(InputHandler.Keys.KEY_SPACE);
        
        this.player.setInput(forward, back, left, right, jump, inputHandler.isKeyDown(InputHandler.Keys.KEY_LSHIFT));
        
        // Check for fullscreen toggle (F11)
        if (inputHandler.isKeyDown(InputHandler.Keys.KEY_F11)) {
            // Implement fullscreen toggle if needed
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
        
        // Release mouse if window loses focus
        if (window != null && !window.hasFocus()) {
            this.releaseMouse();
        }
        
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
     * Checks if a bounding box is free from collisions with entities and the player.
     *
     * @param aabb The bounding box to check
     * @return true if the area is free, false if there's a collision
     */
    private boolean isFree(AABB aabb) {
        // Check for collision with player
        if (this.player.bb.intersects(aabb)) {
            return false;
        }
        
        // Check for collision with any entity
        for (Entity entity : this.entities) {
            if (entity.bb.intersects(aabb)) {
                return false;
            }
        }

        return true;
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
