package com.mojang.minecraft.item.crafting;

import com.mojang.minecraft.item.Item;
import com.mojang.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ShapelessCraftingRecipe implements CraftingRecipe {

    private final ItemStack output;
    private final List<Item> ingredients;

    public ShapelessCraftingRecipe(ItemStack output, List<Item> ingredients) {
        this.output = output;
        this.ingredients = new ArrayList<>(ingredients);
    }

    @Override
    public CraftingMatch matches(ItemStack[][] grid) {
        List<Item> remaining = new ArrayList<>(ingredients);
        for (ItemStack[] row : grid) {
            for (ItemStack stack : row) {
                if (stack == null) {
                    continue;
                }
                boolean matched = false;
                Iterator<Item> iterator = remaining.iterator();
                while (iterator.hasNext()) {
                    Item requirement = iterator.next();
                    if (stack.getItem().equals(requirement)) {
                        matched = true;
                        iterator.remove();
                        break;
                    }
                }
                if (!matched) {
                    return null;
                }
            }
        }
        if (!remaining.isEmpty()) {
            return null;
        }
        return new CraftingMatch(this, -1, -1, createResult());
    }

    @Override
    public ItemStack createResult() {
        return new ItemStack(output.getItem(), output.getCount());
    }

    @Override
    public void consumeIngredients(ItemStack[][] grid, CraftingMatch match) {
        List<Item> remaining = new ArrayList<>(ingredients);
        for (int row = 0; row < grid.length; row++) {
            for (int column = 0; column < grid[row].length; column++) {
                ItemStack stack = grid[row][column];
                if (stack == null) {
                    continue;
                }
                Iterator<Item> iterator = remaining.iterator();
                while (iterator.hasNext()) {
                    Item requirement = iterator.next();
                    if (stack.getItem().equals(requirement)) {
                        stack.decreaseAmount(1);
                        if (stack.getCount() <= 0) {
                            grid[row][column] = null;
                        }
                        iterator.remove();
                        break;
                    }
                }
                if (remaining.isEmpty()) {
                    return;
                }
            }
        }
    }
}
