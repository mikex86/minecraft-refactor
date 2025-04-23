package com.mojang.minecraft.input;

import com.mojang.minecraft.renderer.GameWindow;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Handles keyboard and mouse input using GLFW callbacks.
 * This provides a layer of abstraction for input events and simulates the
 * polling/event queue behavior of LWJGL 2.
 */
public class InputHandler {
    // Keyboard state
    private final boolean[] keyDown = new boolean[GLFW_KEY_LAST + 1];
    private final List<KeyEvent> keyEvents = new ArrayList<>();

    // Mouse state
    private final boolean[] mouseButtonDown = new boolean[GLFW_MOUSE_BUTTON_LAST + 1];
    private final List<MouseButtonEvent> mouseButtonEvents = new ArrayList<>();
    private double mouseX = 0;
    private double mouseY = 0;
    private double lastMouseX = 0;
    private double lastMouseY = 0;
    private double mouseDX = 0;
    private double mouseDY = 0;
    private double mouseScrollX = 0;
    private double mouseScrollY = 0;

    // Reference to the window
    private final GameWindow window;

    /**
     * Creates a new InputHandler for the given window.
     *
     * @param window The GLFW window to handle input for
     */
    public InputHandler(GameWindow window) {
        this.window = window;

        // Set up keyboard callback
        window.setKeyboardCallback((key, scancode, action, mods) -> {
            if (key >= 0 && key <= GLFW_KEY_LAST) {
                boolean pressed = (action != GLFW_RELEASE);
                synchronized (keyEvents) {
                    keyEvents.add(new KeyEvent(key, pressed));
                    keyDown[key] = pressed;
                }
            }
        });

        // Set up mouse callbacks
        window.setMouseCallback(new GameWindow.MouseCallback() {
            @Override
            public void onCursorPos(double xpos, double ypos) {
                // Update mouse position
                lastMouseX = mouseX;
                lastMouseY = mouseY;
                mouseX = xpos;
                mouseY = ypos;
                mouseDX += mouseX - lastMouseX;
                mouseDY += mouseY - lastMouseY;
            }

            @Override
            public void onMouseButton(int button, int action, int mods) {
                if (button >= 0 && button <= GLFW_MOUSE_BUTTON_LAST) {
                    boolean pressed = (action != GLFW_RELEASE);
                    synchronized (mouseButtonEvents) {
                        mouseButtonEvents.add(new MouseButtonEvent(button, pressed));
                        mouseButtonDown[button] = pressed;
                    }
                }
            }

            @Override
            public void onScroll(double xoffset, double yoffset) {
                mouseScrollX += xoffset;
                mouseScrollY += yoffset;
            }
        });
    }

    /**
     * Updates the input state. Call this once per frame before polling for input.
     */
    public void update() {
    }

    /**
     * Checks if a key is currently pressed.
     *
     * @param key GLFW key code
     * @return true if the key is pressed
     */
    public boolean isKeyDown(int key) {
        return key >= 0 && key <= GLFW_KEY_LAST && keyDown[key];
    }

    /**
     * Checks if there are any pending keyboard events.
     *
     * @return true if there are keyboard events to process
     */
    public boolean hasNextKeyEvent() {
        synchronized (keyEvents) {
            return !keyEvents.isEmpty();
        }
    }

    /**
     * Gets the next keyboard event in the queue.
     *
     * @return The next KeyEvent, or null if there are none
     */
    public KeyEvent getNextKeyEvent() {
        synchronized (keyEvents) {
            return !keyEvents.isEmpty() ? keyEvents.remove(0) : null;
        }
    }

    /**
     * Checks if a mouse button is currently pressed.
     *
     * @param button GLFW mouse button code
     * @return true if the button is pressed
     */
    public boolean isMouseButtonDown(int button) {
        return button >= 0 && button <= GLFW_MOUSE_BUTTON_LAST && mouseButtonDown[button];
    }

    /**
     * Checks if there are any pending mouse button events.
     *
     * @return true if there are mouse button events to process
     */
    public boolean hasNextMouseButtonEvent() {
        synchronized (mouseButtonEvents) {
            return !mouseButtonEvents.isEmpty();
        }
    }

