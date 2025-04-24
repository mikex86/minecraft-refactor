package com.mojang.minecraft.gui.screen;

import com.mojang.minecraft.entity.Player;
import com.mojang.minecraft.gui.Font;
import com.mojang.minecraft.gui.TextLabel;
import com.mojang.minecraft.item.BlockItem;
import com.mojang.minecraft.item.Item;
import com.mojang.minecraft.item.ItemStack;
import com.mojang.minecraft.item.inventory.Inventory;
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
    private final Font font;

    private float screenWidth;
    private float screenHeight;
    private float mouseX;
    private float mouseY;

    private final TextLabel[][] stackSizeMainInventoryLabels;
    private final TextLabel[] stackSizeHotbarLabels;
    private final TextLabel stackSizeSelectedItemLabel;

    public InventoryScreen(TextureManager textureManager, Font font, Player player, Inventory inventory) {
        this.textureManager = textureManager;
        this.font = font;
        this.player = player;
        this.inventory = inventory;
        this.stackSizeMainInventoryLabels = new TextLabel[inventory.getMainInventoryRowCount()][inventory.getColumnCount()];

        for (int row = 0; row < inventory.getMainInventoryRowCount(); row++) {
            for (int column = 0; column < inventory.getColumnCount(); column++) {
                this.stackSizeMainInventoryLabels[row][column] = new TextLabel(font, 0xFFFFFF, true);
            }
        }
        this.stackSizeHotbarLabels = new TextLabel[inventory.getHotbarSize()];
        for (int i = 0; i < inventory.getHotbarSize(); i++) {
            this.stackSizeHotbarLabels[i] = new TextLabel(font, 0xFFFFFF, true);
        }
        this.stackSizeSelectedItemLabel = new TextLabel(font, 0xFFFFFF, true);
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

        // draw inventory background
        graphics.setTexture(textureManager.inventoryTexture);
        inventoryQuadMesh.draw(graphics);


        // draw items
        {
            // set terrain texture
            graphics.setTexture(textureManager.terrainTexture);

            // draw main-inventory items
            for (int row = 0; row < inventory.getMainInventoryRowCount(); row++) {
                for (int column = 0; column < inventory.getColumnCount(); column++) {
                    ItemStack itemStack = inventory.getInventoryItem(row, column);
                    if (itemStack == null) {
                        continue;
                    }
                    Item item = itemStack.getItem();
                    if (item instanceof BlockItem) {
                        BlockItem blockItem = (BlockItem) item;
                        graphics.pushMatrix();
                        graphics.translate(centerX - INVENTORY_UI_WIDTH / 2f + 16 + ITEM_SLOT_SIZE * column, centerY + 7 + ITEM_SLOT_SIZE * row + ITEM_SLOT_SIZE / 2f + 1, 0);
                        BlockPreviewRenderer.renderBlock(graphics, blockItem.getBlock(), ITEM_SIZE);
                        graphics.popMatrix();
                    }
                }
            }

            // draw hot-bar items
            for (int i = 0; i < inventory.getHotbarSize(); i++) {
                ItemStack itemStack = inventory.getHotbarItem(i);
                if (itemStack == null) {
                    continue;
                }
                Item item = itemStack.getItem();
                if (item instanceof BlockItem) {
                    BlockItem blockItem = (BlockItem) item;
                    graphics.pushMatrix();
                    graphics.translate(centerX - INVENTORY_UI_WIDTH / 2f + 16 + ITEM_SLOT_SIZE * i, centerY + INVENTORY_UI_HEIGHT / 2f - ITEM_SLOT_SIZE + ITEM_SLOT_SIZE / 2f + 1, 0);
                    BlockPreviewRenderer.renderBlock(graphics, blockItem.getBlock(), ITEM_SIZE);
                    graphics.popMatrix();
                }
            }
        }

        // draw stack size labels
        {
            // draw main inventory stack size labels
            for (int row = 0; row < inventory.getMainInventoryRowCount(); row++) {
                for (int column = 0; column < inventory.getColumnCount(); column++) {
                    ItemStack itemStack = inventory.getInventoryItem(row, column);
                    if (itemStack == null) {
                        continue;
                    }
                    String stackSizeText = String.valueOf(itemStack.getCount());
                    int count = itemStack.getCount();
                    if (count > 1) {
                        this.stackSizeMainInventoryLabels[row][column].setText(stackSizeText);
                        this.stackSizeMainInventoryLabels[row][column].render(graphics, centerX - INVENTORY_UI_WIDTH / 2f + ITEM_SLOT_SIZE * column + 7 + ITEM_SLOT_SIZE - this.stackSizeMainInventoryLabels[row][column].getWidth(), centerY + 10 + ITEM_SLOT_SIZE * row);
                    }
                }
            }

            // draw hotbar stack size labels
            for (int i = 0; i < inventory.getHotbarSize(); i++) {
                ItemStack itemStack = inventory.getHotbarItem(i);
                if (itemStack == null) {
                    continue;
                }
                String stackSizeText = String.valueOf(itemStack.getCount());
                int count = itemStack.getCount();
                if (count > 1) {
                    this.stackSizeHotbarLabels[i].setText(stackSizeText);
                    this.stackSizeHotbarLabels[i].render(graphics, centerX - INVENTORY_UI_WIDTH / 2f + ITEM_SLOT_SIZE * i + 7 + ITEM_SLOT_SIZE - this.stackSizeHotbarLabels[i].getWidth(), centerY + INVENTORY_UI_HEIGHT / 2f - this.font.getFontHeight() - 7);
                }
            }
        }


        // draw selected item at cursor position
        {
            // set terrain texture again after drawing labels
            graphics.setTexture(textureManager.terrainTexture);

            ItemStack selectedItem = inventory.getSelectedItem();
            if (selectedItem != null) {
                Item item = selectedItem.getItem();
                if (item instanceof BlockItem) {
                    BlockItem blockItem = (BlockItem) item;
                    graphics.pushMatrix();
                    graphics.translate(mouseX, mouseY + ITEM_SLOT_SIZE / 2f, 0);
                    BlockPreviewRenderer.renderBlock(graphics, blockItem.getBlock(), ITEM_SIZE);
                    graphics.popMatrix();
                }
            }

            // draw selected item stack size label
            if (selectedItem != null) {
                String stackSizeText = String.valueOf(selectedItem.getCount());
                int count = selectedItem.getCount();
                if (count > 1) {
                    this.stackSizeSelectedItemLabel.setText(stackSizeText);
                    this.stackSizeSelectedItemLabel.render(graphics, mouseX + 9 - this.stackSizeSelectedItemLabel.getWidth(), mouseY + 2);
                }
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
