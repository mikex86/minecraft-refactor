package com.mojang.minecraft.renderer;

import com.mojang.minecraft.Entity;
import com.mojang.minecraft.HitResult;
import com.mojang.minecraft.Minecraft;
import com.mojang.minecraft.Player;
import com.mojang.minecraft.gui.Font;
import com.mojang.minecraft.level.LevelRenderer;
import com.mojang.minecraft.level.tile.Tile;
import com.mojang.minecraft.particle.ParticleEngine;

import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;

/**
 * Handles all rendering operations for Minecraft.
 * Extracted from the main Minecraft class to separate rendering concerns.
 */
public class GameRenderer {
    // The Minecraft instance this renderer belongs to
    private final Minecraft minecraft;
    
    // Rendering resources
    private final FloatBuffer fogColor0;
    private final FloatBuffer fogColor1;
    private final FloatBuffer lightBuffer;
    private final IntBuffer viewportBuffer;
    private final IntBuffer selectBuffer;
    
    // Game components needed for rendering
    private final LevelRenderer levelRenderer;
    private final ParticleEngine particleEngine;
    private final Player player;
    private final List<Entity> entities;
    private final Textures textures;
    private final Font font;
    
    // Window dimensions
    private int width;
    private int height;
    
    /**
     * Creates a new GameRenderer.
     *
     * @param minecraft The Minecraft instance this renderer belongs to
     * @param levelRenderer The level renderer
     * @param particleEngine The particle engine
     * @param player The player
     * @param entities The list of entities
     * @param textures The texture manager
     * @param font The font for rendering text
     * @param width The initial window width
     * @param height The initial window height
     */
    public GameRenderer(Minecraft minecraft, LevelRenderer levelRenderer, 
                        ParticleEngine particleEngine, Player player, 
                        List<Entity> entities, Textures textures, Font font,
                        int width, int height) {
        this.minecraft = minecraft;
        this.levelRenderer = levelRenderer;
        this.particleEngine = particleEngine;
        this.player = player;
        this.entities = entities;
        this.textures = textures;
        this.font = font;
        this.width = width;
        this.height = height;
        
        // Initialize buffers
        this.fogColor0 = BufferUtils.createFloatBuffer(4);
        this.fogColor1 = BufferUtils.createFloatBuffer(4);
        this.lightBuffer = BufferUtils.createFloatBuffer(16);
        this.viewportBuffer = BufferUtils.createIntBuffer(16);
        this.selectBuffer = BufferUtils.createIntBuffer(2000);
        
        // Set up fog colors
        int skyColor = 16710650;  // Light blue
        int fogColor = 920330;    // Dark blue
        
        // Set up the sky color buffer
        this.fogColor0.put(new float[]{
            (float) (skyColor >> 16 & 255) / 255.0F, 
            (float) (skyColor >> 8 & 255) / 255.0F, 
            (float) (skyColor & 255) / 255.0F, 
            1.0F
        });
        this.fogColor0.flip();
        
        // Set up the fog color buffer
        this.fogColor1.put(new float[]{
            (float) (fogColor >> 16 & 255) / 255.0F, 
            (float) (fogColor >> 8 & 255) / 255.0F, 
            (float) (fogColor & 255) / 255.0F, 
            1.0F
        });
        this.fogColor1.flip();
    }

    /**
     * Sets the window dimensions, used for proper viewport configuration.
     * 
     * @param width New window width
     * @param height New window height
     */
    public void setDimensions(int width, int height) {
        this.width = width;
        this.height = height;
    }

    /**
     * Positions the camera based on the player's position and orientation.
     *
     * @param partialTick Interpolation factor between ticks (0.0-1.0)
     */
    private void moveCameraToPlayer(float partialTick) {
        // Position camera slightly behind the player's view point
        glTranslatef(0.0F, 0.0F, -0.3F);
        
        // Rotate camera based on player's orientation
        glRotatef(this.player.xRot, 1.0F, 0.0F, 0.0F);
        glRotatef(this.player.yRot, 0.0F, 1.0F, 0.0F);
        
        // Calculate interpolated position between ticks
        float x = this.player.xo + (this.player.x - this.player.xo) * partialTick;
        float y = this.player.yo + (this.player.y - this.player.yo) * partialTick;
        float z = this.player.zo + (this.player.z - this.player.zo) * partialTick;
        
        // Position camera at player location
        glTranslatef(-x, -y, -z);
    }

    /**
     * Sets up the perspective camera for 3D rendering.
     *
     * @param partialTick Interpolation factor between ticks (0.0-1.0)
     */
    private void setupCamera(float partialTick) {
        // Set up perspective projection
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        
        // Use 70 degree FOV, maintain proper aspect ratio, and reasonable near/far planes
        float aspectRatio = (float) this.width / (float) this.height;

        MatrixUtils.perspective(70.0F, aspectRatio, 0.05F, 1000.0F);
        
        // Set up camera transformation
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        this.moveCameraToPlayer(partialTick);
    }

