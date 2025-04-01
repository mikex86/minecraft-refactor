package com.mojang.minecraft;

import java.applet.Applet;
import java.awt.BorderLayout;
import java.awt.Canvas;

/**
 * Minecraft Applet implementation that allows the game to be embedded in a web browser.
 * Creates and manages the Minecraft game instance.
 */
public class MinecraftApplet extends Applet {
    /** Generated serialVersionUID */
    private static final long serialVersionUID = 1L;
    
    /** The main canvas where the game is rendered */
    private Canvas canvas;
    /** The Minecraft game instance */
    private Minecraft minecraft;
    /** Thread for running the game */
    private Thread thread;

    /**
     * Called when the applet is initialized.
     * Sets up the canvas for rendering.
     */
    public void init() {
        this.canvas = new Canvas();
        this.canvas.setSize(this.getWidth(), this.getHeight());
        this.setLayout(new BorderLayout());
        this.add(this.canvas, "Center");
        this.canvas.setFocusable(true);
        
        this.canvas.requestFocus();
        this.canvas.setFocusTraversalKeysEnabled(false);
    }

    /**
     * Called when the applet is started.
     * Creates and starts the Minecraft game in a separate thread.
     */
    public void start() {
        if (this.minecraft == null) {
            this.minecraft = new Minecraft(this.canvas, 640, 480, false);
            this.minecraft.appletMode = true;
        }

        this.thread = new Thread() {
            public void run() {
                // Set canvas size to match applet
                canvas.createBufferStrategy(2);
                
                // Start the game loop
                minecraft.run();
            }
        };
        
        this.thread.start();
    }

    /**
     * Called when the applet is stopped.
     * Shuts down the game gracefully.
     */
    public void stop() {
        if (this.minecraft != null) {
            this.minecraft.stop();
        }
    }
}
