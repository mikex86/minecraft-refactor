package com.mojang.minecraft.renderer.block;

import com.mojang.minecraft.level.block.Block;
import com.mojang.minecraft.level.block.EnumFacing;
import com.mojang.minecraft.renderer.Tesselator;
import com.mojang.minecraft.renderer.graphics.CommandBuffer;
import com.mojang.minecraft.renderer.graphics.DataType;
import com.mojang.minecraft.renderer.graphics.GraphicsAPI;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums;
import com.mojang.minecraft.renderer.graphics.GraphicsFactory;
import com.mojang.minecraft.renderer.graphics.IndexedMesh;
import com.mojang.minecraft.renderer.graphics.MatrixUniforms;
import com.mojang.minecraft.renderer.graphics.MatrixStack;
import com.mojang.minecraft.renderer.graphics.ImmutableDescriptorSet;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class BlockRenderer {
    private static final Map<GraphicsAPI.Backend, Map<Block, IndexedMesh>> blockMeshesByBackend =
            new EnumMap<>(GraphicsAPI.Backend.class);

    public static void renderBlockPreview(CommandBuffer commandBuffer,
                                          MatrixStack matrixStack,
                                          Block block,
                                          float scale,
                                          ImmutableDescriptorSet noFogTerrainDescriptorSet) {
        matrixStack.scale(scale, scale, scale);
        matrixStack.rotateX(30.0F);
        matrixStack.rotateY(45.0F);
        matrixStack.scale(-1.0F, -1.0F, 1.0F);

        MatrixUniforms.writeStandardMatrices(noFogTerrainDescriptorSet, matrixStack);
        commandBuffer.bindDescriptorSet(noFogTerrainDescriptorSet);
        getBlockMesh(block).draw(commandBuffer);
    }

    public static IndexedMesh getBlockMesh(Block block) {
        GraphicsAPI.Backend backend = GraphicsFactory.getGraphicsAPI().getBackend();
        Map<Block, IndexedMesh> backendMeshes =
                blockMeshesByBackend.computeIfAbsent(backend, key -> new HashMap<>());

        IndexedMesh indexedMesh = backendMeshes.get(block);
        if (indexedMesh == null) {
            Tesselator t = new Tesselator();
            try {
                t.init(DataType.FLOAT, DataType.FLOAT, true);
                block.render(t, null, null, 0, 0, 0, EnumFacing.UP);
                indexedMesh = t.createIndexedMesh(GraphicsEnums.BufferUsage.STATIC);
            } finally {
                t.dispose();
            }
            backendMeshes.put(block, indexedMesh);
        }
        return indexedMesh;
    }

    public static void disposeAllCachedMeshes() {
        for (Map<Block, IndexedMesh> backendMeshes : blockMeshesByBackend.values()) {
            if (backendMeshes == null) {
                continue;
            }
            for (IndexedMesh mesh : backendMeshes.values()) {
                if (mesh != null) {
                    mesh.dispose();
                }
            }
            backendMeshes.clear();
        }
        blockMeshesByBackend.clear();
    }
}
