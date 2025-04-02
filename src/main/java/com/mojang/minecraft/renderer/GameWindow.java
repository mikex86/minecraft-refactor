package com.mojang.minecraft.renderer;

import com.mojang.minecraft.renderer.graphics.GraphicsAPI;
import com.mojang.minecraft.renderer.graphics.GraphicsFactory;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * GameWindow implementation using GLFW for windowing and input handling.
 * Replaces the old LWJGL 2 Display system.
 */
public class GameWindow implements Disposable {

    // Window handle
    private final long window;

    // Window dimensions
    private int width;
    private int height;

    // Input callbacks
    private KeyboardCallback keyboardCallback;
    private MouseCallback mouseCallback;

    // Graphics context
    private final GraphicsAPI graphics;

    // Flag to check if window was created or is embedded
    private final boolean isStandalone;

    // callbacks
    private GLFWKeyCallback keyCallback;
    private GLFWMouseButtonCallback mousebuttonCallback;
    private GLFWCursorPosCallback cursorPosCallback;
    private GLFWScrollCallback scrollCallback;

    /**
     * Creates a new game window with specified dimensions.
     *
     * @param width      Window width
     * @param height     Window height
     * @param title      Window title
     * @param fullscreen Whether to create a fullscreen window
     */
    public GameWindow(int width, int height, String title, boolean fullscreen) {
        this.width = width;
        this.height = height;
        this.isStandalone = true;

        // Setup error callback to print errors to System.err
        GLFWErrorCallback.createPrint(System.err).set();

        // Initialize GLFW
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        // Configure GLFW window
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_FOCUS_ON_SHOW, GLFW_TRUE);

        // Create the window
        if (fullscreen) {
            // For fullscreen, get primary monitor and its video mode
            long monitor = glfwGetPrimaryMonitor();
            GLFWVidMode vidMode = glfwGetVideoMode(monitor);

            if (vidMode != null) {
                this.width = vidMode.width();
                this.height = vidMode.height();
                window = glfwCreateWindow(this.width, this.height, title, monitor, NULL);
                System.out.println("Created fullscreen window: " + this.width + "x" + this.height);
            } else {
                window = glfwCreateWindow(this.width, this.height, title, NULL, NULL);
                System.out.println("Created windowed mode (fallback): " + this.width + "x" + this.height);
            }
        } else {
            window = glfwCreateWindow(this.width, this.height, title, NULL, NULL);
            System.out.println("Created window: " + this.width + "x" + this.height);
        }

        if (window == NULL) {
            throw new RuntimeException("Failed to create the GLFW window");
        }

