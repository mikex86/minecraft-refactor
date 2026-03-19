package com.mojang.minecraft.renderer.shader;

import com.mojang.minecraft.renderer.Disposable;
import com.mojang.minecraft.renderer.graphics.GraphicsFactory;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.BlendFactor;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.CompareFunc;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.CullMode;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.FillMode;
import com.mojang.minecraft.renderer.graphics.Pipeline;
import com.mojang.minecraft.renderer.graphics.PipelineLayout;
import com.mojang.minecraft.renderer.shader.impl.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PipelineRegistry implements Disposable {

    private static PipelineRegistry instance;

    // Cache of loaded shaders
    private final Map<String, Shader> shaders = new HashMap<>();

    // Core shader programs
    private WorldShader worldShader;
    private ParticleShader particleShader;
    private EntityShader entityShader;
    private HudShader hudShader;
    private HudNoTexShader hudNoTexShader;
    private OutlineShader outlineShader;

    private PipelineLayout sharedPipelineLayout;

    // Core pipelines
    private Pipeline worldPipeline;
    private Pipeline particlePipeline;
    private Pipeline entityPipeline;
    private Pipeline hudPipeline;
    private Pipeline hudNoTexPipeline;
    private Pipeline hudNoCullPipeline;
    private Pipeline hudItemPipeline;
    private Pipeline outlinePipeline;
    private Pipeline worldNoCullPipeline;
    private Pipeline worldOverlayPipeline;

    /**
     * Gets the singleton instance of the shader manager.
     *
     * @return The shader manager instance
     */
    public static PipelineRegistry getInstance() {
        if (instance == null) {
            instance = new PipelineRegistry();
        }
        return instance;
    }

    /**
     * Private constructor to enforce singleton pattern.
     */
    private PipelineRegistry() {
    }

    /**
     * Initializes core shaders.
     * Should be called once at the start of the application.
     *
     * @throws IOException If shader loading fails
     */
    public void initialize() throws IOException {
        worldShader = new WorldShader();
        particleShader = new ParticleShader();
        entityShader = new EntityShader();
        hudShader = new HudShader();
        hudNoTexShader = new HudNoTexShader();
        outlineShader = new OutlineShader();

        sharedPipelineLayout = GraphicsFactory.getGraphicsAPI().createPipelineLayout(
                new PipelineLayout.Descriptor(
                        "legacy-shader-layout",
                        Arrays.asList(
                                new PipelineLayout.Binding(
                                        0,
                                        PipelineLayout.ResourceType.UNIFORM_BUFFER,
                                        PipelineLayout.ShaderStage.VERTEX,
                                        PipelineLayout.BindingSemantic.MODEL_VIEW_MATRIX
                                ),
                                new PipelineLayout.Binding(
                                        4,
                                        PipelineLayout.ResourceType.UNIFORM_BUFFER,
                                        PipelineLayout.ShaderStage.VERTEX,
                                        PipelineLayout.BindingSemantic.PROJECTION_MATRIX
                                )
                        )
                )
        );

        Pipeline.BlendState blendDisabled = new Pipeline.BlendState(false, BlendFactor.ONE, BlendFactor.ZERO);
        Pipeline.BlendState blendAlpha = new Pipeline.BlendState(true, BlendFactor.SRC_ALPHA, BlendFactor.ONE_MINUS_SRC_ALPHA);
        Pipeline.BlendState blendBreakOverlay = new Pipeline.BlendState(true, BlendFactor.DST_COLOR, BlendFactor.SRC_COLOR);

        Pipeline.DepthState depthReadWrite = new Pipeline.DepthState(true, true, CompareFunc.LESS_EQUAL);
        Pipeline.DepthState depthReadOnly = new Pipeline.DepthState(true, false, CompareFunc.LESS_EQUAL);
        Pipeline.DepthState depthDisabled = new Pipeline.DepthState(false, true, CompareFunc.ALWAYS);

        Pipeline.RasterizerState cullBack = new Pipeline.RasterizerState(CullMode.BACK, FillMode.SOLID);
        Pipeline.RasterizerState cullNone = new Pipeline.RasterizerState(CullMode.NONE, FillMode.SOLID);

        worldPipeline = createPipeline("world-pipeline", worldShader, blendDisabled, depthReadWrite, cullBack);
        worldNoCullPipeline = createPipeline("world-nocull-pipeline", worldShader, blendDisabled, depthReadWrite, cullNone);
        worldOverlayPipeline = createPipeline("world-overlay-pipeline", worldShader, blendBreakOverlay, depthReadOnly, cullNone);

        particlePipeline = createPipeline("particle-pipeline", particleShader, blendAlpha, depthReadWrite, cullBack);
        entityPipeline = createPipeline("entity-pipeline", entityShader, blendDisabled, depthReadWrite, cullBack);

        hudPipeline = createPipeline("hud-pipeline", hudShader, blendAlpha, depthDisabled, cullBack);
        hudNoCullPipeline = createPipeline("hud-nocull-pipeline", hudShader, blendAlpha, depthDisabled, cullNone);
        hudItemPipeline = createPipeline("hud-item-pipeline", hudShader, blendDisabled, depthReadWrite, cullNone);
        hudNoTexPipeline = createPipeline("hud-notex-pipeline", hudNoTexShader, blendDisabled, depthDisabled, cullBack);

        outlinePipeline = createPipeline("outline-pipeline", outlineShader, blendAlpha, depthReadWrite, cullNone);
    }

    private Pipeline createPipeline(String name,
                                    IShader shader,
                                    Pipeline.BlendState blendState,
                                    Pipeline.DepthState depthState,
                                    Pipeline.RasterizerState rasterizerState) {
        return GraphicsFactory.getGraphicsAPI().createPipeline(
                new Pipeline.Descriptor(name, sharedPipelineLayout, shader, blendState, depthState, rasterizerState)
        );
    }

    /**
     * Gets the world shader.
     *
     * @return The world shader
     */
    public WorldShader getWorldShader() {
        return worldShader;
    }
    
    /**
     * Gets the particle shader.
     *
     * @return The particle shader
     */
    public ParticleShader getParticleShader() {
        return particleShader;
    }

    public EntityShader getEntityShader() {
        return entityShader;
    }

    public HudShader getHudShader() {
        return hudShader;
    }

    public HudNoTexShader getHudNoTexShader() {
        return hudNoTexShader;
    }

    public PipelineLayout getSharedPipelineLayout() {
        return sharedPipelineLayout;
    }

    public Pipeline getWorldPipeline() {
        return worldPipeline;
    }

    public Pipeline getParticlePipeline() {
        return particlePipeline;
    }

    public Pipeline getEntityPipeline() {
        return entityPipeline;
    }

    public Pipeline getHudPipeline() {
        return hudPipeline;
    }

    public Pipeline getHudNoTexPipeline() {
        return hudNoTexPipeline;
    }

    public Pipeline getHudNoCullPipeline() {
        return hudNoCullPipeline;
    }

    public Pipeline getHudItemPipeline() {
        return hudItemPipeline;
    }

    public Pipeline getOutlinePipeline() {
        return outlinePipeline;
    }

    public Pipeline getWorldNoCullPipeline() {
        return worldNoCullPipeline;
    }

    public Pipeline getWorldOverlayPipeline() {
        return worldOverlayPipeline;
    }

    /**
     * Gets a shader by name.
     *
     * @param name The name of the shader
     * @return The shader, or null if not found
     */
    public Shader getShader(String name) {
        return shaders.get(name);
    }

    /**
     * Disposes of all shaders.
     */
    @Override
    public void dispose() {
        for (Shader shader : shaders.values()) {
            shader.dispose();
        }
        shaders.clear();

        if (worldPipeline != null) {
            worldPipeline.dispose();
            worldPipeline = null;
        }
        if (particlePipeline != null) {
            particlePipeline.dispose();
            particlePipeline = null;
        }
        if (entityPipeline != null) {
            entityPipeline.dispose();
            entityPipeline = null;
        }
        if (hudPipeline != null) {
            hudPipeline.dispose();
            hudPipeline = null;
        }
        if (hudNoTexPipeline != null) {
            hudNoTexPipeline.dispose();
            hudNoTexPipeline = null;
        }
        if (hudNoCullPipeline != null) {
            hudNoCullPipeline.dispose();
            hudNoCullPipeline = null;
        }
        if (hudItemPipeline != null) {
            hudItemPipeline.dispose();
            hudItemPipeline = null;
        }
        if (outlinePipeline != null) {
            outlinePipeline.dispose();
            outlinePipeline = null;
        }
        if (worldNoCullPipeline != null) {
            worldNoCullPipeline.dispose();
            worldNoCullPipeline = null;
        }
        if (worldOverlayPipeline != null) {
            worldOverlayPipeline.dispose();
            worldOverlayPipeline = null;
        }
        if (sharedPipelineLayout != null) {
            sharedPipelineLayout.dispose();
            sharedPipelineLayout = null;
        }
        
        if (worldShader != null) {
            worldShader.dispose();
        }
        
        if (particleShader != null) {
            particleShader.dispose();
        }

        if (entityShader != null) {
            entityShader.dispose();
        }

        if (hudShader != null) {
            hudShader.dispose();
        }

        if (hudNoTexShader != null) {
            hudNoTexShader.dispose();
        }

        if (outlineShader != null) {
            outlineShader.dispose();
        }
        
        instance = null;
    }

    public OutlineShader getOutlineShader() {
        return outlineShader;
    }
}
