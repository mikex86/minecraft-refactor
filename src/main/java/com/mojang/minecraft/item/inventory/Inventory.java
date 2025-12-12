package com.mojang.minecraft.item.inventory;

import com.mojang.minecraft.item.BlockItem;
import com.mojang.minecraft.item.Item;
import com.mojang.minecraft.item.ItemStack;
import com.mojang.minecraft.level.block.Block;
import com.mojang.minecraft.level.block.Blocks;

public class Inventory {

    private static final int HOTBAR_SIZE = 9;
    private static final int ARMOR_SLOT_COUNT = 4;

    /**
     * The inventory is a 2D array of ItemStacks in the shape [rows][columns].
     */
    private final ItemStack[][] inventoryBlocks = new ItemStack[4][9];
    private final ItemStack[] armorSlots = new ItemStack[ARMOR_SLOT_COUNT];
    private final ItemStack[][] craftingSlots = new ItemStack[2][2];
    private ItemStack craftingResult = null;
    private ItemStack selectedItem = null;

    private int selectedItemSlotRow = -1, selectedItemSlotColumn = -1;
    private int selectedArmorSlotIndex = -1;
    private int selectedCraftingSlotRow = -1, selectedCraftingSlotColumn = -1;
    private boolean selectedItemFromArmor = false;
    private boolean selectedItemFromCrafting = false;
    private boolean selectedItemFromCraftingResult = false;

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

    public int getArmorSlotCount() {
        return ARMOR_SLOT_COUNT;
    }

    public ItemStack getArmorItem(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= ARMOR_SLOT_COUNT) {
            throw new IndexOutOfBoundsException("Armor index " + slotIndex + " out of bounds");
        }
        return armorSlots[slotIndex];
    }

    public int getCraftingRowCount() {
        return craftingSlots.length;
    }

    public int getCraftingColumnCount() {
        return craftingSlots[0].length;
    }

    public ItemStack getCraftingItem(int row, int column) {
        if (row < 0 || row >= craftingSlots.length || column < 0 || column >= craftingSlots[row].length) {
            throw new IndexOutOfBoundsException("Crafting index [" + row + "][" + column + "] out of bounds");
        }
        return craftingSlots[row][column];
    }

    public void clickItem(int row, int column) {
        ItemStack clickedStack = getInventoryItem(row, column);
        ItemStack currentStack = getSelectedItem();
        if (currentStack == null || clickedStack == null || !currentStack.getItem().equals(clickedStack.getItem())) {
            // swap stacks
            inventoryBlocks[row][column] = selectedItem;
            selectedItem = clickedStack;
            setSelectedInventorySlot(row, column);
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

    public void clickArmorItem(int slotIndex) {
        ItemStack clickedStack = getArmorItem(slotIndex);
        ItemStack currentStack = getSelectedItem();
        if (currentStack == null || clickedStack == null || !currentStack.getItem().equals(clickedStack.getItem())) {
            armorSlots[slotIndex] = selectedItem;
            selectedItem = clickedStack;
            setSelectedArmorSlot(slotIndex);
        } else {
            int increased = armorSlots[slotIndex].increaseAmount(currentStack.getCount(), false);
            if (increased == currentStack.getCount()) {
                selectedItem = null;
                setSelectedArmorSlot(-1);
            } else {
                currentStack.decreaseAmount(increased);
            }
        }
    }

    public ItemStack getCraftingResultItem() {
        return craftingResult;
    }

    public void clickCraftingResultItem() {
        ItemStack currentStack = getSelectedItem();
        if (currentStack == null || craftingResult == null || !currentStack.getItem().equals(craftingResult.getItem())) {
            ItemStack previousResult = craftingResult;
            craftingResult = selectedItem;
            selectedItem = previousResult;
            setSelectedCraftingResult();
        } else {
            int increased = craftingResult.increaseAmount(currentStack.getCount(), false);
            if (increased == currentStack.getCount()) {
                selectedItem = null;
                setSelectedCraftingResult();
            } else {
                currentStack.decreaseAmount(increased);
            }
        }
    }

    public void clickCraftingItem(int row, int column) {
        ItemStack clickedStack = getCraftingItem(row, column);
        ItemStack currentStack = getSelectedItem();
        if (currentStack == null || clickedStack == null || !currentStack.getItem().equals(clickedStack.getItem())) {
            craftingSlots[row][column] = selectedItem;
            selectedItem = clickedStack;
            setSelectedCraftingSlot(row, column);
        } else {
            int increased = craftingSlots[row][column].increaseAmount(currentStack.getCount(), false);
            if (increased == currentStack.getCount()) {
                selectedItem = null;
                setSelectedCraftingSlot(-1, -1);
            } else {
                currentStack.decreaseAmount(increased);
            }
        }
    }

    public void resetSelectedItem() {
        if (selectedItem == null) {
            return;
        }
        if (selectedItemFromArmor && selectedArmorSlotIndex >= 0) {
            armorSlots[selectedArmorSlotIndex] = selectedItem;
        } else if (selectedItemFromCrafting && selectedCraftingSlotRow >= 0 && selectedCraftingSlotColumn >= 0) {
            craftingSlots[selectedCraftingSlotRow][selectedCraftingSlotColumn] = selectedItem;
        } else if (selectedItemSlotRow >= 0 && selectedItemSlotColumn >= 0) {
            inventoryBlocks[selectedItemSlotRow][selectedItemSlotColumn] = selectedItem;
        } else if (selectedItemFromCraftingResult && craftingResult != null) {
            craftingResult = selectedItem;
        }
        selectedItem = null;
        selectedItemSlotRow = -1;
        selectedItemSlotColumn = -1;
        selectedArmorSlotIndex = -1;
        selectedItemFromArmor = false;
        selectedCraftingSlotRow = -1;
        selectedCraftingSlotColumn = -1;
        selectedItemFromCrafting = false;
        selectedItemFromCraftingResult = false;
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

    private void setSelectedInventorySlot(int row, int column) {
        selectedItemSlotRow = row;
        selectedItemSlotColumn = column;
        selectedArmorSlotIndex = -1;
        selectedItemFromArmor = false;
        selectedCraftingSlotRow = -1;
        selectedCraftingSlotColumn = -1;
        selectedItemFromCrafting = false;
    }

    private void setSelectedArmorSlot(int slotIndex) {
        selectedArmorSlotIndex = slotIndex;
        selectedItemSlotRow = -1;
        selectedItemSlotColumn = -1;
        selectedItemFromArmor = slotIndex >= 0;
        if (slotIndex >= 0) {
            selectedCraftingSlotRow = -1;
            selectedCraftingSlotColumn = -1;
            selectedItemFromCrafting = false;
        }
    }

    private void setSelectedCraftingSlot(int row, int column) {
        selectedCraftingSlotRow = row;
        selectedCraftingSlotColumn = column;
        selectedItemFromCrafting = row >= 0 && column >= 0;
        if (selectedItemFromCrafting) {
            selectedItemSlotRow = -1;
            selectedItemSlotColumn = -1;
            selectedArmorSlotIndex = -1;
            selectedItemFromArmor = false;
            selectedItemFromCraftingResult = false;
        }
    }

    private void setSelectedCraftingResult() {
        selectedItemFromCraftingResult = craftingResult != null;
        if (selectedItemFromCraftingResult) {
            selectedItemSlotRow = -1;
            selectedItemSlotColumn = -1;
            selectedArmorSlotIndex = -1;
            selectedItemFromArmor = false;
            selectedCraftingSlotRow = -1;
            selectedCraftingSlotColumn = -1;
            selectedItemFromCrafting = false;
        }
    }
}
