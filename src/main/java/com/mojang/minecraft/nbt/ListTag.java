package com.mojang.minecraft.nbt;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An NBT tag that contains a list of tags of the same type.
 */
public class ListTag extends Tag<List<Tag<?>>> {
    private final List<Tag<?>> value;
    private TagType listType;

    /**
     * Creates a new ListTag with the given name.
     *
     * @param name The name of the tag
     */
    public ListTag(String name) {
        super(name, TagType.LIST);
        this.value = new ArrayList<>();
        this.listType = TagType.END;  // Default to END until a tag is added
    }

    /**
     * Creates a new ListTag with no name.
     * Used for list elements.
     */
    public ListTag() {
        this(null);
    }

    @Override
    public List<Tag<?>> getValue() {
        return Collections.unmodifiableList(value);
    }

    /**
     * Gets the type of tags in this list.
     *
     * @return The type of tags in this list
     */
    public TagType getListType() {
        return listType;
    }

    /**
     * Adds a tag to this list.
     *
     * @param tag The tag to add
     * @return This list tag
     * @throws IllegalArgumentException If the tag has a name or is of the wrong type
     */
    public ListTag add(Tag<?> tag) {
        if (tag.getName() != null) {
            throw new IllegalArgumentException("Tags in lists must not have names");
        }
        
        if (value.isEmpty()) {
            // First tag sets the list type
            listType = tag.getType();
        } else if (tag.getType() != listType) {
            throw new IllegalArgumentException(
                "Cannot add tag of type " + tag.getType() + " to list of type " + listType);
        }
        
        value.add(tag);
        return this;
    }

    /**
     * Gets a tag from this list.
     *
     * @param index The index of the tag to get
     * @return The tag
     * @throws IndexOutOfBoundsException If the index is out of bounds
     */
    public Tag<?> get(int index) {
        return value.get(index);
    }

    /**
     * Gets a tag from this list and casts it to the given type.
     *
     * @param index The index of the tag to get
     * @param tagClass The class to cast the tag to
     * @param <T> The type of the tag
     * @return The tag, or null if the tag is of the wrong type
     * @throws IndexOutOfBoundsException If the index is out of bounds
     */
    @SuppressWarnings("unchecked")
    public <T extends Tag<?>> T get(int index, Class<T> tagClass) {
        Tag<?> tag = value.get(index);
        if (tagClass.isInstance(tag)) {
            return (T) tag;
        }
        return null;
    }

    /**
     * Removes a tag from this list.
     *
     * @param index The index of the tag to remove
     * @return The removed tag
     * @throws IndexOutOfBoundsException If the index is out of bounds
     */
    public Tag<?> remove(int index) {
        return value.remove(index);
    }

    /**
     * Gets the number of tags in this list.
     *
     * @return The number of tags
     */
    public int size() {
        return value.size();
    }

    /**
     * Convenience method to add a byte value
     * 
     * @param value Value to add
     * @return This list tag for chaining
     */
    public ListTag addByte(byte value) {
        return add(new ByteTag(value));
    }

    /**
     * Convenience method to add a short value
     * 
     * @param value Value to add
     * @return This list tag for chaining
     */
    public ListTag addShort(short value) {
        return add(new ShortTag(value));
    }

    /**
     * Convenience method to add an int value
     * 
     * @param value Value to add
     * @return This list tag for chaining
     */
    public ListTag addInt(int value) {
        return add(new IntTag(value));
    }

    /**
     * Convenience method to add a long value
     * 
     * @param value Value to add
     * @return This list tag for chaining
     */
    public ListTag addLong(long value) {
        return add(new LongTag(value));
    }

    /**
     * Convenience method to add a float value
     * 
     * @param value Value to add
     * @return This list tag for chaining
     */
    public ListTag addFloat(float value) {
        return add(new FloatTag(value));
    }

    /**
     * Convenience method to add a double value
     * 
     * @param value Value to add
     * @return This list tag for chaining
     */
    public ListTag addDouble(double value) {
        return add(new DoubleTag(value));
    }

    /**
     * Convenience method to add a string value
     * 
     * @param value Value to add
     * @return This list tag for chaining
     */
    public ListTag addString(String value) {
        return add(new StringTag(value));
    }

    /**
     * Convenience method to add a byte array
     * 
     * @param value Value to add
     * @return This list tag for chaining
     */
    public ListTag addByteArray(byte[] value) {
        return add(new ByteArrayTag(value));
    }

    /**
     * Convenience method to add an int array
     * 
     * @param value Value to add
     * @return This list tag for chaining
     */
    public ListTag addIntArray(int[] value) {
        return add(new IntArrayTag(value));
    }

    /**
     * Convenience method to add a long array
     * 
     * @param value Value to add
     * @return This list tag for chaining
     */
    public ListTag addLongArray(long[] value) {
        return add(new LongArrayTag(value));
    }

    @Override
    protected void writePayload(DataOutputStream dos) throws IOException {
        // Write the type of tags in this list
        dos.writeByte(listType.getId());
        
        // Write the number of tags in this list
        dos.writeInt(value.size());
        
        // Write all the tags in this list
        for (Tag<?> tag : value) {
            tag.writePayload(dos);
        }
    }

    @Override
    protected void readPayload(DataInputStream dis) throws IOException {
        value.clear();
        
        // Read the type of tags in this list
        byte tagId = dis.readByte();
        listType = TagType.fromId(tagId);
        
        // Read the number of tags in this list
        int size = dis.readInt();
        
        // Read all the tags in this list
        for (int i = 0; i < size; i++) {
            Tag<?> tag = createTag(listType, null);
            tag.readPayload(dis);
            value.add(tag);
        }
    }
    
    private Tag<?> createTag(TagType type, String name) {
        switch (type) {
            case BYTE:
                return new ByteTag(name, (byte) 0);
            case SHORT:
                return new ShortTag(name, (short) 0);
            case INT:
                return new IntTag(name, 0);
            case LONG:
                return new LongTag(name, 0L);
            case FLOAT:
                return new FloatTag(name, 0.0f);
            case DOUBLE:
                return new DoubleTag(name, 0.0);
            case BYTE_ARRAY:
                return new ByteArrayTag(name, new byte[0]);
            case STRING:
                return new StringTag(name, "");
            case LIST:
                return new ListTag(name);
            case COMPOUND:
                return new CompoundTag(name);
            case INT_ARRAY:
                return new IntArrayTag(name, new int[0]);
            case LONG_ARRAY:
                return new LongArrayTag(name, new long[0]);
            default:
                throw new UnsupportedOperationException("Tag type not supported: " + type);
        }
    }
} 