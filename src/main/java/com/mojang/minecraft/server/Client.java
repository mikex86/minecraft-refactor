package com.mojang.minecraft.server;

import com.mojang.minecraft.comm.ConnectionListener;
import com.mojang.minecraft.comm.SocketConnection;

import java.nio.ByteBuffer;

/**
 * Represents a connected client in the Minecraft server
 * Handles communication between the server and a client
 */
public class Client implements ConnectionListener {
    /** The network connection to the client */
    public final SocketConnection serverConnection;
    
    /** The server this client is connected to */
    private final MinecraftServer server;

    /**
     * Creates a new client connection
     *
     * @param server The server instance
     * @param serverConnection The socket connection to the client
     */
    public Client(MinecraftServer server, SocketConnection serverConnection) {
        this.server = server;
        this.serverConnection = serverConnection;
        serverConnection.setConnectionListener(this);
    }

    /**
     * Processes a command from the client
     *
     * @param cmd The command byte
     * @param remaining Remaining bytes in the buffer
     * @param in The input buffer containing command data
     */
    public void command(byte cmd, int remaining, ByteBuffer in) {
        // Command processing would go here
    }

    /**
     * Handles exceptions that occur during client communication
     *
     * @param e The exception that occurred
     */
    public void handleException(Exception e) {
        this.disconnect();
    }

    /**
     * Disconnects this client from the server
     */
    public void disconnect() {
        this.server.disconnect(this);
    }
}
