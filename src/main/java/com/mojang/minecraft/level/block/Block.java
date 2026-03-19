package com.mojang.minecraft.level.block;

import com.mojang.minecraft.entity.EntityItem;
import com.mojang.minecraft.item.BlockItem;
import com.mojang.minecraft.item.BlockItems;
import com.mojang.minecraft.item.Item;
import com.mojang.minecraft.item.ItemStack;
import com.mojang.minecraft.level.Level;
import com.mojang.minecraft.level.block.state.BlockState;
import com.mojang.minecraft.level.chunk.Chunk;
import com.mojang.minecraft.particle.Particle;
import com.mojang.minecraft.particle.ParticleEngine;
import com.mojang.minecraft.phys.AABB;
import com.mojang.minecraft.renderer.Tesselator;
import com.mojang.minecraft.renderer.graphics.MutableDescriptorSet;
import com.mojang.minecraft.renderer.graphics.Pipeline;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Base class for all tile types in the game.
 */
public class Block {

    /**
     * Unique, untranslated name key of the tile.
     */
    public final String name;

    /**
     * Texture index for the tile
     */
    protected final int tex;
    private final int id;

    private static int idctr = 0;

    /**
     * Whether the block can be interacted with via right click
     */
    private boolean interactable = false;

    /**
     * Creates a new tile with the specified ID and texture.
     *
     * @param name The unique, untranslated name key of the tile.
     * @param tex  The texture index
     */
    protected Block(String name, int tex) {
        this.name = name;
        this.id = idctr++;
        this.tex = tex;
        Blocks.registerBlock(this);
    }

