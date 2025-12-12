package com.mojang.minecraft.item.crafting;

import com.mojang.minecraft.item.ItemStack;

public interface CraftingRecipe {

    CraftingMatch matches(ItemStack[][] grid);

    ItemStack createResult();

    void consumeIngredients(ItemStack[][] grid, CraftingMatch match);
}
