package com.mojang.minecraft.renderer.block;

import com.mojang.minecraft.level.block.Block;
import com.mojang.minecraft.level.block.EnumFacing;
import com.mojang.minecraft.renderer.Tesselator;
import com.mojang.minecraft.renderer.graphics.GraphicsAPI;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums;
import com.mojang.minecraft.renderer.graphics.IndexedMesh;

import java.util.HashMap;
import java.util.Map;

public class BlockPreviewRenderer {

    private static final Map<Block, IndexedMesh> blockMeshes = new HashMap<>();

    public static void renderBlock(GraphicsAPI graphics, Block block, int scale) {
        graphics.scale(scale, scale, scale);
        graphics.rotateX(30.0F);
        graphics.rotateY(45.0F);
        graphics.scale(-1.0F, -1.0F, 1.0F);

        graphics.updateShaderMatrices();
        getBlockMesh(block).draw(graphics);
    }

    private static IndexedMesh getBlockMesh(Block block) {
        IndexedMesh indexedMesh = blockMeshes.get(block);
        if (indexedMesh == null) {
            Tesselator t = Tesselator.instance;
            t.init();
            block.render(t, null, 0, 0, 0, EnumFacing.UP);
            indexedMesh = t.createIndexedMesh(GraphicsEnums.BufferUsage.STATIC);
            blockMeshes.put(block, indexedMesh);
        }
        return indexedMesh;
    }
}
