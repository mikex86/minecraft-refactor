package com.mojang.minecraft.level;

import com.mojang.minecraft.HitResult;
import com.mojang.minecraft.Player;
import com.mojang.minecraft.level.tile.Tile;
import com.mojang.minecraft.phys.AABB;
import com.mojang.minecraft.renderer.Frustum;
import com.mojang.minecraft.renderer.Tesselator;
import com.mojang.minecraft.renderer.Textures;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;

/**
 * Handles rendering of the Minecraft level.
 */
public class LevelRenderer implements LevelListener {
    // Constants
    public static final int MAX_REBUILDS_PER_FRAME = 8;
    public static final int CHUNK_SIZE = 16;
    
    // Level data
    private final Level level;
    private final Chunk[] chunks;
    private final int xChunks;
    private final int yChunks;
    private final int zChunks;
    private final Textures textures;

    /**
     * Creates a new LevelRenderer for the specified level.
     */
    public LevelRenderer(Level level, Textures textures) {
        this.level = level;
        this.textures = textures;
        level.addListener(this);
        
        // Calculate the number of chunks in each dimension
        this.xChunks = level.width / CHUNK_SIZE;
        this.yChunks = level.depth / CHUNK_SIZE;
        this.zChunks = level.height / CHUNK_SIZE;
        
        // Create the chunks
        this.chunks = new Chunk[this.xChunks * this.yChunks * this.zChunks];

        // Initialize all chunks
        for (int x = 0; x < this.xChunks; ++x) {
            for (int y = 0; y < this.yChunks; ++y) {
                for (int z = 0; z < this.zChunks; ++z) {
                    // Calculate chunk boundaries
                    int x0 = x * CHUNK_SIZE;
                    int y0 = y * CHUNK_SIZE;
                    int z0 = z * CHUNK_SIZE;
                    int x1 = (x + 1) * CHUNK_SIZE;
                    int y1 = (y + 1) * CHUNK_SIZE;
                    int z1 = (z + 1) * CHUNK_SIZE;
                    
                    // Clamp to level bounds
                    if (x1 > level.width) {
                        x1 = level.width;
                    }
                    if (y1 > level.depth) {
                        y1 = level.depth;
                    }
                    if (z1 > level.height) {
                        z1 = level.height;
                    }

                    // Create and store the chunk
                    int chunkIndex = (x + y * this.xChunks) * this.zChunks + z;
                    this.chunks[chunkIndex] = new Chunk(level, x0, y0, z0, x1, y1, z1);
                }
            }
        }
    }

    /**
     * Gets all chunks that need to be rebuilt.
     */
    public List<Chunk> getAllDirtyChunks() {
        ArrayList<Chunk> dirtyChunks = null;

        for (Chunk chunk : this.chunks) {
            if (chunk.isDirty()) {
                if (dirtyChunks == null) {
                    dirtyChunks = new ArrayList<>();
                }
                dirtyChunks.add(chunk);
            }
        }

        return dirtyChunks;
    }

    /**
     * Renders the level for the specified layer.
     */
    public void render(Player player, int layer) {
        // Enable texturing and bind the terrain texture
        glEnable(GL_TEXTURE_2D);
        int textureId = this.textures.loadTexture("/terrain.png", GL_NEAREST);
        glBindTexture(GL_TEXTURE_2D, textureId);
        
        // Get the current view frustum
        Frustum frustum = Frustum.getFrustum();

        // Render all visible chunks
        for (Chunk chunk : this.chunks) {
            if (frustum.isVisible(chunk.aabb)) {
                chunk.render(layer);
            }
        }

        glDisable(GL_TEXTURE_2D);
    }

    /**
     * Updates chunks that need to be rebuilt.
     */
    public void updateDirtyChunks(Player player) {
        List<Chunk> dirtyChunks = this.getAllDirtyChunks();
        if (dirtyChunks != null) {
            // Sort chunks by visibility, age, and distance to player
            dirtyChunks.sort(new DirtyChunkSorter(player, Frustum.getFrustum()));

            // Rebuild at most MAX_REBUILDS_PER_FRAME chunks per frame
            int rebuildCount = Math.min(MAX_REBUILDS_PER_FRAME, dirtyChunks.size());
            for (int i = 0; i < rebuildCount; ++i) {
                dirtyChunks.get(i).rebuild();
            }
        }
    }

