package com.mojang.minecraft.item.crafting;

import com.mojang.minecraft.item.ItemStack;

public final class CraftingMatch {

    private final CraftingRecipe recipe;
    private final int offsetRow;
    private final int offsetColumn;
    private final ItemStack result;

    public CraftingMatch(CraftingRecipe recipe, int offsetRow, int offsetColumn, ItemStack result) {
        this.recipe = recipe;
        this.offsetRow = offsetRow;
        this.offsetColumn = offsetColumn;
        this.result = result;
    }

    public CraftingRecipe getRecipe() {
        return recipe;
    }

    public int getOffsetRow() {
        return offsetRow;
    }

    public int getOffsetColumn() {
        return offsetColumn;
    }

    public ItemStack getResult() {
        return result;
    }
}
