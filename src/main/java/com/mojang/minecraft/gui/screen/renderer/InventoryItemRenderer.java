package com.mojang.minecraft.gui.screen.renderer;

import com.mojang.minecraft.gui.TextLabel;
import com.mojang.minecraft.gui.screen.AbstractInventoryScreen;
import com.mojang.minecraft.gui.screen.InventoryScreen;
import com.mojang.minecraft.item.BlockItem;
import com.mojang.minecraft.item.HeldItem;
import com.mojang.minecraft.item.Item;
import com.mojang.minecraft.item.ItemStack;
import com.mojang.minecraft.item.inventory.Inventory;
import com.mojang.minecraft.optim.pools.StackCountStringPool;
import com.mojang.minecraft.renderer.TextureManager;
import com.mojang.minecraft.renderer.block.BlockRenderer;
import com.mojang.minecraft.renderer.graphics.CommandBuffer;
import com.mojang.minecraft.renderer.graphics.MatrixStack;
import com.mojang.minecraft.renderer.graphics.Pipeline;
import com.mojang.minecraft.renderer.graphics.MutableDescriptorSet;
import com.mojang.minecraft.renderer.item.HeldItemRenderer;
import com.mojang.minecraft.renderer.shader.PipelineRegistry;

import static com.mojang.minecraft.gui.screen.AbstractInventoryScreen.ITEM_SLOT_SIZE;
import static com.mojang.minecraft.gui.screen.InventoryScreen.BLOCK_ITEM_SCALE_FACTOR;
import static com.mojang.minecraft.gui.screen.InventoryScreen.HELD_ITEM_SCALE_FACTOR;

public class InventoryItemRenderer {

    private final Pipeline worldPipeline;
    private final Pipeline hudPipeline;
    private final Pipeline hudNoCullPipeline;
    private final MutableDescriptorSet noFogTerrainDescriptorSet;
    private final MutableDescriptorSet noFogItemsDescriptorSet;

    public InventoryItemRenderer(TextureManager textureManager, PipelineRegistry pipelineRegistry) {
        this.worldPipeline = pipelineRegistry.getWorldPipeline();
        this.hudPipeline = pipelineRegistry.getHudPipeline();
        this.hudNoCullPipeline = pipelineRegistry.getHudNoCullPipeline();
        this.noFogTerrainDescriptorSet = pipelineRegistry.getDescriptorSet(textureManager.terrainTexture, PipelineRegistry.FogPreset.NONE);
        this.noFogItemsDescriptorSet = pipelineRegistry.getDescriptorSet(textureManager.itemsTexture, PipelineRegistry.FogPreset.NONE);
    }

    public void renderInventoryItems(CommandBuffer commandBuffer, MatrixStack matrixStack, HeldItemRenderer heldItemRenderer, AbstractInventoryScreen inventoryScreen, float centerX, float centerY) {

        // set world shader
        commandBuffer.setPipeline(worldPipeline);

        // set terrain texture
        MutableDescriptorSet descriptorSet = noFogTerrainDescriptorSet;
        commandBuffer.bindDescriptorSet(descriptorSet);

        for (InventoryScreen.Slot slot : inventoryScreen.slots) {
            ItemStack itemStack = slot.getItemStack();
            if (itemStack == null) {
                continue;
            }
            Item item = itemStack.getItem();
            matrixStack.pushMatrix();
            matrixStack.translate(slot.getItemRenderX(centerX), slot.getItemRenderY(centerY), 0);
            if (item instanceof BlockItem) {
                BlockItem blockItem = (BlockItem) item;
                BlockRenderer.renderBlockPreview(commandBuffer, matrixStack, blockItem.getBlock(), BLOCK_ITEM_SCALE_FACTOR, noFogTerrainDescriptorSet);
            }
            matrixStack.popMatrix();
        }

        // set items texture
        commandBuffer.setPipeline(hudNoCullPipeline);
        descriptorSet = noFogItemsDescriptorSet;
        commandBuffer.bindDescriptorSet(descriptorSet);

        for (InventoryScreen.Slot slot : inventoryScreen.slots) {
            ItemStack itemStack = slot.getItemStack();
            if (itemStack == null) {
                continue;
            }
            Item item = itemStack.getItem();
            matrixStack.pushMatrix();
            matrixStack.translate(slot.getItemRenderX(centerX) - HELD_ITEM_SCALE_FACTOR / 2f, slot.getItemRenderY(centerY) - HELD_ITEM_SCALE_FACTOR, 0);
            if (item instanceof HeldItem) {
                HeldItem blockItem = (HeldItem) item;
                heldItemRenderer.renderHeldItemPreview(commandBuffer, matrixStack, blockItem, HELD_ITEM_SCALE_FACTOR);
            }
            matrixStack.popMatrix();
        }

        // draw stack size labels
        {
            commandBuffer.setPipeline(hudPipeline);

            for (InventoryScreen.Slot slot : inventoryScreen.slots) {
                ItemStack itemStack = slot.getItemStack();
                if (itemStack == null) {
                    continue;
                }
                int count = itemStack.getCount();
                if (count > 1) {
                    slot.renderStackSize(commandBuffer, matrixStack, centerX, centerY, count);
                }
            }
        }
    }

    public void drawSelectedItem(CommandBuffer commandBuffer, MatrixStack matrixStack, HeldItemRenderer heldItemRenderer, Inventory inventory, float mouseX, float mouseY, TextLabel stackSizeSelectedItemLabel) {
        // draw selected item at cursor position
        {
            // set terrain texture again after drawing labels and the player

            ItemStack selectedItem = inventory.getSelectedItem();
            if (selectedItem != null) {
                Item item = selectedItem.getItem();
                if (item instanceof BlockItem) {
                    commandBuffer.setPipeline(worldPipeline);
                    MutableDescriptorSet descriptorSet = noFogTerrainDescriptorSet;
                    commandBuffer.bindDescriptorSet(descriptorSet);
                    BlockItem blockItem = (BlockItem) item;
                    matrixStack.pushMatrix();
                    matrixStack.translate(mouseX, mouseY + ITEM_SLOT_SIZE / 2f, 0);
                    BlockRenderer.renderBlockPreview(commandBuffer, matrixStack, blockItem.getBlock(), BLOCK_ITEM_SCALE_FACTOR, noFogTerrainDescriptorSet);
                    matrixStack.popMatrix();
                } else if (item instanceof HeldItem) {
                    commandBuffer.setPipeline(hudNoCullPipeline);
                    MutableDescriptorSet descriptorSet = noFogItemsDescriptorSet;
                    commandBuffer.bindDescriptorSet(descriptorSet);
                    HeldItem heldItem = (HeldItem) item;
                    matrixStack.pushMatrix();
                    matrixStack.translate(mouseX - HELD_ITEM_SCALE_FACTOR / 2f, mouseY - HELD_ITEM_SCALE_FACTOR / 2f, 0);
                    heldItemRenderer.renderHeldItemPreview(commandBuffer, matrixStack, heldItem, HELD_ITEM_SCALE_FACTOR);
                    matrixStack.popMatrix();
                }
            }

            // draw selected item stack size label
            if (selectedItem != null) {
                commandBuffer.setPipeline(hudPipeline);
                int count = selectedItem.getCount();
                if (count > 1) {
                    stackSizeSelectedItemLabel.setText(StackCountStringPool.valueOf(count));
                    stackSizeSelectedItemLabel.render(commandBuffer, matrixStack, mouseX + 9 - stackSizeSelectedItemLabel.getWidth(), mouseY + 2);
                }
            }
        }
    }
}