    /**
     * Renders the tile in the world.
     *
     * @param t                   The tesselator for rendering
     * @param currentSection      The current chunk section
     * @param neighboringSections The neighboring sections. Layout:
     *                            Layout:
     *                            [0]: neighborSectionNX: (-1, 0, 0)
     *                            [1]: neighborSectionPX: (1, 0, 0)
     *                            [2]: neighborSectionNZ: (0, 0, -1)
     *                            [3]: neighborSectionPZ: (0, 0, 1)
     *                            [4]: sectionPY: (0, 1, 0)
     *                            [5]: sectionNY: (0, -1, 0)
     * @param x                   X coordinate
     * @param y                   Y coordinate
     * @param z                   Z coordinate
     * @param facing              the enum facing direction of the block
     */
    public void render(Tesselator t, Chunk.ChunkSection currentSection, Chunk.ChunkSection[] neighboringSections, int x, int y, int z, EnumFacing facing) {
        float c1 = 1.0F;  // Top/bottom face lighting (100%)
        float c2 = 0.8F;  // North/south face lighting (80%)
        float c3 = 0.6F;  // East/west face lighting (60%)

        float topColor = c1;
        float bottomColor = c2;
        float northColor = c2;
        float southColor = c2;
        float westColor = c3;
        float eastColor = c3;

        boolean isTopLit = true;
        boolean isBottomLit = true;
        boolean isNorthLit = true;
        boolean isSouthLit = true;
        boolean isWestLit = true;
        boolean isEastLit = true;

        // get facing
        Chunk.ChunkSection bottomSection;
        if (y % Chunk.SECTION_HEIGHT == 0) {
            bottomSection = neighboringSections == null ? null : neighboringSections[5];
        } else {
            bottomSection = currentSection;
        }
        Chunk.ChunkSection topSection;
        if ((y + 1) % Chunk.SECTION_HEIGHT == 0) {
            topSection = neighboringSections == null ? null : neighboringSections[4];
        } else {
            topSection = currentSection;
        }
        Chunk.ChunkSection northSection;
        if (z % Chunk.CHUNK_SIZE == 0) {
            northSection = neighboringSections == null ? null : neighboringSections[2];
        } else {
            northSection = currentSection;
        }
        Chunk.ChunkSection southSection;
        if ((z + 1) % Chunk.CHUNK_SIZE == 0) {
            southSection = neighboringSections == null ? null : neighboringSections[3];
        } else {
            southSection = currentSection;
        }
        Chunk.ChunkSection westSection;
        if (x % Chunk.CHUNK_SIZE == 0) {
            westSection = neighboringSections == null ? null : neighboringSections[0];
        } else {
            westSection = currentSection;
        }
        Chunk.ChunkSection eastSection;
        if ((x + 1) % Chunk.CHUNK_SIZE == 0) {
            eastSection = neighboringSections == null ? null : neighboringSections[1];
        } else {
            eastSection = currentSection;
        }

        // get lighting states
        if (neighboringSections != null) {
            isTopLit = topSection != null && topSection.parentChunk.isSkyLit(x & Chunk.CHUNK_SIZE_MINUS_ONE, (y + 1), z & Chunk.CHUNK_SIZE_MINUS_ONE);
            isBottomLit = bottomSection != null && bottomSection.parentChunk.isSkyLit(x & Chunk.CHUNK_SIZE_MINUS_ONE, y - 1, z & Chunk.CHUNK_SIZE_MINUS_ONE);
            isNorthLit = northSection != null && northSection.parentChunk.isSkyLit(x & Chunk.CHUNK_SIZE_MINUS_ONE, y, (z - 1) & Chunk.CHUNK_SIZE_MINUS_ONE);
            isSouthLit = southSection != null && southSection.parentChunk.isSkyLit(x & Chunk.CHUNK_SIZE_MINUS_ONE, y, (z + 1) & Chunk.CHUNK_SIZE_MINUS_ONE);
            isWestLit = westSection != null && westSection.parentChunk.isSkyLit((x - 1) & Chunk.CHUNK_SIZE_MINUS_ONE, y, z & Chunk.CHUNK_SIZE_MINUS_ONE);
            isEastLit = eastSection != null && eastSection.parentChunk.isSkyLit((x + 1) & Chunk.CHUNK_SIZE_MINUS_ONE, y, z & Chunk.CHUNK_SIZE_MINUS_ONE);
        }

        float darkFactor = 0.5F;
        if (!isTopLit) {
            topColor *= darkFactor;
        }
        if (!isBottomLit) {
            bottomColor *= darkFactor;
        }
        if (!isNorthLit) {
            northColor *= darkFactor;
        }
        if (!isSouthLit) {
            southColor *= darkFactor;
        }
        if (!isWestLit) {
            westColor *= darkFactor;
        }
        if (!isEastLit) {
            eastColor *= darkFactor;
        }

        // Bottom face
        if (shouldRenderFace(bottomSection, x & Chunk.CHUNK_SIZE_MINUS_ONE, (y - 1) & Chunk.SECTION_HEIGHT_MINUS_ONE, z & Chunk.CHUNK_SIZE_MINUS_ONE)) {
            t.grayScale(bottomColor);
            renderFace(t, x, y, z, 0, facing);
        }

        // Top face
        if (shouldRenderFace(topSection, x & Chunk.CHUNK_SIZE_MINUS_ONE, (y + 1) & Chunk.SECTION_HEIGHT_MINUS_ONE, z & Chunk.CHUNK_SIZE_MINUS_ONE)) {
            t.grayScale(topColor);
            renderFace(t, x, y, z, 1, facing);
        }

        // North face
        if (shouldRenderFace(northSection, x & Chunk.CHUNK_SIZE_MINUS_ONE, y & Chunk.SECTION_HEIGHT_MINUS_ONE, (z - 1) & Chunk.CHUNK_SIZE_MINUS_ONE)) {
            t.grayScale(northColor);
            renderFace(t, x, y, z, 2, facing);
        }

        // South face
        if (shouldRenderFace(southSection, x & Chunk.CHUNK_SIZE_MINUS_ONE, y & Chunk.SECTION_HEIGHT_MINUS_ONE, (z + 1) & Chunk.CHUNK_SIZE_MINUS_ONE)) {
            t.grayScale(southColor);
            renderFace(t, x, y, z, 3, facing);
        }

        // West face
        if (shouldRenderFace(westSection, (x - 1) & Chunk.CHUNK_SIZE_MINUS_ONE, y & Chunk.SECTION_HEIGHT_MINUS_ONE, z & Chunk.CHUNK_SIZE_MINUS_ONE)) {
            t.grayScale(westColor);
            renderFace(t, x, y, z, 4, facing);
        }

        // East face
        if (shouldRenderFace(eastSection, (x + 1) & Chunk.CHUNK_SIZE_MINUS_ONE, y & Chunk.SECTION_HEIGHT_MINUS_ONE, z & Chunk.CHUNK_SIZE_MINUS_ONE)) {
            t.grayScale(eastColor);
            renderFace(t, x, y, z, 5, facing);
        }
    }

