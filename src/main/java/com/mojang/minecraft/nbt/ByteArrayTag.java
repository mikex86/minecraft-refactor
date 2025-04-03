package com.mojang.minecraft.nbt;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * An NBT tag that contains a byte array.
 */
public class ByteArrayTag extends Tag<byte[]> {
    private byte[] value;

    /**
     * Creates a new ByteArrayTag with the given name and value.
     *
     * @param name The name of the tag
     * @param value The value of the tag
     */
    public ByteArrayTag(String name, byte[] value) {
        super(name, TagType.BYTE_ARRAY);
        this.value = value != null ? value : new byte[0];
    }

    /**
     * Creates a new ByteArrayTag with no name and the given value.
     * Used for list elements.
     *
     * @param value The value of the tag
     */
    public ByteArrayTag(byte[] value) {
        this(null, value);
    }

    @Override
    public byte[] getValue() {
        return Arrays.copyOf(value, value.length);
    }

    /**
     * Gets the length of the byte array.
     *
     * @return The length of the byte array
     */
    public int length() {
        return value.length;
    }

    /**
     * Gets a byte value at the given index.
     *
     * @param index The index
     * @return The byte value
     * @throws IndexOutOfBoundsException If the index is out of bounds
     */
    public byte get(int index) {
        return value[index];
    }

    /**
     * Sets a byte value at the given index.
     *
     * @param index The index
     * @param value The new value
     * @throws IndexOutOfBoundsException If the index is out of bounds
     */
    public void set(int index, byte value) {
        this.value[index] = value;
    }

    /**
     * Sets the value of this tag.
     *
     * @param value The new value
     */
    public void setValue(byte[] value) {
        this.value = value != null ? value : new byte[0];
    }

    @Override
    protected void writePayload(DataOutputStream dos) throws IOException {
        // Write array length
        dos.writeInt(value.length);
        
        // Write array elements
        dos.write(value);
    }

    @Override
    protected void readPayload(DataInputStream dis) throws IOException {
        // Read array length
        int length = dis.readInt();
        value = new byte[length];
        
        // Read array elements
        dis.readFully(value);
    }
    
    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append(getClass().getSimpleName())
              .append("('")
              .append(getName() != null ? getName() : "")
              .append("'): [");
        
        int displayElements = Math.min(value.length, 10);
        for (int i = 0; i < displayElements; i++) {
            if (i > 0) {
                result.append(", ");
            }
            result.append(value[i]);
        }
        
        if (value.length > 10) {
            result.append(", ... (").append(value.length - 10).append(" more)");
        }
        
        result.append("]");
        return result.toString();
    }
} 