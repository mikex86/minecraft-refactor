package com.mojang.minecraft.renderer;

import com.mojang.minecraft.Minecraft;
import com.mojang.minecraft.entity.Entity;
import com.mojang.minecraft.entity.Player;
import com.mojang.minecraft.gui.Font;
import com.mojang.minecraft.level.Level;
import com.mojang.minecraft.level.LevelRenderer;
import com.mojang.minecraft.level.tile.Tile;
import com.mojang.minecraft.particle.ParticleEngine;
import com.mojang.minecraft.renderer.graphics.GraphicsAPI;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums;
import com.mojang.minecraft.renderer.graphics.GraphicsFactory;
import com.mojang.minecraft.renderer.graphics.Texture;
import com.mojang.minecraft.renderer.shader.ShaderRegistry;
import com.mojang.minecraft.renderer.shader.impl.*;
import com.mojang.minecraft.world.HitResult;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.util.List;

/**
 * Handles all rendering operations for Minecraft.
 * Extracted from the main Minecraft class to separate rendering concerns.
 */
public class GameRenderer implements Disposable {

    // Graphics context
    private final GraphicsAPI graphics;

    // Texture manager
    private final TextureManager textureManager;

    // Buffers for graphic operations
    private final FloatBuffer fogColor0;
    private final FloatBuffer fogColor1;

    private final WorldShader worldShader;
    private final ParticleShader particleShader;
    private final EntityShader entityShader;
    private final HudShader hudShader;
    private final HudNoTexShader hudNoTexShader;

    // Font renderer
    private Font font;

    // Game components
    private final Level level;
    private final LevelRenderer levelRenderer;
    private final ParticleEngine particleEngine;
    private final Player player;
    private final List<Entity> entities;

    // Window dimensions
    private int width;
    private int height;

    /**
     * Creates a new graphics renderer.
     *
     * @param textureManager The texture manager
     * @param shaderRegistry The shader registry
     * @param level          The level
     * @param levelRenderer  The level renderer
     * @param particleEngine The particle engine
     * @param player         The player
     * @param entities       The entity list
     * @param width          The initial window width
     * @param height         The initial window height
     */
    public GameRenderer(TextureManager textureManager, ShaderRegistry shaderRegistry,
                        Level level, LevelRenderer levelRenderer,
                        ParticleEngine particleEngine, Player player,
                        List<Entity> entities, int width, int height) {
        // Get graphics API instance
        this.graphics = GraphicsFactory.getGraphicsAPI();

        this.textureManager = textureManager;
        this.level = level;
        this.levelRenderer = levelRenderer;
        this.particleEngine = particleEngine;
        this.player = player;
        this.entities = entities;
        this.width = width;
        this.height = height;

        // Create game resources
        this.font = new Font("/default.gif", textureManager);


        // Create fog color buffers
        this.fogColor0 = BufferUtils.createFloatBuffer(4);
        this.fogColor1 = BufferUtils.createFloatBuffer(4);

        // Initialize fog colors
        this.fogColor0.clear();
        this.fogColor0.put(0.5F).put(0.8F).put(1.0F).put(1.0F);
        this.fogColor0.flip();

        this.fogColor1.clear();
        this.fogColor1.put(0.0F).put(0.0F).put(0.0F).put(1.0F);
        this.fogColor1.flip();

        this.worldShader = shaderRegistry.getWorldShader();
        this.particleShader = shaderRegistry.getParticleShader();
        this.entityShader = shaderRegistry.getEntityShader();
        this.hudShader = shaderRegistry.getHudShader();
        this.hudNoTexShader = shaderRegistry.getHudNoTexShader();
    }

    /**
     * Sets the window dimensions for proper viewport configuration.
     *
     * @param width  New window width
     * @param height New window height
     */
    public void setDimensions(int width, int height) {
        this.width = width;
        this.height = height;
    }

