package com.mojang.minecraft.gui.screen;

import com.mojang.minecraft.entity.EntityPlayer;
import com.mojang.minecraft.gui.Font;
import com.mojang.minecraft.gui.TextLabel;
import com.mojang.minecraft.item.ItemStack;
import com.mojang.minecraft.item.inventory.Inventory;
import com.mojang.minecraft.optim.pools.StackCountStringPool;
import com.mojang.minecraft.renderer.Tesselator;
import com.mojang.minecraft.renderer.TextureManager;
import com.mojang.minecraft.renderer.graphics.GraphicsAPI;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums;
import com.mojang.minecraft.renderer.graphics.IndexedMesh;
import com.mojang.minecraft.renderer.graphics.Texture;
import com.mojang.minecraft.renderer.item.HeldItemRenderer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public class AbstractInventoryScreen extends GuiScreen {
    public static final int BLOCK_ITEM_SCALE_FACTOR = 10;
    public static final int HELD_ITEM_SCALE_FACTOR = 16;
    protected static final int INVENTORY_UI_WIDTH = 176;
    protected static final int INVENTORY_UI_HEIGHT = 166;
    public static final int ITEM_SLOT_SIZE = 18;
    public final List<Slot> slots = new ArrayList<>();
    protected final EntityPlayer player;
    protected final Inventory inventory;
    protected final TextureManager textureManager;
    protected final Font font;
    protected final HeldItemRenderer heldItemRenderer;
    protected final Set<Slot> rightClickVisitedSlots = new HashSet<>();
    protected final TextLabel stackSizeSelectedItemLabel;
    protected float screenWidth;
    protected float screenHeight;
    protected float mouseX = -1;
    protected float mouseY = -1;
    protected boolean rightMouseDown = false;


    public static final float BASE_X = -INVENTORY_UI_WIDTH / 2f;
    protected IndexedMesh inventoryQuadMesh;

    public AbstractInventoryScreen(TextureManager textureManager, HeldItemRenderer heldItemRenderer, Font font, Inventory inventory) {
        this.player = new EntityPlayer(null, false);
        this.inventory = inventory;
        this.textureManager = textureManager;
        this.font = font;
        this.heldItemRenderer = heldItemRenderer;
        this.stackSizeSelectedItemLabel = new TextLabel(font, 0xFFFFFF, true);
    }

    protected void addMainInventorySlots() {
        for (int row = 0; row < inventory.getMainInventoryRowCount(); row++) {
            for (int column = 0; column < inventory.getColumnCount(); column++) {
                float slotOffsetX = BASE_X + 8 + ITEM_SLOT_SIZE * column;
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

    protected void addHotbarSlots() {
        int hotbarRow = inventory.getMainInventoryRowCount();
        float hotbarSlotOffsetY = ITEM_SLOT_SIZE * hotbarRow + 4;
        for (int column = 0; column < inventory.getHotbarSize(); column++) {
            float slotOffsetX = BASE_X + 8 + ITEM_SLOT_SIZE * column;
            final int slotColumn = column;
            this.slots.add(new Slot(() -> inventory.getInventoryItem(hotbarRow, slotColumn),
                    () -> inventory.clickItem(hotbarRow, slotColumn),
                    () -> inventory.placeSingleInventoryItem(hotbarRow, slotColumn),
                    slotOffsetX, hotbarSlotOffsetY));
        }
    }

    protected Slot findSlotAt(float mouseX, float mouseY) {
        float centerX = screenWidth / 2f;
        float centerY = screenHeight / 2f;
        for (Slot slot : slots) {
            if (slot.contains(mouseX, mouseY, centerX, centerY)) {
                return slot;
            }
        }
        return null;
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

    protected void drawInventoryScreenBackground(GraphicsAPI graphics, float centerX, float centerY, Texture texture) {
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
        graphics.setTexture(texture);
        inventoryQuadMesh.draw(graphics);
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


    public final class Slot {

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

        Slot(Supplier<ItemStack> itemProvider, Runnable clickAction, Runnable placeOneAction, float slotOffsetX, float slotOffsetY) {
            this.itemProvider = itemProvider;
            this.clickAction = clickAction;
            this.placeOneAction = placeOneAction;
            this.slotOffsetX = slotOffsetX;
            this.slotOffsetY = slotOffsetY;
            this.stackSizeLabel = new TextLabel(font, 0xFFFFFF, true);
        }

        public ItemStack getItemStack() {
            return itemProvider.get();
        }

        private float getSlotLeft(float centerX) {
            return centerX + slotOffsetX;
        }

        private float getSlotTop(float centerY) {
            return centerY + slotOffsetY;
        }

        public float getItemRenderX(float centerX) {
            return getSlotLeft(centerX) + SLOT_ITEM_RENDER_OFFSET_X;
        }

        public float getItemRenderY(float centerY) {
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

        void click() {
            clickAction.run();
        }

        public void renderStackSize(GraphicsAPI graphics, float centerX, float centerY, int count) {
            this.stackSizeLabel.setText(StackCountStringPool.valueOf(count));
            this.stackSizeLabel.render(graphics, getLabelX(centerX), getLabelY(centerY));
        }

        void placeOne() {
            if (placeOneAction != null) {
                placeOneAction.run();
            }
        }
    }
}
