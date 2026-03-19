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
import com.mojang.minecraft.renderer.item.HeldItemRenderer;
import com.mojang.minecraft.renderer.shader.PipelineRegistry;
import com.mojang.minecraft.renderer.shader.impl.WorldShader;

import static com.mojang.minecraft.gui.screen.AbstractInventoryScreen.ITEM_SLOT_SIZE;
import static com.mojang.minecraft.gui.screen.InventoryScreen.BLOCK_ITEM_SCALE_FACTOR;
import static com.mojang.minecraft.gui.screen.InventoryScreen.HELD_ITEM_SCALE_FACTOR;

public class InventoryItemRenderer {

    private static final WorldShader WORLD_SHADER = PipelineRegistry.getInstance().getWorldShader();
    private static final Pipeline WORLD_PIPELINE = PipelineRegistry.getInstance().getWorldPipeline();
    private static final Pipeline HUD_PIPELINE = PipelineRegistry.getInstance().getHudPipeline();
    private static final Pipeline HUD_NO_CULL_PIPELINE = PipelineRegistry.getInstance().getHudNoCullPipeline();

    public static void renderInventoryItems(CommandBuffer graphics, MatrixStack matrixStack, TextureManager textureManager, HeldItemRenderer heldItemRenderer, AbstractInventoryScreen inventoryScreen, float centerX, float centerY) {

        // set world shader
        graphics.setPipeline(WORLD_PIPELINE);
        WORLD_SHADER.setFogUniforms(0f, 0f, 0f, 0f, 0f, 0f, 0f);

        // set terrain texture
        graphics.setTexture(textureManager.terrainTexture);

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
                BlockRenderer.renderBlockPreview(graphics, matrixStack, blockItem.getBlock(), BLOCK_ITEM_SCALE_FACTOR
                );
            }
            matrixStack.popMatrix();
        }

        // set items texture
        graphics.setTexture(textureManager.itemsTexture);
        graphics.setPipeline(HUD_NO_CULL_PIPELINE);

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
                heldItemRenderer.renderHeldItemPreview(graphics, matrixStack, blockItem, HELD_ITEM_SCALE_FACTOR);
            }
            matrixStack.popMatrix();
        }

        // draw stack size labels
        {
            graphics.setPipeline(HUD_PIPELINE);

            for (InventoryScreen.Slot slot : inventoryScreen.slots) {
                ItemStack itemStack = slot.getItemStack();
                if (itemStack == null) {
                    continue;
                }
                int count = itemStack.getCount();
                if (count > 1) {
                    slot.renderStackSize(graphics, matrixStack, centerX, centerY, count);
                }
            }
        }
    }

    public static void drawSelectedItem(CommandBuffer graphics, MatrixStack matrixStack, TextureManager textureManager, HeldItemRenderer heldItemRenderer, Inventory inventory, float mouseX, float mouseY, TextLabel stackSizeSelectedItemLabel) {
        // draw selected item at cursor position
        {
            // set terrain texture again after drawing labels and the player

            ItemStack selectedItem = inventory.getSelectedItem();
            if (selectedItem != null) {
                Item item = selectedItem.getItem();
                if (item instanceof BlockItem) {
                    graphics.setPipeline(WORLD_PIPELINE);
                    graphics.setTexture(textureManager.terrainTexture);
                    BlockItem blockItem = (BlockItem) item;
                    matrixStack.pushMatrix();
                    matrixStack.translate(mouseX, mouseY + ITEM_SLOT_SIZE / 2f, 0);
                    BlockRenderer.renderBlockPreview(graphics, matrixStack, blockItem.getBlock(), BLOCK_ITEM_SCALE_FACTOR);
                    matrixStack.popMatrix();
                } else if (item instanceof HeldItem) {
                    graphics.setPipeline(HUD_NO_CULL_PIPELINE);
                    graphics.setTexture(textureManager.itemsTexture);
                    HeldItem heldItem = (HeldItem) item;
                    matrixStack.pushMatrix();
                    matrixStack.translate(mouseX - HELD_ITEM_SCALE_FACTOR / 2f, mouseY - HELD_ITEM_SCALE_FACTOR / 2f, 0);
                    heldItemRenderer.renderHeldItemPreview(graphics, matrixStack, heldItem, HELD_ITEM_SCALE_FACTOR);
                    matrixStack.popMatrix();
                }
            }

            // draw selected item stack size label
            if (selectedItem != null) {
                graphics.setPipeline(HUD_PIPELINE);
                int count = selectedItem.getCount();
                if (count > 1) {
                    stackSizeSelectedItemLabel.setText(StackCountStringPool.valueOf(count));
                    stackSizeSelectedItemLabel.render(graphics, matrixStack, mouseX + 9 - stackSizeSelectedItemLabel.getWidth(), mouseY + 2);
                }
            }
        }
    }
}
