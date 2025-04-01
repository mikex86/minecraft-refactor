package com.mojang.minecraft.comm;

/**
 * Interface for listening to server events
 * Implemented by classes that need to respond to client connections and exceptions
 */
public interface ServerListener {
    /**
     * Called when a client connects to the server
     *
     * @param connection The connection to the client
     */
    void clientConnected(SocketConnection connection);

    /**
     * Called when an exception occurs with a client connection
     *
     * @param connection The connection where the exception occurred
     * @param e The exception that occurred
     */
    void clientException(SocketConnection connection, Exception e);
}
