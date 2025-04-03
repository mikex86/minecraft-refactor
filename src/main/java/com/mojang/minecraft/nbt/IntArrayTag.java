package com.mojang.minecraft.nbt;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * An NBT tag that contains an int array.
 */
public class IntArrayTag extends Tag<int[]> {
    private int[] value;

    /**
     * Creates a new IntArrayTag with the given name and value.
     *
     * @param name The name of the tag
     * @param value The value of the tag
     */
    public IntArrayTag(String name, int[] value) {
        super(name, TagType.INT_ARRAY);
        this.value = value != null ? value : new int[0];
    }

    /**
     * Creates a new IntArrayTag with no name and the given value.
     * Used for list elements.
     *
     * @param value The value of the tag
     */
    public IntArrayTag(int[] value) {
        this(null, value);
    }

    @Override
    public int[] getValue() {
        return Arrays.copyOf(value, value.length);
    }

    /**
     * Gets the length of the int array.
     *
     * @return The length of the int array
     */
    public int length() {
        return value.length;
    }

    /**
     * Gets an int value at the given index.
     *
     * @param index The index
     * @return The int value
     * @throws IndexOutOfBoundsException If the index is out of bounds
     */
    public int get(int index) {
        return value[index];
    }

    /**
     * Sets an int value at the given index.
     *
     * @param index The index
     * @param value The new value
     * @throws IndexOutOfBoundsException If the index is out of bounds
     */
    public void set(int index, int value) {
        this.value[index] = value;
    }

    /**
     * Sets the value of this tag.
     *
     * @param value The new value
     */
    public void setValue(int[] value) {
        this.value = value != null ? value : new int[0];
    }

    @Override
    protected void writePayload(DataOutputStream dos) throws IOException {
        // Write array length
        dos.writeInt(value.length);
        
        // Write array elements
        for (int i : value) {
            dos.writeInt(i);
        }
    }

    @Override
    protected void readPayload(DataInputStream dis) throws IOException {
        // Read array length
        int length = dis.readInt();
        value = new int[length];
        
        // Read array elements
        for (int i = 0; i < length; i++) {
            value[i] = dis.readInt();
        }
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