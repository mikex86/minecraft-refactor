package com.mojang.minecraft.nbt;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * An NBT tag that contains a single byte value.
 */
public class ByteTag extends Tag<Byte> {
    private byte value;

    /**
     * Creates a new ByteTag with the given name and value.
     *
     * @param name The name of the tag
     * @param value The value of the tag
     */
    public ByteTag(String name, byte value) {
        super(name, TagType.BYTE);
        this.value = value;
    }

    /**
     * Creates a new ByteTag with no name and the given value.
     * Used for list elements.
     *
     * @param value The value of the tag
     */
    public ByteTag(byte value) {
        this(null, value);
    }

    @Override
    public Byte getValue() {
        return value;
    }

    /**
     * Sets the value of this tag.
     *
     * @param value The new value
     */
    public void setValue(byte value) {
        this.value = value;
    }

    @Override
    protected void writePayload(DataOutputStream dos) throws IOException {
        dos.writeByte(value);
    }

    @Override
    protected void readPayload(DataInputStream dis) throws IOException {
        value = dis.readByte();
    }
} 