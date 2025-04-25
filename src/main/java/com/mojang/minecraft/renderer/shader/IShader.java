package com.mojang.minecraft.renderer.shader;

import com.mojang.minecraft.renderer.Disposable;

import java.nio.FloatBuffer;

public interface IShader extends Disposable {

    /**
     * Uses this shader program.
     */
    void use();

    /**
     * Stops using this shader program.
     */
    void detach();

    /**
     * Sets a boolean uniform.
     *
     * @param name The name of the uniform
     * @param value The value to set
     */
    void setUniform(String name, boolean value);

    /**
     * Sets an integer uniform.
     *
     * @param name The name of the uniform
     * @param value The value to set
     */
    void setUniform(String name, int value);

    /**
     * Sets a float uniform.
     *
     * @param name The name of the uniform
     * @param value The value to set
     */
    void setUniform(String name, float value);

    /**
     * Sets a vec2 uniform.
     *
     * @param name The name of the uniform
     * @param x The x value
     * @param y The y value
     */
    void setUniform(String name, float x, float y);

    /**
     * Sets a vec3 uniform.
     *
     * @param name The name of the uniform
     * @param x The x value
     * @param y The y value
     * @param z The z value
     */
    void setUniform(String name, float x, float y, float z);

    /**
     * Sets a vec4 uniform.
     *
     * @param name The name of the uniform
     * @param x The x value
     * @param y The y value
     * @param z The z value
     * @param w The w value
     */
    void setUniform(String name, float x, float y, float z, float w);

    /**
     * Sets a vec4 uniform from a float buffer.
     *
     * @param name The name of the uniform
     * @param buffer The buffer containing the values
     */
    void setUniform4fv(String name, FloatBuffer buffer);

    /**
     * Sets a matrix4 uniform.
     *
     * @param name The name of the uniform
     * @param matrix The matrix
     */
    void setUniformMatrix4fv(String name, FloatBuffer matrix);
}
