package com.mojang.minecraft.item.crafting;

import com.mojang.minecraft.item.BlockItem;
import com.mojang.minecraft.item.BlockItems;
import com.mojang.minecraft.item.Item;
import com.mojang.minecraft.item.ItemStack;
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
        Item wood = BlockItems.wood;
        ItemStack planksOutput = new ItemStack(BlockItems.planks, 4);
        addRecipe(new ShapelessCraftingRecipe(planksOutput, Collections.singletonList(wood)));

        Item plankItem = BlockItems.planks;
        Item[][] craftingTablePattern = new Item[][]{
                {plankItem, plankItem},
                {plankItem, plankItem}
        };
        ItemStack craftingTableOutput = new ItemStack(BlockItems.craftingTable, 1);
        addRecipe(new ShapedCraftingRecipe(craftingTableOutput, craftingTablePattern));
    }
}
