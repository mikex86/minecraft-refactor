package com.mojang.minecraft.renderer;

import com.mojang.minecraft.Minecraft;
import com.mojang.minecraft.entity.EntityPlayer;
import com.mojang.minecraft.gui.Font;
import com.mojang.minecraft.gui.TextLabel;
import com.mojang.minecraft.gui.scaling.ScaledResolution;
import com.mojang.minecraft.gui.screen.GuiScreen;
import com.mojang.minecraft.gui.screen.InventoryScreen;
import com.mojang.minecraft.input.GameInputHandler;
import com.mojang.minecraft.item.BlockItem;
import com.mojang.minecraft.item.Item;
import com.mojang.minecraft.item.ItemStack;
import com.mojang.minecraft.level.Level;
import com.mojang.minecraft.level.LevelRenderer;
import com.mojang.minecraft.level.block.Block;
import com.mojang.minecraft.optim.pools.StackCountStringPool;
import com.mojang.minecraft.particle.ParticleEngine;
import com.mojang.minecraft.renderer.block.BlockRenderer;
import com.mojang.minecraft.renderer.graphics.GraphicsAPI;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums;
import com.mojang.minecraft.renderer.graphics.GraphicsFactory;
import com.mojang.minecraft.renderer.graphics.IndexedMesh;
import com.mojang.minecraft.renderer.shader.ShaderRegistry;
import com.mojang.minecraft.renderer.shader.impl.*;
import com.mojang.minecraft.world.HitResult;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

/**
 * Handles all rendering operations for Minecraft.
 * Extracted from the main Minecraft class to separate rendering concerns.
 */
public class GameRenderer implements Disposable {

    // Graphics context
    private final GraphicsAPI graphics;

    // Texture manager
    private final TextureManager textureManager;

    private final int renderDistance = 16; // Render distance in chunks

    // Buffers for graphic operations
    private final FloatBuffer fogColor0;
    private final FloatBuffer fogColor1;

    private final WorldShader worldShader;
    private final ParticleShader particleShader;
    private final EntityShader entityShader;
    private final HudShader hudShader;
    private final HudNoTexShader hudNoTexShader;
    private final OutlineShader outlineShader;

    // Font renderer
    private final Font font;

    // Game components
    private final LevelRenderer levelRenderer;
    private final ParticleEngine particleEngine;
    private final GameInputHandler gameInputHandler;
    private final EntityPlayer player;

    // Window dimensions
    private int width;
    private int height;

    // The current gui screen (if any)
    private GuiScreen currentScreen;

    private final TextLabel versionStringLabel;
    private final TextLabel fpsStringLabel;
    private final TextLabel positionStringLabel;
    private final TextLabel memoryStringLabel1;
    private final TextLabel memoryStringLabel2;
    private final TextLabel memoryStringLabel3;
    private final String[] debugStrings = new String[5];

    /**
     * Creates a new graphics renderer.
     *
     * @param textureManager   The texture manager
     * @param shaderRegistry   The shader registry
     * @param gameInputHandler The game input handler
     * @param level            The level
     * @param levelRenderer    The level renderer
     * @param particleEngine   The particle engine
     * @param player           The player
     * @param width            The initial window width
     * @param height           The initial window height
     */
    public GameRenderer(TextureManager textureManager, ShaderRegistry shaderRegistry, GameInputHandler gameInputHandler,
                        Level level, LevelRenderer levelRenderer,
                        ParticleEngine particleEngine, EntityPlayer player, int width, int height) {
        // Get graphics API instance
        this.graphics = GraphicsFactory.getGraphicsAPI();

        this.textureManager = textureManager;
        this.levelRenderer = levelRenderer;
        this.gameInputHandler = gameInputHandler;
        this.particleEngine = particleEngine;
        this.player = player;
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
        this.outlineShader = shaderRegistry.getOutlineShader();

        this.versionStringLabel = new TextLabel(font, 0xFFFFFF, true);
        this.fpsStringLabel = new TextLabel(font, 0xFFFFFF, true);
        this.positionStringLabel = new TextLabel(font, 0xFFFFFF, true);
        this.memoryStringLabel1 = new TextLabel(font, 0xFFFFFF, true);
        this.memoryStringLabel2 = new TextLabel(font, 0xFFFFFF, true);
        this.memoryStringLabel3 = new TextLabel(font, 0xFFFFFF, true);
    }

