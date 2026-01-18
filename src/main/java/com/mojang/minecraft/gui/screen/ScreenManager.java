package com.mojang.minecraft.gui.screen;

import com.mojang.minecraft.entity.EntityPlayer;
import com.mojang.minecraft.renderer.GameRenderer;
import com.mojang.minecraft.renderer.TextureManager;

public class ScreenManager {

    private final EntityPlayer player;
    private final TextureManager textureManager;
    private final GameRenderer gameRenderer;
    public int lastWidth, lastHeight;

    public ScreenManager(EntityPlayer player, TextureManager textureManager, GameRenderer gameRenderer) {
        this.player = player;
        this.textureManager = textureManager;
        this.gameRenderer = gameRenderer;
    }

    public GuiScreen openScreen(GuiScreen.Kind kind) {
        GuiScreen screen;
        switch (kind) {
            case INVENTORY: {
                screen = new InventoryScreen(textureManager, gameRenderer.heldItemRenderer, gameRenderer.font, player.getInventory());
                break;
            }
            case CRAFTING: {
                screen = new CraftingScreen(textureManager, gameRenderer.heldItemRenderer, gameRenderer.font, player.getInventory());
                break;
            }
            default: {
                throw new IllegalArgumentException("Unknown gui screen kind provided: " + kind);
            }
        }
        gameRenderer.openScreen(screen);
        this.lastWidth = gameRenderer.width;
        this.lastHeight = gameRenderer.height;
        return screen;
    }

    public void closeScreen() {
        gameRenderer.closeScreen();
    }
}
