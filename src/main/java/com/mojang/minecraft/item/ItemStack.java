package com.mojang.minecraft.item;

public class ItemStack {

    private final Item item;
    private final int count;

    public ItemStack(Item item, int count) {
        this.item = item;
        this.count = count;
    }

    public Item getItem() {
        return item;
    }

    public int getCount() {
        return count;
    }
}
