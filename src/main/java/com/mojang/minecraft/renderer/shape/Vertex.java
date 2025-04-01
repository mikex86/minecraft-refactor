package com.mojang.minecraft.renderer.shape;

/**
 * Represents a vertex in 3D space with texture coordinates.
 */
public class Vertex {
    public Vec3 pos;   // Position in 3D space
    public float u;    // U texture coordinate
    public float v;    // V texture coordinate

    /**
     * Creates a new vertex with the given coordinates and texture mapping.
     *
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @param u U texture coordinate
     * @param v V texture coordinate
     */
    public Vertex(float x, float y, float z, float u, float v) {
        this(new Vec3(x, y, z), u, v);
    }

    /**
     * Creates a new vertex with same position but different texture coordinates.
     *
     * @param u New U texture coordinate
     * @param v New V texture coordinate
     * @return A new vertex with updated texture coordinates
     */
    public Vertex remap(float u, float v) {
        return new Vertex(this, u, v);
    }

    /**
     * Creates a new vertex based on an existing vertex but with different texture coordinates.
     *
     * @param vertex The source vertex to copy position from
     * @param u      U texture coordinate
     * @param v      V texture coordinate
     */
    public Vertex(Vertex vertex, float u, float v) {
        this.pos = vertex.pos;
        this.u = u;
        this.v = v;
    }

    /**
     * Creates a new vertex with the given position and texture coordinates.
     *
     * @param pos 3D position vector
     * @param u   U texture coordinate
     * @param v   V texture coordinate
     */
    public Vertex(Vec3 pos, float u, float v) {
        this.pos = pos;
        this.u = u;
        this.v = v;
    }
}
