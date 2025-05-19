package com.mojang.minecraft.input;

import com.mojang.minecraft.entity.EntityPlayer;
import com.mojang.minecraft.gui.scaling.ScaledResolution;
import com.mojang.minecraft.gui.screen.GuiScreen;
import com.mojang.minecraft.item.BlockItem;
import com.mojang.minecraft.item.Item;
import com.mojang.minecraft.item.ItemStack;
import com.mojang.minecraft.level.Level;
import com.mojang.minecraft.level.block.Blocks;
import com.mojang.minecraft.level.block.state.BlockState;
import com.mojang.minecraft.particle.ParticleEngine;
import com.mojang.minecraft.phys.AABB;
import com.mojang.minecraft.world.HitResult;

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

    private int hotbarSlotIndex = 0;

    // Game references needed for input processing
    private final EntityPlayer player;
    private final Level level;
    private final ParticleEngine particleEngine;
    private final boolean fullscreen;

    // Currently open GUI screen (if any)
    private GuiScreen currentScreen = null;

    private int leftClickDelayTimer = 0;
    private int rightClickDelayTimer = 0;

    // whether left / right mouse buttons are currently down
    private boolean leftClickPressed = false;
    private boolean rightClickPressed = false;

    // last hit result
    private HitResult lastHitResult;

    /**
     * Creates a new GameInputHandler.
     *
     * @param inputHandler   Low-level input handler
     * @param player         The player entity
     * @param level          The game level
     * @param particleEngine The particle engine
     * @param fullscreen     Whether the game is in fullscreen mode
     */
    public GameInputHandler(
            InputHandler inputHandler,
            EntityPlayer player,
            Level level,
            ParticleEngine particleEngine,
            boolean fullscreen) {
        this.inputHandler = inputHandler;
        this.player = player;
        this.level = level;
        this.particleEngine = particleEngine;
        this.fullscreen = fullscreen;

        // Initially grab the mouse
        this.grabMouse();
    }

    /**
     * Tick method to update the input handler state.
     * Handles repeated right-click actions and processes left-click actions when buttons are held down.
     */
    public void tick() {
        if (rightClickDelayTimer > 0) {
            rightClickDelayTimer--;
        }
        if (leftClickDelayTimer > 0) {
            leftClickDelayTimer--;
        }
        if (rightClickDelayTimer == 0 && rightClickPressed) {
            if (this.lastHitResult != null) {
                this.handleRightClick(this.lastHitResult);
            }
        }
        if (leftClickDelayTimer == 0 && leftClickPressed) {
            if (this.lastHitResult != null) {
                this.handleLeftClick(this.lastHitResult);
            }
        }
    }

    /**
     * Process all pending input events and update game state accordingly.
     *
     * @param hitResult    The current hit result (block being looked at)
     * @param windowWidth  the window width
     * @param windowHeight the window height
     */
    public void processInput(HitResult hitResult, int windowWidth, int windowHeight) {
        this.lastHitResult = hitResult;

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
                    hotbarSlotIndex = 0;
                }
                if (key == InputHandler.Keys.KEY_2) {
                    hotbarSlotIndex = 1;
                }
                if (key == InputHandler.Keys.KEY_3) {
                    hotbarSlotIndex = 2;
                }
                if (key == InputHandler.Keys.KEY_4) {
                    hotbarSlotIndex = 3;
                }
                if (key == InputHandler.Keys.KEY_5) {
                    hotbarSlotIndex = 4;
                }
                if (key == InputHandler.Keys.KEY_6) {
                    hotbarSlotIndex = 5;
                }
                if (key == InputHandler.Keys.KEY_7) {
                    hotbarSlotIndex = 6;
                }
                if (key == InputHandler.Keys.KEY_8) {
                    hotbarSlotIndex = 7;
                }
                if (key == InputHandler.Keys.KEY_9) {
                    hotbarSlotIndex = 8;
                }

                if (key == InputHandler.Keys.KEY_E) {
                    this.player.toggleInventory();
                }
            }
        }

        // Process all mouse button events
        while (inputHandler.hasNextMouseButtonEvent()) {
            InputHandler.MouseButtonEvent event = inputHandler.getNextMouseButtonEvent();
            int button = event.getButton();
            boolean pressed = event.isPressed();

            if (this.currentScreen != null) {
                float mouseX = (float) inputHandler.getMouseX();
                float mouseY = (float) inputHandler.getMouseY();

                float scaledWidth = ScaledResolution.getScaledWidth(windowWidth, windowHeight);
                float scaledHeight = ScaledResolution.getScaledHeight(windowHeight);

                float mouseXRelative = mouseX / (float) windowWidth;
                float mouseYRelative = mouseY / (float) windowHeight;

                float mouseXScaled = mouseXRelative * scaledWidth;
                float mouseYScaled = mouseYRelative * scaledHeight;

                this.currentScreen.onMouseClicked(mouseXScaled, mouseYScaled, button, pressed);
            }

            if (!this.mouseGrabbed && pressed) {
                // Auto-grab mouse on click when not grabbed
                this.grabMouse();
            } else {
                // Handle left/right mouse buttons (destroy/place blocks)
                if (button == InputHandler.MouseButtons.BUTTON_RIGHT) {
                    if (pressed) {
                        this.handleRightClick(hitResult);
                    }
                    this.rightClickPressed = pressed;
                } else if (button == InputHandler.MouseButtons.BUTTON_LEFT) {
                    if (pressed) {
                        this.handleLeftClick(hitResult);
                    }
                    this.leftClickPressed = pressed;
                }
            }
        }

        // Process scroll events
        double scrollDelta = inputHandler.getMouseScrollY();

        if (currentScreen == null) {
            // process hotbar scroll
            if (scrollDelta < 0) {
                this.hotbarSlotIndex += 1;
            } else if (scrollDelta > 0) {
                this.hotbarSlotIndex -= 1;
            }
            int hotBarSize = player.getInventory().getHotbarSize();
            this.hotbarSlotIndex %= hotBarSize;
            if (this.hotbarSlotIndex < 0) {
                this.hotbarSlotIndex += hotBarSize;
            }
        }

        // Update player movement based on keyboard input
        boolean forward = inputHandler.isKeyDown(InputHandler.Keys.KEY_W);
        boolean back = inputHandler.isKeyDown(InputHandler.Keys.KEY_S);
        boolean left = inputHandler.isKeyDown(InputHandler.Keys.KEY_A);
        boolean right = inputHandler.isKeyDown(InputHandler.Keys.KEY_D);
        boolean jump = inputHandler.isKeyDown(InputHandler.Keys.KEY_SPACE);
        boolean sneak = inputHandler.isKeyDown(InputHandler.Keys.KEY_LSHIFT);
        boolean sprinting = inputHandler.isKeyDown(InputHandler.Keys.KEY_LCONTROL);

        this.player.setInput(forward, back, left, right, jump, sneak, sprinting);

    }

    private void handleRightClick(HitResult hitResult) {
        if (this.handleMouseClick(hitResult, true)) {
            this.rightClickDelayTimer = 4; // Delay for right-click actions
        }
    }

    private void handleLeftClick(HitResult hitResult) {
        if (this.handleMouseClick(hitResult, false)) {
            this.leftClickDelayTimer = 6; // Delay for left-click actions
        }
    }

    /**
     * Processes mouse movement for looking around.
     */
    public void processMouseLook(int windowWidth, int windowHeight) {
        float dX = (float) inputHandler.getMouseDX();
        float dY = (float) inputHandler.getMouseDY();

        if (currentScreen != null) {
            if (dX != 0 || dY != 0) {
                float mouseX = (float) inputHandler.getMouseX();
                float mouseY = (float) inputHandler.getMouseY();

                float scaledWidth = ScaledResolution.getScaledWidth(windowWidth, windowHeight);
                float scaledHeight = ScaledResolution.getScaledHeight(windowHeight);

                float mouseXRelative = mouseX / (float) windowWidth;
                float mouseYRelative = mouseY / (float) windowHeight;

                float mouseXScaled = mouseXRelative * scaledWidth;
                float mouseYScaled = mouseYRelative * scaledHeight;
                currentScreen.onMouseMove(mouseXScaled, mouseYScaled, dX, dY);
            }
        }

        if (this.mouseGrabbed) {
            if (dX != 0 || dY != 0) {
                // Apply mouse movement to player rotation
                this.player.turn(dX, dY * (float) this.yMouseAxis);
            }
        }
    }

    /**
     * Handles mouse click actions in the world, either destroying or placing blocks.
     *
     * @return true if the action was successful, false otherwise
     */
    private boolean handleMouseClick(HitResult hitResult, boolean isRightClick) {
        if (hitResult == null) {
            return false;
        }

        if (!isRightClick) {
            // Destroy mode
            BlockState oldBlock = this.level.getBlockState(hitResult.x, hitResult.y, hitResult.z);
            boolean changed = this.level.setBlockState(hitResult.x, hitResult.y, hitResult.z, null, true);
            if (oldBlock != null && changed) {
                oldBlock.block.destroy(this.level, hitResult.x, hitResult.y, hitResult.z, this.particleEngine);
            }
            return changed;
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
            BlockState blockState = this.level.getBlockState(x, y, z);
            AABB aabb = (blockState == null ? Blocks.rock : blockState.block).getAABB(x, y, z);
            ItemStack itemStack = this.player.getInventory().getHotbarItem(this.hotbarSlotIndex);
            if (itemStack != null) {
                Item item = itemStack.getItem();
                if (item instanceof BlockItem) {
                    BlockItem blockItem = (BlockItem) item;
                    if (this.level.isFreeFromEntities(aabb)) {
                        this.level.setBlockState(x, y, z, blockItem.getBlock().getBlockState(hitResult.facingDirection), true);
                        this.player.getInventory().decreaseHotbarItem(this.hotbarSlotIndex, 1);
                        return true;
                    }
                }
            }
            return false;
        }
    }

    /**
     * State whether grabMouse() should be a no-op.
     * This is needed to cancel mouse grabbing when the window regains focus when a current gui screen is active.
     */
    private boolean lockMouseReleased = false;

    /**
     * Grabs the mouse cursor, hiding it and enabling mouse look.
     */
    public void grabMouse() {
        if (lockMouseReleased) {
            return;
        }
        if (!this.mouseGrabbed) {
            this.mouseGrabbed = true;
            inputHandler.setCursorCaptured(true);
            inputHandler.clearMouseDelta();
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
     * Sets the mouse position.
     *
     * @param x the x coordinate of the mouse
     * @param y the y coordinate of the mouse
     */
    public void setMousePosition(float x, float y) {
        this.inputHandler.setMousePosition(x, y);
    }

    public void setLockMouseReleased(boolean lockMouseReleased) {
        this.lockMouseReleased = lockMouseReleased;
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
     * @return the current hotbar slot index
     */
    public int getHotbarSlotIndex() {
        return hotbarSlotIndex;
    }

    public void setCurrentScreen(GuiScreen screen) {
        this.currentScreen = screen;
    }
}