package com.mojang.minecraft.item;

public class HeldItem extends Item {

    private final int textureId;
    private final int id;

    private static int idctr = 0;

    public HeldItem(String name, int maxStackSize, int textureId) {
        super(name, maxStackSize);
        this.id = idctr++;
        this.textureId = textureId;
    }

    public int getTextureId() {
        return textureId;
    }

    public int getId() {
        return id;
    }
}
