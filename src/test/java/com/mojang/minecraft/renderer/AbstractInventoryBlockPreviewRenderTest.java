package com.mojang.minecraft.renderer;

import com.mojang.minecraft.level.block.Blocks;
import com.mojang.minecraft.renderer.block.BlockRenderer;
import com.mojang.minecraft.renderer.graphics.CommandBuffer;
import com.mojang.minecraft.renderer.graphics.GraphicsAPI;
import com.mojang.minecraft.renderer.graphics.GraphicsFactory;
import com.mojang.minecraft.renderer.graphics.ImmutableDescriptorSet;
import com.mojang.minecraft.renderer.graphics.MatrixStack;
import com.mojang.minecraft.renderer.graphics.RenderPassAttachments;
import com.mojang.minecraft.renderer.graphics.vulkan.VulkanGraphicsAPI;
import com.mojang.minecraft.renderer.shader.PipelineRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.lwjgl.BufferUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glFinish;
import static org.lwjgl.opengl.GL11.glReadPixels;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractInventoryBlockPreviewRenderTest {
    private static final int SCREEN_WIDTH = 384;
    private static final int SCREEN_HEIGHT = 384;
    private static final float BLOCK_PREVIEW_SCALE = 128.0f;
    private static final float CENTERING_PROBE_DELTA = 16.0f;
    private static final float ORTHO_NEAR = 1.0f;
    private static final float ORTHO_FAR = 500.0f;
    private static final float CAMERA_Z = -200.0f;
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

    private GameWindow window;
    private GraphicsAPI graphics;
    private TextureManager textureManager;
    private PipelineRegistry pipelineRegistry;

    protected abstract GraphicsAPI.Backend requestedBackend();

    @BeforeAll
    void initializeGraphicsContext() {
        try {
            System.setProperty("neoblaze3d.backend", requestedBackend().name());
            GraphicsFactory.reset();
            window = new GameWindow(SCREEN_WIDTH, SCREEN_HEIGHT, "inventory-block-preview-test", false);
            graphics = GraphicsFactory.getGraphicsAPI();
            assertEquals(requestedBackend(), graphics.getBackend(), "This test must run with backend " + requestedBackend());

            textureManager = new TextureManager();
            textureManager.loadTextures();

            pipelineRegistry = new PipelineRegistry();
            pipelineRegistry.initialize();
            pipelineRegistry.configureSharedDescriptorSets(textureManager);
        } catch (Throwable t) {
            fail("Failed to initialize context/resources for " + getClass().getSimpleName(), t);
        }
    }

    @AfterAll
    void destroyGraphicsContext() {
        if (pipelineRegistry != null) {
            pipelineRegistry.dispose();
            pipelineRegistry = null;
        }
        if (textureManager != null) {
            textureManager.dispose();
            textureManager = null;
        }
        if (window != null) {
            window.dispose();
            window = null;
        }
        graphics = null;
        System.clearProperty("neoblaze3d.backend");
    }

    @Test
    void rendersGrassBlockPreviewAndExportsPng() throws Exception {
        ImmutableDescriptorSet noFogTerrainDescriptorSet =
                pipelineRegistry.getDescriptorSet(textureManager.terrainTexture, PipelineRegistry.FogPreset.NONE);
        float centerX = SCREEN_WIDTH * 0.5f;
        float centerY = SCREEN_HEIGHT * 0.5f;

        float translateX = centerX;
        float translateY = centerY;

        ByteBuffer baseFrame = renderBlockPreviewFrame(noFogTerrainDescriptorSet, translateX, translateY);
        float[] baseCentroid = calculateVisibleCentroid(baseFrame, SCREEN_WIDTH, SCREEN_HEIGHT);

        ByteBuffer probeXFrame = renderBlockPreviewFrame(noFogTerrainDescriptorSet, translateX + CENTERING_PROBE_DELTA, translateY);
        float[] probeXCentroid = calculateVisibleCentroid(probeXFrame, SCREEN_WIDTH, SCREEN_HEIGHT);
        float xSlope = (probeXCentroid[0] - baseCentroid[0]) / CENTERING_PROBE_DELTA;
        if (Math.abs(xSlope) < 0.01f) {
            xSlope = 1.0f;
        }

        ByteBuffer probeYFrame = renderBlockPreviewFrame(noFogTerrainDescriptorSet, translateX, translateY + CENTERING_PROBE_DELTA);
        float[] probeYCentroid = calculateVisibleCentroid(probeYFrame, SCREEN_WIDTH, SCREEN_HEIGHT);
        float ySlope = (probeYCentroid[1] - baseCentroid[1]) / CENTERING_PROBE_DELTA;
        if (Math.abs(ySlope) < 0.01f) {
            ySlope = -1.0f;
        }

        translateX += (centerX - baseCentroid[0]) / xSlope;
        translateY += (centerY - baseCentroid[1]) / ySlope;

        ByteBuffer frame = renderBlockPreviewFrame(noFogTerrainDescriptorSet, translateX, translateY);

        File output = new File(requestedBackend().name().toLowerCase() + "-grass-block-preview.png");
        writePng(frame, SCREEN_WIDTH, SCREEN_HEIGHT, output);

        int nonBackgroundPixels = countNonBackgroundPixels(frame, SCREEN_WIDTH, SCREEN_HEIGHT);
        assertTrue(nonBackgroundPixels > 40,
                "Expected a visible rendered grass block preview, but image appears nearly empty");

        float[] visibleCentroid = calculateVisibleCentroid(frame, SCREEN_WIDTH, SCREEN_HEIGHT);
        float centeringTolerance = 12.0f;
        assertTrue(Math.abs(visibleCentroid[0] - centerX) <= centeringTolerance,
                "Expected block preview centroid X near center. actual=" + visibleCentroid[0] + ", expected=" + centerX);
        assertTrue(Math.abs(visibleCentroid[1] - centerY) <= centeringTolerance,
                "Expected block preview centroid Y near center. actual=" + visibleCentroid[1] + ", expected=" + centerY);

        assertTrue(output.exists(), "Expected PNG artifact to exist: " + output.getAbsolutePath());
        assertTrue(output.length() > 0L, "Expected PNG artifact to be non-empty: " + output.getAbsolutePath());
    }

    private ByteBuffer renderBlockPreviewFrame(ImmutableDescriptorSet noFogTerrainDescriptorSet, float translateX, float translateY) {
        MatrixStack matrixStack = new MatrixStack();
        matrixStack.setMatrixMode(MatrixStack.MatrixMode.PROJECTION);
        matrixStack.loadIdentity();
        matrixStack.setOrthographic(0.0f, SCREEN_WIDTH, SCREEN_HEIGHT, 0.0f, ORTHO_NEAR, ORTHO_FAR);
        matrixStack.setMatrixMode(MatrixStack.MatrixMode.MODELVIEW);
        matrixStack.loadIdentity();
        matrixStack.translate(0.0f, 0.0f, CAMERA_Z);

        CommandBuffer commandBuffer = graphics.beginFrame();
        boolean renderPassActive = false;
        boolean frameSubmitted = false;
        try {
            commandBuffer.beginRenderPass(TEST_RENDER_PASS, 0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);
            renderPassActive = true;

            commandBuffer.setPipeline(pipelineRegistry.getWorldPipeline());
            commandBuffer.bindDescriptorSet(noFogTerrainDescriptorSet);

            matrixStack.pushMatrix();
            matrixStack.translate(translateX, translateY, 0.0f);
            BlockRenderer.renderBlockPreview(
                    commandBuffer,
                    matrixStack,
                    Blocks.grass,
                    BLOCK_PREVIEW_SCALE,
                    noFogTerrainDescriptorSet
            );
            matrixStack.popMatrix();

            commandBuffer.endRenderPass();
            renderPassActive = false;

            if (isVulkanBackend()) {
                ((VulkanGraphicsAPI) graphics).requestCurrentFrameCaptureRgba();
            }
            graphics.endFrame();
            frameSubmitted = true;

            if (isVulkanBackend()) {
                ByteBuffer captured = ((VulkanGraphicsAPI) graphics).consumeLastFrameCaptureRgba();
                assertNotNull(captured, "Vulkan frame capture returned null");
                return captured;
            }

            glFinish();
        } finally {
            if (!frameSubmitted) {
                if (renderPassActive) {
                    commandBuffer.endRenderPass();
                }
                graphics.endFrame();
            }
        }

        ByteBuffer frame = BufferUtils.createByteBuffer(SCREEN_WIDTH * SCREEN_HEIGHT * 4);
        glReadPixels(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT, GL_RGBA, GL_UNSIGNED_BYTE, frame);
        return frame;
    }

    private boolean isVulkanBackend() {
        return graphics.getBackend() == GraphicsAPI.Backend.VULKAN;
    }

    private int countNonBackgroundPixels(ByteBuffer frame, int width, int height) {
        int visible = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int src = (y * width + x) * 4;
                int r = frame.get(src) & 0xFF;
                int g = frame.get(src + 1) & 0xFF;
                int b = frame.get(src + 2) & 0xFF;
                if (r + g + b > 18) {
                    visible++;
                }
            }
        }
        return visible;
    }

    private float[] calculateVisibleCentroid(ByteBuffer frame, int width, int height) {
        long sumX = 0L;
        long sumY = 0L;
        long count = 0L;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int src = (y * width + x) * 4;
                int r = frame.get(src) & 0xFF;
                int g = frame.get(src + 1) & 0xFF;
                int b = frame.get(src + 2) & 0xFF;
                if (r + g + b > 18) {
                    sumX += x;
                    sumY += y;
                    count++;
                }
            }
        }
        if (count == 0L) {
            return new float[]{0.0f, 0.0f};
        }
        return new float[]{(float) sumX / count, (float) sumY / count};
    }

    private void writePng(ByteBuffer frame, int width, int height, File output) throws Exception {
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
        ImageIO.write(image, "png", output);
    }
}
