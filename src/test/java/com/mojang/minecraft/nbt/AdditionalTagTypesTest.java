package com.mojang.minecraft.nbt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class AdditionalTagTypesTest {

    @TempDir
    Path tempDir;

    @Test
    public void testAllNumericTagTypes() throws IOException {
        // Create tags for all numeric types
        CompoundTag rootTag = new CompoundTag("numericData");
        
        rootTag.putByte("byteValue", (byte) 127);
        rootTag.putShort("shortValue", (short) 32000);
        rootTag.putInt("intValue", 2000000000);
        rootTag.putLong("longValue", 9000000000000000000L);
        rootTag.putFloat("floatValue", 3.14159f);
        rootTag.putDouble("doubleValue", Math.PI);
        
        // Serialize to bytes
        byte[] data = NBTSerializer.serializeToBytes(rootTag);
        
        // Deserialize from bytes
        Tag<?> deserializedTag = NBTSerializer.deserializeFromBytes(data);
        assertTrue(deserializedTag instanceof CompoundTag);
        
        // Check values
        CompoundTag deserializedRoot = (CompoundTag) deserializedTag;
        assertEquals((byte) 127, deserializedRoot.getByte("byteValue", (byte) 0));
        assertEquals((short) 32000, deserializedRoot.getShort("shortValue", (short) 0));
        assertEquals(2000000000, deserializedRoot.getInt("intValue", 0));
        assertEquals(9000000000000000000L, deserializedRoot.getLong("longValue", 0L));
        assertEquals(3.14159f, deserializedRoot.getFloat("floatValue", 0f), 0.00001f);
        assertEquals(Math.PI, deserializedRoot.getDouble("doubleValue", 0.0), 0.00001);
    }
    
    @Test
    public void testArrayTagTypes() throws IOException {
        // Create array tags
        CompoundTag rootTag = new CompoundTag("arrayData");
        
        byte[] byteArray = new byte[] { 1, 2, 3, 4, 5, 10, 20, 30, 40, 50 };
        int[] intArray = new int[] { 100, 200, 300, 400, 500 };
        long[] longArray = new long[] { 1000L, 2000L, 3000L, 4000L, 5000L };
        
        rootTag.putByteArray("byteArray", byteArray);
        rootTag.putIntArray("intArray", intArray);
        rootTag.putLongArray("longArray", longArray);
        
        // Serialize to file
        File file = tempDir.resolve("arrays.nbt").toFile();
        NBTSerializer.serialize(rootTag, file);
        
        // Deserialize from file
        Tag<?> deserializedTag = NBTSerializer.deserialize(file);
        assertTrue(deserializedTag instanceof CompoundTag);
        
        // Check array values
        CompoundTag deserializedRoot = (CompoundTag) deserializedTag;
        
        byte[] deserializedByteArray = deserializedRoot.getByteArray("byteArray");
        assertNotNull(deserializedByteArray);
        assertArrayEquals(byteArray, deserializedByteArray);
        
        int[] deserializedIntArray = deserializedRoot.getIntArray("intArray");
        assertNotNull(deserializedIntArray);
        assertArrayEquals(intArray, deserializedIntArray);
        
        long[] deserializedLongArray = deserializedRoot.getLongArray("longArray");
        assertNotNull(deserializedLongArray);
        assertArrayEquals(longArray, deserializedLongArray);
    }
    
    @Test
    public void testListContainingDifferentTypes() throws IOException {
        CompoundTag rootTag = new CompoundTag("root");
        
        // Create lists of different types
        ListTag byteList = new ListTag("byteList");
        byteList.addByte((byte) 10);
        byteList.addByte((byte) 20);
        byteList.addByte((byte) 30);
        rootTag.put(byteList);
        
        ListTag stringList = new ListTag("stringList");
        stringList.addString("Hello");
        stringList.addString("World");
        rootTag.put(stringList);
        
        ListTag doubleList = new ListTag("doubleList");
        doubleList.addDouble(1.1);
        doubleList.addDouble(2.2);
        doubleList.addDouble(3.3);
        rootTag.put(doubleList);
        
        // Create a list of compound tags
        ListTag compoundList = new ListTag("compoundList");
        
        CompoundTag item1 = new CompoundTag();
        item1.putString("name", "Item 1");
        item1.putFloat("weight", 10.5f);
        compoundList.add(item1);
        
        CompoundTag item2 = new CompoundTag();
        item2.putString("name", "Item 2");
        item2.putFloat("weight", 20.7f);
        compoundList.add(item2);
        
        rootTag.put(compoundList);
        
        // Serialize and deserialize
        byte[] data = NBTSerializer.serializeToBytes(rootTag);
        Tag<?> deserializedTag = NBTSerializer.deserializeFromBytes(data);
        
        // Check the structure
        assertTrue(deserializedTag instanceof CompoundTag);
        CompoundTag deserializedRoot = (CompoundTag) deserializedTag;
        
        // Check byte list
        ListTag deserializedByteList = deserializedRoot.get("byteList", ListTag.class);
        assertNotNull(deserializedByteList);
        assertEquals(3, deserializedByteList.size());
        assertEquals(TagType.BYTE, deserializedByteList.getListType());
        assertEquals((byte) 10, deserializedByteList.get(0, ByteTag.class).getValue());
        assertEquals((byte) 20, deserializedByteList.get(1, ByteTag.class).getValue());
        assertEquals((byte) 30, deserializedByteList.get(2, ByteTag.class).getValue());
        
        // Check string list
        ListTag deserializedStringList = deserializedRoot.get("stringList", ListTag.class);
        assertNotNull(deserializedStringList);
        assertEquals(2, deserializedStringList.size());
        assertEquals(TagType.STRING, deserializedStringList.getListType());
        assertEquals("Hello", deserializedStringList.get(0, StringTag.class).getValue());
        assertEquals("World", deserializedStringList.get(1, StringTag.class).getValue());
        
        // Check double list
        ListTag deserializedDoubleList = deserializedRoot.get("doubleList", ListTag.class);
        assertNotNull(deserializedDoubleList);
        assertEquals(3, deserializedDoubleList.size());
        assertEquals(TagType.DOUBLE, deserializedDoubleList.getListType());
        assertEquals(1.1, deserializedDoubleList.get(0, DoubleTag.class).getValue(), 0.001);
        assertEquals(2.2, deserializedDoubleList.get(1, DoubleTag.class).getValue(), 0.001);
        assertEquals(3.3, deserializedDoubleList.get(2, DoubleTag.class).getValue(), 0.001);
        
        // Check compound list
        ListTag deserializedCompoundList = deserializedRoot.get("compoundList", ListTag.class);
        assertNotNull(deserializedCompoundList);
        assertEquals(2, deserializedCompoundList.size());
        assertEquals(TagType.COMPOUND, deserializedCompoundList.getListType());
        
        CompoundTag deserializedItem1 = deserializedCompoundList.get(0, CompoundTag.class);
        assertEquals("Item 1", deserializedItem1.getString("name", ""));
        assertEquals(10.5f, deserializedItem1.getFloat("weight", 0f), 0.001f);
        
        CompoundTag deserializedItem2 = deserializedCompoundList.get(1, CompoundTag.class);
        assertEquals("Item 2", deserializedItem2.getString("name", ""));
        assertEquals(20.7f, deserializedItem2.getFloat("weight", 0f), 0.001f);
    }
    
    @Test
    public void testComplexStructureWithAllTypes() throws IOException {
        // Create a comprehensive data structure using all tag types
        CompoundTag rootTag = new CompoundTag("allTypes");
        
        // Add primitive values
        rootTag.putByte("byte", (byte) 127);
        rootTag.putShort("short", (short) 32767);
        rootTag.putInt("int", Integer.MAX_VALUE);
        rootTag.putLong("long", Long.MAX_VALUE);
        rootTag.putFloat("float", Float.MAX_VALUE);
        rootTag.putDouble("double", Double.MAX_VALUE);
        rootTag.putString("string", "This is a string value");
        
        // Add arrays
        rootTag.putByteArray("byteArray", new byte[] { 1, 2, 3, 4, 5 });
        rootTag.putIntArray("intArray", new int[] { 100, 200, 300 });
        rootTag.putLongArray("longArray", new long[] { 1000L, 2000L });
        
        // Add a nested compound
        CompoundTag nestedCompound = new CompoundTag("nested");
        nestedCompound.putString("name", "Nested Structure");
        nestedCompound.putInt("level", 2);
        
        // Add a compound list inside the nested compound
        ListTag items = new ListTag("items");
        
        CompoundTag item1 = new CompoundTag();
        item1.putString("id", "item1");
        item1.putByte("count", (byte) 5);
        items.add(item1);
        
        CompoundTag item2 = new CompoundTag();
        item2.putString("id", "item2");
        item2.putByte("count", (byte) 10);
        // Add a further nested compound to demonstrate deep nesting
        CompoundTag metadata = new CompoundTag("metadata");
        metadata.putString("material", "iron");
        metadata.putShort("durability", (short) 250);
        item2.put(metadata);
        items.add(item2);
        
        nestedCompound.put(items);
        rootTag.put(nestedCompound);
        
        // Add assorted lists
        ListTag byteList = new ListTag("byteList");
        for (byte b = 1; b <= 5; b++) {
            byteList.addByte(b);
        }
        rootTag.put(byteList);
        
        ListTag doubleList = new ListTag("doubleList");
        doubleList.addDouble(1.1);
        doubleList.addDouble(2.2);
        rootTag.put(doubleList);
        
        // Nested lists
        ListTag listOfLists = new ListTag("listOfLists");
        
        ListTag subList1 = new ListTag();
        subList1.addString("List 1 Item 1");
        subList1.addString("List 1 Item 2");
        listOfLists.add(subList1);
        
        ListTag subList2 = new ListTag();
        subList2.addString("List 2 Item 1");
        subList2.addString("List 2 Item 2");
        listOfLists.add(subList2);
        
        rootTag.put(listOfLists);
        
        // Serialize
        File file = tempDir.resolve("complex.nbt").toFile();
        NBTSerializer.serialize(rootTag, file);
        
        // Deserialize
        Tag<?> deserializedTag = NBTSerializer.deserialize(file);
        assertTrue(deserializedTag instanceof CompoundTag);
        
        // Verify structure
        CompoundTag deserializedRoot = (CompoundTag) deserializedTag;
        
        // Check primitive values
        assertEquals((byte) 127, deserializedRoot.getByte("byte", (byte) 0));
        assertEquals((short) 32767, deserializedRoot.getShort("short", (short) 0));
        assertEquals(Integer.MAX_VALUE, deserializedRoot.getInt("int", 0));
        assertEquals(Long.MAX_VALUE, deserializedRoot.getLong("long", 0L));
        assertEquals(Float.MAX_VALUE, deserializedRoot.getFloat("float", 0f), 0.001f);
        assertEquals(Double.MAX_VALUE, deserializedRoot.getDouble("double", 0.0), 0.001);
        assertEquals("This is a string value", deserializedRoot.getString("string", ""));
        
        // Check arrays
        assertArrayEquals(new byte[] { 1, 2, 3, 4, 5 }, deserializedRoot.getByteArray("byteArray"));
        assertArrayEquals(new int[] { 100, 200, 300 }, deserializedRoot.getIntArray("intArray"));
        assertArrayEquals(new long[] { 1000L, 2000L }, deserializedRoot.getLongArray("longArray"));
        
        // Check nested compound
        CompoundTag deserializedNested = deserializedRoot.get("nested", CompoundTag.class);
        assertNotNull(deserializedNested);
        assertEquals("Nested Structure", deserializedNested.getString("name", ""));
        assertEquals(2, deserializedNested.getInt("level", 0));
        
        // Check compound list in nested compound
        ListTag deserializedItems = deserializedNested.get("items", ListTag.class);
        assertNotNull(deserializedItems);
        assertEquals(2, deserializedItems.size());
        
        CompoundTag deserializedItem1 = deserializedItems.get(0, CompoundTag.class);
        assertEquals("item1", deserializedItem1.getString("id", ""));
        assertEquals((byte) 5, deserializedItem1.getByte("count", (byte) 0));
        
        CompoundTag deserializedItem2 = deserializedItems.get(1, CompoundTag.class);
        assertEquals("item2", deserializedItem2.getString("id", ""));
        assertEquals((byte) 10, deserializedItem2.getByte("count", (byte) 0));
        
        // Check deep nesting
        CompoundTag deserializedMetadata = deserializedItem2.get("metadata", CompoundTag.class);
        assertNotNull(deserializedMetadata);
        assertEquals("iron", deserializedMetadata.getString("material", ""));
        assertEquals((short) 250, deserializedMetadata.getShort("durability", (short) 0));
        
        // Check lists
        ListTag deserializedByteList = deserializedRoot.get("byteList", ListTag.class);
        assertNotNull(deserializedByteList);
        assertEquals(5, deserializedByteList.size());
        for (byte b = 1; b <= 5; b++) {
            assertEquals(b, deserializedByteList.get(b - 1, ByteTag.class).getValue());
        }
        
        ListTag deserializedDoubleList = deserializedRoot.get("doubleList", ListTag.class);
        assertNotNull(deserializedDoubleList);
        assertEquals(2, deserializedDoubleList.size());
        assertEquals(1.1, deserializedDoubleList.get(0, DoubleTag.class).getValue(), 0.001);
        assertEquals(2.2, deserializedDoubleList.get(1, DoubleTag.class).getValue(), 0.001);
        
        // Check nested lists
        ListTag deserializedListOfLists = deserializedRoot.get("listOfLists", ListTag.class);
        assertNotNull(deserializedListOfLists);
        assertEquals(2, deserializedListOfLists.size());
        
        ListTag deserializedSubList1 = deserializedListOfLists.get(0, ListTag.class);
        assertNotNull(deserializedSubList1);
        assertEquals(2, deserializedSubList1.size());
        assertEquals("List 1 Item 1", deserializedSubList1.get(0, StringTag.class).getValue());
        assertEquals("List 1 Item 2", deserializedSubList1.get(1, StringTag.class).getValue());
        
        ListTag deserializedSubList2 = deserializedListOfLists.get(1, ListTag.class);
        assertNotNull(deserializedSubList2);
        assertEquals(2, deserializedSubList2.size());
        assertEquals("List 2 Item 1", deserializedSubList2.get(0, StringTag.class).getValue());
        assertEquals("List 2 Item 2", deserializedSubList2.get(1, StringTag.class).getValue());
    }
} 