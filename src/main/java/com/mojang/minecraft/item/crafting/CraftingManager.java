package com.mojang.minecraft.item.crafting;

import com.mojang.minecraft.item.*;
import com.mojang.minecraft.level.block.Blocks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class CraftingManager {

    private static final CraftingManager INSTANCE = new CraftingManager();

    private final List<CraftingRecipe> recipes = new ArrayList<>();

    private CraftingManager() {
        registerDefaultRecipes();
    }

    public static CraftingManager getInstance() {
        return INSTANCE;
    }

    public void addRecipe(CraftingRecipe recipe) {
        this.recipes.add(recipe);
    }

    public CraftingMatch findMatch(ItemStack[][] grid) {
        for (CraftingRecipe recipe : recipes) {
            CraftingMatch match = recipe.matches(grid);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private void registerDefaultRecipes() {
        ItemStack planksOutput = new ItemStack(BlockItems.planks, 4);
        addRecipe(new ShapelessCraftingRecipe(planksOutput, Collections.singletonList(BlockItems.wood)));

        addRecipe(new ShapedCraftingRecipe(new ItemStack(BlockItems.craftingTable, 1), new Item[][]{
                {BlockItems.planks, BlockItems.planks},
                {BlockItems.planks, BlockItems.planks}
        }));
        addRecipe(new ShapedCraftingRecipe(new ItemStack(Items.stick, 4), new Item[][]{
                {BlockItems.planks},
                {BlockItems.planks}
        }));

        addRecipe(new ShapedCraftingRecipe(new ItemStack(Items.diamondSword, 1), new Item[][]{
                {Items.diamond},
                {Items.diamond},
                {Items.stick}
        }));
    }
}
