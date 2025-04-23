package com.mojang.minecraft.gui.screen;

import com.mojang.minecraft.renderer.Disposable;
import com.mojang.minecraft.renderer.graphics.GraphicsAPI;

public class GuiScreen implements Disposable {

    public void drawScreen(GraphicsAPI graphics, float screenWidth, float screenHeight) {

    }

    public void onResized(float screenWidth, float screenHeight) {

    }

    @Override
    public void dispose() {
    }
}