    /**
     * Sets the window dimensions for proper viewport configuration.
     *
     * @param width  New window width
     * @param height New window height
     */
    public void setScreenSize(int width, int height) {
        this.width = width;
        this.height = height;

        // cross-hair mesh needs to be recreated
        if (this.crosshairMesh != null) {
            this.crosshairMesh.dispose();
            this.crosshairMesh = null;
        }

        // hotbar mesh needs to be recreated
        if (this.hotbarMesh != null) {
            this.hotbarMesh.dispose();
            this.hotbarMesh = null;
        }

        // notify current screen of resize if it exists
        if (this.currentScreen != null) {
            float scaledWidth = ScaledResolution.getScaledWidth(width, height);
            float scaledHeight = ScaledResolution.getScaledHeight(height);
            this.currentScreen.onResized(scaledWidth, scaledHeight);
        }
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
        graphics.setPerspectiveProjection(70.0F * player.getInterpolatedFOV(partialTick), aspectRatio, 0.05F, 4096.0F);

        // Set up camera transformation
        graphics.setMatrixMode(GraphicsAPI.MatrixMode.MODELVIEW);
        graphics.loadIdentity();

        this.bobView(player, partialTick);
        this.moveCameraToPlayer(partialTick);
    }

    private void bobView(EntityPlayer player, float partialTicks) {
        float walkDelta = player.distanceWalked - player.prevDistanceWalked;
        float h = -(player.distanceWalked + walkDelta * partialTicks);
        float bobAmt = player.prevBob + (player.bob - player.prevBob) * partialTicks;
        float sin = (float) Math.sin(h * Math.PI);
        float cos = (float) Math.cos(h * Math.PI);

        // vanilla translate
        graphics.translate(
                sin * bobAmt * 0.5F,
                -Math.abs(cos * bobAmt),
                0.0F
        );
        // lean left/right (Z) and tilt forward/back (X) as vanilla
        graphics.rotateZ(sin * bobAmt * 3.0F);
        graphics.rotateX(Math.abs((float) Math.cos(h * Math.PI - 0.2F) * bobAmt) * 5.0F);
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
        graphics.rotateX(player.cameraPitch);        // Pitch
        graphics.rotateY(player.cameraYaw);          // Yaw

        graphics.translate(-playerX, -playerY - player.getInterpolatedEyeHeight(partialTick), -playerZ);
    }

    /**
     * Renders a single frame of the game.
     *
     * @param partialTicks Interpolation factor between ticks (0.0-1.0)
     * @param hitResult    The current hit result (block selection)
     */
    public void render(float partialTicks, HitResult hitResult) {
        // Set viewport and clear buffers
        graphics.setViewport(0, 0, this.width, this.height);
        graphics.clear(true, true, 0.5F, 0.8F, 1.0F, 0.0F);

        // Set up the 3D camera
        this.setupCamera(partialTicks);

        // Enable face culling for performance
        graphics.setRasterizerState(GraphicsEnums.CullMode.BACK, GraphicsEnums.FillMode.SOLID);
        graphics.setDepthState(true, true, GraphicsEnums.CompareFunc.LESS_EQUAL);

        // Update chunks that have changed
        this.levelRenderer.updateDirtyChunks(this.player);

        render(partialTicks);

        // Render block selection highlight
        if (hitResult != null) {
            renderBlockOutline(hitResult);
        }

        // Render held block in 3D with view bobbing
        renderHeldItem(partialTicks);

        // Render HUD elements
        {
            drawUI(graphics, debugStrings, gameInputHandler, partialTicks);
        }
    }


