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
import com.mojang.minecraft.renderer.graphics.GraphicsAPI;
import com.mojang.minecraft.renderer.item.HeldItemRenderer;
import com.mojang.minecraft.renderer.shader.ShaderRegistry;
import com.mojang.minecraft.renderer.shader.impl.HudShader;
import com.mojang.minecraft.renderer.shader.impl.WorldShader;

import static com.mojang.minecraft.gui.screen.AbstractInventoryScreen.ITEM_SLOT_SIZE;
import static com.mojang.minecraft.gui.screen.InventoryScreen.BLOCK_ITEM_SCALE_FACTOR;
import static com.mojang.minecraft.gui.screen.InventoryScreen.HELD_ITEM_SCALE_FACTOR;

public class InventoryItemRenderer {

    private static final WorldShader WORLD_SHADER = ShaderRegistry.getInstance().getWorldShader();
    private static final HudShader HUD_SHADER = ShaderRegistry.getInstance().getHudShader();

    public static void renderInventoryItems(GraphicsAPI graphics, TextureManager textureManager, HeldItemRenderer heldItemRenderer, AbstractInventoryScreen inventoryScreen, float centerX, float centerY) {

        // set world shader
        graphics.setShader(WORLD_SHADER);
        WORLD_SHADER.setFogUniforms(0f, 0f, 0f, 0f, 0f, 0f, 0f);

        // set terrain texture
        graphics.setTexture(textureManager.terrainTexture);

        for (InventoryScreen.Slot slot : inventoryScreen.slots) {
            ItemStack itemStack = slot.getItemStack();
            if (itemStack == null) {
                continue;
            }
            Item item = itemStack.getItem();
            graphics.pushMatrix();
            graphics.translate(slot.getItemRenderX(centerX), slot.getItemRenderY(centerY), 0);
            if (item instanceof BlockItem) {
                BlockItem blockItem = (BlockItem) item;
                BlockRenderer.renderBlockPreview(graphics, blockItem.getBlock(), BLOCK_ITEM_SCALE_FACTOR
                );
            }
            graphics.popMatrix();
        }

        // set items texture
        graphics.setTexture(textureManager.itemsTexture);
        graphics.setShader(HUD_SHADER);

        for (InventoryScreen.Slot slot : inventoryScreen.slots) {
            ItemStack itemStack = slot.getItemStack();
            if (itemStack == null) {
                continue;
            }
            Item item = itemStack.getItem();
            graphics.pushMatrix();
            graphics.translate(slot.getItemRenderX(centerX) - HELD_ITEM_SCALE_FACTOR / 2f, slot.getItemRenderY(centerY) - HELD_ITEM_SCALE_FACTOR, 0);
            if (item instanceof HeldItem) {
                HeldItem blockItem = (HeldItem) item;
                heldItemRenderer.renderHeldItemPreview(graphics, blockItem, HELD_ITEM_SCALE_FACTOR);
            }
            graphics.popMatrix();
        }

        // draw stack size labels
        {
            graphics.setShader(HUD_SHADER);

            for (InventoryScreen.Slot slot : inventoryScreen.slots) {
                ItemStack itemStack = slot.getItemStack();
                if (itemStack == null) {
                    continue;
                }
                int count = itemStack.getCount();
                if (count > 1) {
                    slot.renderStackSize(graphics, centerX, centerY, count);
                }
            }
        }
    }

    public static void drawSelectedItem(GraphicsAPI graphics, TextureManager textureManager, HeldItemRenderer heldItemRenderer, Inventory inventory, float mouseX, float mouseY, TextLabel stackSizeSelectedItemLabel) {
        // draw selected item at cursor position
        {
            // set terrain texture again after drawing labels and the player

            ItemStack selectedItem = inventory.getSelectedItem();
            if (selectedItem != null) {
                Item item = selectedItem.getItem();
                if (item instanceof BlockItem) {
                    graphics.setShader(WORLD_SHADER);
                    graphics.setTexture(textureManager.terrainTexture);
                    BlockItem blockItem = (BlockItem) item;
                    graphics.pushMatrix();
                    graphics.translate(mouseX, mouseY + ITEM_SLOT_SIZE / 2f, 0);
                    BlockRenderer.renderBlockPreview(graphics, blockItem.getBlock(), BLOCK_ITEM_SCALE_FACTOR);
                    graphics.popMatrix();
                } else if (item instanceof HeldItem) {
                    graphics.setShader(HUD_SHADER);
                    graphics.setTexture(textureManager.itemsTexture);
                    HeldItem heldItem = (HeldItem) item;
                    graphics.pushMatrix();
                    graphics.translate(mouseX - HELD_ITEM_SCALE_FACTOR / 2f, mouseY - HELD_ITEM_SCALE_FACTOR / 2f, 0);
                    heldItemRenderer.renderHeldItemPreview(graphics, heldItem, HELD_ITEM_SCALE_FACTOR);
                    graphics.popMatrix();
                }
            }

            // draw selected item stack size label
            if (selectedItem != null) {
                graphics.setShader(HUD_SHADER);
                int count = selectedItem.getCount();
                if (count > 1) {
                    stackSizeSelectedItemLabel.setText(StackCountStringPool.valueOf(count));
                    stackSizeSelectedItemLabel.render(graphics, mouseX + 9 - stackSizeSelectedItemLabel.getWidth(), mouseY + 2);
                }
            }
        }
    }
}
