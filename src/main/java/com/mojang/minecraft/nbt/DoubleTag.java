package com.mojang.minecraft.nbt;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * An NBT tag that contains a single double value.
 */
public class DoubleTag extends Tag<Double> {
    private double value;

    /**
     * Creates a new DoubleTag with the given name and value.
     *
     * @param name The name of the tag
     * @param value The value of the tag
     */
    public DoubleTag(String name, double value) {
        super(name, TagType.DOUBLE);
        this.value = value;
    }

    /**
     * Creates a new DoubleTag with no name and the given value.
     * Used for list elements.
     *
     * @param value The value of the tag
     */
    public DoubleTag(double value) {
        this(null, value);
    }

    @Override
    public Double getValue() {
        return value;
    }

    /**
     * Sets the value of this tag.
     *
     * @param value The new value
     */
    public void setValue(double value) {
        this.value = value;
    }

    @Override
    protected void writePayload(DataOutputStream dos) throws IOException {
        dos.writeDouble(value);
    }

    @Override
    protected void readPayload(DataInputStream dis) throws IOException {
        value = dis.readDouble();
    }
} 