package com.mojang.minecraft.renderer;

import com.mojang.minecraft.renderer.graphics.DataType;
import com.mojang.minecraft.renderer.graphics.CommandBuffer;
import com.mojang.minecraft.renderer.graphics.GraphicsAPI;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.BlendFactor;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.BufferUsage;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.CompareFunc;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.CullMode;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.FillMode;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.PrimitiveType;
import com.mojang.minecraft.renderer.graphics.GraphicsFactory;
import com.mojang.minecraft.renderer.graphics.MatrixStack;
import com.mojang.minecraft.renderer.graphics.Pipeline;
import com.mojang.minecraft.renderer.graphics.PipelineLayout;
import com.mojang.minecraft.renderer.graphics.RenderPassAttachments;
import com.mojang.minecraft.renderer.graphics.ShaderProgram;
import com.mojang.minecraft.renderer.graphics.Uniform;
import com.mojang.minecraft.renderer.graphics.MutableDescriptorSet;
import com.mojang.minecraft.renderer.graphics.VertexBuffer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryStack;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glFinish;
import static org.lwjgl.opengl.GL11.glReadPixels;

class HeadlessTriangleRenderTest {

    private static final int SCREEN_WIDTH = 128;
    private static final int SCREEN_HEIGHT = 128;
    private static final float[] V0 = {-0.8f, -0.8f};
    private static final float[] V1 = {0.8f, -0.8f};
    private static final float[] V2 = {0.0f, 0.8f};
    private static final float COLOR_TOLERANCE = 0.16f;
    private static final float BACKGROUND_TOLERANCE = 0.08f;
    private static final RenderPassAttachments TEST_RENDER_PASS = new RenderPassAttachments(
            new RenderPassAttachments.ColorAttachment(
                    RenderPassAttachments.LoadOp.CLEAR,
                    RenderPassAttachments.StoreOp.STORE,
                    0.0f, 0.0f, 0.0f, 1.0f
            ),
            new RenderPassAttachments.DepthAttachment(
                    RenderPassAttachments.LoadOp.CLEAR,
                    RenderPassAttachments.StoreOp.STORE,
                    1.0f
            )
    );
    private static GameWindow window;
    private static GraphicsAPI graphics;

    @BeforeAll
    static void initializeGraphicsContext() {
        try {
            window = new GameWindow(SCREEN_WIDTH, SCREEN_HEIGHT, "neoblaze3d-test", false);
            graphics = GraphicsFactory.getGraphicsAPI();
            GLCapabilities capabilities = GL.getCapabilities();
            boolean supportsSpirv = capabilities != null && (capabilities.OpenGL46 || capabilities.GL_ARB_gl_spirv);
            Assumptions.assumeTrue(supportsSpirv, "Skipping OpenGL tests. ARB_gl_spirv/OpenGL 4.6 is not available.");
        } catch (Throwable t) {
            Assumptions.assumeTrue(false, "Skipping OpenGL tests. Could not initialize hidden context: " + t.getMessage());
        }
    }

    @AfterAll
    static void destroyGraphicsContext() {
        if (window != null) {
            window.dispose();
            window = null;
        }
        graphics = null;
    }