    /**
     * Sets up the picking camera for selection in 3D space.
     *
     * @param partialTick Interpolation factor between ticks (0.0-1.0)
     * @param x X coordinate of pick center (usually screen center)
     * @param y Y coordinate of pick center (usually screen center)
     */
    private void setupPickCamera(float partialTick, int x, int y) {
        // Set up projection matrix for picking
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        
        // Get current viewport dimensions
        this.viewportBuffer.clear();
        glGetIntegerv(GL_VIEWPORT, this.viewportBuffer);
        this.viewportBuffer.flip();
        this.viewportBuffer.limit(16);
        int[] viewport = new int[4];
        viewport[0] = this.viewportBuffer.get(0);
        viewport[1] = this.viewportBuffer.get(1);
        viewport[2] = this.viewportBuffer.get(2);
        viewport[3] = this.viewportBuffer.get(3);
        
        // Create picking matrix centered at the specified point
        MatrixUtils.pickMatrix((float) x, (float) y, 5.0F, 5.0F, viewport);
        MatrixUtils.perspective(70.0F, (float) this.width / (float) this.height, 0.05F, 1000.0F);
        
        // Set up camera transformation
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        this.moveCameraToPlayer(partialTick);
    }

    /**
     * Performs picking to detect which block the player is looking at.
     *
     * @param partialTick Interpolation factor between ticks (0.0-1.0)
     * @return The hit result, or null if no block was hit
     */
    public HitResult pick(float partialTick) {
        // Clear and set up the selection buffer
        this.selectBuffer.clear();
        glSelectBuffer(this.selectBuffer);
        glRenderMode(GL_SELECT);
        
        // Set up the camera for picking at screen center
        this.setupPickCamera(partialTick, this.width / 2, this.height / 2);
        
        // Render the world in selection mode
        this.levelRenderer.pick(this.player, Frustum.getFrustum());
        
        // Get the selection results
        int hits = glRenderMode(GL_RENDER);
        this.selectBuffer.flip();
        this.selectBuffer.limit(this.selectBuffer.capacity());
        
        // Find the closest hit
        long closest = 0L;
        int[] names = new int[10];
        int hitNameCount = 0;

        for (int i = 0; i < hits; ++i) {
            int nameCount = this.selectBuffer.get();
            long minZ = this.selectBuffer.get();
            this.selectBuffer.get(); // Skip maxZ, we only care about minZ
            
            if (minZ >= closest && i != 0) {
                // Skip hits that are farther than the closest one found so far
                for (int j = 0; j < nameCount; ++j) {
                    this.selectBuffer.get();
                }
            } else {
                // Found a closer hit, store it
                closest = minZ;
                hitNameCount = nameCount;

                for (int j = 0; j < nameCount; ++j) {
                    names[j] = this.selectBuffer.get();
                }
            }
        }

        // Create the hit result if a hit was found
        if (hitNameCount > 0) {
            return new HitResult(names[0], names[1], names[2], names[3], names[4]);
        } else {
            return null;
        }
    }

    /**
     * Configures the fog settings for different rendering passes.
     * 
     * @param mode 0 for lit areas (day), 1 for unlit areas (night)
     */
    private void setupFog(int mode) {
        if (mode == 0) {
            // Day fog (lighter, more distant)
            glFogi(GL_FOG_MODE, GL_EXP);
            glFogf(GL_FOG_DENSITY, 0.001F);
            glFogfv(GL_FOG_COLOR, this.fogColor0);
            glDisable(GL_LIGHTING);
        } else if (mode == 1) {
            // Night fog (darker, closer)
            glFogi(GL_FOG_MODE, GL_EXP);
            glFogf(GL_FOG_DENSITY, 0.01F);
            glFogfv(GL_FOG_COLOR, this.fogColor1);
            glEnable(GL_LIGHTING);
            glEnable(GL_COLOR_MATERIAL);
            
            // Set ambient light level
            float brightness = 0.6F;
            glLightModelfv(GL_LIGHT_MODEL_AMBIENT, this.getBuffer(brightness, brightness, brightness, 1.0F));
        }
    }

    /**
     * Helper method to create a float buffer with the specified RGBA values.
     * 
     * @param r Red component (0.0-1.0)
     * @param g Green component (0.0-1.0)
     * @param b Blue component (0.0-1.0)
     * @param a Alpha component (0.0-1.0)
     * @return A FloatBuffer containing the RGBA values
     */
    private FloatBuffer getBuffer(float r, float g, float b, float a) {
        this.lightBuffer.clear();
        this.lightBuffer.put(r).put(g).put(b).put(a);
        this.lightBuffer.flip();
        return this.lightBuffer;
    }

