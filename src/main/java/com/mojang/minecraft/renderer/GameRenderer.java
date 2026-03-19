package com.mojang.minecraft.renderer;

import com.mojang.minecraft.Minecraft;
import com.mojang.minecraft.entity.EntityPlayer;
import com.mojang.minecraft.gui.Font;
import com.mojang.minecraft.gui.TextLabel;
import com.mojang.minecraft.gui.scaling.ScaledResolution;
import com.mojang.minecraft.gui.screen.GuiScreen;
import com.mojang.minecraft.item.BlockItem;
import com.mojang.minecraft.item.HeldItem;
import com.mojang.minecraft.item.Item;
import com.mojang.minecraft.item.ItemStack;
import com.mojang.minecraft.level.Level;
import com.mojang.minecraft.level.LevelRenderer;
import com.mojang.minecraft.level.block.Block;
import com.mojang.minecraft.level.block.state.BlockState;
import com.mojang.minecraft.optim.pools.StackCountStringPool;
import com.mojang.minecraft.particle.ParticleEngine;
import com.mojang.minecraft.renderer.block.BlockRenderer;
import com.mojang.minecraft.renderer.graphics.CommandBuffer;
import com.mojang.minecraft.renderer.graphics.GraphicsAPI;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums;
import com.mojang.minecraft.renderer.graphics.GraphicsFactory;
import com.mojang.minecraft.renderer.graphics.IndexedMesh;
import com.mojang.minecraft.renderer.graphics.MatrixUniforms;
import com.mojang.minecraft.renderer.graphics.MatrixStack;
import com.mojang.minecraft.renderer.graphics.Pipeline;
import com.mojang.minecraft.renderer.graphics.DataType;
import com.mojang.minecraft.renderer.graphics.ImmutableDescriptorSet;
import com.mojang.minecraft.renderer.graphics.RenderPassAttachments;
import com.mojang.minecraft.renderer.item.HeldItemRenderer;
import com.mojang.minecraft.renderer.shader.PipelineRegistry;
import com.mojang.minecraft.world.HitResult;

/**
 * Handles all rendering operations for Minecraft.
 * Extracted from the main Minecraft class to separate rendering concerns.
 */
public class GameRenderer implements Disposable {
    private static final RenderPassAttachments WORLD_RENDER_PASS = new RenderPassAttachments(
            new RenderPassAttachments.ColorAttachment(
                    RenderPassAttachments.LoadOp.CLEAR,
                    RenderPassAttachments.StoreOp.STORE,
                    0.5F, 0.8F, 1.0F, 0.0F
            ),
            new RenderPassAttachments.DepthAttachment(
                    RenderPassAttachments.LoadOp.CLEAR,
                    RenderPassAttachments.StoreOp.STORE,
                    1.0F
            )
    );

    private static final RenderPassAttachments COLOR_LOAD_DEPTH_CLEAR_PASS = new RenderPassAttachments(
            new RenderPassAttachments.ColorAttachment(
                    RenderPassAttachments.LoadOp.LOAD,
                    RenderPassAttachments.StoreOp.STORE,
                    0.0F, 0.0F, 0.0F, 0.0F
            ),
            new RenderPassAttachments.DepthAttachment(
                    RenderPassAttachments.LoadOp.CLEAR,
                    RenderPassAttachments.StoreOp.STORE,
                    1.0F
            )
    );


    // Graphics context
    private final GraphicsAPI device;
    private CommandBuffer commandBuffer;
    private final MatrixStack matrixStack = new MatrixStack();

    // Texture manager
    private final TextureManager textureManager;

    private final int renderDistance = 16; // Render distance in chunks

    private final Pipeline worldPipeline;
    private final Pipeline worldOverlayPipeline;
    private final Pipeline particlePipeline;
    private final Pipeline entityPipeline;
    private final Pipeline hudPipeline;
    private final Pipeline hudNoCullPipeline;
    private final Pipeline hudItemPipeline;
    private final Pipeline hudNoTexPipeline;
    private final Pipeline outlinePipeline;
    private final ImmutableDescriptorSet worldFogTerrainDescriptorSet;
    private final ImmutableDescriptorSet noFogTerrainDescriptorSet;
    private final ImmutableDescriptorSet noFogNoTextureDescriptorSet;
    private final ImmutableDescriptorSet noFogGuiDescriptorSet;
    private final Level level;

    // Font renderer
    public final Font font;

