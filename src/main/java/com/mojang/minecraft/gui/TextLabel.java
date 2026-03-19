package com.mojang.minecraft.gui;

import com.mojang.minecraft.renderer.Disposable;
import com.mojang.minecraft.renderer.graphics.CommandBuffer;
import com.mojang.minecraft.renderer.graphics.GraphicsFactory;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums;
import com.mojang.minecraft.renderer.graphics.IndexedMesh;
import com.mojang.minecraft.renderer.graphics.MatrixStack;

public class TextLabel implements Disposable {

    private final Font font;
    private final int color;
    private final boolean shadow;

    private String text = "";
    private int width = -1;

    private IndexedMesh[] meshes = {null, null};

    public TextLabel(Font font, int color, boolean shadow) {
        this.font = font;
        this.color = color;
        this.shadow = shadow;
    }

    public void render(CommandBuffer graphics, MatrixStack matrixStack, float x, float y) {
        if (this.meshes[0] == null) {
            if (this.shadow) {
                this.font.draw(graphics, text, 1, 1, color, true, false);
                this.meshes[0] = this.font.getTessellator().createIndexedMesh(GraphicsEnums.BufferUsage.STATIC);
                this.font.draw(graphics, text, 0, 0, color, false, false);
                this.meshes[1] = this.font.getTessellator().createIndexedMesh(GraphicsEnums.BufferUsage.STATIC);
            } else {
                this.font.draw(graphics, this.text, 0, 0, color, false, false);
                this.meshes[0] = this.font.getTessellator().createIndexedMesh(GraphicsEnums.BufferUsage.STATIC);
            }
        }

        graphics.setTexture(this.font.getFontTexture());
        matrixStack.pushMatrix();
        matrixStack.translate(x, y, 0);
        GraphicsFactory.getGraphicsAPI().bindCurrentMatrices(matrixStack);
        for (IndexedMesh mesh : this.meshes) {
            mesh.draw(graphics);
        }
        matrixStack.popMatrix();
    }

    public void setText(String text) {
        if (!this.text.equals(text)) {
            this.text = text;
            this.width = -1;
            dispose();
            this.meshes[0] = null;
            this.meshes[1] = null;
        }
    }

    public String getText() {
        return text;
    }

    @Override
    public void dispose() {
        if (this.meshes[0] != null) {
            this.meshes[0].dispose();
        }
        if (this.meshes[1] != null) {
            this.meshes[1].dispose();
        }
    }

    public int getWidth() {
        if (width == -1) {
            width = this.font.width(this.text);
        }
        return width;
    }
}