    /**
     * Renders a single frame of the game.
     *
     * @param partialTick Interpolation factor between ticks (0.0-1.0)
     * @param hitResult The current hit result (block selection)
     * @param editMode The current edit mode (0 = destroy, 1 = place)
     * @param paintTexture The current block to place
     * @param fpsString String containing FPS information to display
     */
    public void render(float partialTick, HitResult hitResult, int editMode, int paintTexture, String fpsString) {
        // Set viewport to full window size
        glViewport(0, 0, this.width, this.height);
        
        // Clear the color and depth buffers
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        
        // Set up the 3D camera
        this.setupCamera(partialTick);
        
        // Enable face culling for performance
        glEnable(GL_CULL_FACE);
        
        // Create frustum for culling
        Frustum frustum = Frustum.getFrustum();
        
        // Update chunks that have changed
        this.levelRenderer.updateDirtyChunks(this.player);
        
        // Render lit parts of the level
        this.setupFog(0);
        glEnable(GL_FOG);
        this.levelRenderer.render(this.player, 0);

        // Render lit entities
        for (Entity entity : this.entities) {
            if (entity.isLit() && frustum.isVisible(entity.bb)) {
                entity.render(partialTick);
            }
        }
        
        // Render lit particles
        this.particleEngine.render(this.player, partialTick, 0);
        
        // Render unlit parts of the level
        this.setupFog(1);
        this.levelRenderer.render(this.player, 1);

        // Render unlit entities
        for (Entity entity : this.entities) {
            if (!entity.isLit() && frustum.isVisible(entity.bb)) {
                entity.render(partialTick);
            }
        }
        
        // Render unlit particles
        this.particleEngine.render(this.player, partialTick, 1);
        
        // Disable 3D rendering features
        glDisable(GL_LIGHTING);
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_FOG);
        
        // Render block selection highlight
        if (hitResult != null) {
            glDisable(GL_ALPHA_TEST);
            this.levelRenderer.renderHit(hitResult, editMode, paintTexture);
            glEnable(GL_ALPHA_TEST);
        }
        
        // Render 2D GUI elements
        this.drawGui(partialTick, paintTexture, fpsString);
    }

    /**
     * Draws the 2D GUI elements (HUD, crosshair, selected block).
     *
     * @param partialTick Interpolation factor between ticks (0.0-1.0)
     * @param paintTexture The current block to place
     * @param fpsString String containing FPS information to display
     */
    private void drawGui(float partialTick, int paintTexture, String fpsString) {
        // Use actual screen dimensions for the GUI
        int screenWidth = this.width * 240 / this.height;
        int screenHeight = this.height * 240 / this.height;

        // Clear depth buffer only
        glClear(GL_DEPTH_BUFFER_BIT);
        
        // Set up orthographic projection for 2D rendering
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0.0F, screenWidth, screenHeight, 0.0F, 100.0F, 300.0F);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        glTranslatef(0.0F, 0.0F, -200.0F);
        
        // Draw the selected block preview
        glPushMatrix();
        glTranslatef(screenWidth - 16, 16.0F, 0.0F);
        Tesselator t = Tesselator.instance;
        glScalef(16.0F, 16.0F, 16.0F);
        glRotatef(30.0F, 1.0F, 0.0F, 0.0F);
        glRotatef(45.0F, 0.0F, 1.0F, 0.0F);
        glTranslatef(-1.5F, 0.5F, -0.5F);
        glScalef(-1.0F, -1.0F, 1.0F);
        
        // Bind texture and render the selected block
        int textureId = this.textures.loadTexture("/terrain.png", GL_NEAREST);
        glBindTexture(GL_TEXTURE_2D, textureId);
        glEnable(GL_TEXTURE_2D);
        t.init();
        Tile.tiles[paintTexture].render(t, minecraft.level, 0, -2, 0, 0);
        t.flush();
        glDisable(GL_TEXTURE_2D);
        glPopMatrix();
        
        // Enable blending for text rendering
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // Draw version and FPS text
        this.font.drawShadow(Minecraft.VERSION_STRING, 2, 2, 0xFFFFFF);
        this.font.drawShadow(fpsString, 2, 12, 0xFFFFFF);

        // Disable blending after text rendering
        glDisable(GL_BLEND);
        
        // Draw crosshair
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;
        glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        t.init();
        
        // Vertical line
        t.vertex(centerX + 1, centerY - 4, 0.0F);
        t.vertex(centerX - 0, centerY - 4, 0.0F);
        t.vertex(centerX - 0, centerY + 5, 0.0F);
        t.vertex(centerX + 1, centerY + 5, 0.0F);
        
        // Horizontal line
        t.vertex(centerX + 5, centerY - 0, 0.0F);
        t.vertex(centerX - 4, centerY - 0, 0.0F);
        t.vertex(centerX - 4, centerY + 1, 0.0F);
        t.vertex(centerX + 5, centerY + 1, 0.0F);
        
        t.flush();
    }
} 