package com.mojang.minecraft.gui.screen;

import com.mojang.minecraft.gui.Font;
import com.mojang.minecraft.gui.screen.renderer.InventoryItemRenderer;
import com.mojang.minecraft.item.inventory.Inventory;
import com.mojang.minecraft.renderer.TextureManager;
import com.mojang.minecraft.renderer.graphics.GraphicsAPI;
import com.mojang.minecraft.renderer.graphics.Pipeline;
import com.mojang.minecraft.renderer.item.HeldItemRenderer;
import com.mojang.minecraft.renderer.shader.ShaderRegistry;
import com.mojang.minecraft.renderer.shader.impl.EntityShader;

import static com.mojang.minecraft.entity.EntityPlayer.PLAYER_MODEL;

public class InventoryScreen extends AbstractInventoryScreen {

    private static final EntityShader ENTITY_SHADER = ShaderRegistry.getInstance().getEntityShader();
    private static final Pipeline ENTITY_PIPELINE = ShaderRegistry.getInstance().getEntityPipeline();
    private static final Pipeline HUD_PIPELINE = ShaderRegistry.getInstance().getHudPipeline();

    public InventoryScreen(TextureManager textureManager, HeldItemRenderer heldItemRenderer, Font font, Inventory inventory) {
        super(textureManager, heldItemRenderer, font, inventory);
    }

    @Override
    public void onInit() {
        addMainInventorySlots();
        addHotbarSlots();
        addPortableCraftingSlots();
        addArmorSlots();
    }

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

        drawInventoryScreenBackground(graphics, centerX, centerY, textureManager.inventoryTexture);

        // draw items
        InventoryItemRenderer.renderInventoryItems(graphics, textureManager, heldItemRenderer, this, centerX, centerY);

        drawPlayerModel(graphics, partialTicks, centerX, centerY);

        InventoryItemRenderer.drawSelectedItem(graphics, textureManager, heldItemRenderer, inventory, mouseX, mouseY, stackSizeSelectedItemLabel);

        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
    }

    @Override
    public void onClose() {
        inventory.resetCrafting(Inventory.CraftingKind.PORTABLE);
    }

    private void drawPlayerModel(GraphicsAPI graphics, float partialTicks, float centerX, float centerY) {
        graphics.setPipeline(ENTITY_PIPELINE);
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
        PLAYER_MODEL.render(graphics, this.player, partialTicks);
        graphics.setPipeline(HUD_PIPELINE);

        graphics.popMatrix();
    }

    protected void addPortableCraftingSlots() {
        int craftingRows = inventory.getCraftingRowCount(Inventory.CraftingKind.PORTABLE);
        int craftingColumns = inventory.getCraftingColumnCount(Inventory.CraftingKind.PORTABLE);
        if (craftingRows == 0 || craftingColumns == 0) {
            return;
        }
        float fourthColumnLeft = BASE_X + 8 + ITEM_SLOT_SIZE * 3;
        float craftingStartX = fourthColumnLeft + ITEM_SLOT_SIZE + 8;
        float bottomRowOffsetY = -(22f + ITEM_SLOT_SIZE);
        for (int row = 0; row < craftingRows; row++) {
            for (int column = 0; column < craftingColumns; column++) {
                float slotOffsetX = craftingStartX + ITEM_SLOT_SIZE * column;
                float slotOffsetY = bottomRowOffsetY - ITEM_SLOT_SIZE * (craftingRows - 1 - row);
                final int craftingRow = row;
                final int craftingColumn = column;
                this.slots.add(new Slot(() -> inventory.getCraftingItem(Inventory.CraftingKind.PORTABLE, craftingRow, craftingColumn),
                        () -> inventory.clickCraftingItem(Inventory.CraftingKind.PORTABLE, craftingRow, craftingColumn),
                        () -> inventory.placeSingleCraftingItem(Inventory.CraftingKind.PORTABLE, craftingRow, craftingColumn),
                        slotOffsetX, slotOffsetY));
            }
        }

        float craftingGridWidth = ITEM_SLOT_SIZE * craftingColumns;
        float resultSlotOffsetX = craftingStartX + craftingGridWidth + 20;
        float craftingTopY = bottomRowOffsetY - ITEM_SLOT_SIZE * (craftingRows - 1) + 1;
        float craftingHeight = ITEM_SLOT_SIZE * craftingRows;
        float resultSlotOffsetY = craftingTopY + craftingHeight / 2f - ITEM_SLOT_SIZE / 2f;
        this.slots.add(new Slot(inventory::getCraftingResultItem,
                () -> inventory.clickCraftingResultItem(Inventory.CraftingKind.PORTABLE),
                null,
                resultSlotOffsetX, resultSlotOffsetY));
    }

    protected void addArmorSlots() {
        if (inventory.getArmorSlotCount() == 0) {
            return;
        }
        float slotOffsetX = BASE_X + 8;
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
}
