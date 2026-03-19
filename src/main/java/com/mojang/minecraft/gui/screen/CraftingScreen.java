package com.mojang.minecraft.gui.screen;

import com.mojang.minecraft.gui.Font;
import com.mojang.minecraft.gui.screen.renderer.InventoryItemRenderer;
import com.mojang.minecraft.item.inventory.Inventory;
import com.mojang.minecraft.renderer.TextureManager;
import com.mojang.minecraft.renderer.graphics.CommandBuffer;
import com.mojang.minecraft.renderer.graphics.MatrixStack;
import com.mojang.minecraft.renderer.item.HeldItemRenderer;

public class CraftingScreen extends AbstractInventoryScreen {

    private float mouseX, mouseY;

    public CraftingScreen(TextureManager textureManager, HeldItemRenderer heldItemRenderer, Font font, Inventory inventory) {
        super(textureManager, heldItemRenderer, font, inventory);
    }

    @Override
    public void onInit() {
        addMainInventorySlots();
        addHotbarSlots();
        addCraftingSlots();
    }

    @Override
    public void onMouseMove(float mouseX, float mouseY, float dX, float dY) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
    }

    @Override
    public void drawScreen(CommandBuffer commandBuffer, MatrixStack matrixStack, float screenWidth, float screenHeight, float partialTicks) {
        float centerX = (float) (int) screenWidth / 2;
        float centerY = (float) (int) screenHeight / 2;

        // if we have not received any mouse input yet, assume it is center of screen
        // this is true because every time we un-grab the mouse, we reset the mouse position to center
        if (mouseX == -1 || mouseY == -1) {
            mouseX = centerX;
            mouseY = centerY;
        }

        drawInventoryScreenBackground(commandBuffer, centerX, centerY, textureManager.craftingTexture);

        InventoryItemRenderer.renderInventoryItems(commandBuffer, matrixStack, textureManager, heldItemRenderer, this, centerX, centerY);
        InventoryItemRenderer.drawSelectedItem(commandBuffer, matrixStack, textureManager, heldItemRenderer, inventory, mouseX, mouseY, stackSizeSelectedItemLabel);

        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
    }

    @Override
    public void onClose() {
        inventory.resetCrafting(Inventory.CraftingKind.TABLE);
    }

    protected void addCraftingSlots() {
        int craftingRows = inventory.getCraftingRowCount(Inventory.CraftingKind.TABLE);
        int craftingColumns = inventory.getCraftingColumnCount(Inventory.CraftingKind.TABLE);
        if (craftingRows == 0 || craftingColumns == 0) {
            return;
        }
        float fourthColumnLeft = BASE_X + 4;
        float craftingStartX = fourthColumnLeft + ITEM_SLOT_SIZE + 8;
        float bottomRowOffsetY = -(13f + ITEM_SLOT_SIZE);
        for (int row = 0; row < craftingRows; row++) {
            for (int column = 0; column < craftingColumns; column++) {
                float slotOffsetX = craftingStartX + ITEM_SLOT_SIZE * column;
                float slotOffsetY = bottomRowOffsetY - ITEM_SLOT_SIZE * (craftingRows - 1 - row);
                final int craftingRow = row;
                final int craftingColumn = column;
                this.slots.add(new Slot(() -> inventory.getCraftingItem(Inventory.CraftingKind.TABLE, craftingRow, craftingColumn),
                        () -> inventory.clickCraftingItem(Inventory.CraftingKind.TABLE, craftingRow, craftingColumn),
                        () -> inventory.placeSingleCraftingItem(Inventory.CraftingKind.TABLE, craftingRow, craftingColumn),
                        slotOffsetX, slotOffsetY));
            }
        }

        float craftingGridWidth = ITEM_SLOT_SIZE * craftingColumns;
        float resultSlotOffsetX = craftingStartX + craftingGridWidth + 40;
        float craftingTopY = bottomRowOffsetY - ITEM_SLOT_SIZE * (craftingRows - 1);
        float craftingHeight = ITEM_SLOT_SIZE * craftingRows;
        float resultSlotOffsetY = craftingTopY + craftingHeight / 2f - ITEM_SLOT_SIZE / 2f;
        this.slots.add(new Slot(inventory::getCraftingResultItem,
                () -> inventory.clickCraftingResultItem(Inventory.CraftingKind.TABLE),
                null,
                resultSlotOffsetX, resultSlotOffsetY));
    }
}
