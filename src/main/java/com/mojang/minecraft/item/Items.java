package com.mojang.minecraft.item;

public class Items {

    private static final int DEFAULT_MAX_STACK_SIZE = 64;

    public static final HeldItem stick = new HeldItem("stick", DEFAULT_MAX_STACK_SIZE, 53);
    public static final HeldItem diamond = new HeldItem("diamond", DEFAULT_MAX_STACK_SIZE, 55);
    public static final HeldItem diamondSword = new HeldItem("diamond_sword", 1, 67);

}
