package com.mojang.minecraft.gui.screen;

import com.mojang.minecraft.renderer.Disposable;
import com.mojang.minecraft.renderer.graphics.GraphicsAPI;

public class GuiScreen implements Disposable {

    public void drawScreen(GraphicsAPI graphics, float screenWidth, float screenHeight) {

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
}
