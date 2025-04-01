package com.mojang.minecraft.gui;

import com.mojang.minecraft.renderer.Tesselator;
import com.mojang.minecraft.renderer.Textures;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Objects;
import javax.imageio.ImageIO;

import static org.lwjgl.opengl.GL11.*;

/**
 * Handles text rendering in the Minecraft GUI.
 * Supports basic character rendering and colored text with shadow effects.
 */
public class Font {
    // Constants for font texture handling
    private static final int CHAR_WIDTH = 8;
    private static final int CHAR_HEIGHT = 8;
    private static final int CHARS_PER_ROW = 16;
    private static final int TEXTURE_SIZE = 128;
    private static final int SPACE_WIDTH = 4;
    private static final int TOTAL_CHARS = 128;
    private static final int COLOR_INDICES = 16;
    private static final String COLOR_CHARS = "0123456789abcdef";
    private static final char COLOR_CHAR = '&';

    // Bitmap font specific constants
    private static final int PIXEL_THRESHOLD = 128;
    private static final int DEFAULT_TEXTURE_PARAM = 9728; // GL_NEAREST
    private static final int COLOR_DARKEN_MASK = 16579836; // Used for shadow effect
    private static final float ALPHA_THRESHOLD = 0.01F;

    // Character width mapping for proportional font rendering
    private final int[] charWidths = new int[256];
    private int fontTexture = 0;

    /**
     * Creates a new Font instance from a texture resource.
     *
     * @param name     The path to the font texture resource
     * @param textures The textures manager to register the font texture with
     */
    public Font(String name, Textures textures) {
        BufferedImage fontImage;
        try {
            fontImage = ImageIO.read(Objects.requireNonNull(Textures.class.getResourceAsStream(name)));
        } catch (IOException e) {
            throw new RuntimeException("Failed to load font texture: " + name, e);
        }

        // Read the font bitmap to determine character widths
        int imageWidth = fontImage.getWidth();
        int imageHeight = fontImage.getHeight();
        int[] rawPixels = new int[imageWidth * imageHeight];
        fontImage.getRGB(0, 0, imageWidth, imageHeight, rawPixels, 0, imageWidth);

        // Calculate width of each character by detecting non-empty columns
        calculateCharacterWidths(rawPixels, imageWidth);

        // Load the font texture
        this.fontTexture = textures.loadTexture(name, DEFAULT_TEXTURE_PARAM);
    }

    /**
     * Calculates the width of each character in the font by analyzing the bitmap.
     *
     * @param rawPixels  Pixel data from the font texture
     * @param imageWidth Width of the font texture
     */
    private void calculateCharacterWidths(int[] rawPixels, int imageWidth) {
        for (int i = 0; i < TOTAL_CHARS; ++i) {
            int charColumn = i % CHARS_PER_ROW;
            int charRow = i / CHARS_PER_ROW;
            int charWidth = 0;

            // Analyze each column of the character to determine its width
            for (boolean emptyColumn = false; charWidth < CHAR_WIDTH && !emptyColumn; ++charWidth) {
                int xPixel = charColumn * CHAR_WIDTH + charWidth;
                emptyColumn = true;

                // Check if column has any visible pixels
                for (int y = 0; y < CHAR_HEIGHT && emptyColumn; ++y) {
                    int yPixel = (charRow * CHAR_HEIGHT + y) * imageWidth;
                    int pixel = rawPixels[xPixel + yPixel] & 0xFF;
                    if (pixel > PIXEL_THRESHOLD) {
                        emptyColumn = false;
                    }
                }
            }

            // Special case for space character
            if (i == 32) {
                charWidth = SPACE_WIDTH;
            }

            this.charWidths[i] = charWidth;
        }
    }

    /**
     * Draws text with a drop shadow.
     *
     * @param text  The text to draw
     * @param x     The x position
     * @param y     The y position
     * @param color The color of the text (RGB format)
     */
    public void drawShadow(String text, int x, int y, int color) {
        this.draw(text, x + 1, y + 1, color, true);
        this.draw(text, x, y, color);
    }

    /**
     * Draws text without a shadow.
     *
     * @param text  The text to draw
     * @param x     The x position
     * @param y     The y position
     * @param color The color of the text (RGB format)
     */
    public void draw(String text, int x, int y, int color) {
        this.draw(text, x, y, color, false);
    }

