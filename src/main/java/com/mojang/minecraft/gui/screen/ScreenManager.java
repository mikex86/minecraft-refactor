package com.mojang.minecraft.gui.screen;

import com.mojang.minecraft.entity.EntityPlayer;
import com.mojang.minecraft.renderer.GameRenderer;
import com.mojang.minecraft.renderer.TextureManager;
import com.mojang.minecraft.renderer.shader.PipelineRegistry;
import com.mojang.minecraft.gui.screen.renderer.InventoryItemRenderer;

public class ScreenManager {

    private final EntityPlayer player;
    private final TextureManager textureManager;
    private final GameRenderer gameRenderer;
    private final PipelineRegistry pipelineRegistry;
    private final InventoryItemRenderer inventoryItemRenderer;
    public int lastWidth, lastHeight;

    public ScreenManager(EntityPlayer player, TextureManager textureManager, GameRenderer gameRenderer, PipelineRegistry pipelineRegistry) {
        this.player = player;
        this.textureManager = textureManager;
        this.gameRenderer = gameRenderer;
        this.pipelineRegistry = pipelineRegistry;
        this.inventoryItemRenderer = new InventoryItemRenderer(textureManager, pipelineRegistry);
    }

    public GuiScreen openScreen(GuiScreen.Kind kind) {
        GuiScreen screen;
        switch (kind) {
            case INVENTORY: {
                screen = new InventoryScreen(textureManager, gameRenderer.heldItemRenderer, gameRenderer.font, player.getInventory(), pipelineRegistry, inventoryItemRenderer);
                break;
            }
            case CRAFTING: {
                screen = new CraftingScreen(textureManager, gameRenderer.heldItemRenderer, gameRenderer.font, player.getInventory(), pipelineRegistry, inventoryItemRenderer);
                break;
            }
            default: {
                throw new IllegalArgumentException("Unknown gui screen kind provided: " + kind);
            }
        }
        this.gameRenderer.openScreen(screen);
        this.lastWidth = gameRenderer.width;
        this.lastHeight = gameRenderer.height;
        return screen;
    }

    public void closeScreen() {
        gameRenderer.closeScreen();
    }
}
