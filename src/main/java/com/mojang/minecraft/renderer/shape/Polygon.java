package com.mojang.minecraft.renderer.shape;

import com.mojang.minecraft.renderer.model.ModelMesh;

/**
 * Represents a polygon with texture mapping in a 3D model.
 */
public class Polygon {
    // Texture mapping constants
    private static final float TEX_WIDTH_SCALE = 63.999F; // Size of texture width
    private static final float TEX_HEIGHT_SCALE = 31.999F; // Size of texture height

    public Vertex[] vertices;
    public int vertexCount;

    /**
     * Creates a new polygon with the given vertices.
     *
     * @param vertices Array of vertices defining the polygon
     */
    public Polygon(Vertex[] vertices) {
        this.vertexCount = 0;
        this.vertices = vertices;
        this.vertexCount = vertices.length;
    }

    /**
     * Creates a new polygon with the given vertices and texture coordinates.
     *
     * @param vertices Array of vertices defining the polygon
     * @param u0       First U texture coordinate
     * @param v0       First V texture coordinate
     * @param u1       Second U texture coordinate
     * @param v1       Second V texture coordinate
     */
    public Polygon(Vertex[] vertices, int u0, int v0, int u1, int v1) {
        this(vertices);
        vertices[0] = vertices[0].remap((float) u1, (float) v0);
        vertices[1] = vertices[1].remap((float) u0, (float) v0);
        vertices[2] = vertices[2].remap((float) u0, (float) v1);
        vertices[3] = vertices[3].remap((float) u1, (float) v1);
    }

    /**
     * Adds this polygon's vertices to the given model mesh.
     * 
     * @param mesh The model mesh to add vertices to
     */
    public void addToMesh(ModelMesh mesh) {
        // Add vertices in reverse order (counter-clockwise winding)
        for (int i = 3; i >= 0; --i) {
            Vertex vertex = this.vertices[i];
            mesh.addVertex(
                vertex.pos.x, 
                vertex.pos.y, 
                vertex.pos.z, 
                vertex.u / TEX_WIDTH_SCALE, 
                vertex.v / TEX_HEIGHT_SCALE
            );
        }
    }
}
