package com.mojang.minecraft.renderer.annotation;

public @interface ThreadOnly {

    /**
     * The thread kind that is allowed to access the annotated method or field.
     * <p>
     * This is used to enforce thread safety in the codebase.
     */
    ThreadKind value();
}
