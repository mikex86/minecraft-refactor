package com.mojang.minecraft.renderer.graphics;

import java.nio.FloatBuffer;

/**
 * Implementation of a matrix stack that emulates OpenGL's matrix stack functionality.
 * This class is used to replace the fixed function pipeline matrix operations.
 * Each matrix mode (MODELVIEW, PROJECTION) has its own stack with pre-allocated matrices.
 */
public class MatrixStack {

    /**
     * Constant for the stack depth
     */
    private static final int STACK_DEPTH = 16;
    
    // Pre-allocated matrix arrays for each stack
    private final Matrix4f[] modelViewMatrices;
    private final Matrix4f[] projectionMatrices;
    
    // Stack pointers (indices to current position in each stack)
    private int modelViewStackPointer;
    private int projectionStackPointer;
    
    // Current matrices (references to the top of each stack)
    private Matrix4f currentModelView;
    private Matrix4f currentProjection;
    
    // Current stack state
    private Matrix4f[] currentStack;
    private int currentStackPointer;
    private Matrix4f currentMatrix;
    
    /**
     * Creates a new matrix stack with pre-allocated identity matrices for all modes.
     */
    public MatrixStack() {
        // Initialize model view stack
        modelViewMatrices = new Matrix4f[STACK_DEPTH];
        for (int i = 0; i < STACK_DEPTH; i++) {
            modelViewMatrices[i] = new Matrix4f();
        }
        modelViewStackPointer = 0;
        
        // Initialize projection stack
        projectionMatrices = new Matrix4f[STACK_DEPTH];
        for (int i = 0; i < STACK_DEPTH; i++) {
            projectionMatrices[i] = new Matrix4f();
        }
        projectionStackPointer = 0;
        
        // Set current pointers
        currentModelView = modelViewMatrices[0];
        currentProjection = projectionMatrices[0];
        
        // Set current stack to model view by default (OpenGL default)
        currentStack = modelViewMatrices;
        currentStackPointer = modelViewStackPointer;
        currentMatrix = currentModelView;
    }
    
    /**
     * Sets the current matrix mode.
     * 
     * @param mode The matrix mode to set
     */
    public void setMatrixMode(GraphicsAPI.MatrixMode mode) {
        switch (mode) {
            case MODELVIEW:
                currentStack = modelViewMatrices;
                currentStackPointer = modelViewStackPointer;
                currentMatrix = currentModelView;
                break;
            case PROJECTION:
                currentStack = projectionMatrices;
                currentStackPointer = projectionStackPointer;
                currentMatrix = currentProjection;
                break;
            default:
                throw new IllegalArgumentException("Unsupported matrix mode: " + mode);
        }
    }
    
    /**
     * Pushes the current matrix onto the stack by copying its values to the next matrix in the stack.
     * 
     * @throws IllegalStateException if the stack is full
     */
    public void pushMatrix() {
        if (currentStackPointer >= STACK_DEPTH - 1) {
            throw new IllegalStateException("Matrix stack overflow");
        }
        
        // Copy the current matrix to the next position in the stack
        Matrix4f source = currentStack[currentStackPointer];
        Matrix4f target = currentStack[currentStackPointer + 1];
        target.set(source);
        
        // Increment stack pointer
        currentStackPointer++;
        
        // Update current matrix and stack pointers
        updateCurrentMatrixReferences();
    }
    
    /**
     * Pops the top matrix from the stack.
     * 
     * @throws IllegalStateException if the stack is empty
     */
    public void popMatrix() {
        if (currentStackPointer <= 0) {
            throw new IllegalStateException("Matrix stack underflow");
        }
        
        // Decrement stack pointer
        currentStackPointer--;
        
        // Update current matrix and stack pointers
        updateCurrentMatrixReferences();
    }
    
    /**
     * Updates the current matrix references based on the current stack pointers.
     */
    private void updateCurrentMatrixReferences() {
        if (currentStack == modelViewMatrices) {
            modelViewStackPointer = currentStackPointer;
            currentModelView = modelViewMatrices[modelViewStackPointer];
            currentMatrix = currentModelView;
        } else if (currentStack == projectionMatrices) {
            projectionStackPointer = currentStackPointer;
            currentProjection = projectionMatrices[projectionStackPointer];
            currentMatrix = currentProjection;
        }
    }
    
    /**
     * Sets the current matrix to the identity matrix.
     */
    public void loadIdentity() {
        currentMatrix.setIdentity();
    }
    
    /**
     * Translates the current matrix.
     * 
     * @param x X translation
     * @param y Y translation
     * @param z Z translation
     */
    public void translate(float x, float y, float z) {
        currentMatrix.translate(x, y, z);
    }
    
    /**
     * Rotates the current matrix around the X axis.
     * 
     * @param angle Angle in degrees
     */
    public void rotateX(float angle) {
        currentMatrix.rotateX(angle);
    }
    
    /**
     * Rotates the current matrix around the Y axis.
     * 
     * @param angle Angle in degrees
     */
    public void rotateY(float angle) {
        currentMatrix.rotateY(angle);
    }
    
    /**
     * Rotates the current matrix around the Z axis.
     * 
     * @param angle Angle in degrees
     */
    public void rotateZ(float angle) {
        currentMatrix.rotateZ(angle);
    }
    
    /**
     * Scales the current matrix.
     * 
     * @param x X scale factor
     * @param y Y scale factor
     * @param z Z scale factor
     */
    public void scale(float x, float y, float z) {
        currentMatrix.scale(x, y, z);
    }
    
    /**
     * Sets up a perspective projection matrix.
     * 
     * @param fov       Field of view angle in degrees
     * @param aspect    Aspect ratio (width / height)
     * @param nearPlane Distance to near clipping plane
     * @param farPlane  Distance to far clipping plane
     */
    public void setPerspective(float fov, float aspect, float nearPlane, float farPlane) {
        currentProjection.setPerspective(fov, aspect, nearPlane, farPlane);
    }
    
    /**
     * Sets up an orthographic projection matrix.
     * 
     * @param left    Left coordinate
     * @param right   Right coordinate
     * @param bottom  Bottom coordinate
     * @param top     Top coordinate
     * @param near    Near clipping plane
     * @param far     Far clipping plane
     */
    public void setOrthographic(float left, float right, float bottom, float top, float near, float far) {
        currentProjection.setOrthographic(left, right, bottom, top, near, far);
    }
    
    /**
     * Gets the current model-view matrix.
     * 
     * @return The model-view matrix
     */
    public Matrix4f getModelViewMatrix() {
        return currentModelView;
    }
    
    /**
     * Gets the current projection matrix.
     * 
     * @return The projection matrix
     */
    public Matrix4f getProjectionMatrix() {
        return currentProjection;
    }
    
    /**
     * Gets the current matrix (depends on the current matrix mode).
     * 
     * @return The current matrix
     */
    public Matrix4f getCurrentMatrix() {
        return currentMatrix;
    }

    /**
     * Gets the current model-view matrix as a FloatBuffer, ready for use with OpenGL.
     * 
     * @return A FloatBuffer containing the model-view matrix data
     */
    public FloatBuffer getModelViewBuffer() {
        return currentModelView.getBuffer();
    }
    
    /**
     * Gets the current projection matrix as a FloatBuffer, ready for use with OpenGL.
     * 
     * @return A FloatBuffer containing the projection matrix data
     */
    public FloatBuffer getProjectionBuffer() {
        return currentProjection.getBuffer();
    }
} 