package com.mojang.minecraft.renderer.graphics;

/**
 * Common enumerations used in the graphics API.
 */
public class GraphicsEnums {
    /**
     * Defines how a buffer should be used.
     */
    public enum BufferUsage {
        /**
         * Buffer data will be modified once and used many times.
         */
        STATIC,

        /**
         * Buffer data will be modified many times and used many times.
         */
        DYNAMIC,

        /**
         * Buffer data will be modified once and used at most a few times.
         */
        STREAM
    }

    /**
     * Defines how to compare values during depth or stencil tests.
     */
    public enum CompareFunc {
        NEVER,
        LESS,
        EQUAL,
        LESS_EQUAL,
        GREATER,
        NOT_EQUAL,
        GREATER_EQUAL,
        ALWAYS
    }

    /**
     * Defines how triangles should be culled.
     */
    public enum CullMode {
        NONE,
        FRONT,
        BACK
    }

    /**
     * Defines how polygons should be rasterized.
     */
    public enum FillMode {
        POINT,
        WIREFRAME,
        SOLID
    }

    /**
     * Defines how to blend pixels.
     */
    public enum BlendFactor {
        ZERO,
        ONE,
        SRC_COLOR,
        ONE_MINUS_SRC_COLOR,
        DST_COLOR,
        ONE_MINUS_DST_COLOR,
        SRC_ALPHA,
        ONE_MINUS_SRC_ALPHA,
        DST_ALPHA,
        ONE_MINUS_DST_ALPHA,
        CONSTANT_COLOR,
        ONE_MINUS_CONSTANT_COLOR,
        CONSTANT_ALPHA,
        ONE_MINUS_CONSTANT_ALPHA,
        SRC_ALPHA_SATURATE
    }

    /**
     * Defines the primitive type to render.
     */
    public enum PrimitiveType {
        POINTS,
        LINES,
        LINE_STRIP,
        TRIANGLES,
        TRIANGLE_STRIP,
        TRIANGLE_FAN,
        QUADS
    }

    /**
     * Defines texture formats.
     */
    public enum TextureFormat {
        R8,
        RG8,
        RGB8,
        RGBA8,
        R16F,
        RG16F,
        RGB16F,
        RGBA16F,
        R32F,
        RG32F,
        RGB32F,
        RGBA32F,
        DEPTH16,
        DEPTH24,
        DEPTH32F
    }
}