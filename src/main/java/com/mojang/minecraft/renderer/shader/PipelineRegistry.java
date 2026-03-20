package com.mojang.minecraft.renderer.shader;

import com.mojang.minecraft.renderer.Disposable;
import com.mojang.minecraft.renderer.TextureManager;
import com.mojang.minecraft.renderer.graphics.GraphicsFactory;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.BlendFactor;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.CompareFunc;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.CullMode;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.FillMode;
import com.mojang.minecraft.renderer.graphics.DataType;
import com.mojang.minecraft.renderer.graphics.ImmutableDescriptorSet;
import com.mojang.minecraft.renderer.graphics.Pipeline;
import com.mojang.minecraft.renderer.graphics.PipelineLayout;
import com.mojang.minecraft.renderer.graphics.ShaderProgram;
import com.mojang.minecraft.renderer.graphics.Texture;
import com.mojang.minecraft.renderer.graphics.Uniform;
import com.mojang.minecraft.renderer.graphics.VertexBuffer;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class PipelineRegistry implements Disposable {

    private static final int MODEL_VIEW_BINDING = 0;
    private static final int DIFFUSE_TEXTURE_BINDING = 1;
    private static final int PROJECTION_BINDING = 4;
    private static final int FOG_DENSITY_BINDING = 8;
    private static final int FOG_START_BINDING = 9;
    private static final int FOG_END_BINDING = 10;
    private static final int FOG_COLOR_BINDING = 11;
    private static final float WORLD_FOG_DENSITY = 0.001F;
    private static final float WORLD_FOG_START = 0.0F;
    private static final float WORLD_FOG_END = 10.0F;
    private static final float WORLD_FOG_R = 0.5F;
    private static final float WORLD_FOG_G = 0.8F;
    private static final float WORLD_FOG_B = 1.0F;
    private static final float WORLD_FOG_A = 1.0F;
    private static final float NO_FOG_DENSITY = 0.0F;
    private static final float NO_FOG_START = 0.0F;
    private static final float NO_FOG_END = 10.0F;
    private static final float NO_FOG_R = 0.5F;
    private static final float NO_FOG_G = 0.8F;
    private static final float NO_FOG_B = 1.0F;
    private static final float NO_FOG_A = 1.0F;

    private static final VertexBuffer.VertexFormat WORLD_VERTEX_FORMAT = new VertexBuffer.VertexFormat(
            DataType.FLOAT, DataType.FLOAT, DataType.FLOAT, DataType.FLOAT, DataType.FLOAT,
            true, false, true, true, false
    );
    
    private static final VertexBuffer.VertexFormat COLOR_TEX_VERTEX_FORMAT = new VertexBuffer.VertexFormat(
            DataType.FLOAT, DataType.FLOAT, DataType.UNSIGNED_BYTE, DataType.FLOAT, DataType.FLOAT,
            true, true, false, true, false
    );
    
    private static final VertexBuffer.VertexFormat COLOR_VERTEX_FORMAT = new VertexBuffer.VertexFormat(
            DataType.FLOAT, DataType.FLOAT, DataType.UNSIGNED_BYTE, DataType.FLOAT, DataType.FLOAT,
            true, true, false, false, false
    );
    
    private static final VertexBuffer.VertexFormat POSITION_ONLY_VERTEX_FORMAT = new VertexBuffer.VertexFormat(
            DataType.FLOAT, DataType.FLOAT, DataType.UNSIGNED_BYTE, DataType.FLOAT, DataType.FLOAT,
            true, false, false, false, false
    );
    
    private static final VertexBuffer.VertexFormat WORLD_OVERLAY_VERTEX_FORMAT = new VertexBuffer.VertexFormat(
            DataType.FLOAT, DataType.FLOAT, DataType.FLOAT, DataType.FLOAT, DataType.FLOAT,
            true, false, true, true, false
    );

    // Core shader programs
    private ShaderProgram worldProgram;
    private ShaderProgram particleProgram;
    private ShaderProgram entityProgram;
    private ShaderProgram hudProgram;
    private ShaderProgram hudNoTexProgram;
    private ShaderProgram outlineProgram;

    private PipelineLayout sharedPipelineLayout;
    private Map<DescriptorKey, ImmutableDescriptorSet> sharedDescriptorSetsByKey;

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

    public PipelineRegistry() {
    }

    public void initialize() throws IOException {
        worldProgram = GraphicsFactory.getGraphicsAPI().createShaderProgramFromPrecompiled("/shaders/world.vert.spv", "/shaders/world.frag.spv");
        particleProgram = GraphicsFactory.getGraphicsAPI().createShaderProgramFromPrecompiled("/shaders/particle.vert.spv", "/shaders/particle.frag.spv");
        entityProgram = GraphicsFactory.getGraphicsAPI().createShaderProgramFromPrecompiled("/shaders/entity.vert.spv", "/shaders/entity.frag.spv");
        hudProgram = GraphicsFactory.getGraphicsAPI().createShaderProgramFromPrecompiled("/shaders/hud.vert.spv", "/shaders/hud.frag.spv");
        hudNoTexProgram = GraphicsFactory.getGraphicsAPI().createShaderProgramFromPrecompiled("/shaders/hud_notexture.vert.spv", "/shaders/hud_notexture.frag.spv");
        outlineProgram = GraphicsFactory.getGraphicsAPI().createShaderProgramFromPrecompiled("/shaders/outline.vert.spv", "/shaders/outline.frag.spv");

        sharedPipelineLayout = GraphicsFactory.getGraphicsAPI().createPipelineLayout(
                new PipelineLayout.Descriptor(
                        "legacy-shader-layout",
                        Arrays.asList(
                                new PipelineLayout.Binding(
                                        MODEL_VIEW_BINDING,
                                        PipelineLayout.ResourceType.UNIFORM_BUFFER,
                                        PipelineLayout.ShaderStage.VERTEX,
                                        PipelineLayout.BindingSemantic.MODEL_VIEW_MATRIX
                                ),
                                new PipelineLayout.Binding(
                                        DIFFUSE_TEXTURE_BINDING,
                                        PipelineLayout.ResourceType.COMBINED_IMAGE_SAMPLER,
                                        PipelineLayout.ShaderStage.FRAGMENT,
                                        PipelineLayout.BindingSemantic.DIFFUSE_TEXTURE
                                ),
                                new PipelineLayout.Binding(
                                        PROJECTION_BINDING,
                                        PipelineLayout.ResourceType.UNIFORM_BUFFER,
                                        PipelineLayout.ShaderStage.VERTEX,
                                        PipelineLayout.BindingSemantic.PROJECTION_MATRIX
                                ),
                                new PipelineLayout.Binding(
                                        FOG_DENSITY_BINDING,
                                        PipelineLayout.ResourceType.UNIFORM_BUFFER,
                                        PipelineLayout.ShaderStage.VERTEX
                                ),
                                new PipelineLayout.Binding(
                                        FOG_START_BINDING,
                                        PipelineLayout.ResourceType.UNIFORM_BUFFER,
                                        PipelineLayout.ShaderStage.VERTEX
                                ),
                                new PipelineLayout.Binding(
                                        FOG_END_BINDING,
                                        PipelineLayout.ResourceType.UNIFORM_BUFFER,
                                        PipelineLayout.ShaderStage.VERTEX
                                ),
                                new PipelineLayout.Binding(
                                        FOG_COLOR_BINDING,
                                        PipelineLayout.ResourceType.UNIFORM_BUFFER,
                                        PipelineLayout.ShaderStage.FRAGMENT
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

        worldPipeline = createPipeline("world-pipeline", worldProgram, WORLD_VERTEX_FORMAT, blendDisabled, depthReadWrite, cullBack);
        worldNoCullPipeline = createPipeline("world-nocull-pipeline", worldProgram, WORLD_VERTEX_FORMAT, blendDisabled, depthReadWrite, cullNone);
        worldOverlayPipeline = createPipeline("world-overlay-pipeline", worldProgram, WORLD_OVERLAY_VERTEX_FORMAT, blendBreakOverlay, depthReadOnly, cullNone);

        particlePipeline = createPipeline("particle-pipeline", particleProgram, COLOR_TEX_VERTEX_FORMAT, blendAlpha, depthReadWrite, cullBack);
        entityPipeline = createPipeline("entity-pipeline", entityProgram, COLOR_TEX_VERTEX_FORMAT, blendDisabled, depthReadWrite, cullBack);

        hudPipeline = createPipeline("hud-pipeline", hudProgram, COLOR_TEX_VERTEX_FORMAT, blendAlpha, depthDisabled, cullBack);
        hudNoCullPipeline = createPipeline("hud-nocull-pipeline", hudProgram, COLOR_TEX_VERTEX_FORMAT, blendAlpha, depthDisabled, cullNone);
        hudItemPipeline = createPipeline("hud-item-pipeline", hudProgram, COLOR_TEX_VERTEX_FORMAT, blendDisabled, depthReadWrite, cullNone);
        hudNoTexPipeline = createPipeline("hud-notex-pipeline", hudNoTexProgram, COLOR_VERTEX_FORMAT, blendDisabled, depthDisabled, cullBack);

        outlinePipeline = createPipeline("outline-pipeline", outlineProgram, POSITION_ONLY_VERTEX_FORMAT, blendAlpha, depthReadWrite, cullNone);
    }

    public void configureSharedDescriptorSets(TextureManager textureManager) {
        Objects.requireNonNull(textureManager, "textureManager cannot be null");
        if (sharedPipelineLayout == null) {
            throw new IllegalStateException("PipelineRegistry must be initialized before configuring descriptor sets");
        }

        disposeSharedDescriptorSets();
        sharedDescriptorSetsByKey = new HashMap<>();

        registerSharedDescriptorSet(textureManager.terrainTexture, FogPreset.WORLD);
        registerSharedDescriptorSet(textureManager.terrainTexture, FogPreset.NONE);
        registerSharedDescriptorSet(textureManager.charTexture, FogPreset.WORLD);
        registerSharedDescriptorSet(textureManager.charTexture, FogPreset.NONE);
        registerSharedDescriptorSet(textureManager.itemsTexture, FogPreset.NONE);
        registerSharedDescriptorSet(textureManager.fontTexture, FogPreset.NONE);
        registerSharedDescriptorSet(textureManager.guiTexture, FogPreset.NONE);
        registerSharedDescriptorSet(textureManager.inventoryTexture, FogPreset.NONE);
        registerSharedDescriptorSet(textureManager.craftingTexture, FogPreset.NONE);
        registerSharedDescriptorSet(null, FogPreset.NONE);
    }

    private Uniform createUniform(int binding, Uniform.ValueType type) {
        return GraphicsFactory.getGraphicsAPI().createUniform(binding, type);
    }

    private Pipeline createPipeline(String name,
                                    ShaderProgram program,
                                    VertexBuffer.VertexFormat vertexFormat,
                                    Pipeline.BlendState blendState,
                                    Pipeline.DepthState depthState,
                                    Pipeline.RasterizerState rasterizerState) {
        return GraphicsFactory.getGraphicsAPI().createPipeline(
                new Pipeline.Descriptor(name, sharedPipelineLayout, program, vertexFormat, blendState, depthState, rasterizerState)
        );
    }

    public ImmutableDescriptorSet getDescriptorSet(Texture texture, FogPreset fogPreset) {
        assertDescriptorSetsConfigured();
        ImmutableDescriptorSet descriptorSet = sharedDescriptorSetsByKey.get(new DescriptorKey(texture, fogPreset));
        if (descriptorSet == null) {
            throw new IllegalStateException("No shared descriptor set registered for fog preset " + fogPreset);
        }
        return descriptorSet;
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

    @Override
    public void dispose() {
        disposePipeline(worldPipeline);
        worldPipeline = null;
        disposePipeline(particlePipeline);
        particlePipeline = null;
        disposePipeline(entityPipeline);
        entityPipeline = null;
        disposePipeline(hudPipeline);
        hudPipeline = null;
        disposePipeline(hudNoTexPipeline);
        hudNoTexPipeline = null;
        disposePipeline(hudNoCullPipeline);
        hudNoCullPipeline = null;
        disposePipeline(hudItemPipeline);
        hudItemPipeline = null;
        disposePipeline(outlinePipeline);
        outlinePipeline = null;
        disposePipeline(worldNoCullPipeline);
        worldNoCullPipeline = null;
        disposePipeline(worldOverlayPipeline);
        worldOverlayPipeline = null;

        if (sharedPipelineLayout != null) {
            sharedPipelineLayout.dispose();
            sharedPipelineLayout = null;
        }
        disposeSharedDescriptorSets();

        disposeShader(worldProgram);
        worldProgram = null;
        disposeShader(particleProgram);
        particleProgram = null;
        disposeShader(entityProgram);
        entityProgram = null;
        disposeShader(hudProgram);
        hudProgram = null;
        disposeShader(hudNoTexProgram);
        hudNoTexProgram = null;
        disposeShader(outlineProgram);
        outlineProgram = null;
    }

    private static void disposePipeline(Pipeline pipeline) {
        if (pipeline != null) {
            pipeline.dispose();
        }
    }

    private static void disposeShader(ShaderProgram shader) {
        if (shader != null) {
            shader.dispose();
        }
    }

    private ImmutableDescriptorSet createSharedDescriptorSet(Texture texture, FogPreset fogPreset) {
        Uniform modelView = createUniform(MODEL_VIEW_BINDING, Uniform.ValueType.MAT4);
        Uniform projection = createUniform(PROJECTION_BINDING, Uniform.ValueType.MAT4);
        Uniform fogDensity = createUniform(FOG_DENSITY_BINDING, Uniform.ValueType.FLOAT1);
        Uniform fogStart = createUniform(FOG_START_BINDING, Uniform.ValueType.FLOAT1);
        Uniform fogEnd = createUniform(FOG_END_BINDING, Uniform.ValueType.FLOAT1);
        Uniform fogColor = createUniform(FOG_COLOR_BINDING, Uniform.ValueType.FLOAT4);
        applyFogPreset(fogPreset, fogDensity, fogStart, fogEnd, fogColor);

        Map<Integer, Texture> texturesByBinding = new HashMap<>();
        if (texture != null) {
            texturesByBinding.put(DIFFUSE_TEXTURE_BINDING, texture);
        }
        return new ImmutableDescriptorSet(
                sharedPipelineLayout,
                Arrays.asList(
                        modelView,
                        projection,
                        fogDensity,
                        fogStart,
                        fogEnd,
                        fogColor
                ),
                texturesByBinding
        );
    }

    private void registerSharedDescriptorSet(Texture texture, FogPreset fogPreset) {
        ImmutableDescriptorSet descriptorSet = createSharedDescriptorSet(texture, fogPreset);
        sharedDescriptorSetsByKey.put(new DescriptorKey(texture, fogPreset), descriptorSet);
    }

    private void assertDescriptorSetsConfigured() {
        if (sharedDescriptorSetsByKey == null) {
            throw new IllegalStateException("Shared descriptor sets are not configured. Call configureSharedDescriptorSets(textureManager) after textures are loaded.");
        }
    }

    private void disposeSharedDescriptorSets() {
        if (sharedDescriptorSetsByKey != null) {
            for (ImmutableDescriptorSet descriptorSet : sharedDescriptorSetsByKey.values()) {
                disposeDescriptorSet(descriptorSet);
            }
            sharedDescriptorSetsByKey.clear();
            sharedDescriptorSetsByKey = null;
        }
    }

    private static void disposeDescriptorSet(ImmutableDescriptorSet descriptorSet) {
        if (descriptorSet != null) {
            descriptorSet.dispose();
        }
    }

    private static void applyFogPreset(FogPreset fogPreset,
                                       Uniform fogDensityUniform,
                                       Uniform fogStartUniform,
                                       Uniform fogEndUniform,
                                       Uniform fogColorUniform) {
        if (fogPreset == FogPreset.WORLD) {
            fogDensityUniform.setFloat(WORLD_FOG_DENSITY);
            fogStartUniform.setFloat(WORLD_FOG_START);
            fogEndUniform.setFloat(WORLD_FOG_END);
            fogColorUniform.setFloat4(WORLD_FOG_R, WORLD_FOG_G, WORLD_FOG_B, WORLD_FOG_A);
            return;
        }
        fogDensityUniform.setFloat(NO_FOG_DENSITY);
        fogStartUniform.setFloat(NO_FOG_START);
        fogEndUniform.setFloat(NO_FOG_END);
        fogColorUniform.setFloat4(NO_FOG_R, NO_FOG_G, NO_FOG_B, NO_FOG_A);
    }

    public enum FogPreset {
        WORLD,
        NONE
    }

    private static final class DescriptorKey {
        private final Texture texture;
        private final FogPreset fogPreset;

        private DescriptorKey(Texture texture, FogPreset fogPreset) {
            this.texture = texture;
            this.fogPreset = fogPreset;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof DescriptorKey)) {
                return false;
            }
            DescriptorKey other = (DescriptorKey) obj;
            return texture == other.texture && fogPreset == other.fogPreset;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(texture) * 31 + fogPreset.hashCode();
        }
    }
}