    /**
     * Sets up the perspective camera for 3D rendering.
     *
     * @param partialTick Interpolation factor between ticks (0.0-1.0)
     */
    private void setupCamera(float partialTick) {
        // Calculate aspect ratio
        float aspectRatio = (float) this.width / (float) this.height;

        // Set viewport
        graphics.setViewport(0, 0, this.width, this.height);

        // Set up projection matrix
        graphics.setPerspectiveProjection(70.0F, aspectRatio, 0.05F, 1000.0F);

        // Set up camera transformation
        graphics.setMatrixMode(GraphicsAPI.MatrixMode.MODELVIEW);
        graphics.loadIdentity();
        this.moveCameraToPlayer(partialTick);
    }

    /**
     * Positions the camera based on the player's position and orientation.
     *
     * @param partialTick Interpolation factor between ticks (0.0-1.0)
     */
    private void moveCameraToPlayer(float partialTick) {
        // Get interpolated player position
        float playerX = player.xo + (player.x - player.xo) * partialTick;
        float playerY = player.yo + (player.y - player.yo) * partialTick;
        float playerZ = player.zo + (player.z - player.zo) * partialTick;

        // Apply camera transforms
        graphics.translate(0.0F, 0.0F, -0.3F);  // Camera offset
        graphics.rotateX(player.xRot);          // Pitch
        graphics.rotateY(player.yRot);          // Yaw
        graphics.translate(-playerX, -playerY - player.getHeightOffset(), -playerZ);
    }

    /**
     * Renders a single frame of the game.
     *
     * @param partialTick  Interpolation factor between ticks (0.0-1.0)
     * @param hitResult    The current hit result (block selection)
     * @param editMode     The current edit mode (0 = destroy, 1 = place)
     * @param paintTexture The current block to place
     * @param fpsString    String containing FPS information to display
     */
    public void render(float partialTick, HitResult hitResult, int editMode,
                       int paintTexture, String fpsString) {
        // Set viewport and clear buffers
        graphics.setViewport(0, 0, this.width, this.height);
        graphics.clear(true, true, 0.5F, 0.8F, 1.0F, 0.0F);

        // Set up the 3D camera
        this.setupCamera(partialTick);

        // Enable face culling for performance
        graphics.setRasterizerState(GraphicsEnums.CullMode.BACK, GraphicsEnums.FillMode.SOLID);
        graphics.setDepthState(true, true, GraphicsEnums.CompareFunc.LESS);

        // Update chunks that have changed
        this.levelRenderer.updateDirtyChunks(this.player);

        renderLayer(true, partialTick);
        renderLayer(false, partialTick);

        // Render block selection highlight
        if (hitResult != null) {
            renderBlockOutline(hitResult);
        }

        // Render HUD elements
        {
            renderHud(graphics, fpsString, editMode, paintTexture);
        }
    }

    private void renderLayer(boolean lit, float partialTick) {
        int layer = lit ? 0 : 1;

        Frustum frustum = Frustum.getFrustum(graphics);

        // render level
        {
            graphics.setShader(worldShader);
            graphics.updateShaderMatrices(worldShader);
            setupFog(worldShader, layer);

            this.levelRenderer.render(layer);
        }

        // render entities
        {
            graphics.setShader(entityShader);
            // cannot set matrices "globally" because entities transform themselves
            setupFog(entityShader, layer);

            for (Entity entity : this.entities) {
                if ((entity.isLit() == lit) && frustum.isVisible(entity.bb)) {
                    entity.render(this.graphics, partialTick);
                }
            }
        }

        // render particles
        {
            graphics.setShader(particleShader);
            graphics.updateShaderMatrices(particleShader);
            setupFog(particleShader, layer);
            this.particleEngine.render(this.graphics, this.player, partialTick, layer);
        }
    }

    /**
     * Configures the fog settings for different rendering passes.
     *
     * @param fogShader the fog shader to configure
     * @param mode      0 for lit areas (day), 1 for unlit areas (night)
     */
    private void setupFog(FogShader fogShader, int mode) {
        if (mode == 0) {
            fogShader.setFogUniforms(true, GraphicsAPI.FogMode.EXP, 0.001F, 0.0F, 10.0F,
                    0.5F, 0.8F, 1.0F, 1.0F);

        } else if (mode == 1) {
            // Night fog (darker, closer)
            fogShader.setFogUniforms(true, GraphicsAPI.FogMode.EXP, 0.01F, 0.0F, 0.0F,
                    0.0F, 0.0F, 0.0F, 1.0F);
        }
    }

