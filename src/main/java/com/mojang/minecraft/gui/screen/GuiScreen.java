package com.mojang.minecraft.gui.screen;

import com.mojang.minecraft.renderer.Disposable;
import com.mojang.minecraft.renderer.graphics.CommandBuffer;
import com.mojang.minecraft.renderer.graphics.MatrixStack;

public class GuiScreen implements Disposable {

    /**
     * Called when the gui screen opens. This is always the first method ever to be invoked on a freshly created instance.
     * No other virtual method may ever run first before onInit.
     */
    public void onInit() {
    }

    public void drawScreen(CommandBuffer commandBuffer, MatrixStack matrixStack, float screenWidth, float screenHeight, float partialTicks) {
    }

    public void onResized(float screenWidth, float screenHeight) {
    }

    public void onMouseClicked(float mouseX, float mouseY, int button, boolean pressed) {
    }

    public void onMouseMove(float mouseX, float mouseY, float dX, float dY) {
    }

    @Override
    public void dispose() {
    }

    /**
     * Called when the screen is closed.
     * Called before any call to {@link #dispose()}.
     */
    public void onClose() {
    }

    public enum Kind {
        INVENTORY, CRAFTING
    }
}
