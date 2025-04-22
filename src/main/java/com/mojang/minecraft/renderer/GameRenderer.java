package com.mojang.minecraft.renderer;

import com.mojang.minecraft.Minecraft;
import com.mojang.minecraft.entity.Entity;
import com.mojang.minecraft.entity.Player;
import com.mojang.minecraft.gui.Font;
import com.mojang.minecraft.level.Level;
import com.mojang.minecraft.level.LevelRenderer;
import com.mojang.minecraft.level.block.Block;
import com.mojang.minecraft.level.block.EnumFacing;
import com.mojang.minecraft.particle.ParticleEngine;
import com.mojang.minecraft.renderer.graphics.*;
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

    private final int renderDistance = 8; // Render distance in chunks

    // Buffers for graphic operations
    private final FloatBuffer fogColor0;
    private final FloatBuffer fogColor1;

    private final WorldShader worldShader;
    private final ParticleShader particleShader;
    private final EntityShader entityShader;
    private final HudShader hudShader;
    private final HudNoTexShader hudNoTexShader;

    // Font renderer
    private final Font font;

    // Game components
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

        // cross-hair mesh needs to be recreated
        this.crosshairMesh.dispose();
        this.crosshairMesh = null;
    }

    /**
     * Sets up the perspective camera for 3D rendering.
     *
     * @param partialTick Interpolation factor between ticks (0.0-1.0)
     */
    private void setupCamera(float partialTick) {
        // Calculate aspect ratio
        float aspectRatio = (float) (this.width) / this.height;

        // Set viewport
        graphics.setViewport(0, 0, this.width, this.height);

        // Set up projection matrix
        graphics.setPerspectiveProjection(70.0F * player.getInterpolatedFOV(partialTick), aspectRatio, 0.05F, 1000.0F);

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
        graphics.rotateX(player.xRot);          // Pitch
        graphics.rotateY(player.yRot);          // Yaw
        graphics.translate(-playerX, -playerY - player.getInterpolatedEyeHeight(partialTick), -playerZ);
    }

    /**
     * Renders a single frame of the game.
     *
     * @param partialTick Interpolation factor between ticks (0.0-1.0)
     * @param hitResult   The current hit result (block selection)
     * @param placeBlock  The current block to place
     * @param fpsString   String containing FPS information to display
     */
    public void render(float partialTick, HitResult hitResult, Block placeBlock, String fpsString) {
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

        render(partialTick);

        // Render block selection highlight
        if (hitResult != null) {
            renderBlockOutline(hitResult);
        }

        // Render HUD elements
        {
            renderHud(graphics, fpsString, placeBlock);
        }
    }

    private void render(float partialTick) {
        Frustum frustum = Frustum.getFrustum(graphics);

        // generate chunks around the player
        // NOTE: This will be a NO-OP most of the time
        // as it checks if the player has moved.
        // Most of the time this is just an int abs if with a return,
        // so not expensive.
        // A ticked frame is already more expensive than a regular one,
        // so we don't want to add world gen on top of that.
        // That should hit a different frame.
        player.loadAndUnloadChunksAroundPlayer(renderDistance);

        // render level
        {
            graphics.setShader(worldShader);
            graphics.updateShaderMatrices();
            setupFog(worldShader);

            this.levelRenderer.render();
        }

        // render entities
        {
            graphics.setShader(entityShader);
            // cannot set matrices "globally" because entities transform themselves
            setupFog(entityShader);

            for (Entity entity : this.entities) {
                if (frustum.isVisible(entity.bb)) {
                    entity.render(this.graphics, partialTick);
                }
            }
        }

        // render particles
        {
            graphics.setShader(particleShader);
            graphics.updateShaderMatrices();
            setupFog(particleShader);
            this.particleEngine.render(this.graphics, this.player, partialTick);
        }
    }

    private void setupFog(FogShader fogShader) {
        fogShader.setFogUniforms(true, GraphicsAPI.FogMode.EXP, 0.001F, 0.0F, 10.0F,
                0.5F, 0.8F, 1.0F, 1.0F);

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
     * @param graphics   The graphics api
     * @param fpsString  The FPS string to display
     * @param placeBlock The current block to place
     */
    private void renderHud(GraphicsAPI graphics, String fpsString, Block placeBlock) {
        graphics.setShader(hudShader);

        // disable depth test
        graphics.setDepthState(false, true, GraphicsEnums.CompareFunc.ALWAYS);

        float screenWidth = (this.width * 240f) / (float) this.height;
        float screenHeight = (this.height * 240f) / (float) this.height;

        graphics.clear(false, true, 0.0F, 0.0F, 0.0F, 0.0F);

        graphics.setOrthographicProjection(0.0F, screenWidth, screenHeight, 0.0F, 100.0F, 300.0F);
        graphics.setMatrixMode(GraphicsAPI.MatrixMode.MODELVIEW);
        graphics.loadIdentity();
        graphics.translate(0.0F, 0.0F, -200.0F);

        // Draw the selected block preview
        drawBlockPreview(graphics, screenWidth, placeBlock);

        // Render debug string
        graphics.setBlendState(true, GraphicsEnums.BlendFactor.SRC_ALPHA, GraphicsEnums.BlendFactor.ONE_MINUS_SRC_ALPHA);
        drawDebugText(graphics, fpsString);

        graphics.setBlendState(false, GraphicsEnums.BlendFactor.SRC_ALPHA, GraphicsEnums.BlendFactor.ONE_MINUS_SRC_ALPHA);

        graphics.setShader(hudNoTexShader);
        graphics.updateShaderMatrices();

        // Draw cross-hair
        drawCrosshair(graphics, screenWidth, screenHeight);
    }

    private void drawDebugText(GraphicsAPI graphics, String fpsString) {
        graphics.updateShaderMatrices();

        this.font.drawShadow(graphics, Minecraft.VERSION_STRING, 2, 2, 0xFFFFFF);
        this.font.drawShadow(graphics, fpsString, 2, 12, 0xFFFFFF);
        this.font.drawShadow(graphics, "x: " + player.x + ", y: " + player.y + ", z: " + player.z, 2, 22, 0xFFFFFF);
    }

    private void drawBlockPreview(GraphicsAPI graphics, float screenWidth, Block placeBlock) {
        Tesselator t = Tesselator.instance;
        graphics.pushMatrix();
        graphics.translate(screenWidth - 16, 32, 0.0F);
        graphics.scale(16.0F, 16.0F, 16.0F);
        graphics.rotateX(30.0F);
        graphics.rotateY(45.0F);
        graphics.scale(-1.0F, -1.0F, 1.0F);

        graphics.updateShaderMatrices();

        graphics.setTexture(textureManager.terrainTexture);
        t.init();
        placeBlock.render(t, null, 0, 0, 0, EnumFacing.UP);
        t.flush();
        graphics.popMatrix();
    }

    private IndexedMesh crosshairMesh;

    private void drawCrosshair(GraphicsAPI graphics, float screenWidth, float screenHeight) {
        float centerX = screenWidth / 2f;
        float centerY = screenHeight / 2f;

        graphics.updateShaderMatrices();

        if (crosshairMesh == null) {
            Tesselator t = new Tesselator();
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

            crosshairMesh = t.createIndexedMesh(GraphicsEnums.BufferUsage.STATIC);
        }

        crosshairMesh.draw(graphics);
    }

    @Override
    public void dispose() {
        // Dispose of any resources if needed
        if (crosshairMesh != null) {
            crosshairMesh.dispose();
            crosshairMesh = null;
        }
    }
} 