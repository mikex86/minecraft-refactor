package com.mojang.minecraft.nbt;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * An NBT tag that contains a collection of named tags.
 * This provides the structure for hierarchical data.
 */
public class CompoundTag extends Tag<Map<String, Tag<?>>> {
    private final Map<String, Tag<?>> value;

    /**
     * Creates a new CompoundTag with the given name.
     *
     * @param name The name of the tag
     */
    public CompoundTag(String name) {
        super(name, TagType.COMPOUND);
        this.value = new HashMap<>();
    }

    /**
     * Creates a new CompoundTag with no name.
     * Used for list elements.
     */
    public CompoundTag() {
        this(null);
    }

    @Override
    public Map<String, Tag<?>> getValue() {
        return new HashMap<>(value);
    }

    /**
     * Puts a tag into this compound tag.
     *
     * @param tag The tag to put
     * @return This compound tag
     * @throws IllegalArgumentException If the tag doesn't have a name
     */
    public CompoundTag put(Tag<?> tag) {
        if (tag.getName() == null) {
            throw new IllegalArgumentException("Tags in compound tags must have names");
        }
        value.put(tag.getName(), tag);
        return this;
    }

    /**
     * Gets a tag from this compound tag.
     *
     * @param name The name of the tag to get
     * @return The tag, or null if no tag with the given name exists
     */
    public Tag<?> get(String name) {
        return value.get(name);
    }

    /**
     * Gets a tag from this compound tag and casts it to the given type.
     *
     * @param name The name of the tag to get
     * @param tagClass The class to cast the tag to
     * @param <T> The type of the tag
     * @return The tag, or null if no tag with the given name exists or the tag is of the wrong type
     */
    @SuppressWarnings("unchecked")
    public <T extends Tag<?>> T get(String name, Class<T> tagClass) {
        Tag<?> tag = value.get(name);
        if (tag != null && tagClass.isInstance(tag)) {
            return (T) tag;
        }
        return null;
    }

    /**
     * Removes a tag from this compound tag.
     *
     * @param name The name of the tag to remove
     * @return The removed tag, or null if no tag with the given name exists
     */
    public Tag<?> remove(String name) {
        return value.remove(name);
    }

    /**
     * Checks if this compound tag contains a tag with the given name.
     *
     * @param name The name to check
     * @return True if this compound tag contains a tag with the given name, false otherwise
     */
    public boolean contains(String name) {
        return value.containsKey(name);
    }

    /**
     * Gets the names of all tags in this compound tag.
     *
     * @return The names of all tags in this compound tag
     */
    public Set<String> getNames() {
        return value.keySet();
    }

    /**
     * Gets the number of tags in this compound tag.
     *
     * @return The number of tags
     */
    public int size() {
        return value.size();
    }

    /**
     * Convenience method to get a byte value
     * 
     * @param name Name of the tag
     * @param defaultValue Value to return if the tag doesn't exist or is wrong type
     * @return The byte value or default
     */
    public byte getByte(String name, byte defaultValue) {
        ByteTag tag = get(name, ByteTag.class);
        return tag != null ? tag.getValue() : defaultValue;
    }

    /**
     * Convenience method to get a short value
     * 
     * @param name Name of the tag
     * @param defaultValue Value to return if the tag doesn't exist or is wrong type
     * @return The short value or default
     */
    public short getShort(String name, short defaultValue) {
        ShortTag tag = get(name, ShortTag.class);
        return tag != null ? tag.getValue() : defaultValue;
    }

    /**
     * Convenience method to get an int value
     * 
     * @param name Name of the tag
     * @param defaultValue Value to return if the tag doesn't exist or is wrong type
     * @return The int value or default
     */
    public int getInt(String name, int defaultValue) {
        IntTag tag = get(name, IntTag.class);
        return tag != null ? tag.getValue() : defaultValue;
    }

    /**
     * Convenience method to get a long value
     * 
     * @param name Name of the tag
     * @param defaultValue Value to return if the tag doesn't exist or is wrong type
     * @return The long value or default
     */
    public long getLong(String name, long defaultValue) {
        LongTag tag = get(name, LongTag.class);
        return tag != null ? tag.getValue() : defaultValue;
    }

    /**
     * Convenience method to get a float value
     * 
     * @param name Name of the tag
     * @param defaultValue Value to return if the tag doesn't exist or is wrong type
     * @return The float value or default
     */
    public float getFloat(String name, float defaultValue) {
        FloatTag tag = get(name, FloatTag.class);
        return tag != null ? tag.getValue() : defaultValue;
    }

    /**
     * Convenience method to get a double value
     * 
     * @param name Name of the tag
     * @param defaultValue Value to return if the tag doesn't exist or is wrong type
     * @return The double value or default
     */
    public double getDouble(String name, double defaultValue) {
        DoubleTag tag = get(name, DoubleTag.class);
        return tag != null ? tag.getValue() : defaultValue;
    }