    /**
     * Gets the next mouse button event in the queue.
     *
     * @return The next MouseButtonEvent, or null if there are none
     */
    public MouseButtonEvent getNextMouseButtonEvent() {
        synchronized (mouseButtonEvents) {
            return !mouseButtonEvents.isEmpty() ? mouseButtonEvents.remove(0) : null;
        }
    }

    /**
     * Gets the current mouse X position.
     *
     * @return Mouse X position in pixels
     */
    public double getMouseX() {
        return mouseX;
    }

    /**
     * Gets the current mouse Y position.
     *
     * @return Mouse Y position in pixels
     */
    public double getMouseY() {
        return mouseY;
    }

    /**
     * Gets the mouse X movement since the last update.
     *
     * @return Mouse X movement in pixels
     */
    public double getMouseDX() {
        double dx = mouseDX;
        mouseDX = 0;
        return dx;
    }

    /**
     * Gets the mouse Y movement since the last update.
     *
     * @return Mouse Y movement in pixels
     */
    public double getMouseDY() {
        double dy = mouseDY;
        mouseDY = 0;
        return dy;
    }

    /**
     * Clears all current mouse delta values.
     */
    public void clearMouseDelta() {
        mouseDX = 0;
        mouseDY = 0;
    }

    /**
     * Sets the mouse position.
     *
     * @param x X position in pixels
     * @param y Y position in pixels
     */
    public void setMousePosition(double x, double y) {
        this.window.setCursorPosition(x, y);
    }

    /**
     * Gets the mouse scroll X movement since the last update.
     *
     * @return Mouse scroll X movement
     */
    public double getMouseScrollX() {
        double scrollX = mouseScrollX;
        mouseScrollX = 0;
        return scrollX;
    }

    /**
     * Gets the mouse scroll Y movement since the last update.
     *
     * @return Mouse scroll Y movement
     */
    public double getMouseScrollY() {
        double scrollY = mouseScrollY;
        mouseScrollY = 0;
        return scrollY;
    }

    /**
     * Captures or releases the cursor.
     * When the cursor is captured, it is hidden and its position is reset to the center of the window.
     * This is useful for first-person camera controls.
     *
     * @param captured true to capture the cursor, false to release it
     */
    public void setCursorCaptured(boolean captured) {
        window.setCursorCaptured(captured);
    }

    /**
     * Gets the associated window.
     *
     * @return The associated GameWindow
     */
    public GameWindow getWindow() {
        return window;
    }

    public void resetMouse() {
    }


    /**
     * Keyboard event class.
     */
    public static class KeyEvent {
        private final int key;
        private final boolean pressed;

        public KeyEvent(int key, boolean pressed) {
            this.key = key;
            this.pressed = pressed;
        }

        public int getKey() {
            return key;
        }

        public boolean isPressed() {
            return pressed;
        }
    }

    /**
     * Mouse button event class.
     */
    public static class MouseButtonEvent {
        private final int button;
        private final boolean pressed;

        public MouseButtonEvent(int button, boolean pressed) {
            this.button = button;
            this.pressed = pressed;
        }

        public int getButton() {
            return button;
        }

        public boolean isPressed() {
            return pressed;
        }
    }

