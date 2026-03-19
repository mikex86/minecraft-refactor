package com.mojang.minecraft.renderer.graphics;

public enum DataType {
    UNSIGNED_BYTE(1),
    BYTE(1),
    UNSIGNED_SHORT(2),
    SHORT(2),
    FLOAT(4),
    HALF_FLOAT(2),
    INT(4);

    private final int size;

    DataType(int size) {
        this.size = size;
    }

    public int getSize() {
        return size;
    }
}
