package com.mojang.minecraft.renderer;

import com.mojang.minecraft.phys.AABB;

import java.nio.FloatBuffer;

import org.lwjgl.BufferUtils;

import static org.lwjgl.opengl.GL11.*;

/**
 * Handles view frustum calculations for 3D rendering
 */
public class Frustum {
    // Frustum planes data
    public float[][] frustumPlanes = new float[6][4];
    
    // Constants for frustum sides
    public static final int RIGHT = 0;
    public static final int LEFT = 1;
    public static final int BOTTOM = 2;
    public static final int TOP = 3;
    public static final int BACK = 4;
    public static final int FRONT = 5;
    
    // Constants for plane equation coefficients
    public static final int A = 0;
    public static final int B = 1;
    public static final int C = 2;
    public static final int D = 3;
    
    // Singleton instance
    private static Frustum instance = new Frustum();
    
    // Buffers for OpenGL matrices
    private FloatBuffer projectionBuffer = BufferUtils.createFloatBuffer(16);
    private FloatBuffer modelviewBuffer = BufferUtils.createFloatBuffer(16);
    private FloatBuffer clipBuffer = BufferUtils.createFloatBuffer(16);
    
    // Arrays to hold matrix data
    private float[] projection = new float[16];
    private float[] modelview = new float[16];
    private float[] clip = new float[16];

    /**
     * Private constructor for singleton pattern
     */
    private Frustum() {
    }

    /**
     * Get the frustum singleton instance
     * @return Updated frustum instance
     */
    public static Frustum getFrustum() {
        instance.calculateFrustum();
        return instance;
    }

    /**
     * Normalize a plane equation
     * @param frustum The frustum planes array
     * @param side The side to normalize
     */
    private void normalizePlane(float[][] frustum, int side) {
        float magnitude = (float) Math.sqrt(
            frustum[side][A] * frustum[side][A] + 
            frustum[side][B] * frustum[side][B] + 
            frustum[side][C] * frustum[side][C]
        );
        
        frustum[side][A] /= magnitude;
        frustum[side][B] /= magnitude;
        frustum[side][C] /= magnitude;
        frustum[side][D] /= magnitude;
    }

