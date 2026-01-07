package com.mojang.minecraft.gui.screen;

import com.mojang.minecraft.entity.EntityPlayer;
import com.mojang.minecraft.gui.Font;
import com.mojang.minecraft.gui.TextLabel;
import com.mojang.minecraft.item.BlockItem;
import com.mojang.minecraft.item.HeldItem;
import com.mojang.minecraft.item.Item;
import com.mojang.minecraft.item.ItemStack;
import com.mojang.minecraft.item.inventory.Inventory;
import com.mojang.minecraft.optim.pools.StackCountStringPool;
import com.mojang.minecraft.renderer.Tesselator;
import com.mojang.minecraft.renderer.TextureManager;
import com.mojang.minecraft.renderer.block.BlockRenderer;
import com.mojang.minecraft.renderer.graphics.GraphicsAPI;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums;
import com.mojang.minecraft.renderer.graphics.IndexedMesh;
import com.mojang.minecraft.renderer.item.HeldItemRenderer;
import com.mojang.minecraft.renderer.shader.ShaderRegistry;
import com.mojang.minecraft.renderer.shader.impl.EntityShader;
import com.mojang.minecraft.renderer.shader.impl.HudShader;
import com.mojang.minecraft.renderer.shader.impl.WorldShader;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static com.mojang.minecraft.entity.EntityPlayer.PLAYER_MODEL;

public class InventoryScreen extends GuiScreen {

    private final EntityPlayer player;
    private final Inventory inventory;
    private final TextureManager textureManager;
    private final Font font;
    private final HeldItemRenderer heldItemRenderer;

    private float screenWidth;
    private float screenHeight;
    private float mouseX = -1;
    private float mouseY = -1;
    private boolean rightMouseDown = false;
    private final Set<Slot> rightClickVisitedSlots = new HashSet<>();

    private final List<Slot> slots = new ArrayList<>();
    private final TextLabel stackSizeSelectedItemLabel;

    private static final EntityShader ENTITY_SHADER = ShaderRegistry.getInstance().getEntityShader();
    private static final WorldShader WORLD_SHADER = ShaderRegistry.getInstance().getWorldShader();
    private static final HudShader HUD_SHADER = ShaderRegistry.getInstance().getHudShader();

    public InventoryScreen(TextureManager textureManager, Font font, Inventory inventory) {
        this.textureManager = textureManager;
        this.font = font;
        this.heldItemRenderer = new HeldItemRenderer(textureManager.itemsTexture);

        this.player = new EntityPlayer(null, false);
        this.stackSizeSelectedItemLabel = new TextLabel(font, 0xFFFFFF, true);
        this.inventory = inventory;

        float baseX = -INVENTORY_UI_WIDTH / 2f;

        addMainInventorySlots(baseX);
        addHotbarSlots(baseX);
        addCraftingSlots(baseX);
        addArmorSlots(baseX);
    }

    private void addMainInventorySlots(float baseX) {
        for (int row = 0; row < inventory.getMainInventoryRowCount(); row++) {
            for (int column = 0; column < inventory.getColumnCount(); column++) {
                float slotOffsetX = baseX + 8 + ITEM_SLOT_SIZE * column;
                float slotOffsetY = ITEM_SLOT_SIZE * row;
                final int slotRow = row;
                final int slotColumn = column;
                this.slots.add(new Slot(() -> inventory.getInventoryItem(slotRow, slotColumn),
                        () -> inventory.clickItem(slotRow, slotColumn),
                        () -> inventory.placeSingleInventoryItem(slotRow, slotColumn),
                        slotOffsetX, slotOffsetY));
            }
        }
    }

