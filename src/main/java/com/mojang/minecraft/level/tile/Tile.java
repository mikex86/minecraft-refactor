package com.mojang.minecraft.level.tile;

import com.mojang.minecraft.level.Level;
import com.mojang.minecraft.particle.Particle;
import com.mojang.minecraft.particle.ParticleEngine;
import com.mojang.minecraft.phys.AABB;
import com.mojang.minecraft.renderer.Tesselator;

import java.util.Random;

/**
 * Base class for all tile types in the game.
 */
public class Tile {
    /**
     * Array of all tile types indexed by ID
     */
    public static final Tile[] tiles = new Tile[256];

    /**
     * Represents an empty tile
     */
    public static final Tile empty = null;

    /**
     * Rock/stone tile
     */
    public static final Tile rock = new Tile(1, 1);

    /**
     * Grass tile
     */
    public static final Tile grass = new GrassTile(2);

    /**
     * Dirt tile
     */
    public static final Tile dirt = new DirtTile(3, 2);

    /**
     * Stone brick tile
     */
    public static final Tile stoneBrick = new Tile(4, 16);

    /**
     * Wooden planks tile
     */
    public static final Tile wood = new Tile(5, 4);

    /**
     * Bush/plant tile
     */
    public static final Tile bush = new Bush(6);

    /**
     * Texture index for the tile
     */
    public int tex;

    /**
     * Unique ID for the tile type
     */
    public final int id;

    /**
     * Creates a new tile with the specified ID.
     *
     * @param id The tile ID
     */
    protected Tile(int id) {
        tiles[id] = this;
        this.id = id;
    }

    /**
     * Creates a new tile with the specified ID and texture.
     *
     * @param id  The tile ID
     * @param tex The texture index
     */
    protected Tile(int id, int tex) {
        this(id);
        this.tex = tex;
    }

    /**
     * Renders the tile in the world.
     *
     * @param t     The tesselator for rendering
     * @param level The current level
     * @param x     X coordinate
     * @param y     Y coordinate
     * @param z     Z coordinate
     */
    public void render(Tesselator t, Level level, int x, int y, int z) {
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

        if (level != null) {
            isTopLit = level.isLit(x, y + 1, z);
            isBottomLit = level.isLit(x, y - 1, z);
            isNorthLit = level.isLit(x, y, z - 1);
            isSouthLit = level.isLit(x, y, z + 1);
            isWestLit = level.isLit(x - 1, y, z);
            isEastLit = level.isLit(x + 1, y, z);
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
        if (shouldRenderFace(level, x, y - 1, z)) {
            t.color(bottomColor, bottomColor, bottomColor);
            renderFace(t, x, y, z, 0);
        }

        // Top face
        if (shouldRenderFace(level, x, y + 1, z)) {
            t.color(topColor, topColor, topColor);
            renderFace(t, x, y, z, 1);
        }

        // North face
        if (shouldRenderFace(level, x, y, z - 1)) {
            t.color(northColor, northColor, northColor);
            renderFace(t, x, y, z, 2);
        }

        // South face
        if (shouldRenderFace(level, x, y, z + 1)) {
            t.color(southColor, southColor, southColor);
            renderFace(t, x, y, z, 3);
        }

        // West face
        if (shouldRenderFace(level, x - 1, y, z)) {
            t.color(westColor, westColor, westColor);
            renderFace(t, x, y, z, 4);
        }

        // East face
        if (shouldRenderFace(level, x + 1, y, z)) {
            t.color(eastColor, eastColor, eastColor);
            renderFace(t, x, y, z, 5);
        }
    }

    /**
     * Determines if a face should be rendered based on neighbor blocks and lighting.
     *
     * @param level The current level
     * @param x     X coordinate
     * @param y     Y coordinate
     * @param z     Z coordinate
     * @return True if the face should be rendered
     */
    private boolean shouldRenderFace(Level level, int x, int y, int z) {
        if (level == null) {
            return true;
        }
        return !level.isSolidTile(x, y, z);
    }

    /**
     * Gets the texture index for a specific face.
     *
     * @param face The face index (0-5)
     * @return The texture index
     */
    protected int getTexture(int face) {
        return tex;
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
    public void renderFace(Tesselator t, int x, int y, int z, int face) {
        int tex = getTexture(face);
        float u0 = (tex % 16) / 16.0F;
        float u1 = u0 + 0.0624375F;
        float v0 = (tex / 16) / 16.0F;
        float v1 = v0 + 0.0624375F;

        float x0 = x;
        float x1 = x + 1.0F;
        float y0 = y;
        float y1 = y + 1.0F;
        float z0 = z;
        float z1 = z + 1.0F;

        // Bottom face
        if (face == 0) {
            t.vertexUV(x0, y0, z1, u0, v1);
            t.vertexUV(x0, y0, z0, u0, v0);
            t.vertexUV(x1, y0, z0, u1, v0);
            t.vertexUV(x1, y0, z1, u1, v1);
        }

        // Top face
        if (face == 1) {
            t.vertexUV(x1, y1, z1, u1, v1);
            t.vertexUV(x1, y1, z0, u1, v0);
            t.vertexUV(x0, y1, z0, u0, v0);
            t.vertexUV(x0, y1, z1, u0, v1);
        }

        // North face
        if (face == 2) {
            t.vertexUV(x0, y1, z0, u1, v0);
            t.vertexUV(x1, y1, z0, u0, v0);
            t.vertexUV(x1, y0, z0, u0, v1);
            t.vertexUV(x0, y0, z0, u1, v1);
        }

        // South face
        if (face == 3) {
            t.vertexUV(x0, y1, z1, u0, v0);
            t.vertexUV(x0, y0, z1, u0, v1);
            t.vertexUV(x1, y0, z1, u1, v1);
            t.vertexUV(x1, y1, z1, u1, v0);
        }

        // West face
        if (face == 4) {
            t.vertexUV(x0, y1, z1, u1, v0);
            t.vertexUV(x0, y1, z0, u0, v0);
            t.vertexUV(x0, y0, z0, u0, v1);
            t.vertexUV(x0, y0, z1, u1, v1);
        }

        // East face
        if (face == 5) {
            t.vertexUV(x1, y0, z1, u0, v1);
            t.vertexUV(x1, y0, z0, u1, v1);
            t.vertexUV(x1, y1, z0, u1, v0);
            t.vertexUV(x1, y1, z1, u0, v0);
        }
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
    public boolean blocksLight() {
        return true;
    }

    /**
     * @return Whether this tile is solid (for collision)
     */
    public boolean isSolid() {
        return true;
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
    public void destroy(Level level, int x, int y, int z, ParticleEngine particleEngine) {
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
    }
}
