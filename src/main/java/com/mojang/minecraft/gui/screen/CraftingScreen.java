package com.mojang.minecraft.gui.screen;

import com.mojang.minecraft.gui.Font;
import com.mojang.minecraft.gui.screen.renderer.InventoryItemRenderer;
import com.mojang.minecraft.item.inventory.Inventory;
import com.mojang.minecraft.renderer.TextureManager;
import com.mojang.minecraft.renderer.graphics.GraphicsAPI;
import com.mojang.minecraft.renderer.graphics.IndexedMesh;
import com.mojang.minecraft.renderer.item.HeldItemRenderer;

public class CraftingScreen extends AbstractInventoryScreen {

    private float mouseX, mouseY;

    public CraftingScreen(TextureManager textureManager, HeldItemRenderer heldItemRenderer, Font font, Inventory inventory) {
        super(textureManager, heldItemRenderer, font, inventory);

        addMainInventorySlots();
        addHotbarSlots();
        addCraftingSlots();
    }


    @Override
    public void onMouseMove(float mouseX, float mouseY, float dX, float dY) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
    }

    private IndexedMesh inventoryQuadMesh;

    private static final int INVENTORY_UI_WIDTH = 176;
    private static final int INVENTORY_UI_HEIGHT = 166;

    @Override
    public void drawScreen(GraphicsAPI graphics, float screenWidth, float screenHeight, float partialTicks) {
        float centerX = (float) (int) screenWidth / 2;
        float centerY = (float) (int) screenHeight / 2;

        // if we have not received any mouse input yet, assume it is center of screen
        // this is true because every time we un-grab the mouse, we reset the mouse position to center
        if (mouseX == -1 || mouseY == -1) {
            mouseX = centerX;
            mouseY = centerY;
        }

        drawInventoryScreenBackground(graphics, centerX, centerY, textureManager.craftingTexture);


        InventoryItemRenderer.renderInventoryItems(graphics, textureManager, heldItemRenderer, this, centerX, centerY);
        InventoryItemRenderer.drawSelectedItem(graphics, textureManager, heldItemRenderer, inventory, mouseX, mouseY, stackSizeSelectedItemLabel);

        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
    }

    private void addCraftingSlots() {

    }
}
