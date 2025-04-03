package com.mojang.minecraft.nbt;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Base class for all Named Binary Tag (NBT) elements.
 * This provides the foundation for a hierarchical data structure that
 * can be serialized to and deserialized from binary format.
 */
public abstract class Tag<T> {
    private final String name;
    private final TagType type;

    /**
     * Constructs a new tag with the given name and type.
     *
     * @param name The name of the tag, may be null for list elements
     * @param type The type of the tag
     */
    protected Tag(String name, TagType type) {
        this.name = name;
        this.type = type;
    }

    /**
     * Gets the name of this tag.
     *
     * @return The tag's name, or null if this tag doesn't have a name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the type of this tag.
     *
     * @return The tag's type
     */
    public TagType getType() {
        return type;
    }

    /**
     * Gets the value of this tag.
     *
     * @return The tag's value
     */
    public abstract T getValue();

    /**
     * Writes this tag to the given output stream.
     *
     * @param dos The output stream to write to
     * @throws IOException If an I/O error occurs
     */
    public void write(DataOutputStream dos) throws IOException {
        // Write the tag type
        dos.writeByte(type.getId());

        // Write the name
        dos.writeBoolean(name != null);
        if (name != null) {
            dos.writeUTF(name);
        }

        // Write the payload
        writePayload(dos);
    }

    /**
     * Writes the payload of this tag to the given output stream.
     *
     * @param dos The output stream to write to
     * @throws IOException If an I/O error occurs
     */
    protected abstract void writePayload(DataOutputStream dos) throws IOException;

    /**
     * Reads the payload of this tag from the given input stream.
     *
     * @param dis The input stream to read from
     * @throws IOException If an I/O error occurs
     */
    protected abstract void readPayload(DataInputStream dis) throws IOException;
} 