    // Game components
    private final LevelRenderer levelRenderer;
    private final ParticleEngine particleEngine;
    private final EntityPlayer player;
    public final HeldItemRenderer heldItemRenderer;

    // Window dimensions
    public int width;
    public int height;

    // The current gui screen (if any)
    public GuiScreen currentScreen;

    private final TextLabel versionStringLabel;
    private final TextLabel fpsStringLabel;
    private final TextLabel positionStringLabel;
    private final TextLabel memoryStringLabel1;
    private final TextLabel memoryStringLabel2;
    private final TextLabel memoryStringLabel3;
    private final String[] debugStrings = new String[5];

    /**
     * Creates a new commandBuffer renderer.
     *
     * @param textureManager The texture manager
     * @param pipelineRegistry The shader registry
     * @param level          The level
     * @param levelRenderer  The level renderer
     * @param particleEngine The particle engine
     * @param player         The player
     * @param width          The initial window width
     * @param height         The initial window height
     */
    public GameRenderer(TextureManager textureManager, PipelineRegistry pipelineRegistry,
                        Level level, LevelRenderer levelRenderer,
                        ParticleEngine particleEngine, EntityPlayer player, int width, int height) {
        // Get commandBuffer API instance
        this.device = GraphicsFactory.getGraphicsAPI();

        this.textureManager = textureManager;
        this.level = level;
        this.levelRenderer = levelRenderer;
        this.particleEngine = particleEngine;
        this.player = player;
        this.width = width;
        this.height = height;
        this.heldItemRenderer = new HeldItemRenderer(textureManager, textureManager.itemsTexture, pipelineRegistry);

        // Create game resources
        this.font = new Font("/default.gif", textureManager, pipelineRegistry);

        this.worldPipeline = pipelineRegistry.getWorldPipeline();
        this.worldOverlayPipeline = pipelineRegistry.getWorldOverlayPipeline();
        this.particlePipeline = pipelineRegistry.getParticlePipeline();
        this.entityPipeline = pipelineRegistry.getEntityPipeline();
        this.hudPipeline = pipelineRegistry.getHudPipeline();
        this.hudNoCullPipeline = pipelineRegistry.getHudNoCullPipeline();
        this.hudItemPipeline = pipelineRegistry.getHudItemPipeline();
        this.hudNoTexPipeline = pipelineRegistry.getHudNoTexPipeline();
        this.outlinePipeline = pipelineRegistry.getOutlinePipeline();
        this.worldFogTerrainDescriptorSet = pipelineRegistry.getDescriptorSet(textureManager.terrainTexture, PipelineRegistry.FogPreset.WORLD);
        this.noFogTerrainDescriptorSet = pipelineRegistry.getDescriptorSet(textureManager.terrainTexture, PipelineRegistry.FogPreset.NONE);
        this.noFogNoTextureDescriptorSet = pipelineRegistry.getDescriptorSet(null, PipelineRegistry.FogPreset.NONE);
        this.noFogGuiDescriptorSet = pipelineRegistry.getDescriptorSet(textureManager.guiTexture, PipelineRegistry.FogPreset.NONE);

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
        commandBuffer.setViewport(0, 0, this.width, this.height);

        // Set up projection matrix
        setPerspectiveProjection(70.0F * player.getInterpolatedFOV(partialTick), aspectRatio, 0.05F, 4096.0F);

        // Set up camera transformation
        matrixStack.setMatrixMode(MatrixStack.MatrixMode.MODELVIEW);
        matrixStack.loadIdentity();

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
        matrixStack.translate(
                sin * bobAmt * 0.5F,
                -Math.abs(cos * bobAmt),
                0.0F
        );
        // lean left/right (Z) and tilt forward/back (X) as vanilla
        matrixStack.rotateZ(sin * bobAmt * 3.0F);
        matrixStack.rotateX(Math.abs((float) Math.cos(h * Math.PI - 0.2F) * bobAmt) * 5.0F);
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
        matrixStack.rotateX(player.cameraPitch);        // Pitch
        matrixStack.rotateY(player.cameraYaw);          // Yaw

        matrixStack.translate(-playerX, -playerY - player.getInterpolatedEyeHeight(partialTick), -playerZ);
    }

