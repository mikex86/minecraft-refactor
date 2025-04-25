package com.mojang.minecraft.entity;

import com.mojang.minecraft.level.Level;

public class EntityLiving extends Entity {

    public float prevLimbSwing;
    public float limbSwing;

    public float prevLimbSwingAmount;
    public float limbSwingAmount;

    public EntityLiving(Level level) {
        super(level);
    }

    @Override
    public void tick() {
        super.tick();
    }

    protected void updateAnimations() {
        this.prevLimbSwingAmount = this.limbSwingAmount;
        this.prevLimbSwing = this.limbSwing;

        double dx = this.x - this.xo;
        double dz = this.z - this.zo;

        float walkSpeed = (float) Math.sqrt(dx * dx + dz * dz) * 4.0F;
        if (walkSpeed > 1.0F) walkSpeed = 1.0F;

        this.limbSwingAmount += (walkSpeed - this.limbSwingAmount) * 0.4F;

        this.limbSwing += this.limbSwingAmount;
    }
}
