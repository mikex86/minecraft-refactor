package com.mojang.minecraft.nbt;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * An NBT tag that contains a long array.
 */
public class LongArrayTag extends Tag<long[]> {
    private long[] value;

    /**
     * Creates a new LongArrayTag with the given name and value.
     *
     * @param name The name of the tag
     * @param value The value of the tag
     */
    public LongArrayTag(String name, long[] value) {
        super(name, TagType.LONG_ARRAY);
        this.value = value != null ? value : new long[0];
    }

    /**
     * Creates a new LongArrayTag with no name and the given value.
     * Used for list elements.
     *
     * @param value The value of the tag
     */
    public LongArrayTag(long[] value) {
        this(null, value);
    }

    @Override
    public long[] getValue() {
        return Arrays.copyOf(value, value.length);
    }

    /**
     * Gets the length of the long array.
     *
     * @return The length of the long array
     */
    public int length() {
        return value.length;
    }

    /**
     * Gets a long value at the given index.
     *
     * @param index The index
     * @return The long value
     * @throws IndexOutOfBoundsException If the index is out of bounds
     */
    public long get(int index) {
        return value[index];
    }

    /**
     * Sets a long value at the given index.
     *
     * @param index The index
     * @param value The new value
     * @throws IndexOutOfBoundsException If the index is out of bounds
     */
    public void set(int index, long value) {
        this.value[index] = value;
    }

    /**
     * Sets the value of this tag.
     *
     * @param value The new value
     */
    public void setValue(long[] value) {
        this.value = value != null ? value : new long[0];
    }

    @Override
    protected void writePayload(DataOutputStream dos) throws IOException {
        // Write array length
        dos.writeInt(value.length);
        
        // Write array elements
        for (long l : value) {
            dos.writeLong(l);
        }
    }

    @Override
    protected void readPayload(DataInputStream dis) throws IOException {
        // Read array length
        int length = dis.readInt();
        value = new long[length];
        
        // Read array elements
        for (int i = 0; i < length; i++) {
            value[i] = dis.readLong();
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