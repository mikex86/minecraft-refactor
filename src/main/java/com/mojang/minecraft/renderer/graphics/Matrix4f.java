package com.mojang.minecraft.renderer.graphics;

import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

/**
 * A 4x4 matrix implementation that mimics OpenGL's matrix operations.
 * This class is used to replace direct OpenGL matrix operations when moving
 * away from the fixed function pipeline.
 * This implementation uses native memory to avoid JVM memory pressure.
 */
public class Matrix4f {
    /**
     * The matrix data in column-major order (OpenGL's format).
     * Stored directly in a native buffer.
     */
    private final FloatBuffer buffer;

    /**
     * Temporary buffer for matrix operations.
     */
    private final FloatBuffer temp = BufferUtils.createFloatBuffer(16);

    /**
     * Constructs a new identity matrix.
     */
    public Matrix4f() {
        // Create a direct buffer in native memory
        buffer = BufferUtils.createFloatBuffer(16);
        setIdentity();
    }

    /**
     * Constructs a new matrix with the data from the given matrix.
     *
     * @param other The matrix to copy
     */
    public Matrix4f(Matrix4f other) {
        // Create a direct buffer in native memory
        buffer = BufferUtils.createFloatBuffer(16);
        set(other);
    }

    /**
     * Sets this matrix to the identity matrix.
     */
    public void setIdentity() {
        buffer.clear();
        
        buffer.put(0, 1.0f);
        buffer.put(1, 0.0f);
        buffer.put(2, 0.0f);
        buffer.put(3, 0.0f);

        buffer.put(4, 0.0f);
        buffer.put(5, 1.0f);
        buffer.put(6, 0.0f);
        buffer.put(7, 0.0f);

        buffer.put(8, 0.0f);
        buffer.put(9, 0.0f);
        buffer.put(10, 1.0f);
        buffer.put(11, 0.0f);

        buffer.put(12, 0.0f);
        buffer.put(13, 0.0f);
        buffer.put(14, 0.0f);
        buffer.put(15, 1.0f);
    }

    /**
     * Sets this matrix to match the given matrix.
     *
     * @param other The matrix to copy
     */
    public void set(Matrix4f other) {
        buffer.clear();
        
        // Copy the data from other's buffer to this buffer
        FloatBuffer otherBuffer = other.getBuffer();
        otherBuffer.clear(); // Reset position without changing contents
        
        for (int i = 0; i < 16; i++) {
            buffer.put(i, otherBuffer.get(i));
        }
    }