    /**
     * Renders an outline around the selected block.
     *
     * @param hitResult The hit result containing the block to highlight
     */
    private void renderBlockOutline(HitResult hitResult) {
        // Implementation would go here
    }

    /**
     * Renders the HUD (head-up display) elements.
     *
     * @param graphics     The graphics api
     * @param fpsString    The FPS string to display
     * @param editMode     The current edit mode
     * @param paintTexture The current block to place
     */
    private void renderHud(GraphicsAPI graphics, String fpsString, int editMode, int paintTexture) {
        graphics.setShader(hudShader);

        // disable depth test
        graphics.setDepthState(false, true, GraphicsEnums.CompareFunc.ALWAYS);

        int screenWidth = this.width * 240 / this.height;
        int screenHeight = this.height * 240 / this.height;

        graphics.clear(false, true, 0.0F, 0.0F, 0.0F, 0.0F);

        graphics.setOrthographicProjection(0.0F, screenWidth, screenHeight, 0.0F, 100.0F, 300.0F);
        graphics.setMatrixMode(GraphicsAPI.MatrixMode.MODELVIEW);
        graphics.loadIdentity();
        graphics.translate(0.0F, 0.0F, -200.0F);

        // Draw the selected block preview
        drawBlockPreview(graphics, screenWidth, screenHeight, paintTexture);

        // Render debug string
        graphics.setBlendState(true, GraphicsEnums.BlendFactor.SRC_ALPHA, GraphicsEnums.BlendFactor.ONE_MINUS_SRC_ALPHA);

        drawDebugText(graphics, fpsString);

        graphics.setBlendState(false, GraphicsEnums.BlendFactor.SRC_ALPHA, GraphicsEnums.BlendFactor.ONE_MINUS_SRC_ALPHA);

        graphics.setShader(hudNoTexShader);
        graphics.updateShaderMatrices(hudNoTexShader);

        // Draw cross-hair
        drawCrosshair(graphics, screenWidth, screenHeight);
    }

    private void drawDebugText(GraphicsAPI graphics, String fpsString) {
        graphics.updateShaderMatrices(hudShader);

        this.font.drawShadow(graphics, Minecraft.VERSION_STRING, 2, 2, 0xFFFFFF);
        this.font.drawShadow(graphics, fpsString, 2, 12, 0xFFFFFF);
    }

    private void drawBlockPreview(GraphicsAPI graphics, int screenWidth, int screenHeight, int paintTexture) {
        Tesselator t = Tesselator.instance;
        graphics.pushMatrix();
        graphics.translate(screenWidth - 16, 32, 0.0F);
        graphics.scale(16.0F, 16.0F, 16.0F);
        graphics.rotateX(30.0F);
        graphics.rotateY(45.0F);
        graphics.scale(-1.0F, -1.0F, 1.0F);

        graphics.updateShaderMatrices(hudShader);

        Texture texture = this.textureManager.loadTexture("/terrain.png", Texture.FilterMode.NEAREST);
        graphics.setTexture(texture);
        t.init();
        Tile.tiles[paintTexture].render(t, null, 0, 0, 0, 0);
        t.flush();
        graphics.popMatrix();
    }


    private void drawCrosshair(GraphicsAPI graphics, int screenWidth, int screenHeight) {
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;

        graphics.updateShaderMatrices(hudNoTexShader);

        Tesselator t = Tesselator.instance;
        t.init();
        t.color(1, 1, 1);

        // Vertical line
        t.vertex(centerX + 1, centerY - 4, 0.0F);
        t.vertex(centerX, centerY - 4, 0.0F);
        t.vertex(centerX, centerY + 5, 0.0F);
        t.vertex(centerX + 1, centerY + 5, 0.0F);

        // Horizontal line
        t.vertex(centerX + 5, centerY, 0.0F);
        t.vertex(centerX - 4, centerY, 0.0F);
        t.vertex(centerX - 4, centerY + 1, 0.0F);
        t.vertex(centerX + 5, centerY + 1, 0.0F);

        t.flush();
    }

    @Override
    public void dispose() {
        // Dispose of any resources if needed
    }
} 