    /**
     * Calculate the frustum planes from current OpenGL matrices
     */
    private void calculateFrustum() {
        // Clear buffers
        this.projectionBuffer.clear();
        this.modelviewBuffer.clear();
        this.clipBuffer.clear();
        
        // Get current OpenGL matrices
        glGetFloat(GL_PROJECTION_MATRIX, this.projectionBuffer);
        glGetFloat(GL_MODELVIEW_MATRIX, this.modelviewBuffer);
        
        // Read matrices into arrays
        this.projectionBuffer.flip().limit(16);
        this.projectionBuffer.get(this.projection);
        this.modelviewBuffer.flip().limit(16);
        this.modelviewBuffer.get(this.modelview);
        
        // Calculate the clip matrix (projection * modelview)
        // Row 1
        this.clip[0] = this.modelview[0] * this.projection[0] + this.modelview[1] * this.projection[4] + 
                      this.modelview[2] * this.projection[8] + this.modelview[3] * this.projection[12];
        this.clip[1] = this.modelview[0] * this.projection[1] + this.modelview[1] * this.projection[5] + 
                      this.modelview[2] * this.projection[9] + this.modelview[3] * this.projection[13];
        this.clip[2] = this.modelview[0] * this.projection[2] + this.modelview[1] * this.projection[6] + 
                      this.modelview[2] * this.projection[10] + this.modelview[3] * this.projection[14];
        this.clip[3] = this.modelview[0] * this.projection[3] + this.modelview[1] * this.projection[7] + 
                      this.modelview[2] * this.projection[11] + this.modelview[3] * this.projection[15];
        
        // Row 2
        this.clip[4] = this.modelview[4] * this.projection[0] + this.modelview[5] * this.projection[4] + 
                      this.modelview[6] * this.projection[8] + this.modelview[7] * this.projection[12];
        this.clip[5] = this.modelview[4] * this.projection[1] + this.modelview[5] * this.projection[5] + 
                      this.modelview[6] * this.projection[9] + this.modelview[7] * this.projection[13];
        this.clip[6] = this.modelview[4] * this.projection[2] + this.modelview[5] * this.projection[6] + 
                      this.modelview[6] * this.projection[10] + this.modelview[7] * this.projection[14];
        this.clip[7] = this.modelview[4] * this.projection[3] + this.modelview[5] * this.projection[7] + 
                      this.modelview[6] * this.projection[11] + this.modelview[7] * this.projection[15];
        
        // Row 3
        this.clip[8] = this.modelview[8] * this.projection[0] + this.modelview[9] * this.projection[4] + 
                      this.modelview[10] * this.projection[8] + this.modelview[11] * this.projection[12];
        this.clip[9] = this.modelview[8] * this.projection[1] + this.modelview[9] * this.projection[5] + 
                      this.modelview[10] * this.projection[9] + this.modelview[11] * this.projection[13];
        this.clip[10] = this.modelview[8] * this.projection[2] + this.modelview[9] * this.projection[6] + 
                       this.modelview[10] * this.projection[10] + this.modelview[11] * this.projection[14];
        this.clip[11] = this.modelview[8] * this.projection[3] + this.modelview[9] * this.projection[7] + 
                       this.modelview[10] * this.projection[11] + this.modelview[11] * this.projection[15];
        
        // Row 4
        this.clip[12] = this.modelview[12] * this.projection[0] + this.modelview[13] * this.projection[4] + 
                       this.modelview[14] * this.projection[8] + this.modelview[15] * this.projection[12];
        this.clip[13] = this.modelview[12] * this.projection[1] + this.modelview[13] * this.projection[5] + 
                       this.modelview[14] * this.projection[9] + this.modelview[15] * this.projection[13];
        this.clip[14] = this.modelview[12] * this.projection[2] + this.modelview[13] * this.projection[6] + 
                       this.modelview[14] * this.projection[10] + this.modelview[15] * this.projection[14];
        this.clip[15] = this.modelview[12] * this.projection[3] + this.modelview[13] * this.projection[7] + 
                       this.modelview[14] * this.projection[11] + this.modelview[15] * this.projection[15];
        
        // Calculate frustum planes from clip matrix
        
        // Right plane
        this.frustumPlanes[RIGHT][A] = this.clip[3] - this.clip[0];
        this.frustumPlanes[RIGHT][B] = this.clip[7] - this.clip[4];
        this.frustumPlanes[RIGHT][C] = this.clip[11] - this.clip[8];
        this.frustumPlanes[RIGHT][D] = this.clip[15] - this.clip[12];
        this.normalizePlane(this.frustumPlanes, RIGHT);
        
        // Left plane
        this.frustumPlanes[LEFT][A] = this.clip[3] + this.clip[0];
        this.frustumPlanes[LEFT][B] = this.clip[7] + this.clip[4];
        this.frustumPlanes[LEFT][C] = this.clip[11] + this.clip[8];
        this.frustumPlanes[LEFT][D] = this.clip[15] + this.clip[12];
        this.normalizePlane(this.frustumPlanes, LEFT);
        
        // Bottom plane
        this.frustumPlanes[BOTTOM][A] = this.clip[3] + this.clip[1];
        this.frustumPlanes[BOTTOM][B] = this.clip[7] + this.clip[5];
        this.frustumPlanes[BOTTOM][C] = this.clip[11] + this.clip[9];
        this.frustumPlanes[BOTTOM][D] = this.clip[15] + this.clip[13];
        this.normalizePlane(this.frustumPlanes, BOTTOM);
        
        // Top plane
        this.frustumPlanes[TOP][A] = this.clip[3] - this.clip[1];
        this.frustumPlanes[TOP][B] = this.clip[7] - this.clip[5];
        this.frustumPlanes[TOP][C] = this.clip[11] - this.clip[9];
        this.frustumPlanes[TOP][D] = this.clip[15] - this.clip[13];
        this.normalizePlane(this.frustumPlanes, TOP);
        
        // Back plane
        this.frustumPlanes[BACK][A] = this.clip[3] - this.clip[2];
        this.frustumPlanes[BACK][B] = this.clip[7] - this.clip[6];
        this.frustumPlanes[BACK][C] = this.clip[11] - this.clip[10];
        this.frustumPlanes[BACK][D] = this.clip[15] - this.clip[14];
        this.normalizePlane(this.frustumPlanes, BACK);
        
        // Front plane
        this.frustumPlanes[FRONT][A] = this.clip[3] + this.clip[2];
        this.frustumPlanes[FRONT][B] = this.clip[7] + this.clip[6];
        this.frustumPlanes[FRONT][C] = this.clip[11] + this.clip[10];
        this.frustumPlanes[FRONT][D] = this.clip[15] + this.clip[14];
        this.normalizePlane(this.frustumPlanes, FRONT);
    }

