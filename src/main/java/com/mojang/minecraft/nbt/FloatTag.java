package com.mojang.minecraft.nbt;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * An NBT tag that contains a single float value.
 */
public class FloatTag extends Tag<Float> {
    private float value;

    /**
     * Creates a new FloatTag with the given name and value.
     *
     * @param name The name of the tag
     * @param value The value of the tag
     */
    public FloatTag(String name, float value) {
        super(name, TagType.FLOAT);
        this.value = value;
    }

    /**
     * Creates a new FloatTag with no name and the given value.
     * Used for list elements.
     *
     * @param value The value of the tag
     */
    public FloatTag(float value) {
        this(null, value);
    }

    @Override
    public Float getValue() {
        return value;
    }

    /**
     * Sets the value of this tag.
     *
     * @param value The new value
     */
    public void setValue(float value) {
        this.value = value;
    }

    @Override
    protected void writePayload(DataOutputStream dos) throws IOException {
        dos.writeFloat(value);
    }

    @Override
    protected void readPayload(DataInputStream dis) throws IOException {
        value = dis.readFloat();
    }
} 