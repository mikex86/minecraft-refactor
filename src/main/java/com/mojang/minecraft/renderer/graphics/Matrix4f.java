package com.mojang.minecraft.renderer.graphics;

import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.util.Arrays;

/**
 * A 4x4 matrix implementation that mimics OpenGL's matrix operations.
 * This class is used to replace direct OpenGL matrix operations when moving
 * away from the fixed function pipeline.
 */
public class Matrix4f {
    /**
     * The matrix data in column-major order (OpenGL's format).
     */
    private final float[] data = new float[16];

    /**
     * Constructs a new identity matrix.
     */
    public Matrix4f() {
        setIdentity();
    }

    /**
     * Constructs a new matrix with the data from the given matrix.
     *
     * @param other The matrix to copy
     */
    public Matrix4f(Matrix4f other) {
        set(other);
    }

    /**
     * Sets this matrix to the identity matrix.
     */
    public void setIdentity() {
        data[0] = 1.0f;
        data[1] = 0.0f;
        data[2] = 0.0f;
        data[3] = 0.0f;

        data[4] = 0.0f;
        data[5] = 1.0f;
        data[6] = 0.0f;
        data[7] = 0.0f;

        data[8] = 0.0f;
        data[9] = 0.0f;
        data[10] = 1.0f;
        data[11] = 0.0f;

        data[12] = 0.0f;
        data[13] = 0.0f;
        data[14] = 0.0f;
        data[15] = 1.0f;
    }

    /**
     * Sets this matrix to match the given matrix.
     *
     * @param other The matrix to copy
     */
    public void set(Matrix4f other) {
        System.arraycopy(other.data, 0, data, 0, 16);
    }

