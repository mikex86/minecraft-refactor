package com.mojang.minecraft.renderer.shape;

import com.mojang.minecraft.renderer.model.ModelMesh;

import static org.lwjgl.opengl.GL11.*;

/**
 * Represents a 3D cube with texture mapping for character models.
 */
public class Cube {
    private static final float RADIANS_TO_DEGREES = 57.29578F; // 180.0f / Math.PI

    private Vertex[] vertices;
    private Polygon[] polygons;
    private int xTexOffs;
    private int yTexOffs;

    // Position
    public float x;
    public float y;
    public float z;

    // Rotation
    public float xRot;
    public float yRot;
    public float zRot;

    // Mesh handling
    private ModelMesh mesh;
    private boolean dirty = true;

    /**
     * Creates a new cube with the given texture offsets.
     *
     * @param xTexOffs X texture offset
     * @param yTexOffs Y texture offset
     */
    public Cube(int xTexOffs, int yTexOffs) {
        this.xTexOffs = xTexOffs;
        this.yTexOffs = yTexOffs;
        this.mesh = new ModelMesh();
    }

    /**
     * Sets the texture offsets for this cube.
     *
     * @param xTexOffs X texture offset
     * @param yTexOffs Y texture offset
     */
    public void setTexOffs(int xTexOffs, int yTexOffs) {
        this.xTexOffs = xTexOffs;
        this.yTexOffs = yTexOffs;
    }

    /**
     * Adds a box to this cube with the given dimensions.
     *
     * @param x0     Starting X coordinate
     * @param y0     Starting Y coordinate
     * @param z0     Starting Z coordinate
     * @param width  Width of the box
     * @param height Height of the box
     * @param depth  Depth of the box
     */
    public void addBox(float x0, float y0, float z0, int width, int height, int depth) {
        this.vertices = new Vertex[8];
        this.polygons = new Polygon[6];

        // Calculate end coordinates
        float x1 = x0 + (float) width;
        float y1 = y0 + (float) height;
        float z1 = z0 + (float) depth;

        // Create vertices for the upper face
        Vertex upperNW = new Vertex(x0, y0, z0, 0.0F, 0.0F);
        Vertex upperNE = new Vertex(x1, y0, z0, 0.0F, 8.0F);
        Vertex upperSE = new Vertex(x1, y1, z0, 8.0F, 8.0F);
        Vertex upperSW = new Vertex(x0, y1, z0, 8.0F, 0.0F);

        // Create vertices for the lower face
        Vertex lowerNW = new Vertex(x0, y0, z1, 0.0F, 0.0F);
        Vertex lowerNE = new Vertex(x1, y0, z1, 0.0F, 8.0F);
        Vertex lowerSE = new Vertex(x1, y1, z1, 8.0F, 8.0F);
        Vertex lowerSW = new Vertex(x0, y1, z1, 8.0F, 0.0F);

        // Store all vertices
        this.vertices[0] = upperNW;
        this.vertices[1] = upperNE;
        this.vertices[2] = upperSE;
        this.vertices[3] = upperSW;
        this.vertices[4] = lowerNW;
        this.vertices[5] = lowerNE;
        this.vertices[6] = lowerSE;
        this.vertices[7] = lowerSW;

        // Create polygons for each face with texture coordinates
        // Right face
        this.polygons[0] = new Polygon(
                new Vertex[]{lowerNE, upperNE, upperSE, lowerSE},
                this.xTexOffs + depth + width, this.yTexOffs + depth,
                this.xTexOffs + depth + width + depth, this.yTexOffs + depth + height
        );

        // Left face
        this.polygons[1] = new Polygon(
                new Vertex[]{upperNW, lowerNW, lowerSW, upperSW},
                this.xTexOffs, this.yTexOffs + depth,
                this.xTexOffs + depth, this.yTexOffs + depth + height
        );

        // Bottom face
        this.polygons[2] = new Polygon(
                new Vertex[]{lowerNE, lowerNW, upperNW, upperNE},
                this.xTexOffs + depth, this.yTexOffs,
                this.xTexOffs + depth + width, this.yTexOffs + depth
        );

        // Top face
        this.polygons[3] = new Polygon(
                new Vertex[]{upperSE, upperSW, lowerSW, lowerSE},
                this.xTexOffs + depth + width, this.yTexOffs,
                this.xTexOffs + depth + width + width, this.yTexOffs + depth
        );

        // Front face
        this.polygons[4] = new Polygon(
                new Vertex[]{upperNE, upperNW, upperSW, upperSE},
                this.xTexOffs + depth, this.yTexOffs + depth,
                this.xTexOffs + depth + width, this.yTexOffs + depth + height
        );

        // Back face
        this.polygons[5] = new Polygon(
                new Vertex[]{lowerNW, lowerNE, lowerSE, lowerSW},
                this.xTexOffs + depth + width + depth, this.yTexOffs + depth,
                this.xTexOffs + depth + width + depth + width, this.yTexOffs + depth + height
        );
        
        // Mark as dirty to rebuild the mesh
        this.dirty = true;
    }

    /**
     * Sets the position of this cube.
     *
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     */
    public void setPos(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * Builds the mesh for this cube if needed.
     */
    private void buildMesh() {
        if (!this.dirty) {
            return;
        }
        
        this.mesh.begin();
        
        // Add all polygons to the mesh
        for (Polygon polygon : this.polygons) {
            polygon.addToMesh(this.mesh);
        }
        
        this.mesh.end();
        this.dirty = false;
    }

    /**
     * Renders this cube.
     */
    public void render() {
        // Build the mesh if needed
        if (this.dirty) {
            buildMesh();
        }

        glPushMatrix();
        glTranslatef(this.x, this.y, this.z);
        glRotatef(this.zRot * RADIANS_TO_DEGREES, 0.0F, 0.0F, 1.0F);
        glRotatef(this.yRot * RADIANS_TO_DEGREES, 0.0F, 1.0F, 0.0F);
        glRotatef(this.xRot * RADIANS_TO_DEGREES, 1.0F, 0.0F, 0.0F);
        
        // Set white color for rendering
        glColor3f(1.0F, 1.0F, 1.0F);
        
        // Render the mesh
        this.mesh.render();
        
        glPopMatrix();
    }
    
    /**
     * Disposes of this cube's resources.
     */
    public void dispose() {
        if (this.mesh != null) {
            this.mesh.dispose();
            this.mesh = null;
        }
    }
}
