package com.mojang.minecraft;

import com.mojang.minecraft.crash.CrashReporter;
import com.mojang.minecraft.engine.GameEngine;
import com.mojang.minecraft.entity.EntityPlayer;
import com.mojang.minecraft.gui.screen.ScreenManager;
import com.mojang.minecraft.input.GameInputHandler;
import com.mojang.minecraft.level.Level;
import com.mojang.minecraft.level.block.state.BlockState;
import com.mojang.minecraft.particle.ParticleEngine;
import com.mojang.minecraft.profiler.GpuMemoryTracker;
import com.mojang.minecraft.profiler.NativeMemoryTracker;
import com.mojang.minecraft.renderer.GameRenderer;
import com.mojang.minecraft.renderer.TextureManager;
import com.mojang.minecraft.renderer.graphics.ImmutableDescriptorSet;
import com.mojang.minecraft.renderer.graphics.Pipeline;
import com.mojang.minecraft.renderer.shader.PipelineRegistry;
import com.mojang.minecraft.util.logging.LoggingUtils;
import com.mojang.minecraft.util.math.MathUtils;
import com.mojang.minecraft.world.HitResult;

import java.io.IOException;

public class Minecraft implements Runnable {
    // Constants
    public static final String VERSION_STRING = "0.0.1";
    public static final String MINECRAFT_VERSION_STRING = "reMinecraft " + VERSION_STRING;
    public static final boolean DEBUG = false;

    // Core systems
    private final GameEngine engine;
    private final PipelineRegistry pipelineRegistry;
    private Pipeline worldPipeline;
    private ImmutableDescriptorSet worldFogTerrainDescriptorSet;
    private GameInputHandler gameInputHandler;
    private GameRenderer renderer;
    private ScreenManager screenManager;


    // Game state
    private GameState gameState;

    // Game resources
    private final TextureManager textureManager;

    // Game flags
    private volatile boolean running = false;


    /**
     * Creates a new Minecraft game instance.
     *
     * @param width      Width of the rendering area
     * @param height     Height of the rendering area
     * @param fullscreen Whether to run in fullscreen mode
     */
    public Minecraft(int width, int height, boolean fullscreen) {
        this.engine = new GameEngine(width, height, fullscreen, MINECRAFT_VERSION_STRING);
        this.textureManager = new TextureManager();
        this.pipelineRegistry = new PipelineRegistry();
    }

    /**
     * Initializes the game, setting up the display, OpenGL, and game objects.
     *
     * @throws IOException If resource loading fails
     */
    public void init() throws IOException {
        try {
            // Initialize the engine
            engine.initialize();

            // Initialize the shader manager
            pipelineRegistry.initialize();

            // Initialize texture manager
            textureManager.loadTextures();
            pipelineRegistry.configureSharedDescriptorSets(textureManager);
            worldPipeline = pipelineRegistry.getWorldPipeline();
            worldFogTerrainDescriptorSet = pipelineRegistry.getDescriptorSet(textureManager.terrainTexture, PipelineRegistry.FogPreset.WORLD);

            // Create game state (manages level, entities, player)
            this.gameState = new GameState(this.textureManager, this.pipelineRegistry);
            this.gameState.initialize();

            // Create renderer
            this.renderer = new GameRenderer(
                    this.textureManager,
                    this.pipelineRegistry,
                    this.gameState.getLevel(),
                    gameState.getLevelRenderer(),
                    gameState.getParticleEngine(),
                    gameState.getPlayer(),
                    engine.getWidth(),
                    engine.getHeight()
            );

            this.screenManager = new ScreenManager(gameState.getPlayer(), this.textureManager, this.renderer, this.pipelineRegistry);

            // Initialize game input handler
            this.gameInputHandler = new GameInputHandler(
                    engine.getInputHandler(),
                    this.screenManager,
                    gameState.getPlayer(),
                    gameState.getLevel(),
                    gameState.getParticleEngine(),
                    engine.isFullscreen()
            );

            this.engine.postInit();
        } catch (Exception e) {
            CrashReporter.handleCrash("Failed to initialize game", e);
            throw new IOException("Failed to initialize game", e);
        }
    }

    /**
     * Cleans up resources and saves the level before shutting down.
     */
    public void destroy() {
        try {
            if (gameState != null) {
                gameState.dispose();
            }
            engine.shutdown();
            textureManager.dispose();
            pipelineRegistry.dispose();
        } catch (Exception e) {
            CrashReporter.handleError("Failed to clean up resources during shutdown", e);
        }
    }

