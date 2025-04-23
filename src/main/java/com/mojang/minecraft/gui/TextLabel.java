package com.mojang.minecraft.gui;

import com.mojang.minecraft.renderer.Disposable;
import com.mojang.minecraft.renderer.graphics.GraphicsAPI;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums;
import com.mojang.minecraft.renderer.graphics.IndexedMesh;

public class TextLabel implements Disposable {

    private final Font font;
    private final int color;
    private final boolean shadow;

    private String text = "";

    private IndexedMesh[] meshes = {null, null};

    public TextLabel(Font font, int color, boolean shadow) {
        this.font = font;
        this.color = color;
        this.shadow = shadow;
    }

    public void render(GraphicsAPI graphics, float x, float y) {
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
        graphics.pushMatrix();
        graphics.translate(x, y, 0);
        graphics.updateShaderMatrices();
        for (IndexedMesh mesh : this.meshes) {
            mesh.draw(graphics);
        }
        graphics.popMatrix();
    }

    public void setText(String text) {
        if (!this.text.equals(text)) {
            this.text = text;
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
}