    private void addHotbarSlots(float baseX) {
        int hotbarRow = inventory.getMainInventoryRowCount();
        float hotbarSlotOffsetY = ITEM_SLOT_SIZE * hotbarRow + 4;
        for (int column = 0; column < inventory.getHotbarSize(); column++) {
            float slotOffsetX = baseX + 8 + ITEM_SLOT_SIZE * column;
            final int slotColumn = column;
            this.slots.add(new Slot(() -> inventory.getInventoryItem(hotbarRow, slotColumn),
                    () -> inventory.clickItem(hotbarRow, slotColumn),
                    () -> inventory.placeSingleInventoryItem(hotbarRow, slotColumn),
                    slotOffsetX, hotbarSlotOffsetY));
        }
    }

    private void addCraftingSlots(float baseX) {
        int craftingRows = inventory.getCraftingRowCount();
        int craftingColumns = inventory.getCraftingColumnCount();
        if (craftingRows == 0 || craftingColumns == 0) {
            return;
        }
        float fourthColumnLeft = baseX + 8 + ITEM_SLOT_SIZE * 3;
        float craftingStartX = fourthColumnLeft + ITEM_SLOT_SIZE + 8;
        float bottomRowOffsetY = -(22f + ITEM_SLOT_SIZE);
        for (int row = 0; row < craftingRows; row++) {
            for (int column = 0; column < craftingColumns; column++) {
                float slotOffsetX = craftingStartX + ITEM_SLOT_SIZE * column;
                float slotOffsetY = bottomRowOffsetY - ITEM_SLOT_SIZE * (craftingRows - 1 - row);
                final int craftingRow = row;
                final int craftingColumn = column;
                this.slots.add(new Slot(() -> inventory.getCraftingItem(craftingRow, craftingColumn),
                        () -> inventory.clickCraftingItem(craftingRow, craftingColumn),
                        () -> inventory.placeSingleCraftingItem(craftingRow, craftingColumn),
                        slotOffsetX, slotOffsetY));
            }
        }

        float craftingGridWidth = ITEM_SLOT_SIZE * craftingColumns;
        float resultSlotOffsetX = craftingStartX + craftingGridWidth + 20;
        float craftingTopY = bottomRowOffsetY - ITEM_SLOT_SIZE * (craftingRows - 1) + 1;
        float craftingHeight = ITEM_SLOT_SIZE * craftingRows;
        float resultSlotOffsetY = craftingTopY + craftingHeight / 2f - ITEM_SLOT_SIZE / 2f;
        this.slots.add(new Slot(inventory::getCraftingResultItem,
                inventory::clickCraftingResultItem,
                null,
                resultSlotOffsetX, resultSlotOffsetY));
    }

    private void addArmorSlots(float baseX) {
        if (inventory.getArmorSlotCount() == 0) {
            return;
        }
        float slotOffsetX = baseX + 8;
        float slotOffsetY = -ITEM_SLOT_SIZE - 4;
        for (int armorIndex = 0; armorIndex < inventory.getArmorSlotCount(); armorIndex++) {
            final int slotIndex = armorIndex;
            float currentSlotOffsetY = slotOffsetY - ITEM_SLOT_SIZE * armorIndex;
            this.slots.add(new Slot(() -> inventory.getArmorItem(slotIndex),
                    () -> inventory.clickArmorItem(slotIndex),
                    () -> inventory.placeSingleArmorItem(slotIndex),
                    slotOffsetX, currentSlotOffsetY));
        }
    }

    private IndexedMesh inventoryQuadMesh;

    private static final int INVENTORY_UI_WIDTH = 176;
    private static final int INVENTORY_UI_HEIGHT = 166;
    private static final int BLOCK_ITEM_SCALE_FACTOR = 10;
    private static final int HELD_ITEM_SCALE_FACTOR = 16;

    private static final int ITEM_SLOT_SIZE = 18;

