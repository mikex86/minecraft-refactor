package com.mojang.minecraft;

import com.mojang.minecraft.character.Zombie;
import com.mojang.minecraft.gui.Font;
import com.mojang.minecraft.level.Chunk;
import com.mojang.minecraft.level.Level;
import com.mojang.minecraft.level.LevelRenderer;
import com.mojang.minecraft.level.tile.Tile;
import com.mojang.minecraft.particle.ParticleEngine;
import com.mojang.minecraft.phys.AABB;
import com.mojang.minecraft.renderer.Frustum;
import com.mojang.minecraft.renderer.Tesselator;
import com.mojang.minecraft.renderer.Textures;

import java.awt.Canvas;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import javax.swing.JOptionPane;

import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Cursor;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.util.glu.GLU;

import static org.lwjgl.opengl.GL11.*;

/**
 * Main game class for Minecraft Classic 0.0.11a.
 * Handles initialization, game loop, rendering, and input.
 */
public class Minecraft implements Runnable {
    // Constants
    public static final String VERSION_STRING = "0.0.11a";

    // Game configuration
    private boolean fullscreen = false;
    private int width;
    private int height;
    
    // Rendering resources
    private FloatBuffer fogColor0 = BufferUtils.createFloatBuffer(4);
    private FloatBuffer fogColor1 = BufferUtils.createFloatBuffer(4);
    private FloatBuffer lightBuffer = BufferUtils.createFloatBuffer(16);
    private IntBuffer viewportBuffer = BufferUtils.createIntBuffer(16);
    private IntBuffer selectBuffer = BufferUtils.createIntBuffer(2000);
    
    // Game state
    private Timer timer = new Timer(20.0F);
    private Level level;
    private LevelRenderer levelRenderer;
    private Player player;
    private ParticleEngine particleEngine;
    private ArrayList<Entity> entities = new ArrayList<>();
    private Canvas parent;
    
    // Game flags
    public boolean appletMode = false;
    public volatile boolean pause = false;
    private volatile boolean running = false;
    private boolean mouseGrabbed = false;
    
    // UI and input state
    private int paintTexture = 1;
    private Cursor emptyCursor;
    private int yMouseAxis = 1;  // Controls if mouse Y axis is inverted
    private int editMode = 0;    // 0 = destroy blocks, 1 = place blocks
    private String fpsString = "";
    private Font font;
    
    // Rendering resources
    public Textures textures;
    private HitResult hitResult = null;

    /**
     * Creates a new Minecraft game instance.
     *
     * @param parent Canvas to render on, or null for standalone window
     * @param width Width of the rendering area
     * @param height Height of the rendering area
     * @param fullscreen Whether to run in fullscreen mode
     */
    public Minecraft(Canvas parent, int width, int height, boolean fullscreen) {
        this.parent = parent;
        this.width = width;
        this.height = height;
        this.fullscreen = fullscreen;
        this.textures = new Textures();
    }

