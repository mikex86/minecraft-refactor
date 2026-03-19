package com.mojang.minecraft.renderer.graphics;

/**
 * Static helpers for writing matrix uniforms from a matrix stack.
 */
public final class MatrixUniforms {
    private MatrixUniforms() {
    }

    public static void writeStandardMatrices(ImmutableDescriptorSet uniforms, MatrixStack matrixStack) {
        Uniform modelViewUniform = uniforms.getRequired(PipelineLayout.BindingSemantic.MODEL_VIEW_MATRIX);
        Uniform projectionUniform = uniforms.getRequired(PipelineLayout.BindingSemantic.PROJECTION_MATRIX);
        modelViewUniform.setFloatBuffer(matrixStack.getModelViewBuffer());
        projectionUniform.setFloatBuffer(matrixStack.getProjectionBuffer());
    }
}
