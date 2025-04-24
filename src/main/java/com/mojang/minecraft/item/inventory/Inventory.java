package com.mojang.minecraft.item.inventory;

import com.mojang.minecraft.item.BlockItem;
import com.mojang.minecraft.item.ItemStack;
import com.mojang.minecraft.level.block.Block;
import com.mojang.minecraft.level.block.Blocks;

public class Inventory {

    private static final int HOTBAR_SIZE = 9;

    private final ItemStack[][] inventoryBlocks = new ItemStack[4][9];
    private ItemStack selectedItem = null;

    {
        ItemStack[] hotbarBlocks = inventoryBlocks[3];
        Block[] defaultBlocks = new Block[]{
                Blocks.grass,
                Blocks.dirt,
                Blocks.rock,
                Blocks.stoneBrick,
                Blocks.glass,
                Blocks.planks,
                Blocks.wood,
                Blocks.leaves,
        };
        for (int i = 0; i < defaultBlocks.length; i++) {
            hotbarBlocks[i] = new ItemStack(new BlockItem(defaultBlocks[i]), 62);
        }

        inventoryBlocks[0][0] = new ItemStack(new BlockItem(Blocks.grass), 4);
        inventoryBlocks[1][1] = new ItemStack(new BlockItem(Blocks.stoneBrick), 2);
        inventoryBlocks[2][2] = new ItemStack(new BlockItem(Blocks.glass), 2);
    }

    public ItemStack getHotbarItem(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= HOTBAR_SIZE) {
            throw new IndexOutOfBoundsException("Hotbar index " + slotIndex + " out of bounds");
        }
        ItemStack[] hotbarBlocks = inventoryBlocks[3];
        return hotbarBlocks[slotIndex];
    }

    public ItemStack getInventoryItem(int row, int column) {
        if (row < 0 || row >= inventoryBlocks.length || column < 0 || column >= inventoryBlocks[row].length) {
            throw new IndexOutOfBoundsException("Inventory index [" + row + "][" + column + "] out of bounds");
        }
        return inventoryBlocks[row][column];
    }

    public int getMainInventoryRowCount() {
        return inventoryBlocks.length - 1;
    }

    public int getColumnCount() {
        return inventoryBlocks[0].length;
    }

    public int getHotbarSize() {
        return HOTBAR_SIZE;
    }

    public void selectItem(int row, int column) {
        ItemStack block = getInventoryItem(row, column);
        inventoryBlocks[row][column] = selectedItem;
        selectedItem = block;
    }

    public ItemStack getSelectedItem() {
        return selectedItem;
    }
}
