package com.mojang.minecraft.entity;

import com.mojang.minecraft.item.BlockItem;
import com.mojang.minecraft.item.Item;
import com.mojang.minecraft.level.Level;
import com.mojang.minecraft.renderer.TextureManager;
import com.mojang.minecraft.renderer.block.BlockRenderer;
import com.mojang.minecraft.renderer.graphics.GraphicsAPI;
import com.mojang.minecraft.renderer.graphics.IndexedMesh;
import com.mojang.minecraft.renderer.shader.ShaderRegistry;
import com.mojang.minecraft.renderer.shader.impl.WorldShader;
import com.mojang.minecraft.util.math.CollisionUtils;

public class EntityItem extends Entity {

    private static final WorldShader WORLD_SHADER = ShaderRegistry.getInstance().getWorldShader();

    private final Item item;

    private final float hoverPhase;

    private EntityPlayer target;

    private int pickupDelay = 10;

    /**
     * Creates a new entity in the specified level.
     *
     * @param level The level this entity belongs to
     * @param item  the item to be represented by this entity
     */
    public EntityItem(Level level, Item item) {
        super(level);
        this.item = item;
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
    public void render(GraphicsAPI graphics, TextureManager textureManager, float partialTick) {
        if (item instanceof BlockItem) {
            renderBlockItem((BlockItem) item, graphics, textureManager, partialTick);
        }
    }

    private void renderBlockItem(BlockItem blockItem, GraphicsAPI graphics, TextureManager textureManager, float partialTicks) {
        IndexedMesh mesh = BlockRenderer.getBlockMesh(blockItem.getBlock());
        graphics.setTexture(textureManager.terrainTexture);
        graphics.setShader(WORLD_SHADER);

        graphics.pushMatrix();

        // Position at interpolated location
        graphics.translate(
                this.xo + (this.x - this.xo) * partialTicks,
                this.yo + (this.y - this.yo) * partialTicks,
                this.zo + (this.z - this.zo) * partialTicks
        );

        float f = (ticksPerformed + partialTicks) / 10.0F + hoverPhase;
        float bobOffset = (float) (Math.sin(f) * 0.1F);
        graphics.translate(0, bobOffset + (3f / 16f), 0);
        graphics.scale(0.25f, 0.25f, 0.25f);

        float rot = ((ticksPerformed + partialTicks) / 20f + hoverPhase) * (180f / (float) Math.PI);

        // Pivot around model center for correct rotation
        graphics.translate(0.5f, 0.0f, 0.5f);
        graphics.rotateY(rot);
        graphics.translate(-0.5f, -0.0f, -0.5f);

        graphics.updateShaderMatrices();

        mesh.draw(graphics);

        graphics.popMatrix();
    }

    @Override
    protected void onCollideWithPlayer(EntityPlayer player) {
        if (this.pickupDelay > 0) {
            return;
        }
        if (this.target == null) {
            if (player.attemptPickupItem(item)) {
                this.target = player;
            }
        }
        if (this.target != null) {
            float dx = player.x - this.x;
            float dy = (player.y + player.getHeightOffset()/2) - this.y;
            float dz = player.z - this.z;
            this.xd += dx / 8.0f;
            this.yd += dy / 8.0f;
            this.zd += dz / 8.0f;
        }
    }
}
