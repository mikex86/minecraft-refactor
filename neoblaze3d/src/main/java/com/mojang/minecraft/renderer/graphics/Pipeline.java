package com.mojang.minecraft.renderer.graphics;

import com.mojang.minecraft.renderer.graphics.GraphicsEnums.BlendFactor;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.CompareFunc;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.CullMode;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.FillMode;

/**
 * Backend-neutral graphics pipeline object.
 */
public interface Pipeline extends GraphicsResource {

    /**
     * Gets this pipeline's layout.
     */
    PipelineLayout getLayout();

    /**
     * Gets the shader program used by this pipeline.
     */
    ShaderProgram getProgram();

    /**
     * Optional blend state.
     */
    BlendState getBlendState();

    /**
     * Optional depth state.
     */
    DepthState getDepthState();

    /**
     * Optional rasterizer state.
     */
    RasterizerState getRasterizerState();

    /**
     * Gets the debug label for this pipeline.
     */
    String getDebugName();

    final class BlendState {
        private final boolean enabled;
        private final BlendFactor srcFactor;
        private final BlendFactor dstFactor;

        public BlendState(boolean enabled, BlendFactor srcFactor, BlendFactor dstFactor) {
            this.enabled = enabled;
            this.srcFactor = srcFactor;
            this.dstFactor = dstFactor;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public BlendFactor getSrcFactor() {
            return srcFactor;
        }

        public BlendFactor getDstFactor() {
            return dstFactor;
        }
    }

    final class DepthState {
        private final boolean depthTest;
        private final boolean depthMask;
        private final CompareFunc compareFunc;

        public DepthState(boolean depthTest, boolean depthMask, CompareFunc compareFunc) {
            this.depthTest = depthTest;
            this.depthMask = depthMask;
            this.compareFunc = compareFunc;
        }

        public boolean isDepthTest() {
            return depthTest;
        }

        public boolean isDepthMask() {
            return depthMask;
        }

        public CompareFunc getCompareFunc() {
            return compareFunc;
        }
    }

    final class RasterizerState {
        private final CullMode cullMode;
        private final FillMode fillMode;

        public RasterizerState(CullMode cullMode, FillMode fillMode) {
            this.cullMode = cullMode;
            this.fillMode = fillMode;
        }

        public CullMode getCullMode() {
            return cullMode;
        }

        public FillMode getFillMode() {
            return fillMode;
        }
    }

    /**
     * Value object used to create pipelines.
     */
    final class Descriptor {
        private final String debugName;
        private final PipelineLayout layout;
        private final ShaderProgram program;
        private final BlendState blendState;
        private final DepthState depthState;
        private final RasterizerState rasterizerState;

        public Descriptor(String debugName,
                          PipelineLayout layout,
                          ShaderProgram program,
                          BlendState blendState,
                          DepthState depthState,
                          RasterizerState rasterizerState) {
            this.debugName = debugName == null ? "" : debugName;
            this.layout = layout;
            this.program = program;
            this.blendState = blendState;
            this.depthState = depthState;
            this.rasterizerState = rasterizerState;
        }

        public String getDebugName() {
            return debugName;
        }

        public PipelineLayout getLayout() {
            return layout;
        }

        public ShaderProgram getProgram() {
            return program;
        }

        public BlendState getBlendState() {
            return blendState;
        }

        public DepthState getDepthState() {
            return depthState;
        }

        public RasterizerState getRasterizerState() {
            return rasterizerState;
        }
    }
}
