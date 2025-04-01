package com.mojang.minecraft.renderer;

import java.nio.FloatBuffer;

import org.lwjgl.BufferUtils;

import static org.lwjgl.opengl.GL11.*;

/**
 * Matrix utility methods to replace functionality from LWJGL 2's GLU library
 */
public class MatrixUtils {

    /**
     * Creates a perspective projection matrix, similar to GLU.gluPerspective
     *
     * @param fovy Field of view in degrees
     * @param aspect Aspect ratio (width/height)
     * @param zNear Near clipping plane
     * @param zFar Far clipping plane
     */
    public static void perspective(float fovy, float aspect, float zNear, float zFar) {
        // Convert FOV from degrees to radians
        float radians = (float) (fovy * Math.PI / 180.0);

        // Calculate the frustum dimensions
        float tanHalfFovy = (float) Math.tan(radians / 2.0);
        float ymax = zNear * tanHalfFovy;
        float ymin = -ymax;
        float xmin = ymin * aspect;
        float xmax = ymax * aspect;

        // Create the perspective matrix with proper scaling
        frustum(xmin, xmax, ymin, ymax, zNear, zFar);
    }

    /**
     * Creates a frustum projection matrix, similar to GLU.gluFrustum
     *
     * @param left Left clipping plane
     * @param right Right clipping plane
     * @param bottom Bottom clipping plane
     * @param top Top clipping plane
     * @param zNear Near clipping plane
     * @param zFar Far clipping plane
     */
    public static void frustum(float left, float right, float bottom, float top, float zNear, float zFar) {
        // Check for degenerate frustum
        if (left == right || bottom == top || zNear == zFar || zNear <= 0.0f || zFar <= 0.0f) {
            System.err.println("WARNING: Invalid frustum parameters!");
            return;
        }

        // Calculate the matrix coefficients
        float a = (right + left) / (right - left);
        float b = (top + bottom) / (top - bottom);
        float c = -(zFar + zNear) / (zFar - zNear);
        float d = -(2.0f * zFar * zNear) / (zFar - zNear);

        // Create and fill the matrix (column-major format for OpenGL)
        FloatBuffer matrix = BufferUtils.createFloatBuffer(16);
        matrix.put(new float[] {
            (2.0f * zNear) / (right - left), 0, 0, 0,
            0, (2.0f * zNear) / (top - bottom), 0, 0,
            a, b, c, -1,
            0, 0, d, 0
        });
        matrix.flip();

        // Apply the matrix
        glMultMatrixf(matrix);
    }

    /**
     * Creates a look-at view matrix, similar to GLU.gluLookAt
     *
     * @param eyeX Eye X position
     * @param eyeY Eye Y position
     * @param eyeZ Eye Z position
     * @param centerX Center X position
     * @param centerY Center Y position
     * @param centerZ Center Z position
     * @param upX Up vector X component
     * @param upY Up vector Y component
     * @param upZ Up vector Z component
     */
    public static void lookAt(float eyeX, float eyeY, float eyeZ,
                              float centerX, float centerY, float centerZ,
                              float upX, float upY, float upZ) {
        // Calculate forward vector (negative z-axis in OpenGL)
        float forwardX = centerX - eyeX;
        float forwardY = centerY - eyeY;
        float forwardZ = centerZ - eyeZ;

        // Normalize the forward vector
        float forwardLength = (float) Math.sqrt(forwardX * forwardX + forwardY * forwardY + forwardZ * forwardZ);
        if (forwardLength < 0.00001f) {
            System.err.println("WARNING: lookAt has eye point equal to center point!");
            return;
        }

        forwardX /= forwardLength;
        forwardY /= forwardLength;
        forwardZ /= forwardLength;

        // Calculate side vector (right vector, x-axis) using cross product of forward and up
        float sideX = forwardY * upZ - forwardZ * upY;
        float sideY = forwardZ * upX - forwardX * upZ;
        float sideZ = forwardX * upY - forwardY * upX;

        // Normalize the side vector
        float sideLength = (float) Math.sqrt(sideX * sideX + sideY * sideY + sideZ * sideZ);
        if (sideLength < 0.00001f) {
            // If the side vector is too small, the up vector is parallel to the forward vector
            // We need to generate a new up vector that's perpendicular to forward
            if (Math.abs(forwardY) < 0.9f) {
                // Create a new up vector using world y-axis
                sideX = forwardY;
                sideY = -forwardX;
                sideZ = 0.0f;
            } else {
                // Use world z-axis instead if forward is close to y-axis
                sideX = forwardZ;
                sideY = 0.0f;
                sideZ = -forwardX;
            }
            sideLength = (float) Math.sqrt(sideX * sideX + sideY * sideY + sideZ * sideZ);
        }

        sideX /= sideLength;
        sideY /= sideLength;
        sideZ /= sideLength;

        // Calculate the new up vector using cross product of side and forward
        float newUpX = sideY * forwardZ - sideZ * forwardY;
        float newUpY = sideZ * forwardX - sideX * forwardZ;
        float newUpZ = sideX * forwardY - sideY * forwardX;

        // Create view matrix in column-major order
        FloatBuffer matrix = BufferUtils.createFloatBuffer(16);
        matrix.put(new float[] {
            sideX, newUpX, -forwardX, 0,
            sideY, newUpY, -forwardY, 0,
            sideZ, newUpZ, -forwardZ, 0,
            -(sideX * eyeX + sideY * eyeY + sideZ * eyeZ),
            -(newUpX * eyeX + newUpY * eyeY + newUpZ * eyeZ),
            forwardX * eyeX + forwardY * eyeY + forwardZ * eyeZ,
            1
        });
        matrix.flip();

        glMultMatrixf(matrix);
    }

    /**
     * Creates a pick matrix, similar to GLU.gluPickMatrix
     *
     * @param x Center X coordinate
     * @param y Center Y coordinate
     * @param width Width of the picking region
     * @param height Height of the picking region
     * @param viewport Viewport coordinates (x, y, width, height)
     */
    public static void pickMatrix(float x, float y, float width, float height, int[] viewport) {
        if (width <= 0 || height <= 0) {
            return;
        }

        // Translate and scale the picking region to the viewport
        glTranslatef(
            (viewport[2] - 2 * (x - viewport[0])) / width,
            (viewport[3] - 2 * (y - viewport[1])) / height,
            0
        );
        glScalef(viewport[2] / width, viewport[3] / height, 1.0f);
    }
}