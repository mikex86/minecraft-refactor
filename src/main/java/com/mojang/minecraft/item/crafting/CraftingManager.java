package com.mojang.minecraft.item.crafting;

import com.mojang.minecraft.item.BlockItems;
import com.mojang.minecraft.item.Item;
import com.mojang.minecraft.item.ItemStack;
import com.mojang.minecraft.item.Items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CraftingManager {

    private final List<CraftingRecipe> recipes = new ArrayList<>();

    public CraftingManager() {
        registerDefaultRecipes();
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

    private void addSwordRecipe(Item resource, Item tool) {
        addRecipe(new ShapedCraftingRecipe(new ItemStack(tool, 1), new Item[][]{
                {resource},
                {resource},
                {Items.stick}
        }));
    }

    private void addPickaxeRecipe(Item resource, Item tool) {
        addRecipe(new ShapedCraftingRecipe(new ItemStack(tool, 1), new Item[][]{
                {resource, resource, resource},
                {null, Items.stick, null},
                {null, Items.stick, null}
        }));
    }


    private void addShovelRecipe(Item resource, Item tool) {
        addRecipe(new ShapedCraftingRecipe(new ItemStack(tool, 1), new Item[][]{
                {resource},
                {Items.stick},
                {Items.stick}
        }));
    }


    private void addAxeRecipe(Item resource, Item tool) {
        addRecipe(new ShapedCraftingRecipe(new ItemStack(tool, 1), new Item[][]{
                {resource, resource, null},
                {resource, Items.stick, null},
                {null, Items.stick, null}
        }));
        addRecipe(new ShapedCraftingRecipe(new ItemStack(tool, 1), new Item[][]{
                {null, resource, resource},
                {null, Items.stick, resource},
                {null, Items.stick, null}
        }));
    }

    private void addHoeRecipe(Item resource, Item tool) {
        addRecipe(new ShapedCraftingRecipe(new ItemStack(tool, 1), new Item[][]{
                {resource, resource, null},
                {null, Items.stick, null},
                {null, Items.stick, null}
        }));
        addRecipe(new ShapedCraftingRecipe(new ItemStack(tool, 1), new Item[][]{
                {null, resource, resource},
                {null, Items.stick, null},
                {null, Items.stick, null}
        }));
    }

    private final Item[][] swordResourceToolPairs = {
            {BlockItems.planks, Items.woodenSword},
            {BlockItems.stoneBrick, Items.stoneSword},
            {Items.diamond, Items.diamondSword},
    };

    private final Item[][] pickaxeResourceToolPairs = {
            {BlockItems.planks, Items.woodenPickaxe},
            {BlockItems.stoneBrick, Items.stonePickaxe},
            {Items.diamond, Items.diamondPickaxe},
    };

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

        for (Item[] resourceToolPair : swordResourceToolPairs) {
            Item resource = resourceToolPair[0];
            Item tool = resourceToolPair[1];
            addSwordRecipe(resource, tool);
        }
        for (Item[] resourceToolPair : pickaxeResourceToolPairs) {
            Item resource = resourceToolPair[0];
            Item tool = resourceToolPair[1];
            addPickaxeRecipe(resource, tool);
        }
    }
}