    /**
     * Internal method to draw text with optional shadow effect.
     *
     * @param text   The text to draw
     * @param x      The x position
     * @param y      The y position
     * @param color  The color of the text (RGB format)
     * @param darken Whether to darken the color (for shadow effect)
     */
    public void draw(String text, int x, int y, int color, boolean darken) {
        char[] chars = text.toCharArray();
        if (darken) {
            color = (color & COLOR_DARKEN_MASK) >> 2;
        }

        // Set up OpenGL for texture rendering
        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, this.fontTexture);

        // Enable alpha testing to handle transparency properly
        glEnable(GL_ALPHA_TEST);
        glAlphaFunc(GL_GREATER, ALPHA_THRESHOLD);

        // Initialize the tessellator for rendering
        Tesselator tessellator = Tesselator.instance;
        tessellator.init();
        tessellator.color(color);
        int xOffset = 0;

        // Draw each character
        for (int i = 0; i < chars.length; ++i) {
            // Handle color codes (format: &[0-9a-f])
            if (chars[i] == COLOR_CHAR && i + 1 < chars.length) {
                int colorIndex = COLOR_CHARS.indexOf(chars[i + 1]);
                if (colorIndex >= 0) {
                    // Extract RGB components from the color index
                    int brightnessFactor = (colorIndex & 8) * 8;
                    int blue = (colorIndex & 1) * 191 + brightnessFactor;
                    int green = ((colorIndex & 2) >> 1) * 191 + brightnessFactor;
                    int red = ((colorIndex & 4) >> 2) * 191 + brightnessFactor;

                    // Combine RGB components into a single color value
                    color = red << 16 | green << 8 | blue;

                    // Apply shadow darkening if needed
                    if (darken) {
                        color = (color & COLOR_DARKEN_MASK) >> 2;
                    }

                    // Update the tessellator color
                    tessellator.color(color);

                    // Skip the color code characters
                    i += 2;

                    // Check if we've reached the end of the string
                    if (i >= chars.length) {
                        break;
                    }
                }
            }

            // Calculate texture coordinates for the character
            int textureX = chars[i] % CHARS_PER_ROW * CHAR_WIDTH;
            int textureY = chars[i] / CHARS_PER_ROW * CHAR_HEIGHT;

            // Define the character quad with texture coordinates
            tessellator.vertexUV(
                    (float) (x + xOffset),
                    (float) (y + CHAR_HEIGHT),
                    0.0F,
                    (float) textureX / TEXTURE_SIZE,
                    (float) (textureY + CHAR_HEIGHT) / TEXTURE_SIZE);
            tessellator.vertexUV(
                    (float) (x + xOffset + CHAR_WIDTH),
                    (float) (y + CHAR_HEIGHT),
                    0.0F,
                    (float) (textureX + CHAR_WIDTH) / TEXTURE_SIZE,
                    (float) (textureY + CHAR_HEIGHT) / TEXTURE_SIZE);
            tessellator.vertexUV(
                    (float) (x + xOffset + CHAR_WIDTH),
                    (float) y,
                    0.0F,
                    (float) (textureX + CHAR_WIDTH) / TEXTURE_SIZE,
                    (float) textureY / TEXTURE_SIZE);
            tessellator.vertexUV(
                    (float) (x + xOffset),
                    (float) y,
                    0.0F,
                    (float) textureX / TEXTURE_SIZE,
                    (float) textureY / TEXTURE_SIZE);

            // Move to the next character position
            xOffset += this.charWidths[chars[i]];
        }

        // Render the text and clean up OpenGL state
        tessellator.flush();
        glDisable(GL_TEXTURE_2D);
    }

    /**
     * Calculates the width of a string in pixels.
     * Takes into account color codes which don't add to the width.
     *
     * @param text The text to measure
     * @return The width of the text in pixels
     */
    public int width(String text) {
        char[] chars = text.toCharArray();
        int width = 0;

        for (int i = 0; i < chars.length; ++i) {
            if (chars[i] == COLOR_CHAR && i + 1 < chars.length && COLOR_CHARS.indexOf(chars[i + 1]) >= 0) {
                // Skip color code characters
                ++i;
            } else {
                width += this.charWidths[chars[i]];
            }
        }

        return width;
    }
}
