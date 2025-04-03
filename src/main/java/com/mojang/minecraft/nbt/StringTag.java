package com.mojang.minecraft.nbt;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * An NBT tag that contains a string value.
 */
public class StringTag extends Tag<String> {
    private String value;

    /**
     * Creates a new StringTag with the given name and value.
     *
     * @param name The name of the tag
     * @param value The value of the tag
     */
    public StringTag(String name, String value) {
        super(name, TagType.STRING);
        this.value = value != null ? value : "";
    }

    /**
     * Creates a new StringTag with no name and the given value.
     * Used for list elements.
     *
     * @param value The value of the tag
     */
    public StringTag(String value) {
        this(null, value);
    }

    @Override
    public String getValue() {
        return value;
    }

    /**
     * Sets the value of this tag.
     *
     * @param value The new value
     */
    public void setValue(String value) {
        this.value = value != null ? value : "";
    }

    @Override
    protected void writePayload(DataOutputStream dos) throws IOException {
        dos.writeUTF(value);
    }

    @Override
    protected void readPayload(DataInputStream dis) throws IOException {
        value = dis.readUTF();
    }
} 