package com.mojang.minecraft.item.inventory;

import com.mojang.minecraft.item.BlockItem;
import com.mojang.minecraft.item.Item;
import com.mojang.minecraft.item.ItemStack;
import com.mojang.minecraft.level.block.Block;
import com.mojang.minecraft.level.block.Blocks;

public class Inventory {

    private static final int HOTBAR_SIZE = 9;

    /**
     * The inventory is a 2D array of ItemStacks in the shape [rows][columns].
     */
    private final ItemStack[][] inventoryBlocks = new ItemStack[4][9];
    private ItemStack selectedItem = null;

    private int selectedItemSlotRow, selectedItemSlotColumn;

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
        ItemStack[] hotbarBlocks = getHotBarBlocks();
        return hotbarBlocks[slotIndex];
    }

    private ItemStack[] getHotBarBlocks() {
        return inventoryBlocks[3];
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

    public void clickItem(int row, int column) {
        ItemStack clickedStack = getInventoryItem(row, column);
        ItemStack currentStack = getSelectedItem();
        if (currentStack == null || clickedStack == null || !currentStack.getItem().equals(clickedStack.getItem())) {
            // swap stacks
            inventoryBlocks[row][column] = selectedItem;
            selectedItem = clickedStack;
            selectedItemSlotColumn = column;
            selectedItemSlotRow = row;
        } else {
            // merge stacks
            int increased = inventoryBlocks[row][column].increaseAmount(currentStack.getCount(), false);
            if (increased == currentStack.getCount()) {
                selectedItem = null;
            } else {
                currentStack.decreaseAmount(increased);
            }
        }
    }

    public void resetSelectedItem() {
        if (selectedItem == null) {
            return;
        }
        inventoryBlocks[selectedItemSlotRow][selectedItemSlotColumn] = selectedItem;
        selectedItem = null;
        selectedItemSlotRow = -1;
        selectedItemSlotColumn = -1;
    }

    public ItemStack getSelectedItem() {
        return selectedItem;
    }

    public void decreaseHotbarItem(int hotbarSlotIndex, int amount) {
        ItemStack hotbarItem = getHotbarItem(hotbarSlotIndex);
        if (hotbarItem != null) {
            hotbarItem.decreaseAmount(amount);
            if (hotbarItem.getCount() <= 0) {
                ItemStack[] hotbarBlocks = getHotBarBlocks();
                hotbarBlocks[hotbarSlotIndex] = null;
            }
        }
    }

    public boolean addItem(Item item, boolean isItemPickup) {
        // add items to inventory in reverse row priority
        for (int i = inventoryBlocks.length - 1; i >= 0; i--) {
            for (int j = 0, m = inventoryBlocks[i].length; j < m; j++) {
                ItemStack itemStack = inventoryBlocks[i][j];
                if (itemStack != null && itemStack.getItem().equals(item)) {
                    if (itemStack.increaseAmount(1, isItemPickup) == 1) {
                        return true;
                    }
                }
            }
        }

        for (int i = inventoryBlocks.length - 1; i >= 0; i--) {
            for (int j = 0, m = inventoryBlocks[i].length; j < m; j++) {
                ItemStack itemStack = inventoryBlocks[i][j];
                if (itemStack == null) {
                    inventoryBlocks[i][j] = new ItemStack(item, 1);
                    return true;
                }
            }
        }

        return false;
    }
}
