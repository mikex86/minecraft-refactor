package com.mojang.minecraft.comm;

import java.nio.ByteBuffer;

/**
 * Interface for listening to connection events
 * Implemented by classes that need to handle connection commands and exceptions
 */
public interface ConnectionListener {
    /**
     * Called when an exception occurs with this connection
     *
     * @param exception The exception that occurred
     */
    void handleException(Exception exception);

    /**
     * Called when a command is received from this connection
     *
     * @param commandType The type of command
     * @param remaining   Number of bytes remaining in the buffer
     * @param data        The buffer containing command data
     */
    void command(byte commandType, int remaining, ByteBuffer data);
}