    /**
     * Renders a single frame of the game.
     *
     * @param partialTicks Interpolation factor between ticks (0.0-1.0)
     * @param hitResult    The current hit result (block selection)
     */
    public void render(float partialTicks, HitResult hitResult) {
        commandBuffer = device.beginFrame();
        try {
            commandBuffer.beginRenderPass(WORLD_RENDER_PASS, 0, 0, this.width, this.height);
            try {
                resetMatricesForFrame();

                // Set up the 3D camera
                this.setupCamera(partialTicks);

                // Update chunks that have changed
                this.levelRenderer.updateDirtyChunks(commandBuffer, matrixStack, this.player);

                render(partialTicks);

                renderBlockBreakingOverlay();

                // Render block selection highlight
                if (hitResult != null) {
                    renderBlockOutline(hitResult);
                }
            } finally {
                commandBuffer.endRenderPass();
            }

            // Render held block in 3D with view bobbing
            ItemStack hotbarItem = player.getInventory().getHotbarItem(player.hotbarSlotIndex);
            if (hotbarItem != null) {
                commandBuffer.beginRenderPass(COLOR_LOAD_DEPTH_CLEAR_PASS, 0, 0, this.width, this.height);
                try {
                    renderHeldItem(partialTicks);
                } finally {
                    commandBuffer.endRenderPass();
                }
            }

            // Render HUD elements
            commandBuffer.beginRenderPass(COLOR_LOAD_DEPTH_CLEAR_PASS, 0, 0, this.width, this.height);
            try {
                drawUI(commandBuffer, debugStrings, partialTicks);
            } finally {
                commandBuffer.endRenderPass();
            }
        } finally {
            device.endFrame();
            commandBuffer = null;
        }
    }


    /**
     * Renders the currently held block in first-person view with view bobbing.
     *
     * @param partialTicks interpolation factor between ticks
     */
    private void renderHeldItem(float partialTicks) {
        float aspectRatio = (float) (this.width) / this.height;
        int hotbarItem = player.hotbarSlotIndex;
        ItemStack itemStack = player.getInventory().getHotbarItem(hotbarItem);
        if (itemStack == null) {
            return;
        }

        // Calculate aspect ratio
        commandBuffer.setPipeline(worldPipeline);

        float handFov = 70.0F * player.getInterpolatedFOV(partialTicks);
        setPerspectiveProjection(handFov, aspectRatio, 0.05F, 4096.0F);
        matrixStack.setMatrixMode(MatrixStack.MatrixMode.MODELVIEW);
        matrixStack.pushMatrix();
        try {
            matrixStack.loadIdentity();

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
            matrixStack.translate(handSign * -0.4F * swingSin, 0.2F * swingSinDouble, -0.2F * swingSinFull);

            // Base hand placement with equip progress offset
            float equipProgress = getMainHandEquipProgress(partialTicks);
            matrixStack.translate(handSign * 0.56F, -0.52F + equipProgress * -0.6F, -0.72F);

            // Attack rotations (match vanilla order so swing-driven rotation composes correctly)
            float swingCurve = (float) Math.sin(swingProgress * swingProgress * Math.PI);
            float swingCurveSqrt = (float) Math.sin(swingSqrt * Math.PI);
            matrixStack.rotateY(handSign * 45.0F);
            matrixStack.rotateY(handSign * swingCurve * -20.0F);
            matrixStack.rotateZ(handSign * swingCurveSqrt * -20.0F);
            matrixStack.rotateX(swingCurveSqrt * -80.0F);
            matrixStack.rotateY(handSign * -45.0F);

            matrixStack.pushMatrix();
            try {
                Item item = itemStack.getItem();
                if (item instanceof BlockItem) {
                    commandBuffer.setPipeline(worldPipeline);
                    ImmutableDescriptorSet descriptorSet = worldFogTerrainDescriptorSet;
                    applyBlockFirstPersonTransform(handSign);
                    MatrixUniforms.writeStandardMatrices(descriptorSet, matrixStack);
                    commandBuffer.bindDescriptorSet(descriptorSet);
                    BlockItem blockItem = (BlockItem) item;
                    Block block = blockItem.getBlock();
                    BlockRenderer.getBlockMesh(block).draw(commandBuffer);
                } else if (item instanceof HeldItem) {
                    commandBuffer.setPipeline(hudItemPipeline);
                    applyHeldItemFirstPersonTransform(handSign);
                    heldItemRenderer.renderHeldItemModel(commandBuffer, matrixStack, (HeldItem) item, 1);
                }
            } finally {
                matrixStack.popMatrix();
            }
        } finally {
            matrixStack.popMatrix();
        }
    }

