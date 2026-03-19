package com.mojang.minecraft.entity;

import com.mojang.minecraft.item.BlockItem;
import com.mojang.minecraft.item.Item;
import com.mojang.minecraft.item.ItemStack;
import com.mojang.minecraft.level.Level;
import com.mojang.minecraft.renderer.TextureManager;
import com.mojang.minecraft.renderer.block.BlockRenderer;
import com.mojang.minecraft.renderer.graphics.CommandBuffer;
import com.mojang.minecraft.renderer.graphics.IndexedMesh;
import com.mojang.minecraft.renderer.graphics.MatrixUniformBinder;
import com.mojang.minecraft.renderer.graphics.MatrixStack;
import com.mojang.minecraft.renderer.graphics.Pipeline;
import com.mojang.minecraft.renderer.shader.PipelineRegistry;
import com.mojang.minecraft.util.math.CollisionUtils;

public class EntityItem extends Entity {

    private static final Pipeline WORLD_PIPELINE = PipelineRegistry.getInstance().getWorldPipeline();

    private final ItemStack itemStack;

    private final float hoverPhase;

    private EntityPlayer target;

    private int pickupDelay = 10;

    /**
     * Creates a new entity in the specified level.
     *
     * @param level     The level this entity belongs to
     * @param itemStack the item stack to render
     */
    public EntityItem(Level level, ItemStack itemStack) {
        super(level);
        this.itemStack = itemStack;
        this.hoverPhase = (float) (Math.random() * Math.PI * 2.0D);
        this.bbWidth = 0.25f;
        this.bbHeight = 0.25f;
    }

    @Override
    public void tick() {
        super.tick();

        // Decrease pickup delay
        if (this.pickupDelay > 0) {
            this.pickupDelay--;
        }

        // Move based on current velocity
        this.move(this.xd, this.yd, this.zd);

        // Apply gravity
        this.yd -= 0.04F;

        // try to push item out of blocks
        if (!this.level.isFreeFromBlocks(this.boundingBox)) {
            this.pushOutOfBlocks(this.x, (this.boundingBox.y0 + this.boundingBox.y1) / 2, this.z);
        }

        // Apply ground friction
        if (this.onGround) {
            float slipperyFactor = 0.6F;
            this.xd *= slipperyFactor * 0.91F;
            this.yd *= 0.98F;
            this.zd *= slipperyFactor * 0.91F;
        } else {
            // Apply air resistance
            this.xd *= 0.91F;
            this.yd *= 0.98F;
            this.zd *= 0.91F;
        }

        // Check for collision with player
        if (target != null) {
            if (CollisionUtils.intersects(boundingBox, target.boundingBox)) {
                this.remove();
            }
        }
    }

    @Override
    public void render(CommandBuffer commandBuffer, MatrixStack matrixStack, TextureManager textureManager, float partialTick) {
        Item item = itemStack.getItem(); // TODO: RENDER > STACK SIZE AS ITEM BUNDLE
        if (item instanceof BlockItem) {
            renderBlockItem((BlockItem) item, commandBuffer, matrixStack, textureManager, partialTick);
        }
    }

    private void renderBlockItem(BlockItem blockItem, CommandBuffer commandBuffer, MatrixStack matrixStack, TextureManager textureManager, float partialTicks) {
        IndexedMesh mesh = BlockRenderer.getBlockMesh(blockItem.getBlock());
        commandBuffer.bindTexture(0, textureManager.terrainTexture);
        commandBuffer.setPipeline(WORLD_PIPELINE);

        matrixStack.pushMatrix();

        // Position at interpolated location
        matrixStack.translate(
                this.xo + (this.x - this.xo) * partialTicks,
                this.yo + (this.y - this.yo) * partialTicks,
                this.zo + (this.z - this.zo) * partialTicks
        );

        float f = (ticksPerformed + partialTicks) / 10.0F + hoverPhase;
        float bobOffset = (float) (Math.sin(f) * 0.1F);
        matrixStack.translate(0, bobOffset + (3f / 16f), 0);
        matrixStack.scale(0.25f, 0.25f, 0.25f);

        float rot = ((ticksPerformed + partialTicks) / 20f + hoverPhase) * (180f / (float) Math.PI);

        // Pivot around model center for correct rotation
        matrixStack.translate(0.5f, 0.0f, 0.5f);
        matrixStack.rotateY(rot);
        matrixStack.translate(-0.5f, -0.0f, -0.5f);

        MatrixUniformBinder.bindStandardMatrices(commandBuffer, PipelineRegistry.getInstance().getSharedUniforms(), matrixStack);

        mesh.draw(commandBuffer);

        matrixStack.popMatrix();
    }

    @Override
    protected void onCollideWithPlayer(EntityPlayer player) {
        if (this.pickupDelay > 0) {
            return;
        }
        if (this.target == null) {
            if (player.attemptPickupItem(itemStack)) {
                this.target = player;
            }
        }
        if (this.target != null) {
            float dx = player.x - this.x;
            float dy = (player.y + player.getHeightOffset() / 2) - this.y;
            float dz = player.z - this.z;
            this.xd += dx / 8.0f;
            this.yd += dy / 8.0f;
            this.zd += dz / 8.0f;
        }
    }
}
