package com.mojang.minecraft.input;

import com.mojang.minecraft.entity.Entity;
import com.mojang.minecraft.entity.Player;
import com.mojang.minecraft.level.Level;
import com.mojang.minecraft.level.tile.Tile;
import com.mojang.minecraft.particle.ParticleEngine;
import com.mojang.minecraft.phys.AABB;
import input.InputHandler;
import com.mojang.minecraft.util.math.CollisionUtils;
import com.mojang.minecraft.world.HitResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles high-level game input processing and mapping to game actions.
 * This separates input handling logic from the main game class.
 */
public class GameInputHandler {
    // Reference to low-level input handler
    private final InputHandler inputHandler;

    // Input state
    private boolean mouseGrabbed = false;
    private final int yMouseAxis = 1;  // Controls if mouse Y axis is inverted
    private int editMode = 0;    // 0 = destroy blocks, 1 = place blocks
    private int paintTexture = 1;  // Current block type for building

    // Game references needed for input processing
    private final Player player;
    private final Level level;
    private final ParticleEngine particleEngine;
    private final ArrayList<Entity> entities;
    private final boolean fullscreen;

    /**
     * Creates a new GameInputHandler.
     *
     * @param inputHandler   Low-level input handler
     * @param player         The player entity
     * @param level          The game level
     * @param particleEngine The particle engine
     * @param entities       List of game entities
     * @param fullscreen     Whether the game is in fullscreen mode
     */
    public GameInputHandler(
            InputHandler inputHandler,
            Player player,
            Level level,
            ParticleEngine particleEngine,
            List<Entity> entities,
            boolean fullscreen) {
        this.inputHandler = inputHandler;
        this.player = player;
        this.level = level;
        this.particleEngine = particleEngine;
        this.entities = new ArrayList<>(entities);
        this.fullscreen = fullscreen;

        // Initially grab the mouse
        this.grabMouse();
    }

    /**
     * Process all pending input events and update game state accordingly.
     *
     * @param hitResult The current hit result (block being looked at)
     */
    public void processInput(HitResult hitResult) {
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
                    this.handleMouseClick(hitResult);
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
        boolean sneak = inputHandler.isKeyDown(InputHandler.Keys.KEY_LSHIFT);

        this.player.setInput(forward, back, left, right, jump, sneak);

    }

    /**
     * Processes mouse movement for looking around.
     */
    public void processMouseLook() {
        if (this.mouseGrabbed) {
            float mouseX = (float) inputHandler.getMouseDX();
            float mouseY = (float) inputHandler.getMouseDY();

            // Apply mouse movement to player rotation
            this.player.turn(mouseX, mouseY * (float) this.yMouseAxis);
        }
    }

    /**
     * Handles mouse click actions in the world, either destroying or placing blocks.
     */
    private void handleMouseClick(HitResult hitResult) {
        if (hitResult == null) {
            return;
        }

        if (this.editMode == 0) {
            // Destroy mode
            Tile oldTile = Tile.tiles[this.level.getTile(hitResult.x, hitResult.y, hitResult.z)];
            boolean changed = this.level.setTile(hitResult.x, hitResult.y, hitResult.z, 0);
            if (oldTile != null && changed) {
                oldTile.destroy(this.level, hitResult.x, hitResult.y, hitResult.z, this.particleEngine);
            }
        } else {
            // Build mode
            int x = hitResult.x;
            int y = hitResult.y;
            int z = hitResult.z;

            // Adjust coordinates based on which face was hit
            if (hitResult.face == 0) {
                --y; // Bottom face
            } else if (hitResult.face == 1) {
                ++y; // Top face
            } else if (hitResult.face == 2) {
                --z; // North face
            } else if (hitResult.face == 3) {
                ++z; // South face
            } else if (hitResult.face == 4) {
                --x; // West face
            } else if (hitResult.face == 5) {
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
     * Checks if a bounding box is free from collisions with entities and the player.
     *
     * @param aabb The bounding box to check
     * @return true if the area is free, false if there's a collision
     */
    private boolean isFree(AABB aabb) {
        // Check for collision with player
        if (CollisionUtils.intersects(this.player.bb, aabb)) {
            return false;
        }

        // Check for collision with any entity
        for (Entity entity : this.entities) {
            if (CollisionUtils.intersects(entity.bb, aabb)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Grabs the mouse cursor, hiding it and enabling mouse look.
     */
    public void grabMouse() {
        if (!this.mouseGrabbed) {
            this.mouseGrabbed = true;
            inputHandler.setCursorCaptured(true);
        }
    }

    /**
     * Releases the mouse cursor, showing it and disabling mouse look.
     */
    public void releaseMouse() {
        if (this.mouseGrabbed) {
            this.mouseGrabbed = false;
            inputHandler.setCursorCaptured(false);
        }
    }

    /**
     * Handles window focus changes.
     *
     * @param hasFocus Whether the window has focus
     */
    public void handleFocusChange(boolean hasFocus) {
        if (!hasFocus) {
            releaseMouse();
        }
    }

    /**
     * Gets the current edit mode.
     *
     * @return 0 for destroy mode, 1 for build mode
     */
    public int getEditMode() {
        return editMode;
    }

    /**
     * Gets the current block type for building.
     *
     * @return The block texture/type ID
     */
    public int getPaintTexture() {
        return paintTexture;
    }
} 