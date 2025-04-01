package com.mojang.minecraft.comm;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Server socket implementation for Minecraft networking
 * Handles accepting connections and routing messages
 */
public class SocketServer {
    /**
     * The server socket channel for accepting connections
     */
    private final ServerSocketChannel ssc;

    /**
     * Listener for server events
     */
    private final ServerListener serverListener;

    /**
     * List of active connections
     */
    private final List<SocketConnection> connections = new LinkedList<>();

    /**
     * Logger for server events
     */
    protected static final Logger logger = Logger.getLogger("SocketServer");

    /**
     * Creates a new socket server
     *
     * @param ips            The IP address to bind to
     * @param port           The port to listen on
     * @param serverListener Listener for server events
     * @throws IOException If the server cannot be created
     */
    public SocketServer(byte[] ips, int port, ServerListener serverListener) throws IOException {
        this.serverListener = serverListener;
        InetAddress hostip = InetAddress.getByAddress(ips);
        this.ssc = ServerSocketChannel.open();
        this.ssc.socket().bind(new InetSocketAddress(hostip, port));
        this.ssc.configureBlocking(false);
    }

    /**
     * Process server events and handle new connections
     *
     * @throws IOException If an error occurs during processing
     */
    public void tick() throws IOException {
        SocketChannel socketChannel;
        while ((socketChannel = this.ssc.accept()) != null) {
            try {
                logger.log(Level.INFO, socketChannel.socket().getRemoteSocketAddress() + " connected");
                socketChannel.configureBlocking(false);
                SocketConnection socketConnection = new SocketConnection(socketChannel);
                this.connections.add(socketConnection);
                this.serverListener.clientConnected(socketConnection);
            } catch (IOException e) {
                socketChannel.close();
                throw e;
            }
        }

        for (int i = 0; i < this.connections.size(); ++i) {
            SocketConnection socketConnection = this.connections.get(i);
            if (!socketConnection.isConnected()) {
                socketConnection.disconnect();
                this.connections.remove(i--);
            } else {
                try {
                    socketConnection.tick();
                } catch (Exception e) {
                    socketConnection.disconnect();
                    this.serverListener.clientException(socketConnection, e);
                }
            }
        }
    }
}
