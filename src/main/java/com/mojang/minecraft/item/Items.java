package com.mojang.minecraft.item;

public class Items {

    private static final int DEFAULT_MAX_STACK_SIZE = 64;

    public static final HeldItem stick = new HeldItem("stick", DEFAULT_MAX_STACK_SIZE, 53);

    public static final HeldItem woodenSword = new HeldItem("wooden_sword", 1, 64);
    public static final HeldItem woodenPickaxe = new HeldItem("wooden_pickaxe", 1, 96);

    public static final HeldItem stoneSword = new HeldItem("stone_sword", 1, 65);
    public static final HeldItem stonePickaxe = new HeldItem("stone_pickaxe", 1, 97);

    public static final HeldItem diamond = new HeldItem("diamond", DEFAULT_MAX_STACK_SIZE, 55);
    public static final HeldItem diamondSword = new HeldItem("diamond_sword", 1, 67);
    public static final HeldItem diamondShovel = new HeldItem("diamond_shovel", 1, 83);
    public static final HeldItem diamondPickaxe = new HeldItem("diamond_pickaxe", 1, 99);
    public static final HeldItem diamondAxe = new HeldItem("diamond_axe", 1, 115);
    public static final HeldItem diamondHoe = new HeldItem("diamond_hoe", 1, 131);


}