    private void applyBlockFirstPersonTransform(float handSign) {
        // Mirrors the default block first-person item transform from block/block.json
        matrixStack.rotateY(handSign * 45.0F);
        matrixStack.scale(0.4F, 0.4F, 0.4F);
        matrixStack.translate(-0.5F, -0.5F, -0.5F);
    }

    private void applyHeldItemFirstPersonTransform(float handSign) {
        // Matches the default handheld first-person transform used by vanilla item models.
        final float handheldScale = 0.68F;
        final float handheldTranslateY = 4.0F / 16.0F;
        final float halfThickness = -(1.0F / 16.0F) * 0.5F;

        matrixStack.translate(0.0F, handheldTranslateY, 0.0F);
        matrixStack.rotateY(handSign * -90.0F);
        matrixStack.rotateZ(handSign * 25.0F);
        matrixStack.scale(handheldScale, handheldScale, handheldScale);
        matrixStack.translate(-0.5F, -0.5F, halfThickness);
    }

    private void applyViewDriftCompensation(float partialTicks) {
        float pitch = lerp(partialTicks, player.prevPitch, player.pitch);
        float yaw = lerp(partialTicks, player.prevYaw, player.yaw);
        float smoothedPitch = lerp(partialTicks, player.prevViewPitchBob, player.viewPitchBob);
        float smoothedYaw = lerp(partialTicks, player.prevViewYawBob, player.viewYawBob);

        matrixStack.rotateX((pitch - smoothedPitch) * 0.1F);
        matrixStack.rotateY((yaw - smoothedYaw) * 0.1F);
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
            ImmutableDescriptorSet descriptorSet = worldFogTerrainDescriptorSet;
            commandBuffer.setPipeline(worldPipeline);
            MatrixUniforms.writeStandardMatrices(descriptorSet, matrixStack);
            commandBuffer.bindDescriptorSet(descriptorSet);

            this.levelRenderer.render(commandBuffer, matrixStack, partialTicks);
        }

        // render entities
        {
            commandBuffer.setPipeline(entityPipeline);
            this.levelRenderer.renderEntities(commandBuffer, matrixStack, partialTicks);
        }