    /**
     * Renders the currently held block in first-person view with view bobbing.
     *
     * @param partialTicks interpolation factor between ticks
     */
    private void renderHeldItem(float partialTicks) {
        float aspectRatio = (float) (this.width) / this.height;
        int hotbarItem = gameInputHandler.getHotbarSlotIndex();
        ItemStack itemStack = player.getInventory().getHotbarItem(hotbarItem);
        if (itemStack == null) {
            return;
        }

        // Calculate aspect ratio
        graphics.setShader(worldShader);
        graphics.setTexture(textureManager.terrainTexture);

        float handFov = 70.0F * player.getInterpolatedFOV(partialTicks);
        graphics.setPerspectiveProjection(handFov, aspectRatio, 0.05F, 4096.0F);
        graphics.setMatrixMode(GraphicsAPI.MatrixMode.MODELVIEW);
        graphics.pushMatrix();
        try {
            graphics.loadIdentity();

            graphics.clear(false, true, 0.0F, 0.0F, 0.0F, 0.0F);
            graphics.setDepthState(false, true, GraphicsEnums.CompareFunc.ALWAYS);

            // In vanilla the hurt tilt is applied before bobbing. The clone currently has no hurt tilt data.
            bobView(player, partialTicks);
            applyViewDriftCompensation(partialTicks);

            float swingProgress = player.getSwingProgress(partialTicks);
            float swingSqrt = (float) Math.sqrt(swingProgress);
            float handSign = 1.0F; // clone is right-handed only

            // Swing translation
            float swingSin = (float) Math.sin(swingSqrt * Math.PI);
            float swingSinFull = (float) Math.sin(swingProgress * Math.PI);
            float swingSinDouble = (float) Math.sin(swingSqrt * (Math.PI * 2.0F));
            graphics.translate(handSign * -0.4F * swingSin, 0.2F * swingSinDouble, -0.2F * swingSinFull);

            // Base hand placement with equip progress offset
            float equipProgress = getMainHandEquipProgress(partialTicks);
            graphics.translate(handSign * 0.56F, -0.52F + equipProgress * -0.6F, -0.72F);

            // Attack rotations (match vanilla order so swing-driven rotation composes correctly)
            float swingCurve = (float) Math.sin(swingProgress * swingProgress * Math.PI);
            float swingCurveSqrt = (float) Math.sin(swingSqrt * Math.PI);
            graphics.rotateY(handSign * 45.0F);
            graphics.rotateY(handSign * swingCurve * -20.0F);
            graphics.rotateZ(handSign * swingCurveSqrt * -20.0F);
            graphics.rotateX(swingCurveSqrt * -80.0F);
            graphics.rotateY(handSign * -45.0F);

            graphics.pushMatrix();
            try {
                applyBlockFirstPersonTransform(handSign);
                graphics.updateShaderMatrices();
                Item item = itemStack.getItem();
                if (item instanceof BlockItem) {
                    BlockItem blockItem = (BlockItem) item;
                    Block block = blockItem.getBlock();
                    BlockRenderer.getBlockMesh(block).draw(graphics);
                }
            } finally {
                graphics.popMatrix();
            }
        } finally {
            graphics.popMatrix();
        }
        graphics.setDepthState(true, true, GraphicsEnums.CompareFunc.LESS_EQUAL);
    }

    private void applyBlockFirstPersonTransform(float handSign) {
        // Mirrors the default block first-person item transform from block/block.json
        graphics.rotateY(handSign * 45.0F);
        graphics.scale(0.4F, 0.4F, 0.4F);
        graphics.translate(-0.5F, -0.5F, -0.5F);
    }

    private void applyViewDriftCompensation(float partialTicks) {
        float pitch = lerp(partialTicks, player.prevPitch, player.pitch);
        float yaw = lerp(partialTicks, player.prevYaw, player.yaw);
        float smoothedPitch = lerp(partialTicks, player.prevViewPitchBob, player.viewPitchBob);
        float smoothedYaw = lerp(partialTicks, player.prevViewYawBob, player.viewYawBob);

        graphics.rotateX((pitch - smoothedPitch) * 0.1F);
        graphics.rotateY((yaw - smoothedYaw) * 0.1F);
    }

    private float getMainHandEquipProgress(float partialTicks) {
        return 0.0F;
    }

    private static float lerp(float delta, float start, float end) {
        return start + (end - start) * delta;
    }
    public void setDebugString(int index, String memoryString) {
        this.debugStrings[index] = memoryString;
    }

    private void render(float partialTicks) {
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

            this.levelRenderer.render(partialTicks);
        }

        // render entities
        {
            graphics.setShader(entityShader);
            // cannot set matrices "globally" because entities transform themselves
            setupFog(entityShader);

            this.levelRenderer.renderEntities(partialTicks);
        }

