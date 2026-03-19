package com.mojang.minecraft.renderer.shader;

import com.mojang.minecraft.renderer.Disposable;

public interface IShader extends Disposable {

    /**
     * Uses this shader program.
     */
    void use();

    /**
     * Stops using this shader program.
     */
    void detach();

}
