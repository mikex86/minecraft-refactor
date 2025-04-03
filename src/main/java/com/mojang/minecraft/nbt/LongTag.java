package com.mojang.minecraft.nbt;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * An NBT tag that contains a single long value.
 */
public class LongTag extends Tag<Long> {
    private long value;

    /**
     * Creates a new LongTag with the given name and value.
     *
     * @param name The name of the tag
     * @param value The value of the tag
     */
    public LongTag(String name, long value) {
        super(name, TagType.LONG);
        this.value = value;
    }

    /**
     * Creates a new LongTag with no name and the given value.
     * Used for list elements.
     *
     * @param value The value of the tag
     */
    public LongTag(long value) {
        this(null, value);
    }

    @Override
    public Long getValue() {
        return value;
    }

    /**
     * Sets the value of this tag.
     *
     * @param value The new value
     */
    public void setValue(long value) {
        this.value = value;
    }

    @Override
    protected void writePayload(DataOutputStream dos) throws IOException {
        dos.writeLong(value);
    }

    @Override
    protected void readPayload(DataInputStream dis) throws IOException {
        value = dis.readLong();
    }
} 