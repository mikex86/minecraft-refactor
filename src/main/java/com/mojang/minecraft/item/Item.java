package com.mojang.minecraft.item;

public class Item {

    private final int maxStackSize;

    public Item(int maxStackSize) {
        this.maxStackSize = maxStackSize;
    }

    public int getMaxStackSize() {
        return maxStackSize;
    }

}
