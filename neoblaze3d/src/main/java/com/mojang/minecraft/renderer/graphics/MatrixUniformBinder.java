package com.mojang.minecraft.renderer.graphics;

/**
 * Static helpers for binding matrix uniforms from a matrix stack.
 */
public final class MatrixUniformBinder {
    private MatrixUniformBinder() {
    }

    public static void bindStandardMatrices(CommandBuffer commandBuffer, UniformCollection uniforms, MatrixStack matrixStack) {
        Uniform modelViewUniform = uniforms.getRequired(PipelineLayout.BindingSemantic.MODEL_VIEW_MATRIX);
        Uniform projectionUniform = uniforms.getRequired(PipelineLayout.BindingSemantic.PROJECTION_MATRIX);
        modelViewUniform.setFloatBuffer(matrixStack.getModelViewBuffer());
        projectionUniform.setFloatBuffer(matrixStack.getProjectionBuffer());
        commandBuffer.bindUniform(modelViewUniform);
        commandBuffer.bindUniform(projectionUniform);
    }
}
