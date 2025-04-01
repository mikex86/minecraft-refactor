package com.mojang.minecraft.server;

import com.mojang.minecraft.comm.ServerListener;
import com.mojang.minecraft.comm.SocketConnection;
import com.mojang.minecraft.comm.SocketServer;
import com.mojang.minecraft.crash.CrashReporter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main server class for Minecraft c0.0.11a
 * Handles client connections and server operations
 */
public class MinecraftServer implements Runnable, ServerListener {
    /**
     * Socket server handling network connections
     */
    private final SocketServer socketServer;

    /**
     * Map of socket connections to client objects
     */
    private final Map<SocketConnection, Client> clientMap = new HashMap<>();

    /**
     * List of active clients
     */
    private final List<Client> clients = new ArrayList<>();

    /**
     * Creates a new Minecraft server
     *
     * @param ips  The IP address to bind to
     * @param port The port to listen on
     * @throws IOException If the server cannot be created
     */
    public MinecraftServer(byte[] ips, int port) throws IOException {
        this.socketServer = new SocketServer(ips, port, this);
    }

    @Override
    public void clientConnected(SocketConnection serverConnection) {
        Client client = new Client(this, serverConnection);
        this.clientMap.put(serverConnection, client);
        this.clients.add(client);
    }

    /**
     * Disconnects a client from the server
     *
     * @param client The client to disconnect
     */
    public void disconnect(Client client) {
        this.clientMap.remove(client.serverConnection);
        this.clients.remove(client);
    }

    @Override
    public void clientException(SocketConnection serverConnection, Exception e) {
        Client client = this.clientMap.get(serverConnection);
        client.handleException(e);
    }

    @Override
    public void run() {
        while (true) {
            this.tick();

            try {
                Thread.sleep(5L);
            } catch (InterruptedException e) {
                // Ignored
            }
        }
    }

    /**
     * Processes a single server tick
     */
    private void tick() {
        try {
            this.socketServer.tick();
        } catch (IOException e) {
            CrashReporter.handleError("Exception during server tick", e);
        }
    }

    /**
     * Server entry point
     *
     * @param args Command line arguments (not used)
     * @throws IOException If the server fails to start
     */
    public static void main(String[] args) throws IOException {
        // Set up uncaught exception handler
        CrashReporter.setupUncaughtExceptionHandler();

        try {
            MinecraftServer server = new MinecraftServer(new byte[]{127, 0, 0, 1}, 20801);
            Thread thread = new Thread(server);
            thread.start();
        } catch (IOException e) {
            CrashReporter.handleCrash("Failed to start Minecraft server", e);
            throw e;
        }
    }
}