    /**
     * Performs picking (determining which block the player is looking at).
     */
    public void pick(Player player, Frustum frustum) {
        Tesselator tesselator = Tesselator.instance;
        
        // Define a box around the player to limit pick testing
        float pickRange = 3.0F;
        AABB pickBox = player.bb.grow(pickRange, pickRange, pickRange);
        
        int x0 = (int) pickBox.x0;
        int x1 = (int) (pickBox.x1 + 1.0F);
        int y0 = (int) pickBox.y0;
        int y1 = (int) (pickBox.y1 + 1.0F);
        int z0 = (int) pickBox.z0;
        int z1 = (int) (pickBox.z1 + 1.0F);
        
        // Initialize selection name stack
        glInitNames();
        glPushName(0);
        glPushName(0);

        // Loop through all blocks in the pick box
        for (int x = x0; x < x1; ++x) {
            glLoadName(x);
            glPushName(0);

            for (int y = y0; y < y1; ++y) {
                glLoadName(y);
                glPushName(0);

                for (int z = z0; z < z1; ++z) {
                    Tile tile = Tile.tiles[this.level.getTile(x, y, z)];
                    if (tile != null && frustum.isVisible(tile.getTileAABB(x, y, z))) {
                        glLoadName(z);
                        glPushName(0);

                        // Render each face for selection
                        for (int face = 0; face < 6; ++face) {
                            glLoadName(face);
                            tesselator.init();
                            tile.renderFaceNoTexture(tesselator, x, y, z, face);
                            tesselator.flush();
                        }

                        glPopName();
                    }
                }

                glPopName();
            }

            glPopName();
        }

        glPopName();
        glPopName();
    }

    /**
     * Renders the highlighted block the player is looking at.
     */
    public void renderHit(HitResult hitResult, int mode, int tileType) {
        Tesselator tesselator = Tesselator.instance;
        
        // Enable blending for transparency
        glEnable(GL_BLEND);
        
        // Calculate the alpha value based on time for a pulsing effect
        float pulsingAlpha = ((float) Math.sin(System.currentTimeMillis() / 100.0) * 0.2F + 0.4F) * 0.5F;
        
        if (mode == 0) {
            // Destruction mode - render an outline of the block being broken
            glBlendFunc(GL_SRC_ALPHA, GL_ONE);
            glColor4f(1.0F, 1.0F, 1.0F, pulsingAlpha);
            
            tesselator.init();
            
            // Render all faces
            for (int face = 0; face < 6; ++face) {
                Tile.rock.renderFaceNoTexture(tesselator, hitResult.x, hitResult.y, hitResult.z, face);
            }
            
            tesselator.flush();
        } else {
            // Building mode - render a preview of the block to be placed
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            
            // Calculate brightness based on time
            float brightness = (float) Math.sin(System.currentTimeMillis() / 100.0) * 0.2F + 0.8F;
            float alpha = (float) Math.sin(System.currentTimeMillis() / 200.0) * 0.2F + 0.5F;
            glColor4f(brightness, brightness, brightness, alpha);
            
            // Enable texturing
            glEnable(GL_TEXTURE_2D);
            int textureId = this.textures.loadTexture("/terrain.png", GL_NEAREST);
            glBindTexture(GL_TEXTURE_2D, textureId);
            
            // Calculate the position to render the block preview
            int x = hitResult.x;
            int y = hitResult.y;
            int z = hitResult.z;
            
            // Adjust position based on which face was hit
            if (hitResult.face == 0) --y;
            if (hitResult.face == 1) ++y;
            if (hitResult.face == 2) --z;
            if (hitResult.face == 3) ++z;
            if (hitResult.face == 4) --x;
            if (hitResult.face == 5) ++x;

            // Render the block preview
            tesselator.init();
            tesselator.noColor();
            Tile.tiles[tileType].render(tesselator, this.level, 0, x, y, z);
            Tile.tiles[tileType].render(tesselator, this.level, 1, x, y, z);
            tesselator.flush();
            
            glDisable(GL_TEXTURE_2D);
        }

        glDisable(GL_BLEND);
    }

    /**
     * Marks a region of chunks as dirty, needing to be rebuilt.
     */
    public void setDirty(int x0, int y0, int z0, int x1, int y1, int z1) {
        // Convert block coordinates to chunk coordinates
        x0 /= CHUNK_SIZE;
        x1 /= CHUNK_SIZE;
        y0 /= CHUNK_SIZE;
        y1 /= CHUNK_SIZE;
        z0 /= CHUNK_SIZE;
        z1 /= CHUNK_SIZE;
        
        // Clamp to valid chunk ranges
        x0 = Math.max(0, x0);
        y0 = Math.max(0, y0);
        z0 = Math.max(0, z0);
        x1 = Math.min(x1, this.xChunks - 1);
        y1 = Math.min(y1, this.yChunks - 1);
        z1 = Math.min(z1, this.zChunks - 1);

        // Mark all chunks in the region as dirty
        for (int x = x0; x <= x1; ++x) {
            for (int y = y0; y <= y1; ++y) {
                for (int z = z0; z <= z1; ++z) {
                    int chunkIndex = (x + y * this.xChunks) * this.zChunks + z;
                    this.chunks[chunkIndex].setDirty();
                }
            }
        }
    }

    /**
     * Called when a tile changes.
     */
    @Override
    public void tileChanged(int x, int y, int z) {
        // Mark a region around the changed tile as dirty
        this.setDirty(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1);
    }

    /**
     * Called when a light column changes.
     */
    @Override
    public void lightColumnChanged(int x, int z, int y0, int y1) {
        // Mark a region around the changed light column as dirty
        this.setDirty(x - 1, y0 - 1, z - 1, x + 1, y1 + 1, z + 1);
    }

    /**
     * Called when the entire level changes.
     */
    @Override
    public void allChanged() {
        // Mark the entire level as dirty
        this.setDirty(0, 0, 0, this.level.width, this.level.depth, this.level.height);
    }
}