    /**
     * Map LWJGL 2 Keyboard constants to GLFW key codes.
     */
    public static final class Keys {
        public static final int KEY_ESCAPE = GLFW_KEY_ESCAPE;
        public static final int KEY_1 = GLFW_KEY_1;
        public static final int KEY_2 = GLFW_KEY_2;
        public static final int KEY_3 = GLFW_KEY_3;
        public static final int KEY_4 = GLFW_KEY_4;
        public static final int KEY_5 = GLFW_KEY_5;
        public static final int KEY_6 = GLFW_KEY_6;
        public static final int KEY_7 = GLFW_KEY_7;
        public static final int KEY_8 = GLFW_KEY_8;
        public static final int KEY_9 = GLFW_KEY_9;
        public static final int KEY_0 = GLFW_KEY_0;
        public static final int KEY_MINUS = GLFW_KEY_MINUS;
        public static final int KEY_EQUALS = GLFW_KEY_EQUAL;
        public static final int KEY_BACK = GLFW_KEY_BACKSPACE;
        public static final int KEY_TAB = GLFW_KEY_TAB;
        public static final int KEY_Q = GLFW_KEY_Q;
        public static final int KEY_W = GLFW_KEY_W;
        public static final int KEY_E = GLFW_KEY_E;
        public static final int KEY_R = GLFW_KEY_R;
        public static final int KEY_T = GLFW_KEY_T;
        public static final int KEY_Y = GLFW_KEY_Y;
        public static final int KEY_U = GLFW_KEY_U;
        public static final int KEY_I = GLFW_KEY_I;
        public static final int KEY_O = GLFW_KEY_O;
        public static final int KEY_P = GLFW_KEY_P;
        public static final int KEY_LBRACKET = GLFW_KEY_LEFT_BRACKET;
        public static final int KEY_RBRACKET = GLFW_KEY_RIGHT_BRACKET;
        public static final int KEY_RETURN = GLFW_KEY_ENTER;
        public static final int KEY_LCONTROL = GLFW_KEY_LEFT_CONTROL;
        public static final int KEY_A = GLFW_KEY_A;
        public static final int KEY_S = GLFW_KEY_S;
        public static final int KEY_D = GLFW_KEY_D;
        public static final int KEY_F = GLFW_KEY_F;
        public static final int KEY_G = GLFW_KEY_G;
        public static final int KEY_H = GLFW_KEY_H;
        public static final int KEY_J = GLFW_KEY_J;
        public static final int KEY_K = GLFW_KEY_K;
        public static final int KEY_L = GLFW_KEY_L;
        public static final int KEY_SEMICOLON = GLFW_KEY_SEMICOLON;
        public static final int KEY_APOSTROPHE = GLFW_KEY_APOSTROPHE;
        public static final int KEY_GRAVE = GLFW_KEY_GRAVE_ACCENT;
        public static final int KEY_LSHIFT = GLFW_KEY_LEFT_SHIFT;
        public static final int KEY_BACKSLASH = GLFW_KEY_BACKSLASH;
        public static final int KEY_Z = GLFW_KEY_Z;
        public static final int KEY_X = GLFW_KEY_X;
        public static final int KEY_C = GLFW_KEY_C;
        public static final int KEY_V = GLFW_KEY_V;
        public static final int KEY_B = GLFW_KEY_B;
        public static final int KEY_N = GLFW_KEY_N;
        public static final int KEY_M = GLFW_KEY_M;
        public static final int KEY_COMMA = GLFW_KEY_COMMA;
        public static final int KEY_PERIOD = GLFW_KEY_PERIOD;
        public static final int KEY_SLASH = GLFW_KEY_SLASH;
        public static final int KEY_RSHIFT = GLFW_KEY_RIGHT_SHIFT;
        public static final int KEY_MULTIPLY = GLFW_KEY_KP_MULTIPLY;
        public static final int KEY_LMENU = GLFW_KEY_LEFT_ALT;
        public static final int KEY_SPACE = GLFW_KEY_SPACE;
        public static final int KEY_CAPITAL = GLFW_KEY_CAPS_LOCK;
        public static final int KEY_F1 = GLFW_KEY_F1;
        public static final int KEY_F2 = GLFW_KEY_F2;
        public static final int KEY_F3 = GLFW_KEY_F3;
        public static final int KEY_F4 = GLFW_KEY_F4;
        public static final int KEY_F5 = GLFW_KEY_F5;
        public static final int KEY_F6 = GLFW_KEY_F6;
        public static final int KEY_F7 = GLFW_KEY_F7;
        public static final int KEY_F8 = GLFW_KEY_F8;
        public static final int KEY_F9 = GLFW_KEY_F9;
        public static final int KEY_F10 = GLFW_KEY_F10;
        public static final int KEY_F11 = GLFW_KEY_F11;
        public static final int KEY_F12 = GLFW_KEY_F12;
    }

    /**
     * Map LWJGL 2 Mouse constants to GLFW mouse button codes.
     */
    public static final class MouseButtons {
        public static final int BUTTON_LEFT = GLFW_MOUSE_BUTTON_LEFT;
        public static final int BUTTON_RIGHT = GLFW_MOUSE_BUTTON_RIGHT;
        public static final int BUTTON_MIDDLE = GLFW_MOUSE_BUTTON_MIDDLE;
    }
} 