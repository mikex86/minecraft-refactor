package com.mojang.minecraft.renderer.graphics;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.GL_HALF_FLOAT;

public enum DataType {
    UNSIGNED_BYTE(GL_UNSIGNED_BYTE, 1),
    BYTE(GL_BYTE, 1),
    UNSIGNED_SHORT(GL_UNSIGNED_SHORT, 2),
    SHORT(GL_SHORT, 2),
    FLOAT(GL_FLOAT, 4),
    HALF_FLOAT(GL_HALF_FLOAT, 2),
    INT(GL_INT, 4);

    private final int glType;
    private final int size;

    DataType(int glType, int size) {
        this.glType = glType;
        this.size = size;
    }

    public int getGLType() {
        return glType;
    }

    public int getSize() {
        return size;
    }
}