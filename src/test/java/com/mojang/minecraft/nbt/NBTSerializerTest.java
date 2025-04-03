package com.mojang.minecraft.nbt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class NBTSerializerTest {

    @TempDir
    Path tempDir;

    @Test
    public void testSimpleTagSerialization() throws IOException {
        // Create a simple tag
        ByteTag byteTag = new ByteTag("byte", (byte) 42);
        
        // Serialize to bytes
        byte[] data = NBTSerializer.serializeToBytes(byteTag);
        
        // Deserialize from bytes
        Tag<?> deserializedTag = NBTSerializer.deserializeFromBytes(data);
        
        // Check the tag
        assertNotNull(deserializedTag);
        assertEquals(TagType.BYTE, deserializedTag.getType());
        assertEquals("byte", deserializedTag.getName());
        assertTrue(deserializedTag instanceof ByteTag);
        assertEquals((byte) 42, ((ByteTag) deserializedTag).getValue());
    }
    
    @Test
    public void testComplexTagSerialization() throws IOException {
        // Create a complex tag structure
        CompoundTag rootTag = new CompoundTag("root");
        
        // Add some simple tags
        rootTag.putByte("byteValue", (byte) 127);
        rootTag.putInt("intValue", 12345);
        rootTag.putString("stringValue", "Hello, NBT!");
        
        // Add a nested compound tag
        CompoundTag nestedTag = new CompoundTag("nested");
        nestedTag.putInt("nestedInt", 54321);
        rootTag.put(nestedTag);
        
        // Add a list tag
        ListTag listTag = new ListTag("intList");
        listTag.addInt(1);
        listTag.addInt(2);
        listTag.addInt(3);
        rootTag.put(listTag);
        
        // Serialize to file
        File file = tempDir.resolve("test.nbt").toFile();
        NBTSerializer.serialize(rootTag, file);
        
        // Deserialize from file
        Tag<?> deserializedTag = NBTSerializer.deserialize(file);
        
        // Check the tag
        assertNotNull(deserializedTag);
        assertEquals(TagType.COMPOUND, deserializedTag.getType());
        assertEquals("root", deserializedTag.getName());
        assertTrue(deserializedTag instanceof CompoundTag);
        
        CompoundTag deserializedRoot = (CompoundTag) deserializedTag;
        
        // Check simple values
        assertEquals((byte) 127, deserializedRoot.getByte("byteValue", (byte) 0));
        assertEquals(12345, deserializedRoot.getInt("intValue", 0));
        assertEquals("Hello, NBT!", deserializedRoot.getString("stringValue", ""));
        
        // Check nested compound
        CompoundTag deserializedNested = deserializedRoot.get("nested", CompoundTag.class);
        assertNotNull(deserializedNested);
        assertEquals(54321, deserializedNested.getInt("nestedInt", 0));
        
        // Check list
        ListTag deserializedList = deserializedRoot.get("intList", ListTag.class);
        assertNotNull(deserializedList);
        assertEquals(3, deserializedList.size());
        assertEquals(TagType.INT, deserializedList.getListType());
        assertEquals(1, deserializedList.get(0, IntTag.class).getValue());
        assertEquals(2, deserializedList.get(1, IntTag.class).getValue());
        assertEquals(3, deserializedList.get(2, IntTag.class).getValue());
    }
    
    @Test
    public void testEmptyCompoundTag() throws IOException {
        // Create an empty compound tag
        CompoundTag emptyTag = new CompoundTag("empty");
        
        // Serialize to bytes
        byte[] data = NBTSerializer.serializeToBytes(emptyTag);
        
        // Deserialize from bytes
        Tag<?> deserializedTag = NBTSerializer.deserializeFromBytes(data);
        
        // Check the tag
        assertNotNull(deserializedTag);
        assertEquals(TagType.COMPOUND, deserializedTag.getType());
        assertEquals("empty", deserializedTag.getName());
        assertTrue(deserializedTag instanceof CompoundTag);
        assertEquals(0, ((CompoundTag) deserializedTag).size());
    }
    
    @Test
    public void testNestedListStructure() throws IOException {
        // Create a compound tag with a nested list of compounds
        CompoundTag rootTag = new CompoundTag("root");
        
        // Create a list of compound tags
        ListTag personList = new ListTag("persons");
        
        // Add some person entries
        CompoundTag person1 = new CompoundTag();
        person1.putString("name", "Alice");
        person1.putInt("age", 30);
        personList.add(person1);
        
        CompoundTag person2 = new CompoundTag();
        person2.putString("name", "Bob");
        person2.putInt("age", 25);
        personList.add(person2);
        
        // Add the list to the root tag
        rootTag.put(personList);
        
        // Serialize to bytes
        byte[] data = NBTSerializer.serializeToBytes(rootTag);
        
        // Deserialize from bytes
        Tag<?> deserializedTag = NBTSerializer.deserializeFromBytes(data);
        
        // Check the tag structure
        assertTrue(deserializedTag instanceof CompoundTag);
        CompoundTag deserializedRoot = (CompoundTag) deserializedTag;
        
        // Check the persons list
        ListTag deserializedPersons = deserializedRoot.get("persons", ListTag.class);
        assertNotNull(deserializedPersons);
        assertEquals(2, deserializedPersons.size());
        
        // Check the first person
        CompoundTag deserializedPerson1 = deserializedPersons.get(0, CompoundTag.class);
        assertEquals("Alice", deserializedPerson1.getString("name", ""));
        assertEquals(30, deserializedPerson1.getInt("age", 0));
        
        // Check the second person
        CompoundTag deserializedPerson2 = deserializedPersons.get(1, CompoundTag.class);
        assertEquals("Bob", deserializedPerson2.getString("name", ""));
        assertEquals(25, deserializedPerson2.getInt("age", 0));
    }
    
    @Test
    public void testRealWorldExample() throws IOException {
        // Create a compound tag representing a game save
        CompoundTag saveData = new CompoundTag("gameState");
        
        // Add game metadata
        saveData.putString("gameName", "Adventure Quest");
        saveData.putInt("saveVersion", 1);
        saveData.putString("saveDate", "2023-08-01");
        
        // Add player data
        CompoundTag playerData = new CompoundTag("player");
        playerData.putString("name", "Adventurer");
        playerData.putInt("level", 5);
        playerData.putInt("health", 85);
        playerData.putInt("maxHealth", 100);
        
        // Add player inventory
        ListTag inventory = new ListTag("inventory");
        
        // Add some items
        CompoundTag item1 = new CompoundTag();
        item1.putString("id", "sword");
        item1.putString("name", "Steel Sword");
        item1.putInt("damage", 15);
        inventory.add(item1);
        
        CompoundTag item2 = new CompoundTag();
        item2.putString("id", "potion");
        item2.putString("name", "Health Potion");
        item2.putInt("restoreAmount", 50);
        inventory.add(item2);
        
        // Add inventory to player
        playerData.put(inventory);
        
        // Add quest data
        ListTag quests = new ListTag("quests");
        
        CompoundTag quest1 = new CompoundTag();
        quest1.putString("id", "quest1");
        quest1.putString("name", "The Lost Artifact");
        quest1.putByte("completed", (byte) 1);
        quests.add(quest1);
        
        CompoundTag quest2 = new CompoundTag();
        quest2.putString("id", "quest2");
        quest2.putString("name", "Dragon's Lair");
        quest2.putByte("completed", (byte) 0);
        quests.add(quest2);
        
        // Add quests to player
        playerData.put(quests);
        
        // Add player to save data
        saveData.put(playerData);
        
        // Serialize to file
        File file = tempDir.resolve("gamesave.nbt").toFile();
        NBTSerializer.serialize(saveData, file);
        
        // Deserialize from file
        Tag<?> deserializedTag = NBTSerializer.deserialize(file);
        
        // Check the tag structure
        assertTrue(deserializedTag instanceof CompoundTag);
        CompoundTag deserializedSave = (CompoundTag) deserializedTag;
        
        // Check game metadata
        assertEquals("Adventure Quest", deserializedSave.getString("gameName", ""));
        assertEquals(1, deserializedSave.getInt("saveVersion", 0));
        assertEquals("2023-08-01", deserializedSave.getString("saveDate", ""));
        
        // Check player data
        CompoundTag deserializedPlayer = deserializedSave.get("player", CompoundTag.class);
        assertNotNull(deserializedPlayer);
        assertEquals("Adventurer", deserializedPlayer.getString("name", ""));
        assertEquals(5, deserializedPlayer.getInt("level", 0));
        assertEquals(85, deserializedPlayer.getInt("health", 0));
        assertEquals(100, deserializedPlayer.getInt("maxHealth", 0));
        
        // Check inventory
        ListTag deserializedInventory = deserializedPlayer.get("inventory", ListTag.class);
        assertNotNull(deserializedInventory);
        assertEquals(2, deserializedInventory.size());
        
        // Check first item
        CompoundTag deserializedItem1 = deserializedInventory.get(0, CompoundTag.class);
        assertEquals("sword", deserializedItem1.getString("id", ""));
        assertEquals("Steel Sword", deserializedItem1.getString("name", ""));
        assertEquals(15, deserializedItem1.getInt("damage", 0));
        
        // Check quests
        ListTag deserializedQuests = deserializedPlayer.get("quests", ListTag.class);
        assertNotNull(deserializedQuests);
        assertEquals(2, deserializedQuests.size());
        
        // Check second quest
        CompoundTag deserializedQuest2 = deserializedQuests.get(1, CompoundTag.class);
        assertEquals("quest2", deserializedQuest2.getString("id", ""));
        assertEquals("Dragon's Lair", deserializedQuest2.getString("name", ""));
        assertEquals((byte) 0, deserializedQuest2.getByte("completed", (byte) 2));
    }
} 