    /**
     * Multiplies this matrix with the given matrix (this = this * right).
     *
     * @param right The right-hand side matrix
     */
    public void multiply(Matrix4f right) {
        float[] result = new float[16];
        
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                float sum = 0.0f;
                for (int i = 0; i < 4; i++) {
                    // Matrix multiplication in column-major order
                    // this[row, i] * right[i, col]
                    sum += data[i * 4 + row] * right.data[col * 4 + i];
                }
                result[col * 4 + row] = sum;
            }
        }
        
        System.arraycopy(result, 0, data, 0, 16);
    }

    /**
     * Translates this matrix.
     *
     * @param x X translation
     * @param y Y translation
     * @param z Z translation
     */
    public void translate(float x, float y, float z) {
        // Translation matrix
        // 1 0 0 x
        // 0 1 0 y
        // 0 0 1 z
        // 0 0 0 1
        
        // Column 0: data[0], data[1], data[2], data[3]
        // Column 1: data[4], data[5], data[6], data[7]
        // Column 2: data[8], data[9], data[10], data[11]
        // Column 3: data[12], data[13], data[14], data[15]
        
        // Optimized multiplication with translation matrix
        data[12] = data[0] * x + data[4] * y + data[8] * z + data[12];
        data[13] = data[1] * x + data[5] * y + data[9] * z + data[13];
        data[14] = data[2] * x + data[6] * y + data[10] * z + data[14];
        data[15] = data[3] * x + data[7] * y + data[11] * z + data[15];
    }

    /**
     * Rotates this matrix around the X axis.
     *
     * @param angle Angle in degrees
     */
    public void rotateX(float angle) {
        float angleRad = (float) Math.toRadians(angle);
        float cosA = (float) Math.cos(angleRad);
        float sinA = (float) Math.sin(angleRad);
        
        // Rotation matrix for X axis
        // 1  0    0    0
        // 0  cosA -sinA 0
        // 0  sinA cosA  0
        // 0  0    0    1
        
        // Create a temporary matrix to avoid modifying values during computation
        float[] temp = new float[16];
        System.arraycopy(data, 0, temp, 0, 16);
        
        // Apply rotation to the matrix (column-major format)
        // Column 1 (Y components)
        data[4] = temp[4] * cosA + temp[8] * sinA;
        data[5] = temp[5] * cosA + temp[9] * sinA;
        data[6] = temp[6] * cosA + temp[10] * sinA;
        data[7] = temp[7] * cosA + temp[11] * sinA;

        // Column 2 (Z components)
        data[8] = temp[8] * cosA - temp[4] * sinA;
        data[9] = temp[9] * cosA - temp[5] * sinA;
        data[10] = temp[10] * cosA - temp[6] * sinA;
        data[11] = temp[11] * cosA - temp[7] * sinA;
    }

    /**
     * Rotates this matrix around the Y axis.
     *
     * @param angle Angle in degrees
     */
    public void rotateY(float angle) {
        float angleRad = (float) Math.toRadians(angle);
        float cosA = (float) Math.cos(angleRad);
        float sinA = (float) Math.sin(angleRad);
        
        // Rotation matrix for Y axis
        // cosA  0  sinA  0
        // 0     1  0     0
        // -sinA 0  cosA  0
        // 0     0  0     1
        
        // Create a temporary matrix to avoid modifying values during computation
        float[] temp = new float[16];
        System.arraycopy(data, 0, temp, 0, 16);
        
        // Apply rotation to the matrix (column-major format)
        // Column 0 (X components)
        data[0] = temp[0] * cosA - temp[8] * sinA;
        data[1] = temp[1] * cosA - temp[9] * sinA;
        data[2] = temp[2] * cosA - temp[10] * sinA;
        data[3] = temp[3] * cosA - temp[11] * sinA;

        // Column 2 (Z components)
        data[8] = temp[0] * sinA + temp[8] * cosA;
        data[9] = temp[1] * sinA + temp[9] * cosA;
        data[10] = temp[2] * sinA + temp[10] * cosA;
        data[11] = temp[3] * sinA + temp[11] * cosA;
    }

    /**
     * Rotates this matrix around the Z axis.
     *
     * @param angle Angle in degrees
     */
    public void rotateZ(float angle) {
        float angleRad = (float) Math.toRadians(angle);
        float cosA = (float) Math.cos(angleRad);
        float sinA = (float) Math.sin(angleRad);
        
        // Rotation matrix for Z axis
        // cosA -sinA 0  0
        // sinA cosA  0  0
        // 0    0     1  0
        // 0    0     0  1
        
        // Create a temporary matrix to avoid modifying values during computation
        float[] temp = new float[16];
        System.arraycopy(data, 0, temp, 0, 16);
        
        // Apply rotation to the matrix (column-major format)
        // Column 0 (X components)
        data[0] = temp[0] * cosA + temp[4] * sinA;
        data[1] = temp[1] * cosA + temp[5] * sinA;
        data[2] = temp[2] * cosA + temp[6] * sinA;
        data[3] = temp[3] * cosA + temp[7] * sinA;

        // Column 1 (Y components)
        data[4] = temp[4] * cosA - temp[0] * sinA;
        data[5] = temp[5] * cosA - temp[1] * sinA;
        data[6] = temp[6] * cosA - temp[2] * sinA;
        data[7] = temp[7] * cosA - temp[3] * sinA;
    }

    /**
     * Scales this matrix.
     *
     * @param x X scale factor
     * @param y Y scale factor
     * @param z Z scale factor
     */
    public void scale(float x, float y, float z) {
        // Scale matrix
        // x 0 0 0
        // 0 y 0 0
        // 0 0 z 0
        // 0 0 0 1
        
        // Apply scaling (each column is multiplied by the corresponding scale factor)
        data[0] *= x;
        data[1] *= x;
        data[2] *= x;
        data[3] *= x;
        
        data[4] *= y;
        data[5] *= y;
        data[6] *= y;
        data[7] *= y;
        
        data[8] *= z;
        data[9] *= z;
        data[10] *= z;
        data[11] *= z;
    }

    /**
     * Sets this matrix to a perspective projection matrix.
     *
     * @param fov       Field of view angle in degrees
     * @param aspect    Aspect ratio (width / height)
     * @param nearPlane Distance to near clipping plane
     * @param farPlane  Distance to far clipping plane
     */
    public void setPerspective(float fov, float aspect, float nearPlane, float farPlane) {
        Arrays.fill(data, 0);
        float yScale = (float) (1.0 / Math.tan(Math.toRadians(fov / 2.0)));
        float xScale = yScale / aspect;
        float frustumLength = farPlane - nearPlane;
        
        data[0] = xScale;
        data[5] = yScale;
        data[10] = -((farPlane + nearPlane) / frustumLength);
        data[11] = -1;
        data[14] = -((2 * nearPlane * farPlane) / frustumLength);
    }

    /**
     * Sets this matrix to an orthographic projection matrix.
     *
     * @param left   Left coordinate
     * @param right  Right coordinate
     * @param bottom Bottom coordinate
     * @param top    Top coordinate
     * @param near   Near clipping plane
     * @param far    Far clipping plane
     */
    public void setOrthographic(float left, float right, float bottom, float top, float near, float far) {
        setIdentity();
        
        data[0] = 2 / (right - left);
        data[5] = 2 / (top - bottom);
        data[10] = -2 / (far - near);
        data[12] = -(right + left) / (right - left);
        data[13] = -(top + bottom) / (top - bottom);
        data[14] = -(far + near) / (far - near);
    }

    /**
     * Gets the matrix data as a FloatBuffer.
     *
     * @return A FloatBuffer containing the matrix data
     */
    public FloatBuffer getBuffer() {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
        buffer.put(data);
        buffer.flip();
        return buffer;
    }

    /**
     * Gets the internal data array of this matrix.
     *
     * @return The matrix data in column-major order
     */
    public float[] getData() {
        return data;
    }
} 