    @Test
    void rendersGradientTriangleAtExpectedPixels() throws Exception {
        ShaderProgram shaderProgram = null;
        VertexBuffer vertexBuffer = null;
        PipelineLayout pipelineLayout = null;
        Pipeline pipeline = null;
        MutableDescriptorSet descriptorSet = null;
        Uniform modelViewUniform = null;
        Uniform projectionUniform = null;
        CommandBuffer commandBuffer = graphics.beginFrame();
        boolean renderPassActive = false;
        boolean frameSubmitted = false;
        MatrixStack matrixStack = new MatrixStack();

        try {
            commandBuffer.beginRenderPass(TEST_RENDER_PASS, 0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);
            renderPassActive = true;

            shaderProgram = graphics.createShaderProgramFromPrecompiled("/shaders/test_triangle.vert.spv", "/shaders/test_triangle.frag.spv");
            pipelineLayout = graphics.createPipelineLayout(
                    new PipelineLayout.Descriptor("test-triangle-layout", Collections.<PipelineLayout.Binding>emptyList())
            );
            VertexBuffer.VertexFormat format = new VertexBuffer.VertexFormat(
                    DataType.FLOAT,
                    DataType.FLOAT,
                    null,
                    null,
                    null,
                    true,
                    true,
                    false,
                    false,
                    false
            );
            pipeline = createTestPipeline("test-triangle-pipeline", pipelineLayout, shaderProgram, format);
            commandBuffer.setPipeline(pipeline);

            vertexBuffer = graphics.createVertexBuffer(BufferUsage.STATIC);

            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer vertexData = stack.malloc(3 * 6 * Float.BYTES);
                // The current vertex input layout packs color first, then position.
                putVertex(vertexData, 1.0f, 0.0f, 0.0f, V0[0], V0[1], 0.0f);
                putVertex(vertexData, 0.0f, 1.0f, 0.0f, V1[0], V1[1], 0.0f);
                putVertex(vertexData, 0.0f, 0.0f, 1.0f, V2[0], V2[1], 0.0f);
                vertexData.flip();
                vertexBuffer.setData(vertexData, vertexData.remaining());
            }

            commandBuffer.draw(PrimitiveType.TRIANGLES, vertexBuffer, null, 0, 3);
            commandBuffer.endRenderPass();
            renderPassActive = false;
            graphics.endFrame();
            frameSubmitted = true;
            glFinish();

            verifyGradientAtNdcPoint(SCREEN_WIDTH, SCREEN_HEIGHT, -0.3f, -0.3f, V0, V1, V2, COLOR_TOLERANCE);
            verifyGradientAtNdcPoint(SCREEN_WIDTH, SCREEN_HEIGHT, 0.3f, -0.3f, V0, V1, V2, COLOR_TOLERANCE);
            verifyGradientAtNdcPoint(SCREEN_WIDTH, SCREEN_HEIGHT, 0.0f, 0.2f, V0, V1, V2, COLOR_TOLERANCE);

            maybeExportPng(SCREEN_WIDTH, SCREEN_HEIGHT, "triangle-frame.png");
        } finally {
            if (!frameSubmitted) {
                if (renderPassActive) {
                    commandBuffer.endRenderPass();
                }
                graphics.endFrame();
            }
            if (pipeline != null) {
                pipeline.dispose();
            }
            if (pipelineLayout != null) {
                pipelineLayout.dispose();
            }
            if (vertexBuffer != null) {
                vertexBuffer.dispose();
            }
            if (shaderProgram != null) {
                shaderProgram.dispose();
            }
        }
    }

    @Test
    void transformsTriangleWithTranslateAndRotateAndMatchesExpectedPixels() throws Exception {
        final float translateX = 0.1f;
        final float translateY = 0.0f;
        final float rotateDegrees = 180.0f;

        float[] transformedV0 = transformVertex(V0, translateX, translateY, rotateDegrees);
        float[] transformedV1 = transformVertex(V1, translateX, translateY, rotateDegrees);
        float[] transformedV2 = transformVertex(V2, translateX, translateY, rotateDegrees);

        ShaderProgram shaderProgram = null;
        VertexBuffer vertexBuffer = null;
        PipelineLayout pipelineLayout = null;
        Pipeline pipeline = null;
        MutableDescriptorSet descriptorSet = null;
        Uniform modelViewUniform = null;
        Uniform projectionUniform = null;
        CommandBuffer commandBuffer = graphics.beginFrame();
        boolean renderPassActive = false;
        boolean frameSubmitted = false;
        MatrixStack matrixStack = new MatrixStack();

        try {
            commandBuffer.beginRenderPass(TEST_RENDER_PASS, 0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);
            renderPassActive = true;

            shaderProgram = graphics.createShaderProgramFromPrecompiled("/shaders/test_triangle_matrix.vert.spv", "/shaders/test_triangle.frag.spv");
            pipelineLayout = graphics.createPipelineLayout(
                    createMatrixPipelineLayoutDescriptor("test-triangle-matrix-layout")
            );
            VertexBuffer.VertexFormat format = new VertexBuffer.VertexFormat(
                    DataType.FLOAT,
                    DataType.FLOAT,
                    null,
                    null,
                    null,
                    true,
                    true,
                    false,
                    false,
                    false
            );
            pipeline = createTestPipeline("test-triangle-matrix-pipeline", pipelineLayout, shaderProgram, format);
            modelViewUniform = graphics.createUniform(0, Uniform.ValueType.MAT4);
            projectionUniform = graphics.createUniform(4, Uniform.ValueType.MAT4);
            descriptorSet = new MutableDescriptorSet(pipelineLayout, List.of(modelViewUniform, projectionUniform));
            commandBuffer.setPipeline(pipeline);

            matrixStack.setMatrixMode(MatrixStack.MatrixMode.PROJECTION);
            matrixStack.loadIdentity();
            matrixStack.setMatrixMode(MatrixStack.MatrixMode.MODELVIEW);
            matrixStack.loadIdentity();
            matrixStack.translate(translateX, translateY, 0.0f);
            matrixStack.rotateZ(rotateDegrees);
            bindMatrixUniforms(commandBuffer, matrixStack, descriptorSet);

            vertexBuffer = graphics.createVertexBuffer(BufferUsage.STATIC);

            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer vertexData = stack.malloc(3 * 6 * Float.BYTES);
                putVertex(vertexData, 1.0f, 0.0f, 0.0f, V0[0], V0[1], 0.0f);
                putVertex(vertexData, 0.0f, 1.0f, 0.0f, V1[0], V1[1], 0.0f);
                putVertex(vertexData, 0.0f, 0.0f, 1.0f, V2[0], V2[1], 0.0f);
                vertexData.flip();
                vertexBuffer.setData(vertexData, vertexData.remaining());
            }

            commandBuffer.draw(PrimitiveType.TRIANGLES, vertexBuffer, null, 0, 3);
            commandBuffer.endRenderPass();
            renderPassActive = false;
            graphics.endFrame();
            frameSubmitted = true;
            glFinish();

            verifyGradientAtNdcPoint(SCREEN_WIDTH, SCREEN_HEIGHT, 0.5f, 0.4f, transformedV0, transformedV1, transformedV2, COLOR_TOLERANCE);
            verifyGradientAtNdcPoint(SCREEN_WIDTH, SCREEN_HEIGHT, -0.4f, 0.4f, transformedV0, transformedV1, transformedV2, COLOR_TOLERANCE);
            verifyGradientAtNdcPoint(SCREEN_WIDTH, SCREEN_HEIGHT, 0.1f, -0.3f, transformedV0, transformedV1, transformedV2, COLOR_TOLERANCE);
            verifyBackgroundAtNdcPoint(SCREEN_WIDTH, SCREEN_HEIGHT, -0.6f, -0.6f, BACKGROUND_TOLERANCE);

            maybeExportPng(SCREEN_WIDTH, SCREEN_HEIGHT, "triangle-frame-transformed.png");
        } finally {
            if (!frameSubmitted) {
                if (renderPassActive) {
                    commandBuffer.endRenderPass();
                }
                graphics.endFrame();
            }
            if (pipeline != null) {
                pipeline.dispose();
            }
            if (descriptorSet != null) {
                descriptorSet.dispose();
            }
            if (pipelineLayout != null) {
                pipelineLayout.dispose();
            }
            if (vertexBuffer != null) {
                vertexBuffer.dispose();
            }
            if (shaderProgram != null) {
                shaderProgram.dispose();
            }
        }
    }

    @Test
    void poppedMatrixFrameDoesNotAffectLowerFrameDraw() throws Exception {
        final float lowerTranslateX = 0.2f;
        final float lowerTranslateY = -0.1f;
        final float poppedTranslateX = 0.8f;
        final float poppedTranslateY = 0.0f;
        final float poppedRotateDegrees = 180.0f;

        float[] expectedV0 = transformVertex(V0, lowerTranslateX, lowerTranslateY, 0.0f);
        float[] expectedV1 = transformVertex(V1, lowerTranslateX, lowerTranslateY, 0.0f);
        float[] expectedV2 = transformVertex(V2, lowerTranslateX, lowerTranslateY, 0.0f);

        // If popMatrix() were broken and leaked transforms, this is where the triangle would be.
        float leakedTx = lowerTranslateX + poppedTranslateX;
        float leakedTy = lowerTranslateY + poppedTranslateY;
        float[] leakedV0 = transformVertex(V0, leakedTx, leakedTy, poppedRotateDegrees);
        float[] leakedV1 = transformVertex(V1, leakedTx, leakedTy, poppedRotateDegrees);
        float[] leakedV2 = transformVertex(V2, leakedTx, leakedTy, poppedRotateDegrees);

        ShaderProgram shaderProgram = null;
        VertexBuffer vertexBuffer = null;
        PipelineLayout pipelineLayout = null;
        Pipeline pipeline = null;
        MutableDescriptorSet descriptorSet = null;
        Uniform modelViewUniform = null;
        Uniform projectionUniform = null;
        CommandBuffer commandBuffer = graphics.beginFrame();
        boolean renderPassActive = false;
        boolean frameSubmitted = false;
        MatrixStack matrixStack = new MatrixStack();

        try {
            commandBuffer.beginRenderPass(TEST_RENDER_PASS, 0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);
            renderPassActive = true;

            shaderProgram = graphics.createShaderProgramFromPrecompiled("/shaders/test_triangle_matrix.vert.spv", "/shaders/test_triangle.frag.spv");
            pipelineLayout = graphics.createPipelineLayout(
                    createMatrixPipelineLayoutDescriptor("test-triangle-stack-layout")
            );
            VertexBuffer.VertexFormat format = new VertexBuffer.VertexFormat(
                    DataType.FLOAT,
                    DataType.FLOAT,
                    null,
                    null,
                    null,
                    true,
                    true,
                    false,
                    false,
                    false
            );
            pipeline = createTestPipeline("test-triangle-stack-pipeline", pipelineLayout, shaderProgram, format);
            modelViewUniform = graphics.createUniform(0, Uniform.ValueType.MAT4);
            projectionUniform = graphics.createUniform(4, Uniform.ValueType.MAT4);
            descriptorSet = new MutableDescriptorSet(pipelineLayout, List.of(modelViewUniform, projectionUniform));
            commandBuffer.setPipeline(pipeline);

            matrixStack.setMatrixMode(MatrixStack.MatrixMode.PROJECTION);
            matrixStack.loadIdentity();
            matrixStack.setMatrixMode(MatrixStack.MatrixMode.MODELVIEW);
            matrixStack.loadIdentity();

            // Lower frame operations that should affect rendering.
            matrixStack.translate(lowerTranslateX, lowerTranslateY, 0.0f);

            // Inner frame operations that must not affect rendering after pop.
            matrixStack.pushMatrix();
            matrixStack.translate(poppedTranslateX, poppedTranslateY, 0.0f);
            matrixStack.rotateZ(poppedRotateDegrees);
            matrixStack.popMatrix();

            bindMatrixUniforms(commandBuffer, matrixStack, descriptorSet);

            vertexBuffer = graphics.createVertexBuffer(BufferUsage.STATIC);

            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer vertexData = stack.malloc(3 * 6 * Float.BYTES);
                putVertex(vertexData, 1.0f, 0.0f, 0.0f, V0[0], V0[1], 0.0f);
                putVertex(vertexData, 0.0f, 1.0f, 0.0f, V1[0], V1[1], 0.0f);
                putVertex(vertexData, 0.0f, 0.0f, 1.0f, V2[0], V2[1], 0.0f);
                vertexData.flip();
                vertexBuffer.setData(vertexData, vertexData.remaining());
            }

            commandBuffer.draw(PrimitiveType.TRIANGLES, vertexBuffer, null, 0, 3);
            commandBuffer.endRenderPass();
            renderPassActive = false;
            graphics.endFrame();
            frameSubmitted = true;
            glFinish();

            verifyGradientAtNdcPoint(SCREEN_WIDTH, SCREEN_HEIGHT, -0.2f, -0.4f, expectedV0, expectedV1, expectedV2, COLOR_TOLERANCE);
            verifyGradientAtNdcPoint(SCREEN_WIDTH, SCREEN_HEIGHT, 0.3f, -0.4f, expectedV0, expectedV1, expectedV2, COLOR_TOLERANCE);
            verifyGradientAtNdcPoint(SCREEN_WIDTH, SCREEN_HEIGHT, 0.2f, 0.1f, expectedV0, expectedV1, expectedV2, COLOR_TOLERANCE);

            // This point should remain black if popMatrix worked.
            // It is intentionally chosen to be inside the leaked transform's triangle.
            assertPointInsideTriangle(0.9f, 0.3f, leakedV0, leakedV1, leakedV2);
            verifyBackgroundAtNdcPoint(SCREEN_WIDTH, SCREEN_HEIGHT, 0.9f, 0.3f, BACKGROUND_TOLERANCE);

            maybeExportPng(SCREEN_WIDTH, SCREEN_HEIGHT, "triangle-frame-stack-pop.png");
        } finally {
            if (!frameSubmitted) {
                if (renderPassActive) {
                    commandBuffer.endRenderPass();
                }
                graphics.endFrame();
            }
            if (pipeline != null) {
                pipeline.dispose();
            }
            if (descriptorSet != null) {
                descriptorSet.dispose();
            }
            if (pipelineLayout != null) {
                pipelineLayout.dispose();
            }
            if (vertexBuffer != null) {
                vertexBuffer.dispose();
            }
            if (shaderProgram != null) {
                shaderProgram.dispose();
            }
        }
    }

    private static void verifyGradientAtNdcPoint(int width, int height, float ndcX, float ndcY,
                                                 float[] v0, float[] v1, float[] v2, float tolerance) {
        int x = ndcToPixel(ndcX, width);
        int y = ndcToPixel(ndcY, height);

        float[] sampleNdc = pixelCenterToNdc(x, y, width, height);
        float[] bary = barycentric(sampleNdc[0], sampleNdc[1], v0, v1, v2);
        assertTrue(bary[0] > 0.0f && bary[1] > 0.0f && bary[2] > 0.0f,
                "Sample pixel must be inside triangle at ndc (" + ndcX + "," + ndcY + ")");

        float expectedR = bary[0];
        float expectedG = bary[1];
        float expectedB = bary[2];

        float[] actual = readPixelRgbaNormalized(x, y);
        assertTrue(Math.abs(actual[0] - expectedR) <= tolerance,
                "Red channel outside expected gradient range at (" + x + "," + y + ")");
        assertTrue(Math.abs(actual[1] - expectedG) <= tolerance,
                "Green channel outside expected gradient range at (" + x + "," + y + ")");
        assertTrue(Math.abs(actual[2] - expectedB) <= tolerance,
                "Blue channel outside expected gradient range at (" + x + "," + y + ")");
    }

    private static void verifyBackgroundAtNdcPoint(int width, int height, float ndcX, float ndcY, float tolerance) {
        int x = ndcToPixel(ndcX, width);
        int y = ndcToPixel(ndcY, height);
        float[] actual = readPixelRgbaNormalized(x, y);
        assertTrue(actual[0] <= tolerance, "Expected black red channel at (" + x + "," + y + ")");
        assertTrue(actual[1] <= tolerance, "Expected black green channel at (" + x + "," + y + ")");
        assertTrue(actual[2] <= tolerance, "Expected black blue channel at (" + x + "," + y + ")");
    }

    private static void assertPointInsideTriangle(float ndcX, float ndcY, float[] v0, float[] v1, float[] v2) {
        float[] bary = barycentric(ndcX, ndcY, v0, v1, v2);
        assertTrue(bary[0] > 0.0f && bary[1] > 0.0f && bary[2] > 0.0f,
                "Expected point (" + ndcX + "," + ndcY + ") to be inside reference triangle");
    }

    private static void putVertex(ByteBuffer buffer, float r, float g, float b, float x, float y, float z) {
        buffer.putFloat(r).putFloat(g).putFloat(b);
        buffer.putFloat(x).putFloat(y).putFloat(z);
    }

    private static void bindMatrixUniforms(CommandBuffer commandBuffer, MatrixStack matrixStack, MutableDescriptorSet descriptorSet) {
        Uniform modelViewUniform = descriptorSet.getRequired(PipelineLayout.BindingSemantic.MODEL_VIEW_MATRIX);
        Uniform projectionUniform = descriptorSet.getRequired(PipelineLayout.BindingSemantic.PROJECTION_MATRIX);
        modelViewUniform.setFloatBuffer(matrixStack.getModelViewBuffer());
        projectionUniform.setFloatBuffer(matrixStack.getProjectionBuffer());
        commandBuffer.bindDescriptorSet(descriptorSet);
    }

    private static Pipeline createTestPipeline(String debugName,
                                               PipelineLayout layout,
                                               ShaderProgram shaderProgram,
                                               VertexBuffer.VertexFormat vertexFormat) {
        return graphics.createPipeline(new Pipeline.Descriptor(
                debugName,
                layout,
                shaderProgram,
                vertexFormat,
                new Pipeline.BlendState(false, BlendFactor.ONE, BlendFactor.ZERO),
                new Pipeline.DepthState(false, false, CompareFunc.ALWAYS),
                new Pipeline.RasterizerState(CullMode.NONE, FillMode.SOLID)
        ));
    }

    private static PipelineLayout.Descriptor createMatrixPipelineLayoutDescriptor(String debugName) {
        return new PipelineLayout.Descriptor(
                debugName,
                List.of(
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
        );
    }

    private static float[] readPixelRgbaNormalized(int x, int y) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer pixel = stack.malloc(4);
            glReadPixels(x, y, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
            return new float[]{
                    (pixel.get(0) & 0xFF) / 255.0f,
                    (pixel.get(1) & 0xFF) / 255.0f,
                    (pixel.get(2) & 0xFF) / 255.0f,
                    (pixel.get(3) & 0xFF) / 255.0f
            };
        }
    }

    private static int ndcToPixel(float ndc, int size) {
        float pixel = ((ndc + 1.0f) * 0.5f) * size;
        int result = (int) Math.floor(pixel);
        if (result < 0) {
            return 0;
        }
        return Math.min(result, size - 1);
    }

    private static float[] pixelCenterToNdc(int x, int y, int width, int height) {
        float ndcX = (((x + 0.5f) / width) * 2.0f) - 1.0f;
        float ndcY = (((y + 0.5f) / height) * 2.0f) - 1.0f;
        return new float[]{ndcX, ndcY};
    }

    private static float[] barycentric(float px, float py, float[] a, float[] b, float[] c) {
        float denom = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1]);
        float w0 = ((b[1] - c[1]) * (px - c[0]) + (c[0] - b[0]) * (py - c[1])) / denom;
        float w1 = ((c[1] - a[1]) * (px - c[0]) + (a[0] - c[0]) * (py - c[1])) / denom;
        float w2 = 1.0f - w0 - w1;
        return new float[]{w0, w1, w2};
    }

    private static float[] transformVertex(float[] vertex, float tx, float ty, float angleDegrees) {
        float radians = (float) Math.toRadians(angleDegrees);
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        float x = vertex[0];
        float y = vertex[1];

        float rotatedX = x * cos - y * sin;
        float rotatedY = x * sin + y * cos;
        return new float[]{rotatedX + tx, rotatedY + ty};
    }

    private static void maybeExportPng(int width, int height, String fileName) throws Exception {
        if (!isInspectionModeEnabled()) {
            return;
        }

        ByteBuffer frame = BufferUtils.createByteBuffer(width * height * 4);
        glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, frame);

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int src = (y * width + x) * 4;
                int r = frame.get(src) & 0xFF;
                int g = frame.get(src + 1) & 0xFF;
                int b = frame.get(src + 2) & 0xFF;
                int a = frame.get(src + 3) & 0xFF;
                int argb = (a << 24) | (r << 16) | (g << 8) | b;
                image.setRGB(x, (height - 1) - y, argb);
            }
        }

        File outFile = new File("build/reports/neoblaze3d/" + fileName);
        File parent = outFile.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Failed to create directory: " + parent.getAbsolutePath());
        }
        ImageIO.write(image, "png", outFile);
    }

    private static boolean isInspectionModeEnabled() {
        if (Boolean.getBoolean("neoblaze3d.test.inspection")) {
            return true;
        }
        String env = System.getenv("NEOBLAZE3D_TEST_INSPECTION");
        if (env == null) {
            return false;
        }
        return "1".equals(env) || "true".equalsIgnoreCase(env);
    }
}