        // render particles
        {
            graphics.setShader(particleShader);
            graphics.updateShaderMatrices();
            this.particleEngine.render(this.graphics, this.player, partialTicks);
        }
    }

    private void setupFog(FogShader fogShader) {
        fogShader.setFogUniforms(0.001F, 0.0F, 10.0F,
                0.5F, 0.8F, 1.0F, 1.0F);

    }

    private IndexedMesh blockOutlineMesh;

    /**
     * Renders an outline around the selected block.
     *
     * @param hitResult The hit result containing the block to highlight
     */
    private void renderBlockOutline(HitResult hitResult) {
        if (blockOutlineMesh == null) {
            Tesselator t = Tesselator.instance;
            t.init();

            final float width = 0.003F;
            final float eps = 0.00F;

            // Edges along X axis
            // (0,0,0)-(1,0,0)
            t.vertex(0 - eps, -width - eps, 0 - eps);
            t.vertex(0 - eps, 0 - eps, -width - eps);
            t.vertex(1 + eps, 0 - eps, -width - eps);
            t.vertex(1 + eps, -width - eps, 0 - eps);
            // (0,1,0)-(1,1,0)
            t.vertex(0 - eps, 1 + width + eps, 0 - eps);
            t.vertex(1 + eps, 1 + width + eps, 0 - eps);
            t.vertex(1 + eps, 1 + eps, -width - eps);
            t.vertex(0 - eps, 1 + eps, -width - eps);
            // (0,1,1)-(1,1,1)
            t.vertex(0 - eps, 1 + width + eps, 1 + eps);
            t.vertex(1 + eps, 1 + width + eps, 1 + eps);
            t.vertex(1 + eps, 1 + eps, 1 + width + eps);
            t.vertex(0 - eps, 1 + eps, 1 + width + eps);
            // (0,0,1)-(1,0,1)
            t.vertex(0 - eps, -width - eps, 1 + eps);
            t.vertex(1 + eps, -width - eps, 1 + eps);
            t.vertex(1 + eps, 0 - eps, 1 + width + eps);
            t.vertex(0 - eps, 0 - eps, 1 + width + eps);

            // Edges along Y axis
            // (0,0,0)-(0,1,0)
            t.vertex(-width - eps, 0 - eps, 0 - eps);
            t.vertex(0 - eps, 0 - eps, -width - eps);
            t.vertex(0 - eps, 1 + eps, -width - eps);
            t.vertex(-width - eps, 1 + eps, 0 - eps);
            // (1,0,0)-(1,1,0)
            t.vertex(1 + width + eps, 0 - eps, 0 - eps);
            t.vertex(1 + eps, 0 - eps, -width - eps);
            t.vertex(1 + eps, 1 + eps, -width - eps);
            t.vertex(1 + width + eps, 1 + eps, 0 - eps);
            // (1,0,1)-(1,1,1)
            t.vertex(1 + width + eps, 0 - eps, 1 + eps);
            t.vertex(1 + eps, 0 - eps, 1 + width + eps);
            t.vertex(1 + eps, 1 + eps, 1 + width + eps);
            t.vertex(1 + width + eps, 1 + eps, 1 + eps);
            // (0,0,1)-(0,1,1)
            t.vertex(-width - eps, 0 - eps, 1 + eps);
            t.vertex(0 - eps, 0 - eps, 1 + width + eps);
            t.vertex(0 - eps, 1 + eps, 1 + width + eps);
            t.vertex(-width - eps, 1 + eps, 1 + eps);

            // Edges along Z axis
            // (0,0,0)-(0,0,1)
            t.vertex(-width - eps, 0 - eps, 0 - eps);
            t.vertex(0 - eps, -width - eps, 0 - eps);
            t.vertex(0 - eps, -width - eps, 1 + eps);
            t.vertex(-width - eps, 0 - eps, 1 + eps);
            // (1,0,0)-(1,0,1)
            t.vertex(1 + width + eps, 0 - eps, 0 - eps);
            t.vertex(1 + eps, -width - eps, 0 - eps);
            t.vertex(1 + eps, -width - eps, 1 + eps);
            t.vertex(1 + width + eps, 0 - eps, 1 + eps);
            // (1,1,0)-(1,1,1)
            t.vertex(1 + width + eps, 1 + eps, 0 - eps);
            t.vertex(1 + eps, 1 + width + eps, 0 - eps);
            t.vertex(1 + eps, 1 + width + eps, 1 + eps);
            t.vertex(1 + width + eps, 1 + eps, 1 + eps);
            // (0,1,0)-(0,1,1)
            t.vertex(-width - eps, 1 + eps, 0 - eps);
            t.vertex(0 - eps, 1 + width + eps, 0 - eps);
            t.vertex(0 - eps, 1 + width + eps, 1 + eps);
            t.vertex(-width - eps, 1 + eps, 1 + eps);

            blockOutlineMesh = t.createIndexedMesh(GraphicsEnums.BufferUsage.STATIC);
        }

        graphics.pushMatrix();
        graphics.translate(hitResult.x, hitResult.y, hitResult.z);

        graphics.setShader(outlineShader);
        graphics.updateShaderMatrices();

        graphics.setRasterizerState(GraphicsEnums.CullMode.NONE, GraphicsEnums.FillMode.SOLID);
        graphics.setDepthState(true, true, GraphicsEnums.CompareFunc.LESS_EQUAL);

        graphics.setBlendState(true, GraphicsEnums.BlendFactor.SRC_ALPHA,
                GraphicsEnums.BlendFactor.ONE_MINUS_SRC_ALPHA);

        blockOutlineMesh.draw(graphics, GraphicsEnums.PrimitiveType.TRIANGLES);

        graphics.setRasterizerState(GraphicsEnums.CullMode.BACK, GraphicsEnums.FillMode.SOLID);

        graphics.popMatrix();
    }

    /**
     * Draws all UI elements, including the hotbar and crosshair.
     *
     * @param graphics         The graphics api
     * @param debugStrings     the debug strings to display
     * @param gameInputHandler The game input handler
     * @param partialTicks     The partial ticks for animation
     */
    private void drawUI(GraphicsAPI graphics, String[] debugStrings, GameInputHandler gameInputHandler, float partialTicks) {
        graphics.setShader(hudShader);

        // disable depth test
        graphics.setDepthState(false, true, GraphicsEnums.CompareFunc.ALWAYS);

        float scaledWidth = ScaledResolution.getScaledWidth(this.width, this.height);
        float scaledHeight = ScaledResolution.getScaledHeight(this.height);

        graphics.clear(false, true, 0.0F, 0.0F, 0.0F, 0.0F);

        graphics.setOrthographicProjection(0.0F, scaledWidth, scaledHeight, 0.0F, 100.0F, 300.0F);
        graphics.setMatrixMode(GraphicsAPI.MatrixMode.MODELVIEW);
        graphics.loadIdentity();
        graphics.translate(0.0F, 0.0F, -200.0F);

        // Render debug string
        graphics.setBlendState(true, GraphicsEnums.BlendFactor.SRC_ALPHA, GraphicsEnums.BlendFactor.ONE_MINUS_SRC_ALPHA);

        drawDebugText(graphics, debugStrings);

        if (player.isInventoryOpen()) {
            if (currentScreen == null) {
                openScreen(new InventoryScreen(textureManager, font, player.getInventory()));
            }
        } else {
            if (currentScreen != null) {
                closeScreen();
            }
        }

        // Draw hotbar
        drawHotbar(graphics, scaledWidth, scaledHeight, gameInputHandler.getHotbarSlotIndex());

        graphics.setBlendState(false, GraphicsEnums.BlendFactor.SRC_ALPHA, GraphicsEnums.BlendFactor.ONE_MINUS_SRC_ALPHA);

        graphics.setShader(hudNoTexShader);
        graphics.updateShaderMatrices();

        // Draw cross-hair
        drawCrosshair(graphics, scaledWidth, scaledHeight);

        graphics.setShader(hudShader);
        graphics.updateShaderMatrices();

        // Draw current screen if it exists
        if (currentScreen != null) {
            currentScreen.drawScreen(graphics, scaledWidth, scaledHeight, partialTicks);
        }
    }

    public void openScreen(GuiScreen screen) {
        if (this.currentScreen == null) {
            this.gameInputHandler.setLockMouseReleased(true);
            this.gameInputHandler.releaseMouse();
            this.gameInputHandler.setMousePosition(this.width / 2f, this.height / 2f);
        }
        this.gameInputHandler.setCurrentScreen(screen);
        this.currentScreen = screen;
        this.currentScreen.onResized(this.width, this.height);
    }

    public void closeScreen() {
        if (this.currentScreen != null) {
            this.currentScreen.onClose();
            this.currentScreen.dispose();
            this.currentScreen = null;
            this.gameInputHandler.setCurrentScreen(null);
            this.gameInputHandler.setLockMouseReleased(false);
            this.gameInputHandler.grabMouse();
        }
    }

    private IndexedMesh hotbarMesh;
    private IndexedMesh hotbarSelectorMesh;

    private static final int HOTBAR_HEIGHT = 22;
    private static final int HOTBAR_WIDTH = 92 * 2;
    private static final int HOTBAR_SELECTOR_SIZE = 24;
    private static final int HOTBAR_SLOT_WIDTH = 20;
    private static final int ITEM_SIZE = 10;

    private TextLabel[] stackSizeHotbarLabels;

    private void drawHotbar(GraphicsAPI graphics, float screenWidth, float screenHeight, int hotbarSlotIndex) {
        float centerX = screenWidth / 2f;

        if (hotbarMesh == null) {
            Tesselator t = Tesselator.instance;
            t.init();
            t.color(1, 1, 1);

            // draw quad
            t.vertexUV(centerX + HOTBAR_WIDTH / 2f, screenHeight - HOTBAR_HEIGHT, 0.0F, (HOTBAR_WIDTH) / 256f, 0.0F);
            t.vertexUV(centerX - HOTBAR_WIDTH / 2f, screenHeight - HOTBAR_HEIGHT, 0.0F, 0.0F, 0.0F);
            t.vertexUV(centerX - HOTBAR_WIDTH / 2f, screenHeight, 0.0F, 0.0F, HOTBAR_HEIGHT / 256f);
            t.vertexUV(centerX + HOTBAR_WIDTH / 2f, screenHeight, 0.0F, (HOTBAR_WIDTH) / 256f, HOTBAR_HEIGHT / 256f);

            hotbarMesh = t.createIndexedMesh(GraphicsEnums.BufferUsage.STATIC);
        }

        if (hotbarSelectorMesh == null) {
            Tesselator t = Tesselator.instance;
            t.init();
            t.color(1, 1, 1);

            // draw selector quad
            t.vertexUV((float) HOTBAR_SELECTOR_SIZE, 0, 0.0F, HOTBAR_SELECTOR_SIZE / 256f, HOTBAR_HEIGHT / 256f);
            t.vertexUV(0, 0, 0.0F, 0.0F, HOTBAR_HEIGHT / 256f);
            t.vertexUV(0, HOTBAR_SELECTOR_SIZE, 0.0F, 0.0F, (HOTBAR_HEIGHT + HOTBAR_SELECTOR_SIZE) / 256f);
            t.vertexUV((float) HOTBAR_SELECTOR_SIZE, HOTBAR_SELECTOR_SIZE, 0.0F, HOTBAR_SELECTOR_SIZE / 256f, (HOTBAR_HEIGHT + HOTBAR_SELECTOR_SIZE) / 256f);

            hotbarSelectorMesh = t.createIndexedMesh(GraphicsEnums.BufferUsage.STATIC);
        }

        // draw hot-bar background
        graphics.setTexture(textureManager.guiTexture);
        graphics.updateShaderMatrices();
        hotbarMesh.draw(graphics);

        // draw hot-bar items
        graphics.setTexture(textureManager.terrainTexture);
        graphics.setShader(worldShader);
        worldShader.setFogUniforms(0.0F, 0.0F, 10.0F,
                0.5F, 0.8F, 1.0F, 1.0F);

        int hotBarSize = player.getInventory().getHotbarSize();
        for (int i = 0; i < hotBarSize; i++) {
            ItemStack itemStack = player.getInventory().getHotbarItem(i);
            if (itemStack == null) {
                continue;
            }
            Item item = itemStack.getItem();
            if (item instanceof BlockItem) {
                BlockItem blockItem = (BlockItem) item;
                graphics.pushMatrix();
                graphics.translate(centerX - HOTBAR_WIDTH / 2f + (i * HOTBAR_SLOT_WIDTH) + HOTBAR_SLOT_WIDTH / 2f + 1, screenHeight - HOTBAR_SELECTOR_SIZE + ITEM_SIZE * 2 + 1, 0);


                // render item pickup animation
                {
                    // real mc has a tick-updated animation counter here, we don't do that.
                    // we just use time millis
                    double ticksPassed = (System.currentTimeMillis() - itemStack.lastPickupTimeMs) / 50.0;
                    double animTicks = Math.min(ticksPassed, 8.0);
                    double anim = 8.0 - animTicks;
                    float f = (float) (anim / 8.0);
                    float scaleFactor = 1.0f + f * f * 0.5f;
                    if (anim > 0) {
                        graphics.scale(1.0f / scaleFactor, (scaleFactor + 1.0f) / 2.0f, 1.0f);
                    }
                }

                BlockRenderer.renderBlockPreview(graphics, blockItem.getBlock(), ITEM_SIZE);
                graphics.popMatrix();
            }
        }

        // draw selector (selector is drawn before stack sizes)
        graphics.setShader(hudShader);
        {
            graphics.pushMatrix();
            graphics.translate(centerX - HOTBAR_WIDTH / 2f + hotbarSlotIndex * HOTBAR_SLOT_WIDTH - 1, screenHeight - HOTBAR_SELECTOR_SIZE + 1, 0.0F);
            graphics.updateShaderMatrices();
            graphics.setTexture(textureManager.guiTexture);
            hotbarSelectorMesh.draw(graphics);
            graphics.popMatrix();
        }

        // draw stack sizes
        {
            if (stackSizeHotbarLabels == null) {
                stackSizeHotbarLabels = new TextLabel[player.getInventory().getHotbarSize()];
                for (int i = 0; i < stackSizeHotbarLabels.length; i++) {
                    stackSizeHotbarLabels[i] = new TextLabel(font, 0xFFFFFF, true);
                }
            }
            for (int i = 0; i < stackSizeHotbarLabels.length; i++) {
                ItemStack itemStack = player.getInventory().getHotbarItem(i);
                if (itemStack == null) {
                    continue;
                }
                int count = itemStack.getCount();
                if (count > 1) {
                    stackSizeHotbarLabels[i].setText(StackCountStringPool.valueOf(count));
                    stackSizeHotbarLabels[i].render(graphics, centerX - HOTBAR_WIDTH / 2f + HOTBAR_SLOT_WIDTH + HOTBAR_SLOT_WIDTH * i - stackSizeHotbarLabels[i].getWidth(), screenHeight - font.getFontHeight() - 2);
                }
            }
        }
    }

    private void drawDebugText(GraphicsAPI graphics, String[] debugStrings) {
        String fpsString = debugStrings[0];
        String positionString = debugStrings[1];
        String memoryString1 = debugStrings[2];
        String memoryString2 = debugStrings[3];
        String memoryString3 = debugStrings[4];

        graphics.updateShaderMatrices();

        this.versionStringLabel.setText(Minecraft.MINECRAFT_VERSION_STRING);
        this.versionStringLabel.render(graphics, 2, 2);
        this.fpsStringLabel.setText(fpsString);
        this.fpsStringLabel.render(graphics, 2, 12);
        this.positionStringLabel.setText(positionString);
        this.positionStringLabel.render(graphics, 2, 22);
        this.memoryStringLabel1.setText(memoryString1);
        this.memoryStringLabel1.render(graphics, 2, 32);
        this.memoryStringLabel2.setText(memoryString2);
        this.memoryStringLabel2.render(graphics, 2, 42);
        this.memoryStringLabel3.setText(memoryString3);
        this.memoryStringLabel3.render(graphics, 2, 52);
    }

    private IndexedMesh crosshairMesh;

    private void drawCrosshair(GraphicsAPI graphics, float screenWidth, float screenHeight) {
        float centerX = screenWidth / 2f;
        float centerY = screenHeight / 2f;

        graphics.updateShaderMatrices();

        if (crosshairMesh == null) {
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
