package com.mojang.minecraft.gui.screen;

import com.mojang.minecraft.entity.Player;
import com.mojang.minecraft.item.Inventory;
import com.mojang.minecraft.level.block.Block;
import com.mojang.minecraft.renderer.Tesselator;
import com.mojang.minecraft.renderer.TextureManager;
import com.mojang.minecraft.renderer.block.BlockPreviewRenderer;
import com.mojang.minecraft.renderer.graphics.GraphicsAPI;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums;
import com.mojang.minecraft.renderer.graphics.IndexedMesh;

public class InventoryScreen extends GuiScreen {

    private final Player player;
    private final Inventory inventory;
    private final TextureManager textureManager;

    private float screenWidth;
    private float screenHeight;
    private float mouseX;
    private float mouseY;

    public InventoryScreen(TextureManager textureManager, Player player, Inventory inventory) {
        this.textureManager = textureManager;
        this.player = player;
        this.inventory = inventory;
    }

    private IndexedMesh inventoryQuadMesh;

    private static final int INVENTORY_UI_WIDTH = 176;
    private static final int INVENTORY_UI_HEIGHT = 166;
    private static final int ITEM_SIZE = 10;

    private static final int ITEM_SLOT_SIZE = 18;

    @Override
    public void drawScreen(GraphicsAPI graphics, float screenWidth, float screenHeight) {
        float centerX = screenWidth / 2f;
        float centerY = screenHeight / 2f;

        if (inventoryQuadMesh == null) {
            Tesselator t = Tesselator.instance;
            t.init();
            t.color(1, 1, 1);

            // draw quad
            t.vertexUV(centerX + INVENTORY_UI_WIDTH / 2f, centerY - INVENTORY_UI_HEIGHT / 2f, 0.0F, (INVENTORY_UI_WIDTH) / 256f, 0.0F);
            t.vertexUV(centerX - INVENTORY_UI_WIDTH / 2f, centerY - INVENTORY_UI_HEIGHT / 2f, 0.0F, 0.0F, 0.0F);
            t.vertexUV(centerX - INVENTORY_UI_WIDTH / 2f, centerY + INVENTORY_UI_HEIGHT / 2f, 0.0F, 0.0F, INVENTORY_UI_HEIGHT / 256f);
            t.vertexUV(centerX + INVENTORY_UI_WIDTH / 2f, centerY + INVENTORY_UI_HEIGHT / 2f, 0.0F, (INVENTORY_UI_WIDTH) / 256f, INVENTORY_UI_HEIGHT / 256f);

            inventoryQuadMesh = t.createIndexedMesh(GraphicsEnums.BufferUsage.STATIC);
        }

        graphics.setTexture(textureManager.inventoryTexture);
        inventoryQuadMesh.draw(graphics);

        // draw hot-bar items
        graphics.setTexture(textureManager.terrainTexture);
        for (int i = 0; i < inventory.getHotbarSize(); i++) {
            Block block = inventory.getHotbarItem(i);
            if (block == null) {
                continue;
            }
            graphics.pushMatrix();
            graphics.translate(centerX - INVENTORY_UI_WIDTH / 2f + 16 + ITEM_SLOT_SIZE * i, centerY + INVENTORY_UI_HEIGHT / 2f - ITEM_SLOT_SIZE + ITEM_SLOT_SIZE / 2f + 1, 0);
            BlockPreviewRenderer.renderBlock(graphics, block, ITEM_SIZE);
            graphics.popMatrix();
        }

        // draw main-inventory items
        for (int row = 0; row < inventory.getMainInventoryRowCount(); row++) {
            for (int column = 0; column < inventory.getColumnCount(); column++) {
                Block block = inventory.getInventoryItem(row, column);
                if (block == null) {
                    continue;
                }
                graphics.pushMatrix();
                graphics.translate(centerX - INVENTORY_UI_WIDTH / 2f + 16 + ITEM_SLOT_SIZE * column, centerY + 7 + ITEM_SLOT_SIZE * row + ITEM_SLOT_SIZE / 2f + 1, 0);
                BlockPreviewRenderer.renderBlock(graphics, block, ITEM_SIZE);
                graphics.popMatrix();
            }
        }

        // draw selected item at cursor position
        {
            Block selectedItem = inventory.getSelectedItem();
            if (selectedItem != null) {
                graphics.pushMatrix();
                graphics.translate(mouseX, mouseY + ITEM_SLOT_SIZE / 2f, 0);
                BlockPreviewRenderer.renderBlock(graphics, selectedItem, ITEM_SIZE);
                graphics.popMatrix();
            }
        }

        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
    }

    @Override
    public void onMouseClicked(float mouseX, float mouseY, int button, boolean pressed) {
        if (button != 0 || !pressed) {
            return;
        }

        float centerX = screenWidth / 2f;
        float centerY = screenHeight / 2f;

        // main inventory selection checking
        for (int row = 0; row < inventory.getMainInventoryRowCount(); row++) {
            for (int column = 0; column < inventory.getColumnCount(); column++) {
                float x = centerX - INVENTORY_UI_WIDTH / 2f + 8 + ITEM_SLOT_SIZE * column;
                float y = centerY + ITEM_SLOT_SIZE * row;
                if (mouseX >= x && mouseX <= x + ITEM_SLOT_SIZE && mouseY >= y && mouseY <= y + ITEM_SLOT_SIZE) {
                    inventory.selectItem(row, column);
                }
            }
        }

        // hotbar selection checking
        for (int i = 0; i < inventory.getHotbarSize(); i++) {
            float x = centerX - INVENTORY_UI_WIDTH / 2f + 8 + ITEM_SLOT_SIZE * i;
            float y = centerY + ITEM_SLOT_SIZE * inventory.getMainInventoryRowCount();
            if (mouseX >= x && mouseX <= x + ITEM_SLOT_SIZE && mouseY >= y && mouseY <= y + ITEM_SLOT_SIZE) {
                inventory.selectItem(inventory.getMainInventoryRowCount(), i);
            }
        }
    }

    @Override
    public void onMouseMove(float mouseX, float mouseY, float dX, float dY) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
    }

    @Override
    public void onResized(float screenWidth, float screenHeight) {
        if (inventoryQuadMesh != null) {
            inventoryQuadMesh.dispose();
            inventoryQuadMesh = null;
        }
    }

    @Override
    public void dispose() {
        if (inventoryQuadMesh != null) {
            inventoryQuadMesh.dispose();
            inventoryQuadMesh = null;
        }
    }
}