    /**
     * Determines if a face should be rendered based on neighbor blocks and lighting.
     *
     * @param section The chunk section
     * @param lx      The local x coordinate inside the chunk section
     * @param ly      The local y coordinate inside the chunk section
     * @param lz      The local z coordinate inside the chunk section
     * @return True if the face should be rendered
     */
    protected boolean shouldRenderFace(Chunk.ChunkSection section, int lx, int ly, int lz) {
        if (section == null) {
            return true;
        }
        if (section.empty) {
            return true;
        }
        BlockState blockState = section.getBlockState(lx, ly, lz);
        return !(blockState != null && blockState.block.isSolid() && !blockState.block.isTransparent());
    }

    /**
     * Gets the texture index for a specific face.
     *
     * @param face The face index (0-5)
     * @return The texture index
     */
    protected int getTexture(int face, EnumFacing facing) {
        return tex;
    }

    /**
     * Gets the rotation index for a specific face.
     *
     * @param face   the face index (0-5)
     * @param facing the enum facing of the block
     * @return 0-4 for all 90 degree rotation angles
     */
    protected int getRotation(int face, EnumFacing facing) {
        return 0;
    }

    /**
     * @return the set of legal block states of a particular block
     */
    @SuppressWarnings("MismatchedJavadocCode") // this should still be a list, but it is logically a set
    protected List<BlockState> getValidBlockStates() {
        return Collections.singletonList(
                // by default, the only legal block state is the block state with enum facing up
                new BlockState(this, EnumFacing.UP, 0)
        );
    }


