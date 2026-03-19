package com.mojang.minecraft.gui;

import com.mojang.minecraft.renderer.Disposable;
import com.mojang.minecraft.renderer.graphics.CommandBuffer;
import com.mojang.minecraft.renderer.graphics.GraphicsEnums;
import com.mojang.minecraft.renderer.graphics.IndexedMesh;
import com.mojang.minecraft.renderer.graphics.MatrixUniformBinder;
import com.mojang.minecraft.renderer.graphics.MatrixStack;
import com.mojang.minecraft.renderer.shader.PipelineRegistry;

public class TextLabel implements Disposable {
    private final Font font;
    private final int color;
    private final boolean shadow;

    private String text = "";
    private int width = -1;

    private final IndexedMesh[] meshes = {null, null};

    public TextLabel(Font font, int color, boolean shadow) {
        this.font = font;
        this.color = color;
        this.shadow = shadow;
    }

    public void render(CommandBuffer commandBuffer, MatrixStack matrixStack, float x, float y) {
        if (this.meshes[0] == null) {
            if (this.shadow) {
                this.font.draw(commandBuffer, text, 1, 1, color, true, false);
                this.meshes[0] = this.font.getTessellator().createIndexedMesh(GraphicsEnums.BufferUsage.STATIC);
                this.font.draw(commandBuffer, text, 0, 0, color, false, false);
                this.meshes[1] = this.font.getTessellator().createIndexedMesh(GraphicsEnums.BufferUsage.STATIC);
            } else {
                this.font.draw(commandBuffer, this.text, 0, 0, color, false, false);
                this.meshes[0] = this.font.getTessellator().createIndexedMesh(GraphicsEnums.BufferUsage.STATIC);
            }
        }

        commandBuffer.bindTexture(0, this.font.getFontTexture());
        matrixStack.pushMatrix();
        matrixStack.translate(x, y, 0);
        MatrixUniformBinder.bindStandardMatrices(commandBuffer, PipelineRegistry.getInstance().getSharedUniforms(), matrixStack);
        for (IndexedMesh mesh : this.meshes) {
            mesh.draw(commandBuffer);
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