    /**
     * Initializes the game, setting up the display, OpenGL, and game objects.
     * 
     * @throws LWJGLException If LWJGL fails to initialize
     * @throws IOException If resource loading fails
     */
    public void init() throws LWJGLException, IOException {
        // Setup fog colors
        int skyColor = 16710650;  // Light blue
        int fogColor = 920330;    // Dark blue
        float red = 0.5F;
        float green = 0.8F;
        float blue = 1.0F;
        
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
        
        // Set up the display
        if (this.parent != null) {
            Display.setParent(this.parent);
        } else if (this.fullscreen) {
            Display.setFullscreen(true);
            this.width = Display.getDisplayMode().getWidth();
            this.height = Display.getDisplayMode().getHeight();
        } else {
            Display.setDisplayMode(new DisplayMode(this.width, this.height));
        }

        Display.setTitle("Minecraft " + VERSION_STRING);

        try {
            Display.create();
        } catch (LWJGLException e) {
            e.printStackTrace();

            try {
                Thread.sleep(1000L);
            } catch (InterruptedException var9) {
                // Ignore
            }

            Display.create();
        }

        // Initialize input devices
        Keyboard.create();
        Mouse.create();
        
        this.checkGlError("Pre startup");
        
        // Configure OpenGL state
        glEnable(GL_TEXTURE_2D);
        glShadeModel(GL_SMOOTH);
        glClearColor(red, green, blue, 0.0F);
        glClearDepth(1.0F);
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glEnable(GL_ALPHA_TEST);
        glAlphaFunc(GL_ALWAYS, 0.0F);
        
        // Initialize projection matrix
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glMatrixMode(GL_MODELVIEW);
        
        this.checkGlError("Startup");
        
        // Create game objects
        this.level = new Level(256, 256, 64);
        this.levelRenderer = new LevelRenderer(this.level, this.textures);
        this.player = new Player(this.level);
        this.particleEngine = new ParticleEngine(this.level, this.textures);
        this.font = new Font("/default.gif", this.textures);

        // Add some zombies to the level
        for (int i = 0; i < 10; ++i) {
            Zombie zombie = new Zombie(this.level, this.textures, 128.0F, 0.0F, 128.0F);
            zombie.resetPos();
            this.entities.add(zombie);
        }

        // Setup empty cursor for mouse grabbing
        IntBuffer imgData = BufferUtils.createIntBuffer(256);
        imgData.clear().limit(256);
        if (this.appletMode) {
            try {
                this.emptyCursor = new Cursor(16, 16, 0, 0, 1, imgData, null);
            } catch (LWJGLException e) {
                e.printStackTrace();
            }
        } else {
            this.grabMouse();
        }

        this.checkGlError("Post startup");
    }

    /**
     * Checks for OpenGL errors and exits if any are found.
     *
     * @param context Description of where in the code the check is happening
     */
    private void checkGlError(String context) {
        int errorCode = glGetError();
        if (errorCode != 0) {
            String errorString = GLU.gluErrorString(errorCode);
            System.out.println("########## GL ERROR ##########");
            System.out.println("@ " + context);
            System.out.println(errorCode + ": " + errorString);
            System.exit(0);
        }
    }

