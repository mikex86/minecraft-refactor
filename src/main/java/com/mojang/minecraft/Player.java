package com.mojang.minecraft;

import com.mojang.minecraft.level.Level;
import org.lwjgl.input.Keyboard;

/**
 * Represents the player entity in the game.
 * Handles player movement, input, and interaction with the world.
 */
public class Player extends Entity {
    /**
     * Creates a new Player instance.
     * 
     * @param level The level in which the player exists
     */
    public Player(Level level) {
        super(level);
        this.heightOffset = 1.62F; // Eye height offset
    }

    /**
     * Updates the player's position and movement based on keyboard input.
     * Called every game tick.
     */
    public void tick() {
        // Store previous position
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        
        float xa = 0.0F; // X movement input
        float ya = 0.0F; // Z movement input (forward/backward)
        
        // Reset position when R is pressed
        if (Keyboard.isKeyDown(19)) { // R key
            this.resetPos();
        }

        // Forward movement (W or up arrow)
        if (Keyboard.isKeyDown(200) || Keyboard.isKeyDown(17)) {
            --ya;
        }

        // Backward movement (S or down arrow)
        if (Keyboard.isKeyDown(208) || Keyboard.isKeyDown(31)) {
            ++ya;
        }

        // Left movement (A or left arrow)
        if (Keyboard.isKeyDown(203) || Keyboard.isKeyDown(30)) {
            --xa;
        }

        // Right movement (D or right arrow)
        if (Keyboard.isKeyDown(205) || Keyboard.isKeyDown(32)) {
            ++xa;
        }

        // Jump (space or home key)
        if ((Keyboard.isKeyDown(57) || Keyboard.isKeyDown(219)) && this.onGround) {
            this.yd = 0.5F; // Vertical velocity for jumping
        }

        // Apply movement - different friction when in air vs on ground
        this.moveRelative(xa, ya, this.onGround ? 0.1F : 0.02F);
        
        // Apply gravity
        this.yd = (float)((double)this.yd - 0.08);
        
        // Move based on current velocity
        this.move(this.xd, this.yd, this.zd);
        
        // Apply air resistance
        this.xd *= 0.91F;
        this.yd *= 0.98F;
        this.zd *= 0.91F;
        
        // Apply ground friction
        if (this.onGround) {
            this.xd *= 0.7F;
            this.zd *= 0.7F;
        }
    }
}
