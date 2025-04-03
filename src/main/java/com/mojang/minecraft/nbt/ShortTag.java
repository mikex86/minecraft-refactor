package com.mojang.minecraft.nbt;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * An NBT tag that contains a single short value.
 */
public class ShortTag extends Tag<Short> {
    private short value;

    /**
     * Creates a new ShortTag with the given name and value.
     *
     * @param name The name of the tag
     * @param value The value of the tag
     */
    public ShortTag(String name, short value) {
        super(name, TagType.SHORT);
        this.value = value;
    }

    /**
     * Creates a new ShortTag with no name and the given value.
     * Used for list elements.
     *
     * @param value The value of the tag
     */
    public ShortTag(short value) {
        this(null, value);
    }

    @Override
    public Short getValue() {
        return value;
    }

    /**
     * Sets the value of this tag.
     *
     * @param value The new value
     */
    public void setValue(short value) {
        this.value = value;
    }

    @Override
    protected void writePayload(DataOutputStream dos) throws IOException {
        dos.writeShort(value);
    }

    @Override
    protected void readPayload(DataInputStream dis) throws IOException {
        value = dis.readShort();
    }
} 