        // render particles
        {
            ImmutableDescriptorSet descriptorSet = worldFogTerrainDescriptorSet;
            commandBuffer.setPipeline(particlePipeline);
            MatrixUniforms.writeStandardMatrices(descriptorSet, matrixStack);
            commandBuffer.bindDescriptorSet(descriptorSet);
            this.particleEngine.render(this.commandBuffer, this.player, partialTicks);
        }
    }

    private IndexedMesh blockOutlineMesh;
    private static final int BLOCK_BREAKING_STAGES = 10; // vanilla stages (0-9)
    private IndexedMesh[] blockBreakingMeshes;

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

        matrixStack.pushMatrix();
        matrixStack.translate(hitResult.x, hitResult.y, hitResult.z);

        commandBuffer.setPipeline(outlinePipeline);
        ImmutableDescriptorSet descriptorSet = noFogNoTextureDescriptorSet;
        MatrixUniforms.writeStandardMatrices(descriptorSet, matrixStack);
        commandBuffer.bindDescriptorSet(descriptorSet);

        blockOutlineMesh.draw(commandBuffer, GraphicsEnums.PrimitiveType.TRIANGLES);

        matrixStack.popMatrix();
    }

    private void renderBlockBreakingOverlay() {
        if (!player.isBreakingBlock()) {
            return;
        }

        BlockState state = level.getBlockState(player.breakingBlockX, player.breakingBlockY, player.breakingBlockZ);
        if (state == null || state.block == null) {
            return;
        }

        int stage = Math.min(BLOCK_BREAKING_STAGES - 1, Math.max(0, player.getBlockBreakingProgress()));

        ensureBlockBreakingMeshes();
        IndexedMesh breakingMesh = blockBreakingMeshes[stage];
        if (breakingMesh == null) {
            return;
        }

        matrixStack.pushMatrix();
        matrixStack.translate(player.breakingBlockX, player.breakingBlockY, player.breakingBlockZ);

        commandBuffer.setPipeline(worldOverlayPipeline);
        ImmutableDescriptorSet descriptorSet = worldFogTerrainDescriptorSet;
        MatrixUniforms.writeStandardMatrices(descriptorSet, matrixStack);
        commandBuffer.bindDescriptorSet(descriptorSet);

        breakingMesh.draw(commandBuffer);

        matrixStack.popMatrix();
    }

    private void ensureBlockBreakingMeshes() {
        if (blockBreakingMeshes != null) {
            return;
        }

        blockBreakingMeshes = new IndexedMesh[BLOCK_BREAKING_STAGES];
        final float eps = 0.0025F;

        for (int stage = 0; stage < BLOCK_BREAKING_STAGES; stage++) {
            int textureIndex = 240 + stage;
            float u0 = (textureIndex % 16) / 16.0F;
            float u1 = u0 + 0.0624375F;
            float v0 = (textureIndex / 16) / 16.0F;
            float v1 = v0 + 0.0624375F;

            Tesselator t = Tesselator.instance;
            // Overlay uses float position/UV so the small epsilon face offset is preserved.
            t.init(DataType.FLOAT, DataType.FLOAT, true);
            t.grayScale(1.0F);

            float x0 = 0.0F;
            float x1 = 1.0F;
            float y0 = 0.0F;
            float y1 = 1.0F;
            float z0 = 0.0F;
            float z1 = 1.0F;

            // bottom
            t.vertexUV(x0, y0 - eps, z1, u0, v1);
            t.vertexUV(x0, y0 - eps, z0, u0, v0);
            t.vertexUV(x1, y0 - eps, z0, u1, v0);
            t.vertexUV(x1, y0 - eps, z1, u1, v1);

            // top
            t.vertexUV(x1, y1 + eps, z1, u1, v1);
            t.vertexUV(x1, y1 + eps, z0, u1, v0);
            t.vertexUV(x0, y1 + eps, z0, u0, v0);
            t.vertexUV(x0, y1 + eps, z1, u0, v1);

            // north (negative z)
            t.vertexUV(x0, y1, z0 - eps, u1, v0);
            t.vertexUV(x1, y1, z0 - eps, u0, v0);
            t.vertexUV(x1, y0, z0 - eps, u0, v1);
            t.vertexUV(x0, y0, z0 - eps, u1, v1);

            // south (positive z)
            t.vertexUV(x0, y1, z1 + eps, u0, v0);
            t.vertexUV(x0, y0, z1 + eps, u0, v1);
            t.vertexUV(x1, y0, z1 + eps, u1, v1);
            t.vertexUV(x1, y1, z1 + eps, u1, v0);

            // west (negative x)
            t.vertexUV(x0 - eps, y1, z1, u1, v0);
            t.vertexUV(x0 - eps, y1, z0, u0, v0);
            t.vertexUV(x0 - eps, y0, z0, u0, v1);
            t.vertexUV(x0 - eps, y0, z1, u1, v1);

            // east (positive x)
            t.vertexUV(x1 + eps, y1, z1, u0, v0);
            t.vertexUV(x1 + eps, y0, z1, u0, v1);
            t.vertexUV(x1 + eps, y0, z0, u1, v1);
            t.vertexUV(x1 + eps, y1, z0, u1, v0);

            blockBreakingMeshes[stage] = t.createIndexedMesh(GraphicsEnums.BufferUsage.STATIC);
        }
    }

    /**
     * Draws all UI elements, including the hotbar and crosshair.
     *
     * @param commandBuffer     The commandBuffer api
     * @param debugStrings the debug strings to display
     * @param partialTicks The partial ticks for animation
     */
    private void drawUI(CommandBuffer commandBuffer, String[] debugStrings, float partialTicks) {
        commandBuffer.setPipeline(hudNoCullPipeline);

        float scaledWidth = ScaledResolution.getScaledWidth(this.width, this.height);
        float scaledHeight = ScaledResolution.getScaledHeight(this.height);

        setOrthographicProjection(0.0F, scaledWidth, scaledHeight, 0.0F, 100.0F, 300.0F);
        matrixStack.setMatrixMode(MatrixStack.MatrixMode.MODELVIEW);
        matrixStack.loadIdentity();
        matrixStack.translate(0.0F, 0.0F, -200.0F);

        drawDebugText(commandBuffer, debugStrings);

        // Draw hotbar
        drawHotbar(commandBuffer, scaledWidth, scaledHeight, player.hotbarSlotIndex);

        commandBuffer.setPipeline(hudNoTexPipeline);
        ImmutableDescriptorSet noTextureDescriptorSet = noFogNoTextureDescriptorSet;
        MatrixUniforms.writeStandardMatrices(noTextureDescriptorSet, matrixStack);
        commandBuffer.bindDescriptorSet(noTextureDescriptorSet);

        // Draw cross-hair
        drawCrosshair(commandBuffer, scaledWidth, scaledHeight);

        commandBuffer.setPipeline(hudPipeline);
        MatrixUniforms.writeStandardMatrices(noTextureDescriptorSet, matrixStack);
        commandBuffer.bindDescriptorSet(noTextureDescriptorSet);

        // Draw current screen if it exists
        if (currentScreen != null) {
            currentScreen.drawScreen(commandBuffer, matrixStack, scaledWidth, scaledHeight, partialTicks);
        }
    }

    public void openScreen(GuiScreen screen) {
        this.currentScreen = screen;
        this.currentScreen.onInit();
        this.currentScreen.onResized(this.width, this.height);
    }

    public void closeScreen() {
        if (this.currentScreen != null) {
            this.currentScreen.onClose();
            this.currentScreen.dispose();
            this.currentScreen = null;
        }
    }

    private IndexedMesh hotbarMesh;
    private IndexedMesh hotbarSelectorMesh;

    private static final int HOTBAR_HEIGHT = 22;
    private static final int HOTBAR_WIDTH = 92 * 2;
    private static final int HOTBAR_SELECTOR_SIZE = 24;
    private static final int HOTBAR_SLOT_WIDTH = 20;
    private static final int BLOCK_ITEM_SIZE = 10;
    private static final int HELD_ITEM_SIZE = 16;

    private TextLabel[] stackSizeHotbarLabels;

    private void drawHotbar(CommandBuffer commandBuffer, float screenWidth, float screenHeight, int hotbarSlotIndex) {
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
        ImmutableDescriptorSet guiDescriptorSet = noFogGuiDescriptorSet;
        MatrixUniforms.writeStandardMatrices(guiDescriptorSet, matrixStack);
        commandBuffer.bindDescriptorSet(guiDescriptorSet);
        hotbarMesh.draw(commandBuffer);

        // draw hot-bar blocks
        commandBuffer.setPipeline(worldPipeline);

        int hotBarSize = player.getInventory().getHotbarSize();
        for (int i = 0; i < hotBarSize; i++) {
            ItemStack itemStack = player.getInventory().getHotbarItem(i);
            if (itemStack == null) {
                continue;
            }
            Item item = itemStack.getItem();
            if (item instanceof BlockItem) {
                BlockItem blockItem = (BlockItem) item;
                matrixStack.pushMatrix();
                matrixStack.translate(centerX - HOTBAR_WIDTH / 2f + (i * HOTBAR_SLOT_WIDTH) + HOTBAR_SLOT_WIDTH / 2f + 1, screenHeight - HOTBAR_SELECTOR_SIZE + BLOCK_ITEM_SIZE * 2 + 1, 0);

                // render item pickup animation
                renderPickupAnimation(itemStack);

                BlockRenderer.renderBlockPreview(commandBuffer, matrixStack, blockItem.getBlock(), BLOCK_ITEM_SIZE, noFogTerrainDescriptorSet);
                matrixStack.popMatrix();
            }
        }

        // draw hot-bar items
        commandBuffer.setPipeline(hudPipeline);

        for (int i = 0; i < hotBarSize; i++) {
            ItemStack itemStack = player.getInventory().getHotbarItem(i);
            if (itemStack == null) {
                continue;
            }
            Item item = itemStack.getItem();
            if (item instanceof HeldItem) {
                HeldItem heldItem = (HeldItem) item;
                matrixStack.pushMatrix();
                matrixStack.translate(centerX - HOTBAR_WIDTH / 2f + (i * HOTBAR_SLOT_WIDTH) + HOTBAR_SLOT_WIDTH / 2f + 1 - HELD_ITEM_SIZE / 2f, screenHeight - HOTBAR_HEIGHT / 2f - HELD_ITEM_SIZE / 2f, 0);

                // render item pickup animation
                renderPickupAnimation(itemStack);

                heldItemRenderer.renderHeldItemPreview(commandBuffer, matrixStack, heldItem, HELD_ITEM_SIZE);
                matrixStack.popMatrix();
            }
        }

        // draw selector (selector is drawn before stack sizes)
        commandBuffer.setPipeline(hudPipeline);
        {
            matrixStack.pushMatrix();
            matrixStack.translate(centerX - HOTBAR_WIDTH / 2f + hotbarSlotIndex * HOTBAR_SLOT_WIDTH - 1, screenHeight - HOTBAR_SELECTOR_SIZE + 1, 0.0F);
            ImmutableDescriptorSet selectorDescriptorSet = noFogGuiDescriptorSet;
            MatrixUniforms.writeStandardMatrices(selectorDescriptorSet, matrixStack);
            commandBuffer.bindDescriptorSet(selectorDescriptorSet);
            hotbarSelectorMesh.draw(commandBuffer);
            matrixStack.popMatrix();
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
                    stackSizeHotbarLabels[i].render(commandBuffer, matrixStack, centerX - HOTBAR_WIDTH / 2f + HOTBAR_SLOT_WIDTH + HOTBAR_SLOT_WIDTH * i - stackSizeHotbarLabels[i].getWidth(), screenHeight - font.getFontHeight() - 2);
                }
            }
        }
    }

    private void renderPickupAnimation(ItemStack itemStack) {
        // real mc has a tick-updated animation counter here, we don't do that.
        // we just use time millis
        double ticksPassed = (System.currentTimeMillis() - itemStack.lastPickupTimeMs) / 50.0;
        double animTicks = Math.min(ticksPassed, 8.0);
        double anim = 8.0 - animTicks;
        float f = (float) (anim / 8.0);
        float scaleFactor = 1.0f + f * f * 0.5f;
        if (anim > 0) {
            matrixStack.scale(1.0f / scaleFactor, (scaleFactor + 1.0f) / 2.0f, 1.0f);
        }

    }

    private void drawDebugText(CommandBuffer commandBuffer, String[] debugStrings) {
        String fpsString = debugStrings[0];
        String positionString = debugStrings[1];
        String memoryString1 = debugStrings[2];
        String memoryString2 = debugStrings[3];
        String memoryString3 = debugStrings[4];

        this.versionStringLabel.setText(Minecraft.MINECRAFT_VERSION_STRING);
        this.versionStringLabel.render(commandBuffer, matrixStack, 2, 2);
        this.fpsStringLabel.setText(fpsString);
        this.fpsStringLabel.render(commandBuffer, matrixStack, 2, 12);
        this.positionStringLabel.setText(positionString);
        this.positionStringLabel.render(commandBuffer, matrixStack, 2, 22);
        this.memoryStringLabel1.setText(memoryString1);
        this.memoryStringLabel1.render(commandBuffer, matrixStack, 2, 32);
        this.memoryStringLabel2.setText(memoryString2);
        this.memoryStringLabel2.render(commandBuffer, matrixStack, 2, 42);
        this.memoryStringLabel3.setText(memoryString3);
        this.memoryStringLabel3.render(commandBuffer, matrixStack, 2, 52);
    }

    private void resetMatricesForFrame() {
        matrixStack.setMatrixMode(MatrixStack.MatrixMode.PROJECTION);
        matrixStack.loadIdentity();
        matrixStack.setMatrixMode(MatrixStack.MatrixMode.MODELVIEW);
        matrixStack.loadIdentity();
    }

    private void setPerspectiveProjection(float fov, float aspect, float nearPlane, float farPlane) {
        matrixStack.setMatrixMode(MatrixStack.MatrixMode.PROJECTION);
        matrixStack.loadIdentity();
        matrixStack.setPerspective(fov, aspect, nearPlane, farPlane);
    }

    private void setOrthographicProjection(float left, float right, float bottom, float top, float near, float far) {
        matrixStack.setMatrixMode(MatrixStack.MatrixMode.PROJECTION);
        matrixStack.loadIdentity();
        matrixStack.setOrthographic(left, right, bottom, top, near, far);
    }

    private IndexedMesh crosshairMesh;

    private void drawCrosshair(CommandBuffer commandBuffer, float screenWidth, float screenHeight) {
        float centerX = screenWidth / 2f;
        float centerY = screenHeight / 2f;

        ImmutableDescriptorSet descriptorSet = noFogNoTextureDescriptorSet;
        MatrixUniforms.writeStandardMatrices(descriptorSet, matrixStack);
        commandBuffer.bindDescriptorSet(descriptorSet);

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

        crosshairMesh.draw(commandBuffer);
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