    /**
     * Renders a single face of the tile.
     *
     * @param t    The tesselator for rendering
     * @param x    X coordinate
     * @param y    Y coordinate
     * @param z    Z coordinate
     * @param face The face to render (0-5)
     */
    public final void renderFace(Tesselator t, int x, int y, int z, int face, EnumFacing facing) {
        int tex = getTexture(face, facing);
        int rotation = getRotation(face, facing);
        float u0 = (tex % 16) / 16.0F;
        float u1 = u0 + 0.0624375F;
        float v0 = (tex / 16) / 16.0F;
        float v1 = v0 + 0.0624375F;

        float tmp;

        if (rotation == 2) {
            // rotate 180 deg; flip vertically
            tmp = v1;
            v1 = v0;
            v0 = tmp;
        }

        float x0 = x;
        float x1 = x + 1.0F;
        float y0 = y;
        float y1 = y + 1.0F;
        float z0 = z;
        float z1 = z + 1.0F;

        float v0u = 0, v0v = 0;
        float v1u = 0, v1v = 0;
        float v2u = 0, v2v = 0;
        float v3u = 0, v3v = 0;

        float v0x = 0, v0y = 0, v0z = 0;
        float v1x = 0, v1y = 0, v1z = 0;
        float v2x = 0, v2y = 0, v2z = 0;
        float v3x = 0, v3y = 0, v3z = 0;

        // Bottom face
        if (face == 0) {
            v0x = x0;
            v0y = y0;
            v0z = z1;
            v1x = x0;
            v1y = y0;
            v1z = z0;
            v2x = x1;
            v2y = y0;
            v2z = z0;
            v3x = x1;
            v3y = y0;
            v3z = z1;
            v0u = u0;
            v0v = v1;
            v1u = u0;
            v1v = v0;
            v2u = u1;
            v2v = v0;
            v3u = u1;
            v3v = v1;
        }

        // Top face
        else if (face == 1) {
            v0x = x1;
            v0y = y1;
            v0z = z1;
            v1x = x1;
            v1y = y1;
            v1z = z0;
            v2x = x0;
            v2y = y1;
            v2z = z0;
            v3x = x0;
            v3y = y1;
            v3z = z1;
            v0u = u1;
            v0v = v1;
            v1u = u1;
            v1v = v0;
            v2u = u0;
            v2v = v0;
            v3u = u0;
            v3v = v1;
        }

        // North face
        else if (face == 2) {
            v0x = x0;
            v0y = y1;
            v0z = z0;
            v1x = x1;
            v1y = y1;
            v1z = z0;
            v2x = x1;
            v2y = y0;
            v2z = z0;
            v3x = x0;
            v3y = y0;
            v3z = z0;
            v0u = u1;
            v0v = v0;
            v1u = u0;
            v1v = v0;
            v2u = u0;
            v2v = v1;
            v3u = u1;
            v3v = v1;
        }

        // South face
        else if (face == 3) {
            v0x = x0;
            v0y = y1;
            v0z = z1;
            v1x = x0;
            v1y = y0;
            v1z = z1;
            v2x = x1;
            v2y = y0;
            v2z = z1;
            v3x = x1;
            v3y = y1;
            v3z = z1;
            v0u = u0;
            v0v = v0;
            v1u = u0;
            v1v = v1;
            v2u = u1;
            v2v = v1;
            v3u = u1;
            v3v = v0;
        }

        // West face
        else if (face == 4) {
            v0x = x0;
            v0y = y1;
            v0z = z1;
            v1x = x0;
            v1y = y1;
            v1z = z0;
            v2x = x0;
            v2y = y0;
            v2z = z0;
            v3x = x0;
            v3y = y0;
            v3z = z1;
            v0u = u1;
            v0v = v0;
            v1u = u0;
            v1v = v0;
            v2u = u0;
            v2v = v1;
            v3u = u1;
            v3v = v1;
        }

        // East face
        else if (face == 5) {
            v0x = x1;
            v0y = y0;
            v0z = z1;
            v1x = x1;
            v1y = y0;
            v1z = z0;
            v2x = x1;
            v2y = y1;
            v2z = z0;
            v3x = x1;
            v3y = y1;
            v3z = z1;
            v0u = u0;
            v0v = v1;
            v1u = u1;
            v1v = v1;
            v2u = u1;
            v2v = v0;
            v3u = u0;
            v3v = v0;
        }

        switch (rotation) {
            case 0:
                // do nothing
                break;
            case 1:
                // rotate 90 deg
                tmp = v0u;
                v0u = v1u;
                v1u = v2u;
                v2u = v3u;
                v3u = tmp;

                tmp = v0v;
                v0v = v1v;
                v1v = v2v;
                v2v = v3v;
                v3v = tmp;
                break;
            case 2:
                // rotate 180 deg; handled above
                break;
            case 3:
                // rotate 270 deg
                tmp = v3u;
                v3u = v2u;
                v2u = v1u;
                v1u = v0u;
                v0u = tmp;

                tmp = v3v;
                v3v = v2v;
                v2v = v1v;
                v1v = v0v;
                v0v = tmp;
                break;
        }
        t.vertexUV(v0x, v0y, v0z, v0u, v0v);
        t.vertexUV(v1x, v1y, v1z, v1u, v1v);
        t.vertexUV(v2x, v2y, v2z, v2u, v2v);
        t.vertexUV(v3x, v3y, v3z, v3u, v3v);
    }