        // Get the actual framebuffer size which might be different from the window size (e.g., on high DPI displays)
        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);

            glfwGetFramebufferSize(window, pWidth, pHeight);

            this.width = pWidth.get(0);
            this.height = pHeight.get(0);
            System.out.println("Framebuffer size: " + this.width + "x" + this.height);
        }

        // Set up window position (centered)
        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);

            glfwGetWindowSize(window, pWidth, pHeight);

            GLFWVidMode vidMode = glfwGetVideoMode(glfwGetPrimaryMonitor());

            if (vidMode != null) {
                glfwSetWindowPos(
                        window,
                        (vidMode.width() - pWidth.get(0)) / 2,
                        (vidMode.height() - pHeight.get(0)) / 2
                );
            }
        }

        // Make the OpenGL context current
        glfwMakeContextCurrent(window);

        // Disable v-sync
        glfwSwapInterval(0);

        // Initialize OpenGL capabilities (needed for LWJGL to work with OpenGL)
        GL.createCapabilities();

        // Get the graphics API
        this.graphics = GraphicsFactory.getGraphicsAPI();

        // Initialize the graphics API
        graphics.initialize();
    }

    public void show() {
        glfwShowWindow(window);
    }

    /**
     * Gets the window width.
     *
     * @return Window width in pixels
     */
    public int getWidth() {
        return width;
    }

    /**
     * Gets the window height.
     *
     * @return Window height in pixels
     */
    public int getHeight() {
        return height;
    }

    /**
     * Updates the window, swapping buffers and polling events.
     *
     * @return true if the window should remain open, false if it should close
     */
    public boolean update() {
        // Swap buffers
        glfwSwapBuffers(window);

        // Poll for events
        glfwPollEvents();

        // Update window width & height
        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);

            glfwGetFramebufferSize(window, pWidth, pHeight);

            int newWidth = pWidth.get(0);
            int newHeight = pHeight.get(0);

            if (newWidth != width || newHeight != height) {
                width = newWidth;
                height = newHeight;
                graphics.setViewport(0, 0, width, height);
            }
        }

        // Check if window should close
        return !glfwWindowShouldClose(window);
    }

    /**
     * Checks if the window has focus.
     *
     * @return true if the window has focus
     */
    public boolean hasFocus() {
        return glfwGetWindowAttrib(window, GLFW_FOCUSED) == GLFW_TRUE;
    }

    /**
     * Sets a keyboard callback for key events.
     *
     * @param callback The keyboard callback
     */
    public void setKeyboardCallback(KeyboardCallback callback) {
        this.keyboardCallback = callback;

        this.keyCallback = glfwSetKeyCallback(window, (window, key, scancode, action, mods) -> {
            if (keyboardCallback != null) {
                keyboardCallback.onKey(key, scancode, action, mods);
            }
        });
    }

    /**
     * Sets a mouse callback for mouse button and movement events.
     *
     * @param callback The mouse callback
     */
    public void setMouseCallback(MouseCallback callback) {
        this.mouseCallback = callback;

        // Mouse button callback
        if (this.mousebuttonCallback != null) {
            this.mousebuttonCallback.free();
        }
        this.mousebuttonCallback = glfwSetMouseButtonCallback(window, (window, button, action, mods) -> {
            if (mouseCallback != null) {
                mouseCallback.onMouseButton(button, action, mods);
            }
        });

        // Mouse position callback
        if (this.cursorPosCallback != null) {
            this.cursorPosCallback.free();
        }
        this.cursorPosCallback = glfwSetCursorPosCallback(window, (window, xpos, ypos) -> {
            if (mouseCallback != null) {
                mouseCallback.onCursorPos(xpos, ypos);
            }
        });

        // Scroll callback
        if (this.scrollCallback != null) {
            this.scrollCallback.free();
        }
        this.scrollCallback = glfwSetScrollCallback(window, (window, xoffset, yoffset) -> {
            if (mouseCallback != null) {
                mouseCallback.onScroll(xoffset, yoffset);
            }
        });
    }

    @Override
    public void dispose() {
        if (keyCallback != null) {
            keyCallback.free();
        }
        if (mousebuttonCallback != null) {
            mousebuttonCallback.free();
        }
        if (cursorPosCallback != null) {
            cursorPosCallback.free();
        }
        if (scrollCallback != null) {
            scrollCallback.free();
        }
        if (isStandalone) {
            // Free the callbacks
            glfwFreeCallbacks(window);

            // Destroy the window
            glfwDestroyWindow(window);

            // Shutdown the graphics API
            graphics.shutdown();

            // Terminate GLFW
            glfwTerminate();
        }
    }

    public void setCursorPosition(double x, double y) {
        glfwSetCursorPos(window, x, y);
    }

    public void setCursorCaptured(boolean captured) {
        glfwSetInputMode(window, GLFW_CURSOR, !captured ? GLFW_CURSOR_NORMAL : GLFW_CURSOR_DISABLED);
    }

    public void requestFocus() {
        glfwFocusWindow(window);
    }

    /**
     * Interface for keyboard input callbacks.
     */
    public interface KeyboardCallback {
        /**
         * Called when a key event occurs.
         *
         * @param key      The key code
         * @param scancode The platform-specific scan code
         * @param action   The key action (GLFW_PRESS, GLFW_RELEASE, or GLFW_REPEAT)
         * @param mods     The modifier keys
         */
        void onKey(int key, int scancode, int action, int mods);
    }

    /**
     * Interface for mouse input callbacks.
     */
    public interface MouseCallback {
        /**
         * Called when a mouse button event occurs.
         *
         * @param button The mouse button
         * @param action The button action (GLFW_PRESS or GLFW_RELEASE)
         * @param mods   The modifier keys
         */
        void onMouseButton(int button, int action, int mods);

        /**
         * Called when the mouse cursor moves.
         *
         * @param xpos The new x position
         * @param ypos The new y position
         */
        void onCursorPos(double xpos, double ypos);

        /**
         * Called when the mouse wheel is scrolled.
         *
         * @param xoffset The scroll offset along the x-axis
         * @param yoffset The scroll offset along the y-axis
         */
        void onScroll(double xoffset, double yoffset);
    }
} 