    /**
     * Main game loop. Initializes the game and handles game state updates.
     */
    public void run() {
        this.running = true;

        try {
            // Initialize the game
            this.init();
        } catch (Exception e) {
            CrashReporter.handleCrash("Failed to start Minecraft", e);
            return;
        }

        try {
            // Main game loop
            while (this.running) {
                // Check if window is closed
                if (!engine.update()) {
                    this.stop();
                }

                // Get time information from engine
                int ticksToProcess = engine.getTicksToProcess();
                float partialTick = engine.getPartialTick();

                // Process input
                HitResult hitResult = this.gameState.getLevel().raycast(this.gameState.getPlayer(), partialTick);
                this.gameState.getPlayer().setCurrentHitResult(hitResult);
                gameInputHandler.processInput(hitResult, this.engine.getWidth(), this.engine.getHeight());

                // Update client player
                if (ticksToProcess > 0) {
                    updateClientPlayer(partialTick);
                }

                // Process game ticks
                for (int i = 0; i < ticksToProcess; ++i) {
                    tick();
                }

                // Handle mouse look
                gameInputHandler.processMouseLook(this.engine.getWidth(), this.engine.getHeight());
                engine.resetMouse();

                // Render the frame
                this.renderer.render(
                        partialTick,
                        hitResult
                );

                // Check for window size changes
                if (engine.hasResized()) {
                    int newWidth = engine.getWidth();
                    int newHeight = engine.getHeight();
                    renderer.setScreenSize(newWidth, newHeight);
                }

                // Handle window focus change
                if (!engine.hasFocus()) {
                    gameInputHandler.handleFocusChange(false);
                }
            }
        } catch (Exception e) {
            CrashReporter.handleCrash("Uncaught exception in main game loop", e);
        } finally {
            // Clean up resources
            this.destroy();
        }
    }

    private void updateClientPlayer(float partialTick) {
        EntityPlayer player = this.gameState.getPlayer();
        if (player.updateBlockBreaking()) {
            this.breakBlock(player.breakingBlockX, player.breakingBlockY, player.breakingBlockZ);
            player.resetBreakingBlockPos();
        }
    }

    public boolean breakBlock(int x, int y, int z) {
        Level level = this.gameState.getLevel();
        ParticleEngine particleEngine = this.gameState.getParticleEngine();
        BlockState oldBlock = level.getBlockState(x, y, z);
        boolean changed = level.setBlockState(x, y, z, null);
        if (oldBlock != null && changed) {
            oldBlock.block.destroy(level, x, y, z, particleEngine, worldPipeline, worldFogTerrainDescriptorSet);
        }
        return changed;
    }

    private void tick() {
        this.gameInputHandler.tick();
        this.gameState.tick();
        this.updateDebugStrings();
    }

    private void updateDebugStrings() {
        EntityPlayer player = this.gameState.getPlayer();
        this.renderer.setDebugString(0, String.format("x: %.3f y: %.3f z: %.3f, xd: %.3f yd: %.3f zd: %.3f", player.x, player.y, player.z, player.xd, player.yd, player.zd));
        this.renderer.setDebugString(1, this.engine.getFpsString());

        long usedJavaHeap = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long totalJavaHeap = Runtime.getRuntime().totalMemory();
        this.renderer.setDebugString(2, "Used Java Heap: " + MathUtils.humanReadableByteCountSI(usedJavaHeap) + ", Java Heap Size " + MathUtils.humanReadableByteCountSI(totalJavaHeap));
        this.renderer.setDebugString(3, "Native memory: " + MathUtils.humanReadableByteCountSI(NativeMemoryTracker.ALLOCATED_NATIVE_MEMORY.get()) + ", Uploaded GPU memory: " + MathUtils.humanReadableByteCountSI(GpuMemoryTracker.UPLOADED_GPU_MEMORY) + ", Total GPU memory: " + MathUtils.humanReadableByteCountSI(GpuMemoryTracker.TOTAL_GPU_MEMORY));
        this.renderer.setDebugString(4, "Pooled GPU memory: " + MathUtils.humanReadableByteCountSI(GpuMemoryTracker.POOLED_GPU_MEMORY) + ", Pooled used GPU memory: " + MathUtils.humanReadableByteCountSI(GpuMemoryTracker.POOLED_USED_GPU_MEMORY));
    }

    /**
     * Stops the game loop.
     */
    public void stop() {
        this.running = false;
    }

    /**
     * Main entry point for standalone game.
     */
    public static void main(String[] args) {
        // Initialize the crash reporting system
        initializeCrashReporting();

        try {
            Minecraft minecraft = new Minecraft(854, 480, false);
            Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
            minecraft.run();
        } catch (Exception e) {
            CrashReporter.handleCrash("Failed to start Minecraft", e);
        }
    }

    /**
     * Sets up the crash reporting system.
     */
    private static void initializeCrashReporting() {
        // Initialize logging
        LoggingUtils.initialize();

        // Set up global exception handler
        LoggingUtils.setupUncaughtExceptionHandler((thread, exception) -> {
            CrashReporter.handleCrash("Uncaught exception in thread " + thread.getName(), exception);
        });

        // Redirect error output to capture any missed printStackTrace() calls
        LoggingUtils.redirectErrorOutput();

        System.out.println("Crash reporting system initialized");
    }
}
