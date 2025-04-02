package com.mojang.minecraft.renderer.graphics;

import java.nio.FloatBuffer;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Implementation of a matrix stack that emulates OpenGL's matrix stack functionality.
 * This class is used to replace the fixed function pipeline matrix operations.
 * Each matrix mode (MODELVIEW, PROJECTION, TEXTURE) has its own stack.
 */
public class MatrixStack {

    /**
     * Constant for the maximum stack depth (as per OpenGL specs)
     */
    private static final int MAX_STACK_DEPTH = 32;
    
    private final Deque<Matrix4f> modelViewStack = new ArrayDeque<>();
    private final Deque<Matrix4f> projectionStack = new ArrayDeque<>();
    private final Deque<Matrix4f> textureStack = new ArrayDeque<>();
    
    private Matrix4f currentModelView;
    private Matrix4f currentProjection;
    
    private Deque<Matrix4f> currentStack;
    private Matrix4f currentMatrix;
    
    /**
     * Creates a new matrix stack with identity matrices for all modes.
     */
    public MatrixStack() {
        // Initialize all stacks with identity matrices
        currentModelView = new Matrix4f();
        currentProjection = new Matrix4f();
        
        // Set current stack to model view by default (OpenGL default)
        currentStack = modelViewStack;
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
                currentStack = modelViewStack;
                currentMatrix = currentModelView;
                break;
            case PROJECTION:
                currentStack = projectionStack;
                currentMatrix = currentProjection;
                break;
            default:
                throw new IllegalArgumentException("Unsupported matrix mode: " + mode);
        }
    }
    
    /**
     * Pushes the current matrix onto the stack.
     * 
     * @throws IllegalStateException if the stack is full
     */
    public void pushMatrix() {
        if (currentStack.size() >= MAX_STACK_DEPTH) {
            throw new IllegalStateException("Matrix stack overflow");
        }
        
        // Push a copy of the current matrix onto the stack
        Matrix4f copy = new Matrix4f(currentMatrix);
        currentStack.push(copy);
    }
    
    /**
     * Pops the top matrix from the stack and makes it the current matrix.
     * 
     * @throws IllegalStateException if the stack is empty
     */
    public void popMatrix() {
        if (currentStack.isEmpty()) {
            throw new IllegalStateException("Matrix stack underflow");
        }
        
        // Pop the top matrix from the stack
        Matrix4f popped = currentStack.pop();
        
        // Update the correct current matrix depending on the current mode
        if (currentStack == modelViewStack) {
            currentModelView = popped;
            currentMatrix = currentModelView;
        } else if (currentStack == projectionStack) {
            currentProjection = popped;
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