    /**
     * Check if a point is inside the frustum
     * @param x X-coordinate
     * @param y Y-coordinate
     * @param z Z-coordinate
     * @return true if point is inside frustum
     */
    public boolean pointInFrustum(float x, float y, float z) {
        for (int i = 0; i < 6; ++i) {
            if (this.frustumPlanes[i][A] * x + 
                this.frustumPlanes[i][B] * y + 
                this.frustumPlanes[i][C] * z + 
                this.frustumPlanes[i][D] <= 0.0F) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if a sphere is inside or intersects the frustum
     * @param x X-coordinate of sphere center
     * @param y Y-coordinate of sphere center
     * @param z Z-coordinate of sphere center
     * @param radius Radius of the sphere
     * @return true if sphere is inside or intersects frustum
     */
    public boolean sphereInFrustum(float x, float y, float z, float radius) {
        for (int i = 0; i < 6; ++i) {
            if (this.frustumPlanes[i][A] * x + 
                this.frustumPlanes[i][B] * y + 
                this.frustumPlanes[i][C] * z + 
                this.frustumPlanes[i][D] <= -radius) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if a cube is fully inside the frustum
     * @param x1 Minimum X-coordinate
     * @param y1 Minimum Y-coordinate
     * @param z1 Minimum Z-coordinate
     * @param x2 Maximum X-coordinate
     * @param y2 Maximum Y-coordinate
     * @param z2 Maximum Z-coordinate
     * @return true if cube is fully inside frustum
     */
    public boolean cubeFullyInFrustum(float x1, float y1, float z1, float x2, float y2, float z2) {
        for (int i = 0; i < 6; ++i) {
            // Check all 8 corners of the cube against current plane
            if (this.frustumPlanes[i][A] * x1 + this.frustumPlanes[i][B] * y1 + 
                this.frustumPlanes[i][C] * z1 + this.frustumPlanes[i][D] <= 0.0F) {
                return false;
            }
            
            if (this.frustumPlanes[i][A] * x2 + this.frustumPlanes[i][B] * y1 + 
                this.frustumPlanes[i][C] * z1 + this.frustumPlanes[i][D] <= 0.0F) {
                return false;
            }
            
            if (this.frustumPlanes[i][A] * x1 + this.frustumPlanes[i][B] * y2 + 
                this.frustumPlanes[i][C] * z1 + this.frustumPlanes[i][D] <= 0.0F) {
                return false;
            }
            
            if (this.frustumPlanes[i][A] * x2 + this.frustumPlanes[i][B] * y2 + 
                this.frustumPlanes[i][C] * z1 + this.frustumPlanes[i][D] <= 0.0F) {
                return false;
            }
            
            if (this.frustumPlanes[i][A] * x1 + this.frustumPlanes[i][B] * y1 + 
                this.frustumPlanes[i][C] * z2 + this.frustumPlanes[i][D] <= 0.0F) {
                return false;
            }
            
            if (this.frustumPlanes[i][A] * x2 + this.frustumPlanes[i][B] * y1 + 
                this.frustumPlanes[i][C] * z2 + this.frustumPlanes[i][D] <= 0.0F) {
                return false;
            }
            
            if (this.frustumPlanes[i][A] * x1 + this.frustumPlanes[i][B] * y2 + 
                this.frustumPlanes[i][C] * z2 + this.frustumPlanes[i][D] <= 0.0F) {
                return false;
            }
            
            if (this.frustumPlanes[i][A] * x2 + this.frustumPlanes[i][B] * y2 + 
                this.frustumPlanes[i][C] * z2 + this.frustumPlanes[i][D] <= 0.0F) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if a cube is partially inside the frustum
     * @param x1 Minimum X-coordinate
     * @param y1 Minimum Y-coordinate
     * @param z1 Minimum Z-coordinate
     * @param x2 Maximum X-coordinate
     * @param y2 Maximum Y-coordinate
     * @param z2 Maximum Z-coordinate
     * @return true if cube is at least partially inside frustum
     */
    public boolean cubeInFrustum(float x1, float y1, float z1, float x2, float y2, float z2) {
        for (int i = 0; i < 6; ++i) {
            // If all 8 points are outside the same plane, the cube is outside the frustum
            if (!(this.frustumPlanes[i][A] * x1 + this.frustumPlanes[i][B] * y1 + 
                  this.frustumPlanes[i][C] * z1 + this.frustumPlanes[i][D] > 0.0F) && 
                !(this.frustumPlanes[i][A] * x2 + this.frustumPlanes[i][B] * y1 + 
                  this.frustumPlanes[i][C] * z1 + this.frustumPlanes[i][D] > 0.0F) && 
                !(this.frustumPlanes[i][A] * x1 + this.frustumPlanes[i][B] * y2 + 
                  this.frustumPlanes[i][C] * z1 + this.frustumPlanes[i][D] > 0.0F) && 
                !(this.frustumPlanes[i][A] * x2 + this.frustumPlanes[i][B] * y2 + 
                  this.frustumPlanes[i][C] * z1 + this.frustumPlanes[i][D] > 0.0F) && 
                !(this.frustumPlanes[i][A] * x1 + this.frustumPlanes[i][B] * y1 + 
                  this.frustumPlanes[i][C] * z2 + this.frustumPlanes[i][D] > 0.0F) && 
                !(this.frustumPlanes[i][A] * x2 + this.frustumPlanes[i][B] * y1 + 
                  this.frustumPlanes[i][C] * z2 + this.frustumPlanes[i][D] > 0.0F) && 
                !(this.frustumPlanes[i][A] * x1 + this.frustumPlanes[i][B] * y2 + 
                  this.frustumPlanes[i][C] * z2 + this.frustumPlanes[i][D] > 0.0F) && 
                !(this.frustumPlanes[i][A] * x2 + this.frustumPlanes[i][B] * y2 + 
                  this.frustumPlanes[i][C] * z2 + this.frustumPlanes[i][D] > 0.0F)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if an axis-aligned bounding box is visible
     * @param aabb The axis-aligned bounding box
     * @return true if AABB is at least partially inside frustum
     */
    public boolean isVisible(AABB aabb) {
        return this.cubeInFrustum(aabb.x0, aabb.y0, aabb.z0, aabb.x1, aabb.y1, aabb.z1);
    }
}
