package com.mojang.minecraft.gui.screen;

import com.mojang.minecraft.gui.Font;
import com.mojang.minecraft.gui.screen.renderer.InventoryItemRenderer;
import com.mojang.minecraft.item.inventory.Inventory;
import com.mojang.minecraft.renderer.TextureManager;
import com.mojang.minecraft.renderer.graphics.CommandBuffer;
import com.mojang.minecraft.renderer.graphics.MatrixStack;
import com.mojang.minecraft.renderer.graphics.Pipeline;
import com.mojang.minecraft.renderer.graphics.ImmutableDescriptorSet;
import com.mojang.minecraft.renderer.item.HeldItemRenderer;
import com.mojang.minecraft.renderer.shader.PipelineRegistry;

import static com.mojang.minecraft.entity.EntityPlayer.PLAYER_MODEL;

public class InventoryScreen extends AbstractInventoryScreen {

    private final Pipeline entityPipeline;
    private final Pipeline hudPipeline;
    private final ImmutableDescriptorSet noFogCharDescriptorSet;
    private final ImmutableDescriptorSet noFogInventoryDescriptorSet;
    private final InventoryItemRenderer inventoryItemRenderer;

    public InventoryScreen(TextureManager textureManager,
                           HeldItemRenderer heldItemRenderer,
                           Font font,
                           Inventory inventory,
                           PipelineRegistry pipelineRegistry,
                           InventoryItemRenderer inventoryItemRenderer) {
        super(textureManager, heldItemRenderer, font, inventory);
        this.entityPipeline = pipelineRegistry.getEntityPipeline();
        this.hudPipeline = pipelineRegistry.getHudPipeline();
        this.noFogCharDescriptorSet = pipelineRegistry.getDescriptorSet(textureManager.charTexture, PipelineRegistry.FogPreset.NONE);
        this.noFogInventoryDescriptorSet = pipelineRegistry.getDescriptorSet(textureManager.inventoryTexture, PipelineRegistry.FogPreset.NONE);
        this.inventoryItemRenderer = inventoryItemRenderer;
    }

    @Override
    public void onInit() {
        addMainInventorySlots();
        addHotbarSlots();
        addPortableCraftingSlots();
        addArmorSlots();
    }

    @Override
    public void drawScreen(CommandBuffer commandBuffer, MatrixStack matrixStack, float screenWidth, float screenHeight, float partialTicks) {
        float centerX = (float) (int) screenWidth / 2;
        float centerY = (float) (int) screenHeight / 2;

        // if we have not received any mouse input yet, assume it is center of screen
        // this is true because every time we un-grab the mouse, we reset the mouse position to center
        if (mouseX == -1 || mouseY == -1) {
            mouseX = centerX;
            mouseY = centerY;
        }

        drawInventoryScreenBackground(commandBuffer, matrixStack, centerX, centerY, noFogInventoryDescriptorSet);

        // draw items
        inventoryItemRenderer.renderInventoryItems(commandBuffer, matrixStack, heldItemRenderer, this, centerX, centerY);

        drawPlayerModel(commandBuffer, matrixStack, partialTicks, centerX, centerY);

        inventoryItemRenderer.drawSelectedItem(commandBuffer, matrixStack, heldItemRenderer, inventory, mouseX, mouseY, stackSizeSelectedItemLabel);

        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
    }

    @Override
    public void onClose() {
        inventory.resetCrafting(Inventory.CraftingKind.PORTABLE);
    }

    private void drawPlayerModel(CommandBuffer commandBuffer, MatrixStack matrixStack, float partialTicks, float centerX, float centerY) {
        commandBuffer.setPipeline(entityPipeline);
        ImmutableDescriptorSet descriptorSet = noFogCharDescriptorSet;
        commandBuffer.bindDescriptorSet(noFogCharDescriptorSet);
        matrixStack.pushMatrix();

        // Apply scaling and orientation
        matrixStack.translate(centerX - INVENTORY_UI_WIDTH / 2f + 42 + 10, centerY - INVENTORY_UI_HEIGHT / 2f + 24 + 4, 0);
        matrixStack.scale(1.85f, 1.85f, 1.85f);

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

        matrixStack.rotateX(-mousePitch);

        // Render the model
        PLAYER_MODEL.render(commandBuffer, matrixStack, descriptorSet, this.player, partialTicks);
        commandBuffer.setPipeline(hudPipeline);

        matrixStack.popMatrix();
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