    /**
     * Multiplies this matrix with the given matrix (this = this * right).
     *
     * @param right The right-hand side matrix
     */
    public void multiply(Matrix4f right) {
        // Create a temporary buffer to hold the result
        FloatBuffer rightBuffer = right.getBuffer();
        
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                float sum = 0.0f;
                for (int i = 0; i < 4; i++) {
                    // Matrix multiplication in column-major order
                    // this[row, i] * right[i, col]
                    sum += buffer.get(i * 4 + row) * rightBuffer.get(col * 4 + i);
                }
                temp.put(col * 4 + row, sum);
            }
        }
        
        // Copy the result back to this buffer
        for (int i = 0; i < 16; i++) {
            buffer.put(i, temp.get(i));
        }
    }

    /**
     * Translates this matrix.
     *
     * @param x X translation
     * @param y Y translation
     * @param z Z translation
     */
    public void translate(float x, float y, float z) {
        // Optimized multiplication with translation matrix
        float tx = buffer.get(0) * x + buffer.get(4) * y + buffer.get(8) * z + buffer.get(12);
        float ty = buffer.get(1) * x + buffer.get(5) * y + buffer.get(9) * z + buffer.get(13);
        float tz = buffer.get(2) * x + buffer.get(6) * y + buffer.get(10) * z + buffer.get(14);
        float tw = buffer.get(3) * x + buffer.get(7) * y + buffer.get(11) * z + buffer.get(15);
        
        buffer.put(12, tx);
        buffer.put(13, ty);
        buffer.put(14, tz);
        buffer.put(15, tw);
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
        
        // Create a temporary buffer to avoid modifying values during computation
        for (int i = 0; i < 16; i++) {
            temp.put(i, buffer.get(i));
        }
        
        // Apply rotation to the matrix (column-major format)
        // Column 1 (Y components)
        buffer.put(4, temp.get(4) * cosA + temp.get(8) * sinA);
        buffer.put(5, temp.get(5) * cosA + temp.get(9) * sinA);
        buffer.put(6, temp.get(6) * cosA + temp.get(10) * sinA);
        buffer.put(7, temp.get(7) * cosA + temp.get(11) * sinA);

        // Column 2 (Z components)
        buffer.put(8, temp.get(8) * cosA - temp.get(4) * sinA);
        buffer.put(9, temp.get(9) * cosA - temp.get(5) * sinA);
        buffer.put(10, temp.get(10) * cosA - temp.get(6) * sinA);
        buffer.put(11, temp.get(11) * cosA - temp.get(7) * sinA);
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

        for (int i = 0; i < 16; i++) {
            temp.put(i, buffer.get(i));
        }
        
        // Apply rotation to the matrix (column-major format)
        // Column 0 (X components)
        buffer.put(0, temp.get(0) * cosA - temp.get(8) * sinA);
        buffer.put(1, temp.get(1) * cosA - temp.get(9) * sinA);
        buffer.put(2, temp.get(2) * cosA - temp.get(10) * sinA);
        buffer.put(3, temp.get(3) * cosA - temp.get(11) * sinA);

        // Column 2 (Z components)
        buffer.put(8, temp.get(0) * sinA + temp.get(8) * cosA);
        buffer.put(9, temp.get(1) * sinA + temp.get(9) * cosA);
        buffer.put(10, temp.get(2) * sinA + temp.get(10) * cosA);
        buffer.put(11, temp.get(3) * sinA + temp.get(11) * cosA);
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

        for (int i = 0; i < 16; i++) {
            temp.put(i, buffer.get(i));
        }
        
        // Apply rotation to the matrix (column-major format)
        // Column 0 (X components)
        buffer.put(0, temp.get(0) * cosA + temp.get(4) * sinA);
        buffer.put(1, temp.get(1) * cosA + temp.get(5) * sinA);
        buffer.put(2, temp.get(2) * cosA + temp.get(6) * sinA);
        buffer.put(3, temp.get(3) * cosA + temp.get(7) * sinA);

        // Column 1 (Y components)
        buffer.put(4, temp.get(4) * cosA - temp.get(0) * sinA);
        buffer.put(5, temp.get(5) * cosA - temp.get(1) * sinA);
        buffer.put(6, temp.get(6) * cosA - temp.get(2) * sinA);
        buffer.put(7, temp.get(7) * cosA - temp.get(3) * sinA);
    }

    /**
     * Scales this matrix.
     *
     * @param x X scale factor
     * @param y Y scale factor
     * @param z Z scale factor
     */
    public void scale(float x, float y, float z) {
        // Apply scaling (each column is multiplied by the corresponding scale factor)
        buffer.put(0, buffer.get(0) * x);
        buffer.put(1, buffer.get(1) * x);
        buffer.put(2, buffer.get(2) * x);
        buffer.put(3, buffer.get(3) * x);
        
        buffer.put(4, buffer.get(4) * y);
        buffer.put(5, buffer.get(5) * y);
        buffer.put(6, buffer.get(6) * y);
        buffer.put(7, buffer.get(7) * y);
        
        buffer.put(8, buffer.get(8) * z);
        buffer.put(9, buffer.get(9) * z);
        buffer.put(10, buffer.get(10) * z);
        buffer.put(11, buffer.get(11) * z);
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
        // Fill buffer with zeros
        for (int i = 0; i < 16; i++) {
            buffer.put(i, 0.0f);
        }
        
        float yScale = (float) (1.0 / Math.tan(Math.toRadians(fov / 2.0)));
        float xScale = yScale / aspect;
        float frustumLength = farPlane - nearPlane;
        
        buffer.put(0, xScale);
        buffer.put(5, yScale);
        buffer.put(10, -((farPlane + nearPlane) / frustumLength));
        buffer.put(11, -1);
        buffer.put(14, -((2 * nearPlane * farPlane) / frustumLength));
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
        
        buffer.put(0, 2 / (right - left));
        buffer.put(5, 2 / (top - bottom));
        buffer.put(10, -2 / (far - near));
        buffer.put(12, -(right + left) / (right - left));
        buffer.put(13, -(top + bottom) / (top - bottom));
        buffer.put(14, -(far + near) / (far - near));
    }

    /**
     * Gets the matrix data as a FloatBuffer.
     * This returns a direct reference to the internal buffer, not a copy.
     *
     * @return A FloatBuffer containing the matrix data
     */
    public FloatBuffer getBuffer() {
        buffer.rewind();
        return buffer;
    }
} 