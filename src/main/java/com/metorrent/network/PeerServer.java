package com.metorrent.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Accepts incoming TCP connections on this peer's listen port. Every
 * meTorrent instance runs one of these so it can act as a server to other
 * peers while simultaneously acting as a client via {@link PeerClient}.
 */
public class PeerServer {

    private static final Logger log = LoggerFactory.getLogger(PeerServer.class);

    private final int requestedPort;
    private final ConnectionListener onNewConnection;
    private final ExecutorService acceptExecutor =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "peer-server-accept");
                t.setDaemon(true);
                return t;
            });

    private ServerSocket serverSocket;
    private volatile boolean running;
    private volatile int boundPort;

    /** {@code requestedPort} may be 0 to let the OS assign an ephemeral port (useful for tests). */
    public PeerServer(int requestedPort, ConnectionListener onNewConnection) {
        this.requestedPort = requestedPort;
        this.onNewConnection = onNewConnection;
    }

    public synchronized void start() throws IOException {
        if (running) {
            return;
        }
        serverSocket = new ServerSocket(requestedPort);
        boundPort = serverSocket.getLocalPort();
        running = true;
        acceptExecutor.submit(this::acceptLoop);
        log.info("PeerServer listening on port {}", boundPort);
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                Connection connection = new Connection(socket);
                log.info("Accepted incoming connection from {}", connection);
                onNewConnection.onNewConnection(connection);
            } catch (IOException e) {
                if (running) {
                    log.warn("Error accepting incoming connection: {}", e.getMessage());
                }
                // else: expected IOException from serverSocket.close() during stop()
            }
        }
    }

    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        try {
            serverSocket.close();
        } catch (IOException ignored) {
            // Already closed or unusable.
        }
        acceptExecutor.shutdownNow();
        log.info("PeerServer on port {} stopped", boundPort);
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return boundPort;
    }
}