    /**
     * Cleans up resources and saves the level before shutting down.
     */
    public void destroy() {
        try {
            this.level.save();
        } catch (Exception e) {
            // Ignore save errors on shutdown
        }

        Mouse.destroy();
        Keyboard.destroy();
        Display.destroy();
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
                    if (this.parent == null && Display.isCloseRequested()) {
                        this.stop();
                    }

                    // Update timer and calculate ticks
                    this.timer.advanceTime();

                    // Process game ticks
                    for (int i = 0; i < this.timer.ticks; ++i) {
                        this.tick();
                    }

                    // Render the frame
                    this.checkGlError("Pre render");
                    this.render(this.timer.partialTick);
                    this.checkGlError("Post render");
                    
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
            if (this.appletMode) {
                try {
                    Mouse.setNativeCursor(this.emptyCursor);
                    Mouse.setCursorPosition(this.width / 2, this.height / 2);
                } catch (LWJGLException e) {
                    e.printStackTrace();
                }
            } else {
                Mouse.setGrabbed(true);
            }
        }
    }

    /**
     * Releases the mouse cursor, showing it and disabling mouse look.
     */
    public void releaseMouse() {
        if (this.mouseGrabbed) {
            this.mouseGrabbed = false;
            if (this.appletMode) {
                try {
                    Mouse.setNativeCursor(null);
                } catch (LWJGLException e) {
                    e.printStackTrace();
                }
            } else {
                Mouse.setGrabbed(false);
            }
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
        // Process all mouse events
        while (Mouse.next()) {
            if (!this.mouseGrabbed && Mouse.getEventButtonState()) {
                // Auto-grab mouse on click when not grabbed
                this.grabMouse();
            } else {
                // Handle left mouse button (destroy/place blocks)
                if (Mouse.getEventButton() == 0 && Mouse.getEventButtonState()) {
                    this.handleMouseClick();
                }

                // Handle right mouse button (toggle edit mode)
                if (Mouse.getEventButton() == 1 && Mouse.getEventButtonState()) {
                    this.editMode = (this.editMode + 1) % 2;
                }
            }
        }

        // Process all keyboard events
        while (Keyboard.next()) {
            if (Keyboard.getEventKeyState()) {
                // Escape key - release mouse in windowed/applet mode
                if (Keyboard.getEventKey() == Keyboard.KEY_ESCAPE && (this.appletMode || !this.fullscreen)) {
                    this.releaseMouse();
                }

                // Enter key - save level
                if (Keyboard.getEventKey() == Keyboard.KEY_RETURN) {
                    this.level.save();
                }

                // Block selection keys
                if (Keyboard.getEventKey() == Keyboard.KEY_1) {
                    this.paintTexture = 1;  // Stone
                }
                if (Keyboard.getEventKey() == Keyboard.KEY_2) {
                    this.paintTexture = 3;  // Dirt
                }
                if (Keyboard.getEventKey() == Keyboard.KEY_3) {
                    this.paintTexture = 4;  // Cobblestone
                }
                if (Keyboard.getEventKey() == Keyboard.KEY_4) {
                    this.paintTexture = 5;  // Wooden planks
                }
                if (Keyboard.getEventKey() == Keyboard.KEY_5) {
                    this.paintTexture = 6;  // Sapling
                }

                // Y key - invert mouse Y axis
                if (Keyboard.getEventKey() == Keyboard.KEY_Y) {
                    this.yMouseAxis *= -1;
                }

                // G key - spawn zombie
                if (Keyboard.getEventKey() == Keyboard.KEY_G) {
                    this.entities.add(new Zombie(this.level, this.textures, 
                        this.player.x, this.player.y, this.player.z));
                }
            }
        }

        // Update the level
        this.level.tick();
        
        // Update particles
        this.particleEngine.tick();

        // Update all entities
        for (int i = 0; i < this.entities.size(); ++i) {
            Entity entity = this.entities.get(i);
            entity.tick();
            
            // Remove entities marked for removal
            if (entity.removed) {
                this.entities.remove(i--);
            }
        }

        // Update the player
        this.player.tick();
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
        GLU.gluPerspective(70.0F, (float) this.width / (float) this.height, 0.05F, 1000.0F);
        
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
        glGetInteger(GL_VIEWPORT, this.viewportBuffer);
        this.viewportBuffer.flip();
        this.viewportBuffer.limit(16);
        
        // Create picking matrix centered at the specified point
        GLU.gluPickMatrix((float) x, (float) y, 5.0F, 5.0F, this.viewportBuffer);
        GLU.gluPerspective(70.0F, (float) this.width / (float) this.height, 0.05F, 1000.0F);
        
        // Set up camera transformation
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        this.moveCameraToPlayer(partialTick);
    }

    /**
     * Performs picking to detect which block the player is looking at.
     *
     * @param partialTick Interpolation factor between ticks (0.0-1.0)
     */
    private void pick(float partialTick) {
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
            long minZ = (long) this.selectBuffer.get();
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
            this.hitResult = new HitResult(names[0], names[1], names[2], names[3], names[4]);
        } else {
            this.hitResult = null;
        }
    }

    /**
     * Renders a single frame of the game.
     *
     * @param partialTick Interpolation factor between ticks (0.0-1.0)
     */
    public void render(float partialTick) {
        // Release mouse if window loses focus
        if (!Display.isActive()) {
            this.releaseMouse();
        }

        // Set viewport to full window
        glViewport(0, 0, this.width, this.height);
        
        // Handle mouse look if mouse is grabbed
        if (this.mouseGrabbed) {
            float mouseX = 0.0F;
            float mouseY = 0.0F;
            
            // Get mouse movement
            mouseX = (float) Mouse.getDX();
            mouseY = (float) Mouse.getDY();
            
            // Special handling for applet mode
            if (this.appletMode) {
                Display.processMessages();
                Mouse.poll();
                mouseX = (float) (Mouse.getX() - this.width / 2);
                mouseY = (float) (Mouse.getY() - this.height / 2);
                Mouse.setCursorPosition(this.width / 2, this.height / 2);
            }

            // Apply mouse movement to player rotation
            this.player.turn(mouseX, mouseY * (float) this.yMouseAxis);
        }

        // Setup for rendering
        this.checkGlError("Set viewport");
        this.pick(partialTick);
        this.checkGlError("Picked");
        
        // Clear the color and depth buffers
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        
        // Set up the 3D camera
        this.setupCamera(partialTick);
        this.checkGlError("Set up camera");
        
        // Enable face culling for performance
        glEnable(GL_CULL_FACE);
        
        // Create frustum for culling
        Frustum frustum = Frustum.getFrustum();
        
        // Update chunks that have changed
        this.levelRenderer.updateDirtyChunks(this.player);
        this.checkGlError("Update chunks");
        
        // Render lit parts of the level
        this.setupFog(0);
        glEnable(GL_FOG);
        this.levelRenderer.render(this.player, 0);
        this.checkGlError("Rendered level");

        // Render lit entities
        for (int i = 0; i < this.entities.size(); ++i) {
            Entity entity = this.entities.get(i);
            if (entity.isLit() && frustum.isVisible(entity.bb)) {
                entity.render(partialTick);
            }
        }
        this.checkGlError("Rendered entities");
        
        // Render lit particles
        this.particleEngine.render(this.player, partialTick, 0);
        this.checkGlError("Rendered particles");
        
        // Render unlit parts of the level
        this.setupFog(1);
        this.levelRenderer.render(this.player, 1);

        // Render unlit entities
        for (int i = 0; i < this.entities.size(); ++i) {
            Entity entity = this.entities.get(i);
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
        this.checkGlError("Rendered rest");
        
        // Render block selection highlight
        if (this.hitResult != null) {
            glDisable(GL_ALPHA_TEST);
            this.levelRenderer.renderHit(this.hitResult, this.editMode, this.paintTexture);
            glEnable(GL_ALPHA_TEST);
        }
        this.checkGlError("Rendered hit");
        
        // Render 2D GUI elements
        this.drawGui(partialTick);
        this.checkGlError("Rendered gui");
        
        // Swap buffers
        Display.update();
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
            glFog(GL_FOG_COLOR, this.fogColor0);
            glDisable(GL_LIGHTING);
        } else if (mode == 1) {
            // Night fog (darker, closer)
            glFogi(GL_FOG_MODE, GL_EXP);
            glFogf(GL_FOG_DENSITY, 0.01F);
            glFog(GL_FOG_COLOR, this.fogColor1);
            glEnable(GL_LIGHTING);
            glEnable(GL_COLOR_MATERIAL);
            
            // Set ambient light level
            float brightness = 0.6F;
            glLightModel(GL_LIGHT_MODEL_AMBIENT, this.getBuffer(brightness, brightness, brightness, 1.0F));
        }
    }

    /**
     * Draws the 2D GUI elements (HUD, crosshair, selected block).
     *
     * @param partialTick Interpolation factor between ticks (0.0-1.0)
     */
    private void drawGui(float partialTick) {
        // Calculate scaled screen dimensions that maintain aspect ratio
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
        this.checkGlError("GUI: Init");
        
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
        int textureId = this.textures.loadTexture("/terrain.png", 9728);
        glBindTexture(GL_TEXTURE_2D, textureId);
        glEnable(GL_TEXTURE_2D);
        t.init();
        Tile.tiles[this.paintTexture].render(t, this.level, 0, -2, 0, 0);
        t.flush();
        glDisable(GL_TEXTURE_2D);
        glPopMatrix();
        this.checkGlError("GUI: Draw selected");
        
        // Enable blending for text rendering
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        // Draw version and FPS text
        this.font.drawShadow(VERSION_STRING, 2, 2, 0xFFFFFF);
        this.font.drawShadow(this.fpsString, 2, 12, 0xFFFFFF);
        
        // Disable blending after text rendering
        glDisable(GL_BLEND);
        
        this.checkGlError("GUI: Draw text");
        
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
        this.checkGlError("GUI: Draw crosshair");
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
     * Static utility method to check for OpenGL errors and throw an exception if found.
     * Used for debugging.
     */
    public static void checkError() {
        int errorCode = glGetError();
        if (errorCode != 0) {
            throw new IllegalStateException(GLU.gluErrorString(errorCode));
        }
    }

    /**
     * Main entry point for the standalone application.
     * 
     * @param args Command line arguments (not used)
     * @throws LWJGLException If LWJGL fails to initialize
     */
    public static void main(String[] args) throws LWJGLException {
        Minecraft minecraft = new Minecraft(null, 854, 480, false);
        (new Thread(minecraft)).start();
    }
}