    @Override
    public void drawScreen(GraphicsAPI graphics, float screenWidth, float screenHeight, float partialTicks) {
        float centerX = (float) (int) screenWidth / 2;
        float centerY = (float) (int) screenHeight / 2;

        // if we have not received any mouse input yet, assume it is center of screen
        // this is true because every time we un-grab the mouse, we reset the mouse position to center
        if (mouseX == -1 || mouseY == -1) {
            mouseX = centerX;
            mouseY = centerY;
        }

        if (inventoryQuadMesh == null) {
            Tesselator t = Tesselator.instance;
            t.init();
            t.color(1, 1, 1);

            // draw quad
            t.vertexUV(centerX + INVENTORY_UI_WIDTH / 2f, centerY - INVENTORY_UI_HEIGHT / 2f, 0.0F, INVENTORY_UI_WIDTH / 256f, 0.0F);
            t.vertexUV(centerX - INVENTORY_UI_WIDTH / 2f, centerY - INVENTORY_UI_HEIGHT / 2f, 0.0F, 0.0F, 0.0F);
            t.vertexUV(centerX - INVENTORY_UI_WIDTH / 2f, centerY + INVENTORY_UI_HEIGHT / 2f, 0.0F, 0.0F, INVENTORY_UI_HEIGHT / 256f);
            t.vertexUV(centerX + INVENTORY_UI_WIDTH / 2f, centerY + INVENTORY_UI_HEIGHT / 2f, 0.0F, INVENTORY_UI_WIDTH / 256f, INVENTORY_UI_HEIGHT / 256f);

            inventoryQuadMesh = t.createIndexedMesh(GraphicsEnums.BufferUsage.STATIC);
        }

        // draw inventory background
        graphics.setTexture(textureManager.inventoryTexture);
        inventoryQuadMesh.draw(graphics);

        // draw items
        {
            // set world shader
            graphics.setShader(WORLD_SHADER);
            WORLD_SHADER.setFogUniforms(0f, 0f, 0f, 0f, 0f, 0f, 0f);

            // set terrain texture
            graphics.setTexture(textureManager.terrainTexture);

            for (Slot slot : slots) {
                ItemStack itemStack = slot.getItemStack();
                if (itemStack == null) {
                    continue;
                }
                Item item = itemStack.getItem();
                graphics.pushMatrix();
                graphics.translate(slot.getItemRenderX(centerX), slot.getItemRenderY(centerY), 0);
                if (item instanceof BlockItem) {
                    BlockItem blockItem = (BlockItem) item;
                    BlockRenderer.renderBlockPreview(graphics, blockItem.getBlock(), BLOCK_ITEM_SCALE_FACTOR);
                }
                graphics.popMatrix();
            }

            // set items texture
            graphics.setTexture(textureManager.itemsTexture);
            graphics.setShader(HUD_SHADER);

            for (Slot slot : slots) {
                ItemStack itemStack = slot.getItemStack();
                if (itemStack == null) {
                    continue;
                }
                Item item = itemStack.getItem();
                graphics.pushMatrix();
                graphics.translate(slot.getItemRenderX(centerX) - HELD_ITEM_SCALE_FACTOR / 2f, slot.getItemRenderY(centerY) - HELD_ITEM_SCALE_FACTOR, 0);
                if (item instanceof HeldItem) {
                    HeldItem blockItem = (HeldItem) item;
                    heldItemRenderer.renderHeldItemPreview(graphics, blockItem, HELD_ITEM_SCALE_FACTOR);
                }
                graphics.popMatrix();
            }
        }

        // draw stack size labels
        {
            graphics.setShader(HUD_SHADER);

            for (Slot slot : slots) {
                ItemStack itemStack = slot.getItemStack();
                if (itemStack == null) {
                    continue;
                }
                int count = itemStack.getCount();
                if (count > 1) {
                    slot.renderStackSize(graphics, centerX, centerY, count);
                }
            }
        }

        // draw player model
        {
            graphics.setShader(ENTITY_SHADER);
            ENTITY_SHADER.setFogUniforms(0f, 0f, 0f, 0f, 0f, 0f, 0f);
            graphics.setTexture(textureManager.charTexture);
            graphics.pushMatrix();

            // Apply scaling and orientation
            graphics.translate(centerX - INVENTORY_UI_WIDTH / 2f + 42 + 10, centerY - INVENTORY_UI_HEIGHT / 2f + 24 + 4, 0);
            graphics.scale(1.85f, 1.85f, 1.85f);

            // calculate angle to look at mouse position
            float mouseYaw;
            float mousePitch;
            {
                float eyePosX = centerX - INVENTORY_UI_WIDTH / 2f + 42 + 10;
                float eyePosY = centerY - INVENTORY_UI_HEIGHT / 2f + 24 + 4;
                float dx = mouseX - eyePosX;
                float dy = mouseY - eyePosY;

                mouseYaw = (float) Math.atan(dx / 40.0F) * 20f;
                mousePitch = (float) Math.atan(dy / 40.0F) * 20f;

                this.player.yaw = this.player.prevYaw = mouseYaw * 2 - 180;
                this.player.bodyYaw = this.player.prevBodyYaw = mouseYaw - 180;
                this.player.pitch = this.player.prevPitch = mousePitch;
            }

            graphics.rotateX(-mousePitch);

            // Render the model
            graphics.setDepthState(true, true, GraphicsEnums.CompareFunc.LESS_EQUAL);
            PLAYER_MODEL.render(graphics, this.player, partialTicks);
            graphics.setDepthState(false, true, GraphicsEnums.CompareFunc.ALWAYS);

            graphics.popMatrix();
        }

        // draw selected item at cursor position
        {
            // set terrain texture again after drawing labels and the player

            ItemStack selectedItem = inventory.getSelectedItem();
            if (selectedItem != null) {
                Item item = selectedItem.getItem();
                if (item instanceof BlockItem) {
                    graphics.setShader(WORLD_SHADER);
                    graphics.setTexture(textureManager.terrainTexture);
                    BlockItem blockItem = (BlockItem) item;
                    graphics.pushMatrix();
                    graphics.translate(mouseX, mouseY + ITEM_SLOT_SIZE / 2f, 0);
                    BlockRenderer.renderBlockPreview(graphics, blockItem.getBlock(), BLOCK_ITEM_SCALE_FACTOR);
                    graphics.popMatrix();
                } else if (item instanceof HeldItem) {
                    graphics.setShader(HUD_SHADER);
                    graphics.setTexture(textureManager.itemsTexture);
                    HeldItem heldItem = (HeldItem) item;
                    graphics.pushMatrix();
                    graphics.translate(mouseX - HELD_ITEM_SCALE_FACTOR / 2f, mouseY - HELD_ITEM_SCALE_FACTOR / 2f, 0);
                    heldItemRenderer.renderHeldItemPreview(graphics, heldItem, HELD_ITEM_SCALE_FACTOR);
                    graphics.popMatrix();
                }
            }

            // draw selected item stack size label
            if (selectedItem != null) {
                graphics.setShader(HUD_SHADER);
                int count = selectedItem.getCount();
                if (count > 1) {
                    this.stackSizeSelectedItemLabel.setText(StackCountStringPool.valueOf(count));
                    this.stackSizeSelectedItemLabel.render(graphics, mouseX + 9 - this.stackSizeSelectedItemLabel.getWidth(), mouseY + 2);
                }
            }
        }

        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
    }

