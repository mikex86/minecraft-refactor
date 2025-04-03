package com.mojang.minecraft.nbt;

import java.io.*;
import java.nio.file.Files;

/**
 * Utility class for serializing and deserializing NBT data.
 */
public class NBTSerializer {

    /**
     * Deserializes a named tag from a data input stream.
     *
     * @param dis The data input stream to deserialize from
     * @return The deserialized tag
     * @throws IOException If an I/O error occurs
     */
    public static Tag<?> deserialize(DataInputStream dis) throws IOException {
        byte tagId = dis.readByte();
        TagType type = TagType.fromId(tagId);

        if (type == TagType.END) {
            return null;
        }

        boolean hasName = dis.readBoolean();
        String name = hasName ? dis.readUTF() : null;

        Tag<?> tag = createTag(type, name);
        tag.readPayload(dis);

        return tag;
    }

    /**
     * Deserializes a named tag from a file.
     *
     * @param file The file to deserialize from
     * @return The deserialized tag
     * @throws IOException If an I/O error occurs
     */
    public static Tag<?> deserialize(File file) throws IOException {
        try (DataInputStream dis = new DataInputStream(Files.newInputStream(file.toPath()))) {
            return deserialize(dis);
        }
    }

    /**
     * Deserializes a named tag from a byte array.
     *
     * @param data The byte array to deserialize from
     * @return The deserialized tag
     * @throws IOException If an I/O error occurs
     */
    public static Tag<?> deserializeFromBytes(byte[] data) throws IOException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             DataInputStream dis = new DataInputStream(bais)) {
            return deserialize(dis);
        }
    }

    /**
     * Serializes a named tag to a byte array.
     *
     * @param tag The tag to serialize
     * @return The serialized tag as a byte array
     * @throws IOException If an I/O error occurs
     */
    public static byte[] serializeToBytes(Tag<?> tag) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(baos)) {
            tag.write(dos);
            return baos.toByteArray();
        }
    }

    /**
     * Serializes a named tag to a file.
     *
     * @param tag  The tag to serialize
     * @param file The file to serialize to
     * @throws IOException If an I/O error occurs
     */
    public static void serialize(Tag<?> tag, File file) throws IOException {
        try (DataOutputStream dos = new DataOutputStream(Files.newOutputStream(file.toPath()))) {
            tag.write(dos);
        }
    }

    private static Tag<?> createTag(TagType type, String name) {
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