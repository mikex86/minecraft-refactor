package com.mojang.minecraft.nbt;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * An NBT tag that contains a single integer value.
 */
public class IntTag extends Tag<Integer> {
    private int value;

    /**
     * Creates a new IntTag with the given name and value.
     *
     * @param name The name of the tag
     * @param value The value of the tag
     */
    public IntTag(String name, int value) {
        super(name, TagType.INT);
        this.value = value;
    }

    /**
     * Creates a new IntTag with no name and the given value.
     * Used for list elements.
     *
     * @param value The value of the tag
     */
    public IntTag(int value) {
        this(null, value);
    }

    @Override
    public Integer getValue() {
        return value;
    }

    /**
     * Sets the value of this tag.
     *
     * @param value The new value
     */
    public void setValue(int value) {
        this.value = value;
    }

    @Override
    protected void writePayload(DataOutputStream dos) throws IOException {
        dos.writeInt(value);
    }

    @Override
    protected void readPayload(DataInputStream dis) throws IOException {
        value = dis.readInt();
    }
} 