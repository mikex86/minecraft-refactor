package com.mojang.minecraft.item;

public class Item {

    private final String name;
    private final int maxStackSize;

    public Item(String name, int maxStackSize) {
        this.name = name;
        this.maxStackSize = maxStackSize;
    }

    public String getName() {
        return name;
    }

    public int getMaxStackSize() {
        return maxStackSize;
    }

}