    /**
     * Convenience method to get a string value
     * 
     * @param name Name of the tag
     * @param defaultValue Value to return if the tag doesn't exist or is wrong type
     * @return The string value or default
     */
    public String getString(String name, String defaultValue) {
        StringTag tag = get(name, StringTag.class);
        return tag != null ? tag.getValue() : defaultValue;
    }

    /**
     * Convenience method to get a byte array
     * 
     * @param name Name of the tag
     * @return The byte array or null if the tag doesn't exist or is wrong type
     */
    public byte[] getByteArray(String name) {
        ByteArrayTag tag = get(name, ByteArrayTag.class);
        return tag != null ? tag.getValue() : null;
    }

    /**
     * Convenience method to get an int array
     * 
     * @param name Name of the tag
     * @return The int array or null if the tag doesn't exist or is wrong type
     */
    public int[] getIntArray(String name) {
        IntArrayTag tag = get(name, IntArrayTag.class);
        return tag != null ? tag.getValue() : null;
    }

    /**
     * Convenience method to get a long array
     * 
     * @param name Name of the tag
     * @return The long array or null if the tag doesn't exist or is wrong type
     */
    public long[] getLongArray(String name) {
        LongArrayTag tag = get(name, LongArrayTag.class);
        return tag != null ? tag.getValue() : null;
    }

    /**
     * Convenience method to put a byte value
     * 
     * @param name Name of the tag
     * @param value Value to store
     * @return This compound tag for chaining
     */
    public CompoundTag putByte(String name, byte value) {
        return put(new ByteTag(name, value));
    }

    /**
     * Convenience method to put a short value
     * 
     * @param name Name of the tag
     * @param value Value to store
     * @return This compound tag for chaining
     */
    public CompoundTag putShort(String name, short value) {
        return put(new ShortTag(name, value));
    }

    /**
     * Convenience method to put an int value
     * 
     * @param name Name of the tag
     * @param value Value to store
     * @return This compound tag for chaining
     */
    public CompoundTag putInt(String name, int value) {
        return put(new IntTag(name, value));
    }

    /**
     * Convenience method to put a long value
     * 
     * @param name Name of the tag
     * @param value Value to store
     * @return This compound tag for chaining
     */
    public CompoundTag putLong(String name, long value) {
        return put(new LongTag(name, value));
    }

    /**
     * Convenience method to put a float value
     * 
     * @param name Name of the tag
     * @param value Value to store
     * @return This compound tag for chaining
     */
    public CompoundTag putFloat(String name, float value) {
        return put(new FloatTag(name, value));
    }

    /**
     * Convenience method to put a double value
     * 
     * @param name Name of the tag
     * @param value Value to store
     * @return This compound tag for chaining
     */
    public CompoundTag putDouble(String name, double value) {
        return put(new DoubleTag(name, value));
    }

    /**
     * Convenience method to put a string value
     * 
     * @param name Name of the tag
     * @param value Value to store
     * @return This compound tag for chaining
     */
    public CompoundTag putString(String name, String value) {
        return put(new StringTag(name, value));
    }

    /**
     * Convenience method to put a byte array
     * 
     * @param name Name of the tag
     * @param value Value to store
     * @return This compound tag for chaining
     */
    public CompoundTag putByteArray(String name, byte[] value) {
        return put(new ByteArrayTag(name, value));
    }

    /**
     * Convenience method to put an int array
     * 
     * @param name Name of the tag
     * @param value Value to store
     * @return This compound tag for chaining
     */
    public CompoundTag putIntArray(String name, int[] value) {
        return put(new IntArrayTag(name, value));
    }

    /**
     * Convenience method to put a long array
     * 
     * @param name Name of the tag
     * @param value Value to store
     * @return This compound tag for chaining
     */
    public CompoundTag putLongArray(String name, long[] value) {
        return put(new LongArrayTag(name, value));
    }

    @Override
    protected void writePayload(DataOutputStream dos) throws IOException {
        // Write all the tags in this compound tag
        for (Tag<?> tag : value.values()) {
            tag.write(dos);
        }
        
        // Write an end tag to signify the end of this compound tag
        dos.writeByte(TagType.END.getId());
    }

    @Override
    protected void readPayload(DataInputStream dis) throws IOException {
        value.clear();
        
        // Read tags until we encounter an end tag
        byte tagId;
        while ((tagId = dis.readByte()) != TagType.END.getId()) {
            TagType type = TagType.fromId(tagId);
            boolean hasName = dis.readBoolean();
            String name = hasName ? dis.readUTF() : null;
            if (name == null) {
                throw new IOException("Tag name cannot be null");
            }
            
            Tag<?> tag = createTag(type, name);
            tag.readPayload(dis);
            value.put(name, tag);
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