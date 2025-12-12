package com.mojang.minecraft.item.crafting;

import com.mojang.minecraft.item.Item;
import com.mojang.minecraft.item.ItemStack;

public class ShapedCraftingRecipe implements CraftingRecipe {

    private final ItemStack output;
    private final Item[][] pattern;
    private final int width;
    private final int height;

    public ShapedCraftingRecipe(ItemStack output, Item[][] pattern) {
        this.output = output;
        this.pattern = pattern;
        this.height = pattern.length;
        this.width = pattern.length == 0 ? 0 : pattern[0].length;
    }

    @Override
    public CraftingMatch matches(ItemStack[][] grid) {
        int gridRows = grid.length;
        int gridCols = grid[0].length;
        for (int rowOffset = 0; rowOffset <= gridRows - height; rowOffset++) {
            for (int columnOffset = 0; columnOffset <= gridCols - width; columnOffset++) {
                if (matchesAtOffset(grid, rowOffset, columnOffset)) {
                    return new CraftingMatch(this, rowOffset, columnOffset, createResult());
                }
            }
        }
        return null;
    }

    private boolean matchesAtOffset(ItemStack[][] grid, int rowOffset, int columnOffset) {
        for (int row = 0; row < grid.length; row++) {
            for (int column = 0; column < grid[row].length; column++) {
                boolean insidePattern = row >= rowOffset && row < rowOffset + height && column >= columnOffset && column < columnOffset + width;
                if (!insidePattern) {
                    if (grid[row][column] != null) {
                        return false;
                    }
                    continue;
                }
                Item required = pattern[row - rowOffset][column - columnOffset];
                ItemStack stack = grid[row][column];
                if (required == null) {
                    if (stack != null) {
                        return false;
                    }
                } else {
                    if (stack == null || !stack.getItem().equals(required)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    @Override
    public ItemStack createResult() {
        return new ItemStack(output.getItem(), output.getCount());
    }

    @Override
    public void consumeIngredients(ItemStack[][] grid, CraftingMatch match) {
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                Item required = pattern[row][column];
                if (required == null) {
                    continue;
                }
                int gridRow = row + match.getOffsetRow();
                int gridColumn = column + match.getOffsetColumn();
                ItemStack stack = grid[gridRow][gridColumn];
                if (stack != null) {
                    stack.decreaseAmount(1);
                    if (stack.getCount() <= 0) {
                        grid[gridRow][gridColumn] = null;
                    }
                }
            }
        }
    }
}
