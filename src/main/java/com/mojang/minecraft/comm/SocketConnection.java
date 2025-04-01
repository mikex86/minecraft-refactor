package com.mojang.minecraft.comm;

import com.mojang.minecraft.crash.CrashReporter;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * Handles socket communication for the Minecraft networking layer
 * Manages reading from and writing to a socket channel
 */
public class SocketConnection {
    /**
     * Default buffer size for read/write operations
     */
    public static final int BUFFER_SIZE = 131068;

    /**
     * Connection state
     */
    private boolean connected;

    /**
     * The underlying socket channel
     */
    private final SocketChannel socketChannel;

    /**
     * Buffer for reading data from the socket
     */
    public ByteBuffer readBuffer = ByteBuffer.allocate(BUFFER_SIZE);

    /**
     * Buffer for writing data to the socket
     */
    public ByteBuffer writeBuffer = ByteBuffer.allocate(BUFFER_SIZE);

    /**
     * Timestamp of the last read operation
     */
    protected long lastRead;

    /**
     * Listener for connection events
     */
    private ConnectionListener connectionListener;

    /**
     * Total bytes read from this connection
     */
    private int bytesRead;

    /**
     * Total bytes written to this connection
     */
    private int totalBytesWritten;

    /**
     * Maximum number of blocks to process per iteration
     */
    private int maxBlocksPerIteration = 3;

    /**
     * The underlying socket
     */
    private Socket socket;

    /**
     * Input stream for reading from the socket
     */
    private BufferedInputStream in;

    /**
     * Output stream for writing to the socket
     */
    private BufferedOutputStream out;

    /**
     * Creates a socket connection to a remote host
     *
     * @param ip   The IP address to connect to
     * @param port The port to connect to
     * @throws UnknownHostException If the host cannot be resolved
     * @throws IOException          If the connection cannot be established
     */
    public SocketConnection(String ip, int port) throws UnknownHostException, IOException {
        this.socketChannel = SocketChannel.open();
        this.socketChannel.connect(new InetSocketAddress(ip, port));
        this.socketChannel.configureBlocking(false);
        this.lastRead = System.currentTimeMillis();
        this.connected = true;
        this.readBuffer.clear();
        this.writeBuffer.clear();
    }

    /**
     * Creates a socket connection from an existing socket channel
     *
     * @param socketChannel The socket channel to use
     * @throws IOException If the connection cannot be established
     */
    public SocketConnection(SocketChannel socketChannel) throws IOException {
        this.socketChannel = socketChannel;
        socketChannel.configureBlocking(false);
        this.lastRead = System.currentTimeMillis();
        this.socket = socketChannel.socket();
        this.connected = true;
        this.readBuffer.clear();
        this.writeBuffer.clear();
    }

    /**
     * Sets the maximum number of blocks to process per iteration
     *
     * @param maxBlocksPerIteration The maximum number of blocks
     */
    public void setMaxBlocksPerIteration(int maxBlocksPerIteration) {
        this.maxBlocksPerIteration = maxBlocksPerIteration;
    }

    /**
     * Gets the IP address of the remote host
     *
     * @return The IP address as a string
     */
    public String getIp() {
        return this.socket.getInetAddress().toString();
    }

    /**
     * Gets the write buffer for this connection
     *
     * @return The write buffer
     */
    public ByteBuffer getBuffer() {
        return this.writeBuffer;
    }

    /**
     * Sets the listener for connection events
     *
     * @param connectionListener The connection listener
     */
    public void setConnectionListener(ConnectionListener connectionListener) {
        this.connectionListener = connectionListener;
    }

    /**
     * Checks if the connection is still active
     *
     * @return true if connected, false otherwise
     */
    public boolean isConnected() {
        return this.connected;
    }

    /**
     * Disconnects this connection and closes all resources
     */
    public void disconnect() {
        this.connected = false;

        try {
            if (this.in != null) {
                this.in.close();
            }

            this.in = null;
        } catch (Exception e) {
            CrashReporter.handleError("Failed to close input stream during disconnect", e);
        }

        try {
            if (this.out != null) {
                this.out.close();
            }

            this.out = null;
        } catch (Exception e) {
            CrashReporter.handleError("Failed to close output stream during disconnect", e);
        }

        try {
            if (this.socket != null) {
                this.socket.close();
            }

            this.socket = null;
        } catch (Exception e) {
            CrashReporter.handleError("Failed to close socket during disconnect", e);
        }
    }

    /**
     * Processes read/write operations for this connection
     *
     * @throws IOException If an I/O error occurs
     */
    public void tick() throws IOException {
        this.writeBuffer.flip();
        this.socketChannel.write(this.writeBuffer);
        this.writeBuffer.compact();
        this.readBuffer.compact();
        this.socketChannel.read(this.readBuffer);
        this.readBuffer.flip();
        if (this.readBuffer.remaining() > 0) {
            this.connectionListener.command(this.readBuffer.get(0), this.readBuffer.remaining(), this.readBuffer);
        }
    }

    /**
     * Gets the total number of bytes sent by this connection
     *
     * @return The number of bytes sent
     */
    public int getSentBytes() {
        return this.totalBytesWritten;
    }

    /**
     * Gets the total number of bytes read by this connection
     *
     * @return The number of bytes read
     */
    public int getReadBytes() {
        return this.bytesRead;
    }

    /**
     * Resets the sent bytes counter
     */
    public void clearSentBytes() {
        this.totalBytesWritten = 0;
    }

    /**
     * Resets the read bytes counter
     */
    public void clearReadBytes() {
        this.bytesRead = 0;
    }
}