    @Override
    public void onMouseClicked(float mouseX, float mouseY, int button, boolean pressed) {
        if (button == 1) {
            if (pressed) {
                rightMouseDown = true;
                rightClickVisitedSlots.clear();
                Slot slot = findSlotAt(mouseX, mouseY);
                if (slot != null) {
                    slot.placeOne();
                    rightClickVisitedSlots.add(slot);
                }
            } else {
                rightMouseDown = false;
                rightClickVisitedSlots.clear();
            }
            return;
        }

        if (button != 0 || pressed) { // only handle mouse 0 release
            return;
        }

        Slot slot = findSlotAt(mouseX, mouseY);
        if (slot != null) {
            slot.click();
        }
    }

    @Override
    public void onMouseMove(float mouseX, float mouseY, float dX, float dY) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        if (rightMouseDown) {
            Slot slot = findSlotAt(mouseX, mouseY);
            if (slot != null && !rightClickVisitedSlots.contains(slot)) {
                slot.placeOne();
                rightClickVisitedSlots.add(slot);
            }
        }
    }

    @Override
    public void onResized(float screenWidth, float screenHeight) {
        if (inventoryQuadMesh != null) {
            inventoryQuadMesh.dispose();
            inventoryQuadMesh = null;
        }
    }

    @Override
    public void dispose() {
        if (inventoryQuadMesh != null) {
            inventoryQuadMesh.dispose();
            inventoryQuadMesh = null;
        }
    }

    @Override
    public void onClose() {
        inventory.resetSelectedItem();
    }

    private final class Slot {

        private static final float SLOT_ITEM_RENDER_OFFSET_X = 8f;
        private static final float SLOT_ITEM_RENDER_OFFSET_Y = 7 + ITEM_SLOT_SIZE / 2f + 1;
        private static final float SLOT_LABEL_RIGHT_OFFSET = ITEM_SLOT_SIZE - 1;
        private static final float SLOT_LABEL_OFFSET_Y = 10f;

        private final Supplier<ItemStack> itemProvider;
        private final Runnable clickAction;
        private final Runnable placeOneAction;
        private final float slotOffsetX;
        private final float slotOffsetY;
        private final TextLabel stackSizeLabel;

        private Slot(Supplier<ItemStack> itemProvider, Runnable clickAction, Runnable placeOneAction, float slotOffsetX, float slotOffsetY) {
            this.itemProvider = itemProvider;
            this.clickAction = clickAction;
            this.placeOneAction = placeOneAction;
            this.slotOffsetX = slotOffsetX;
            this.slotOffsetY = slotOffsetY;
            this.stackSizeLabel = new TextLabel(font, 0xFFFFFF, true);
        }

        private ItemStack getItemStack() {
            return itemProvider.get();
        }

        private float getSlotLeft(float centerX) {
            return centerX + slotOffsetX;
        }

        private float getSlotTop(float centerY) {
            return centerY + slotOffsetY;
        }

        private float getItemRenderX(float centerX) {
            return getSlotLeft(centerX) + SLOT_ITEM_RENDER_OFFSET_X;
        }

        private float getItemRenderY(float centerY) {
            return getSlotTop(centerY) + SLOT_ITEM_RENDER_OFFSET_Y;
        }

        private float getLabelX(float centerX) {
            return getSlotLeft(centerX) + SLOT_LABEL_RIGHT_OFFSET - this.stackSizeLabel.getWidth();
        }

        private float getLabelY(float centerY) {
            return getSlotTop(centerY) + SLOT_LABEL_OFFSET_Y;
        }

        private boolean contains(float mouseX, float mouseY, float centerX, float centerY) {
            float x = getSlotLeft(centerX);
            float y = getSlotTop(centerY);
            return mouseX >= x && mouseX <= x + ITEM_SLOT_SIZE && mouseY >= y && mouseY <= y + ITEM_SLOT_SIZE;
        }

        private void click() {
            clickAction.run();
        }

        private void renderStackSize(GraphicsAPI graphics, float centerX, float centerY, int count) {
            this.stackSizeLabel.setText(StackCountStringPool.valueOf(count));
            this.stackSizeLabel.render(graphics, getLabelX(centerX), getLabelY(centerY));
        }

        private void placeOne() {
            if (placeOneAction != null) {
                placeOneAction.run();
            }
        }
    }

    private Slot findSlotAt(float mouseX, float mouseY) {
        float centerX = screenWidth / 2f;
        float centerY = screenHeight / 2f;
        for (Slot slot : slots) {
            if (slot.contains(mouseX, mouseY, centerX, centerY)) {
                return slot;
            }
        }
        return null;
    }
}
