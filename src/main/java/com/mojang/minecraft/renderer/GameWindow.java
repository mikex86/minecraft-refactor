package com.mojang.minecraft.renderer;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

import java.nio.IntBuffer;

import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

/**
 * GameWindow implementation using GLFW for windowing and input handling.
 * Replaces the old LWJGL 2 Display system.
 */
public class GameWindow {
    // Window handle
    private long window;
    
    // Window dimensions
    private int width;
    private int height;
    
    // Input callbacks
    private KeyboardCallback keyboardCallback;
    private MouseCallback mouseCallback;
    
    // Flag to check if window was created or is embedded
    private boolean isStandalone;
    
    /**
     * Creates a new GameWindow with specified dimensions.
     * 
     * @param width Window width
     * @param height Window height
     * @param title Window title
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
        
        // Use OpenGL compatibility profile to support fixed-function pipeline
        // (we're removing the core profile requirements for now)
        
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
        
        // Show the window
        glfwShowWindow(window);
        
        // Initialize OpenGL capabilities
        GL.createCapabilities();
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
        
        // Check if window should close
        return !glfwWindowShouldClose(window);
    }
    
    /**
     * Destroys the window and cleans up GLFW resources.
     */
    public void destroy() {
        if (isStandalone) {
            // Free the callbacks
            glfwFreeCallbacks(window);
            
            // Destroy the window
            glfwDestroyWindow(window);
            
            // Terminate GLFW
            glfwTerminate();
            
            // Remove the error callback
            GLFWErrorCallback callback = glfwSetErrorCallback(null);
            if (callback != null) {
                callback.free();
            }
        }
    }
    
    /**
     * Gets the window width.
     * 
     * @return Window width
     */
    public int getWidth() {
        return width;
    }
    
    /**
     * Gets the window height.
     * 
     * @return Window height
     */
    public int getHeight() {
        return height;
    }
    
    /**
     * Gets the GLFW window handle.
     * 
     * @return Window handle
     */
    public long getWindowHandle() {
        return window;
    }
    
    /**
     * Checks if the window currently has focus.
     * 
     * @return true if the window has focus, false otherwise
     */
    public boolean hasFocus() {
        return glfwGetWindowAttrib(window, GLFW_FOCUSED) == GLFW_TRUE;
    }
    
    /**
     * Sets the keyboard callback.
     * 
     * @param callback Keyboard callback to set
     */
    public void setKeyboardCallback(KeyboardCallback callback) {
        this.keyboardCallback = callback;
        
        glfwSetKeyCallback(window, (windowHandle, key, scancode, action, mods) -> {
            if (callback != null) {
                callback.invoke(key, scancode, action, mods);
            }
        });
    }
    
    /**
     * Sets the mouse callback.
     * 
     * @param callback Mouse callback to set
     */
    public void setMouseCallback(MouseCallback callback) {
        this.mouseCallback = callback;
        
        glfwSetCursorPosCallback(window, (windowHandle, xpos, ypos) -> {
            if (callback != null) {
                callback.onCursorPos(xpos, ypos);
            }
        });
        
        glfwSetMouseButtonCallback(window, (windowHandle, button, action, mods) -> {
            if (callback != null) {
                callback.onMouseButton(button, action, mods);
            }
        });
        
        glfwSetScrollCallback(window, (windowHandle, xoffset, yoffset) -> {
            if (callback != null) {
                callback.onScroll(xoffset, yoffset);
            }
        });
    }
    
    /**
     * Sets whether the cursor should be visible or hidden.
     * 
     * @param visible true to show cursor, false to hide it
     */
    public void setCursorVisible(boolean visible) {
        glfwSetInputMode(window, GLFW_CURSOR, visible ? GLFW_CURSOR_NORMAL : GLFW_CURSOR_DISABLED);
    }
    
    /**
     * Interface for keyboard callbacks.
     */
    public interface KeyboardCallback {
        /**
         * Called when a keyboard event occurs.
         * 
         * @param key GLFW key code
         * @param scancode Platform-specific scancode
         * @param action GLFW action (press, release, repeat)
         * @param mods Modifier keys
         */
        void invoke(int key, int scancode, int action, int mods);
    }
    
    /**
     * Interface for mouse callbacks.
     */
    public interface MouseCallback {
        /**
         * Called when the cursor position changes.
         * 
         * @param xpos X position of cursor
         * @param ypos Y position of cursor
         */
        void onCursorPos(double xpos, double ypos);
        
        /**
         * Called when a mouse button is pressed or released.
         * 
         * @param button GLFW mouse button
         * @param action GLFW action (press or release)
         * @param mods Modifier keys
         */
        void onMouseButton(int button, int action, int mods);
        
        /**
         * Called when the scroll wheel is moved.
         * 
         * @param xoffset Horizontal scroll offset
         * @param yoffset Vertical scroll offset
         */
        void onScroll(double xoffset, double yoffset);
    }
} 