    public Block setInteractable(boolean interactable) {
        this.interactable = interactable;
        return this;
    }

    /**
     * Gets the tile's bounding box with standard dimensions.
     *
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @return The tile's axis-aligned bounding box
     */
    public final AABB getTileAABB(int x, int y, int z) {
        return new AABB(x, y, z, x + 1, y + 1, z + 1);
    }

    /**
     * Gets the collision box for this tile.
     * Can be overridden by subclasses to provide custom collision shapes.
     *
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @return The collision box for this tile
     */
    public AABB getAABB(int x, int y, int z) {
        return new AABB(x, y, z, x + 1, y + 1, z + 1);
    }

    /**
     * @return Whether this tile blocks light
     */
    public boolean isLightBlocker() {
        return true;
    }

    /**
     * @return Whether this tile is solid (for collision)
     */
    public boolean isSolid() {
        return true;
    }

    public boolean isTransparent() {
        return false;
    }

    /**
     * Update method called each tick for this tile.
     *
     * @param level  The current level
     * @param x      X coordinate
     * @param y      Y coordinate
     * @param z      Z coordinate
     * @param random Random number generator
     */
    public void tick(Level level, int x, int y, int z, Random random) {
        // Default implementation does nothing
    }

    /**
     * Called when this tile is destroyed.
     * Creates particles for the breaking effect.
     *
     * @param level          The current level
     * @param x              X coordinate
     * @param y              Y coordinate
     * @param z              Z coordinate
     * @param particleEngine The particle engine
     */
    public void destroy(Level level,
                        int x,
                        int y,
                        int z,
                        ParticleEngine particleEngine,
                        Pipeline worldPipeline,
                        MutableDescriptorSet worldFogTerrainDescriptorSet) {
        int subdivisionsPerAxis = 4;

        for (int xx = 0; xx < subdivisionsPerAxis; ++xx) {
            for (int yy = 0; yy < subdivisionsPerAxis; ++yy) {
                for (int zz = 0; zz < subdivisionsPerAxis; ++zz) {
                    float xp = x + (xx + 0.5F) / subdivisionsPerAxis;
                    float yp = y + (yy + 0.5F) / subdivisionsPerAxis;
                    float zp = z + (zz + 0.5F) / subdivisionsPerAxis;

                    // Calculate velocity away from center
                    float xVel = xp - x - 0.5F;
                    float yVel = yp - y - 0.5F;
                    float zVel = zp - z - 0.5F;

                    particleEngine.add(new Particle(level, xp, yp, zp, xVel, yVel, zVel, tex));
                }
            }
        }
        BlockItem blockItem = BlockItems.getBlockItemForBlockOrNull(this);
        EntityItem entityItem = new EntityItem(level, new ItemStack(blockItem, 1), worldPipeline, worldFogTerrainDescriptorSet);
        entityItem.setPosition(x + 0.25f, y + 0.01f, z + 0.25f);
        entityItem.xd = (float) (ThreadLocalRandom.current().nextGaussian() * 0.05F);
        entityItem.yd = (float) (ThreadLocalRandom.current().nextGaussian() * 0.05F + 0.2F);
        entityItem.zd = (float) (ThreadLocalRandom.current().nextGaussian() * 0.05F);
        level.spawnEntity(entityItem);
    }

    public BlockState getDefaultBlockState() {
        return getValidBlockStates().get(0);
    }

    public BlockState getBlockState(EnumFacing facingDirection) {
        List<BlockState> validBlockStates = getValidBlockStates();
        if (validBlockStates.size() == 1) {
            return validBlockStates.get(0);
        }
        for (BlockState state : validBlockStates) {
            if (state.facing == facingDirection) {
                return state;
            }
        }
        return validBlockStates.get(0); // default block state
    }

    public boolean isBlockingMovement() {
        return true;
    }

    public float getSlipperiness() {
        return 0.6F;
    }

    public int getId() {
        return id;
    }

    public boolean isInteractable() {
        return interactable;
    }
}
