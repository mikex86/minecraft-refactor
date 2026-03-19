package com.mojang.minecraft.renderer.block;

import com.mojang.minecraft.level.block.Block;
import com.mojang.minecraft.level.block.EnumFacing;
import com.mojang.minecraft.renderer.Tesselator;
import com.mojang.minecraft.renderer.graphics.CommandBuffer;
import com.mojang.minecraft.renderer.graphics.DataType;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums;
import com.mojang.minecraft.renderer.graphics.IndexedMesh;
import com.mojang.minecraft.renderer.graphics.MatrixUniforms;
import com.mojang.minecraft.renderer.graphics.MatrixStack;
import com.mojang.minecraft.renderer.graphics.MutableDescriptorSet;

import java.util.HashMap;
import java.util.Map;

public class BlockRenderer {
    private static final Map<Block, IndexedMesh> blockMeshes = new HashMap<>();

    public static void renderBlockPreview(CommandBuffer commandBuffer,
                                          MatrixStack matrixStack,
                                          Block block,
                                          float scale,
                                          MutableDescriptorSet noFogTerrainDescriptorSet) {
        matrixStack.scale(scale, scale, scale);
        matrixStack.rotateX(30.0F);
        matrixStack.rotateY(45.0F);
        matrixStack.scale(-1.0F, -1.0F, 1.0F);

        MatrixUniforms.writeStandardMatrices(noFogTerrainDescriptorSet, matrixStack);
        commandBuffer.bindDescriptorSet(noFogTerrainDescriptorSet);
        getBlockMesh(block).draw(commandBuffer);
    }

    public static IndexedMesh getBlockMesh(Block block) {
        IndexedMesh indexedMesh = blockMeshes.get(block);
        if (indexedMesh == null) {
            Tesselator t = Tesselator.instance;
            t.init(DataType.SHORT, DataType.HALF_FLOAT, true);
            block.render(t, null, null, 0, 0, 0, EnumFacing.UP);
            indexedMesh = t.createIndexedMesh(GraphicsEnums.BufferUsage.STATIC);
            blockMeshes.put(block, indexedMesh);
        }
        return indexedMesh;
    }
}
