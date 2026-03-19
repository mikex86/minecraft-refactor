package com.mojang.minecraft.renderer;

import com.mojang.minecraft.renderer.graphics.DataType;
import com.mojang.minecraft.renderer.graphics.GraphicsAPI;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.BlendFactor;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.BufferUsage;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.CompareFunc;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.CullMode;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.FillMode;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums.PrimitiveType;
import com.mojang.minecraft.renderer.graphics.GraphicsFactory;
import com.mojang.minecraft.renderer.graphics.VertexArrayObject;
import com.mojang.minecraft.renderer.graphics.VertexBuffer;
import com.mojang.minecraft.renderer.shader.Shader;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glFinish;
import static org.lwjgl.opengl.GL11.glReadPixels;

class HeadlessTriangleRenderTest {

    private static final float[] V0 = {-0.8f, -0.8f};
    private static final float[] V1 = {0.8f, -0.8f};
    private static final float[] V2 = {0.0f, 0.8f};

    @Test
    void rendersGradientTriangleAtExpectedPixels() throws Exception {
        final int width = 128;
        final int height = 128;

        GameWindow window;
        try {
            window = new GameWindow(width, height, "neoblaze3d-test", false);
        } catch (Throwable t) {
            Assumptions.assumeTrue(false, "Skipping OpenGL test. Could not initialize hidden context: " + t.getMessage());
            return;
        }

        Shader shader = null;
        VertexBuffer vertexBuffer = null;
        VertexArrayObject vao = null;

        try {
            GraphicsAPI graphics = GraphicsFactory.getGraphicsAPI();
            graphics.setViewport(0, 0, width, height);
            graphics.setDepthState(false, false, CompareFunc.ALWAYS);
            graphics.setBlendState(false, BlendFactor.ONE, BlendFactor.ZERO);
            graphics.setRasterizerState(CullMode.NONE, FillMode.SOLID);
            graphics.clear(true, true, 0.0f, 0.0f, 0.0f, 1.0f);

            shader = new Shader("/shaders/test_triangle.vert", "/shaders/test_triangle.frag");
            graphics.setShader(shader);

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

            vertexBuffer = graphics.createVertexBuffer(BufferUsage.STATIC);
            vertexBuffer.setFormat(format);

            ByteBuffer vertexData = BufferUtils.createByteBuffer(3 * 6 * Float.BYTES);
            // OpenGLVertexArrayObject expects color first, then position in this format.
            putVertex(vertexData, 1.0f, 0.0f, 0.0f, V0[0], V0[1], 0.0f);
            putVertex(vertexData, 0.0f, 1.0f, 0.0f, V1[0], V1[1], 0.0f);
            putVertex(vertexData, 0.0f, 0.0f, 1.0f, V2[0], V2[1], 0.0f);
            vertexData.flip();
            vertexBuffer.setData(vertexData, vertexData.remaining());

            vao = graphics.createVertexArrayObject();
            vao.setVertexBuffer(vertexBuffer);

            graphics.drawPrimitives(vao, PrimitiveType.TRIANGLES, 0, 3);
            glFinish();

            verifyGradientAtNdcPoint(width, height, -0.3f, -0.3f);
            verifyGradientAtNdcPoint(width, height, 0.3f, -0.3f);
            verifyGradientAtNdcPoint(width, height, 0.0f, 0.2f);

            maybeExportPng(width, height);
        } finally {
            if (vao != null) {
                vao.dispose();
            }
            if (vertexBuffer != null) {
                vertexBuffer.dispose();
            }
            if (shader != null) {
                shader.dispose();
            }
            window.dispose();
        }
    }

    private static void verifyGradientAtNdcPoint(int width, int height, float ndcX, float ndcY) {
        int x = ndcToPixel(ndcX, width);
        int y = ndcToPixel(ndcY, height);

        float[] sampleNdc = pixelCenterToNdc(x, y, width, height);
        float[] bary = barycentric(sampleNdc[0], sampleNdc[1], V0, V1, V2);
        assertTrue(bary[0] > 0.0f && bary[1] > 0.0f && bary[2] > 0.0f, "Sample pixel must be inside triangle");

        float expectedR = bary[0];
        float expectedG = bary[1];
        float expectedB = bary[2];

        float[] actual = readPixelRgbaNormalized(x, y);
        float tolerance = 0.16f;

        assertTrue(Math.abs(actual[0] - expectedR) <= tolerance,
                "Red channel outside expected gradient range at (" + x + "," + y + ")");
        assertTrue(Math.abs(actual[1] - expectedG) <= tolerance,
                "Green channel outside expected gradient range at (" + x + "," + y + ")");
        assertTrue(Math.abs(actual[2] - expectedB) <= tolerance,
                "Blue channel outside expected gradient range at (" + x + "," + y + ")");
    }

    private static void putVertex(ByteBuffer buffer, float r, float g, float b, float x, float y, float z) {
        buffer.putFloat(r).putFloat(g).putFloat(b);
        buffer.putFloat(x).putFloat(y).putFloat(z);
    }

    private static float[] readPixelRgbaNormalized(int x, int y) {
        ByteBuffer pixel = BufferUtils.createByteBuffer(4);
        glReadPixels(x, y, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
        return new float[]{
                (pixel.get(0) & 0xFF) / 255.0f,
                (pixel.get(1) & 0xFF) / 255.0f,
                (pixel.get(2) & 0xFF) / 255.0f,
                (pixel.get(3) & 0xFF) / 255.0f
        };
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

    private static void maybeExportPng(int width, int height) throws Exception {
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

        File outFile = new File("triangle-frame.png");
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
