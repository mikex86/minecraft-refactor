package com.mojang.minecraft.nbt;

/**
 * Enumeration of all possible NBT tag types.
 */
public enum TagType {
    END(0),
    BYTE(1),
    SHORT(2),
    INT(3),
    LONG(4),
    FLOAT(5),
    DOUBLE(6),
    BYTE_ARRAY(7),
    STRING(8),
    LIST(9),
    COMPOUND(10),
    INT_ARRAY(11),
    LONG_ARRAY(12);

    private final int id;

    TagType(int id) {
        this.id = id;
    }

    /**
     * Gets the numeric ID of this tag type.
     *
     * @return The tag type's ID
     */
    public int getId() {
        return id;
    }

    /**
     * Gets a tag type by its numeric ID.
     *
     * @param id The numeric ID of the tag type
     * @return The corresponding tag type
     * @throws IllegalArgumentException If no tag type with the given ID exists
     */
    public static TagType fromId(int id) {
        for (TagType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        throw new IllegalArgumentException("No tag type with ID " + id);
    }
} 