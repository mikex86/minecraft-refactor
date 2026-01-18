package com.mojang.minecraft.item.inventory;

import com.mojang.minecraft.item.BlockItems;
import com.mojang.minecraft.item.Item;
import com.mojang.minecraft.item.ItemStack;
import com.mojang.minecraft.item.Items;
import com.mojang.minecraft.item.crafting.CraftingManager;
import com.mojang.minecraft.item.crafting.CraftingMatch;
import com.mojang.minecraft.level.block.Block;
import com.mojang.minecraft.level.block.Blocks;

public class Inventory {

    private static final int HOTBAR_SIZE = 9;
    private static final int ARMOR_SLOT_COUNT = 4;

    /**
     * The inventory is a 2D array of ItemStacks in the shape [rows][columns].
     */
    private final ItemStack[][] inventorySlots = new ItemStack[4][9];
    private final ItemStack[] armorSlots = new ItemStack[ARMOR_SLOT_COUNT];
    private final ItemStack[][] craftingSlots = new ItemStack[2][2];
    private ItemStack craftingResult = null;
    private CraftingMatch cachedCraftingMatch = null;
    private ItemStack selectedItem = null;

    private int selectedItemSlotRow = -1, selectedItemSlotColumn = -1;
    private int selectedArmorSlotIndex = -1;
    private int selectedCraftingSlotRow = -1, selectedCraftingSlotColumn = -1;
    private boolean selectedItemFromArmor = false;
    private boolean selectedItemFromCrafting = false;
    private boolean selectedItemFromCraftingResult = false;

    {
        ItemStack[] hotbarBlocks = inventorySlots[3];
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
            Item blockItem = BlockItems.getBlockItemForBlockOrNull(defaultBlocks[i]);
            assert blockItem != null; // debug only and also code is to be removed anyways
            hotbarBlocks[i] = new ItemStack(blockItem, 62);
        }

        inventorySlots[0][0] = new ItemStack(BlockItems.grass, 4);
        inventorySlots[1][1] = new ItemStack(BlockItems.stoneBrick, 2);
        inventorySlots[2][2] = new ItemStack(BlockItems.glass, 2);
        inventorySlots[2][4] = new ItemStack(Items.diamond, 1);
    }

    public Inventory() {
        updateCraftingResult();
    }

    public ItemStack getHotbarItem(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= HOTBAR_SIZE) {
            throw new IndexOutOfBoundsException("Hotbar index " + slotIndex + " out of bounds");
        }
        ItemStack[] hotbarBlocks = getHotBarSlots();
        return hotbarBlocks[slotIndex];
    }

    private ItemStack[] getHotBarSlots() {
        return inventorySlots[3];
    }

    public ItemStack getInventoryItem(int row, int column) {
        if (row < 0 || row >= inventorySlots.length || column < 0 || column >= inventorySlots[row].length) {
            throw new IndexOutOfBoundsException("Inventory index [" + row + "][" + column + "] out of bounds");
        }
        return inventorySlots[row][column];
    }

    public int getMainInventoryRowCount() {
        return inventorySlots.length - 1;
    }

    public int getColumnCount() {
        return inventorySlots[0].length;
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
            inventorySlots[row][column] = selectedItem;
            selectedItem = clickedStack;
            setSelectedInventorySlot(row, column);
        } else {
            // merge stacks
            int increased = inventorySlots[row][column].increaseAmount(currentStack.getCount(), false);
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
        if (craftingResult == null || cachedCraftingMatch == null) {
            return;
        }

        ItemStack resultCopy = new ItemStack(craftingResult.getItem(), craftingResult.getCount());
        if (selectedItem == null) {
            selectedItem = resultCopy;
        } else {
            if (!selectedItem.getItem().equals(resultCopy.getItem())) {
                return;
            }
            int increased = selectedItem.increaseAmount(resultCopy.getCount(), false);
            if (increased != resultCopy.getCount()) {
                selectedItem.decreaseAmount(increased);
                return;
            }
        }

        cachedCraftingMatch.getRecipe().consumeIngredients(craftingSlots, cachedCraftingMatch);
        setSelectedCraftingResult();
        updateCraftingResult();
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
        updateCraftingResult();
    }

    public void placeSingleInventoryItem(int row, int column) {
        if (placeSingleInGrid(inventorySlots, row, column)) {
            // Nothing extra
        }
    }

    public void placeSingleArmorItem(int slotIndex) {
        if (selectedItem == null) {
            return;
        }
        ItemStack stack = armorSlots[slotIndex];
        if (stack == null) {
            armorSlots[slotIndex] = new ItemStack(selectedItem.getItem(), 1);
            decreaseSelectedItemCount(1);
        } else if (stack.getItem().equals(selectedItem.getItem())) {
            int increased = stack.increaseAmount(1, false);
            if (increased == 1) {
                decreaseSelectedItemCount(1);
            }
        }
    }

    public void placeSingleCraftingItem(int row, int column) {
        if (placeSingleInGrid(craftingSlots, row, column)) {
            updateCraftingResult();
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
            inventorySlots[selectedItemSlotRow][selectedItemSlotColumn] = selectedItem;
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
        updateCraftingResult();
    }

    public ItemStack getSelectedItem() {
        return selectedItem;
    }

    public void decreaseHotbarItem(int hotbarSlotIndex, int amount) {
        ItemStack hotbarItem = getHotbarItem(hotbarSlotIndex);
        if (hotbarItem != null) {
            hotbarItem.decreaseAmount(amount);
            if (hotbarItem.getCount() <= 0) {
                ItemStack[] hotbarBlocks = getHotBarSlots();
                hotbarBlocks[hotbarSlotIndex] = null;
            }
        }
    }

    public boolean addItem(Item item, boolean isItemPickup) {
        // add items to inventory in reverse row priority
        for (int i = inventorySlots.length - 1; i >= 0; i--) {
            for (int j = 0, m = inventorySlots[i].length; j < m; j++) {
                ItemStack itemStack = inventorySlots[i][j];
                if (itemStack != null && itemStack.getItem().equals(item)) {
                    if (itemStack.increaseAmount(1, isItemPickup) == 1) {
                        return true;
                    }
                }
            }
        }

        for (int i = inventorySlots.length - 1; i >= 0; i--) {
            for (int j = 0, m = inventorySlots[i].length; j < m; j++) {
                ItemStack itemStack = inventorySlots[i][j];
                if (itemStack == null) {
                    inventorySlots[i][j] = new ItemStack(item, 1);
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

    private boolean placeSingleInGrid(ItemStack[][] grid, int row, int column) {
        if (selectedItem == null) {
            return false;
        }
        ItemStack target = grid[row][column];
        if (target == null) {
            grid[row][column] = new ItemStack(selectedItem.getItem(), 1);
            decreaseSelectedItemCount(1);
            return true;
        }
        if (target.getItem().equals(selectedItem.getItem())) {
            int increased = target.increaseAmount(1, false);
            if (increased == 1) {
                decreaseSelectedItemCount(1);
                return true;
            }
        }
        return false;
    }

    private void decreaseSelectedItemCount(int amount) {
        if (selectedItem == null || amount <= 0) {
            return;
        }
        selectedItem.decreaseAmount(amount);
        if (selectedItem.getCount() <= 0) {
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
    }

    private void updateCraftingResult() {
        CraftingMatch match = CraftingManager.getInstance().findMatch(craftingSlots);
        if (match == null) {
            craftingResult = null;
            cachedCraftingMatch = null;
        } else {
            craftingResult = match.getResult();
            cachedCraftingMatch = match;
        }
    }
}
