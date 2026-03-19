package com.mojang.minecraft.renderer.item;

import com.mojang.minecraft.item.HeldItem;
import com.mojang.minecraft.renderer.Tesselator;
import com.mojang.minecraft.renderer.TextureManager;
import com.mojang.minecraft.renderer.graphics.CommandBuffer;
import com.mojang.minecraft.renderer.graphics.GraphicsFactory;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums;
import com.mojang.minecraft.renderer.graphics.IndexedMesh;
import com.mojang.minecraft.renderer.graphics.MatrixStack;
import com.mojang.minecraft.renderer.graphics.Texture;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class HeldItemRenderer {

    private final Map<HeldItem, IndexedMesh> itemQuadMeshes = new HashMap<>();

    private static final int ITEM_TEXTURE_SIZE = 16;
    private static final float ITEM_DEPTH = 1.0F / 16.0F;

    private final ByteBuffer itemsTextureData;
    private final int itemsTextureWidth;
    private final int itemsTextureHeight;

    public HeldItemRenderer(TextureManager textureManager, Texture itemsTexture) {
        ByteBuffer hostData = textureManager.getRetainedTextureData(itemsTexture)
                .orElseThrow(() -> new IllegalStateException("Items texture does not have retained CPU data"));
        this.itemsTextureWidth = itemsTexture.getWidth();
        this.itemsTextureHeight = itemsTexture.getHeight();
        this.itemsTextureData = hostData;
    }

    public void renderHeldItemPreview(CommandBuffer graphics, MatrixStack matrixStack, HeldItem heldItem, int itemSize) {
        matrixStack.pushMatrix();
        try {
            // GUI space has Y going down; flip to keep item upright.
            matrixStack.translate(0.0F, itemSize, 0.0F);
            matrixStack.scale(1.0F, -1.0F, 1.0F);
            renderHeldItemModel(graphics, matrixStack, heldItem, itemSize);
        } finally {
            matrixStack.popMatrix();
        }
    }

    private int getTextureU(int tex) {
        return tex % 16 * 16;
    }

    private int getTextureV(int tex) {
        return tex / 16 * 16;
    }

    private boolean isPixelTransparent(int texX, int texY) {
        if (texX < 0 || texY < 0 || texX >= itemsTextureWidth || texY >= itemsTextureHeight) {
            return true;
        }

        int alphaIndex = (texY * itemsTextureWidth + texX) * 4 + 3;
        return (itemsTextureData.get(alphaIndex) & 0xFF) == 0;
    }

    public void renderHeldItemModel(CommandBuffer graphics, MatrixStack matrixStack, HeldItem heldItem, int itemSize) {
        IndexedMesh itemQuadMesh = getItemQuadMesh(heldItem);

        matrixStack.scale(itemSize, itemSize, itemSize);
        GraphicsFactory.getGraphicsAPI().bindCurrentMatrices(matrixStack);
        itemQuadMesh.draw(graphics);
    }

    private IndexedMesh getItemQuadMesh(HeldItem heldItem) {
        IndexedMesh itemQuadMesh = itemQuadMeshes.get(heldItem);
        if (itemQuadMesh == null) {
            itemQuadMesh = buildItemMesh(heldItem.getTextureId());
            itemQuadMeshes.put(heldItem, itemQuadMesh);
        }
        return itemQuadMesh;
    }

    /**
     * Builds an extruded quad mesh for flat held items, matching the generated
     * item model thickness used by vanilla (1/16th of a block).
     */
    private IndexedMesh buildItemMesh(int textureId) {
        Tesselator t = Tesselator.instance;
        t.init();

        int u = getTextureU(textureId);
        int v = getTextureV(textureId);

        float atlasWidth = (float) itemsTextureWidth;
        float atlasHeight = (float) itemsTextureHeight;

        float minU = u / atlasWidth;
        float minV = v / atlasHeight;
        float maxU = (u + ITEM_TEXTURE_SIZE) / atlasWidth;
        float maxV = (v + ITEM_TEXTURE_SIZE) / atlasHeight;

        float uStep = (maxU - minU) / ITEM_TEXTURE_SIZE;
        float vStep = (maxV - minV) / ITEM_TEXTURE_SIZE;

        float zFront = 0.0F;
        float zBack = -ITEM_DEPTH;

        // Front (normal +Z) CCW when viewed from +Z
        t.color(1.0F, 1.0F, 1.0F);
        t.vertexUV(0.0F, 0.0F, zFront, minU, maxV);
        t.vertexUV(1.0F, 0.0F, zFront, maxU, maxV);
        t.vertexUV(1.0F, 1.0F, zFront, maxU, minV);
        t.vertexUV(0.0F, 1.0F, zFront, minU, minV);

        // Back (normal -Z) CCW when viewed from -Z
        t.vertexUV(0.0F, 0.0F, zBack, minU, maxV);
        t.vertexUV(0.0F, 1.0F, zBack, minU, minV);
        t.vertexUV(1.0F, 1.0F, zBack, maxU, minV);
        t.vertexUV(1.0F, 0.0F, zBack, maxU, maxV);

        for (int py = 0; py < ITEM_TEXTURE_SIZE; py++) {
            for (int px = 0; px < ITEM_TEXTURE_SIZE; px++) {
                int atlasX = u + px;
                int atlasY = v + py;

                if (isPixelTransparent(atlasX, atlasY)) {
                    continue;
                }

                float x0 = px / (float) ITEM_TEXTURE_SIZE;
                float x1 = (px + 1) / (float) ITEM_TEXTURE_SIZE;
                float y0 = (ITEM_TEXTURE_SIZE - py - 1) / (float) ITEM_TEXTURE_SIZE;
                float y1 = (ITEM_TEXTURE_SIZE - py) / (float) ITEM_TEXTURE_SIZE;

                float u0 = minU + px * uStep;
                float u1 = u0 + uStep;
                float v0 = minV + py * vStep;
                float v1 = v0 + vStep;

                boolean leftTransparent = px == 0 || isPixelTransparent(atlasX - 1, atlasY);
                boolean rightTransparent = px == ITEM_TEXTURE_SIZE - 1 || isPixelTransparent(atlasX + 1, atlasY);
                boolean upTransparent = py == 0 || isPixelTransparent(atlasX, atlasY - 1);
                boolean downTransparent = py == ITEM_TEXTURE_SIZE - 1 || isPixelTransparent(atlasX, atlasY + 1);

                if (leftTransparent) {
                    t.color(0.8F, 0.8F, 0.8F);
                    t.vertexUV(x0, y0, zBack, u0 + uStep, v1);
                    t.vertexUV(x0, y1, zBack, u0 + uStep, v0);
                    t.vertexUV(x0, y1, zFront, u0, v0);
                    t.vertexUV(x0, y0, zFront, u0, v1);
                }

                if (rightTransparent) {
                    t.color(0.8F, 0.8F, 0.8F);
                    t.vertexUV(x1, y0, zFront, u1, v1);
                    t.vertexUV(x1, y1, zFront, u1, v0);
                    t.vertexUV(x1, y1, zBack, u1 - uStep, v0);
                    t.vertexUV(x1, y0, zBack, u1 - uStep, v1);
                }

                if (upTransparent) {
                    t.color(0.7F, 0.7F, 0.7F);
                    t.vertexUV(x0, y1, zFront, u0, v0);
                    t.vertexUV(x1, y1, zFront, u1, v0);
                    t.vertexUV(x1, y1, zBack, u1, v0 + vStep);
                    t.vertexUV(x0, y1, zBack, u0, v0 + vStep);
                }

                if (downTransparent) {
                    t.color(0.7F, 0.7F, 0.7F);
                    t.vertexUV(x0, y0, zBack, u0, v1 - vStep);
                    t.vertexUV(x1, y0, zBack, u1, v1 - vStep);
                    t.vertexUV(x1, y0, zFront, u1, v1);
                    t.vertexUV(x0, y0, zFront, u0, v1);
                }
            }
        }

        return t.createIndexedMesh(GraphicsEnums.BufferUsage.STATIC);
    }


}
