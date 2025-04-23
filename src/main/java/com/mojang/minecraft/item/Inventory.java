package com.mojang.minecraft.item;

import com.mojang.minecraft.level.block.Block;
import com.mojang.minecraft.level.block.Blocks;

public class Inventory {

    private static final int HOTBAR_SIZE = 9;

    private final Block[][] inventoryBlocks = new Block[4][9];
    private Block selectedItem = null;

    {
        Block[] hotbarBlocks = inventoryBlocks[3];
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
        System.arraycopy(defaultBlocks, 0, hotbarBlocks, 0, defaultBlocks.length);

        inventoryBlocks[0][0] = Blocks.grass;
        inventoryBlocks[1][1] = Blocks.stoneBrick;
        inventoryBlocks[2][2] = Blocks.glass;
    }

    public Block getHotbarItem(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= HOTBAR_SIZE) {
            throw new IndexOutOfBoundsException("Hotbar index " + slotIndex + " out of bounds");
        }
        Block[] hotbarBlocks = inventoryBlocks[3];
        return hotbarBlocks[slotIndex];
    }

    public Block getInventoryItem(int row, int column) {
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
        Block block = getInventoryItem(row, column);
        inventoryBlocks[row][column] = selectedItem;
        selectedItem = block;
    }

    public Block getSelectedItem() {
        return selectedItem;
    }
}
