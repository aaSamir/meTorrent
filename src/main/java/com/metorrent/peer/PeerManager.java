package com.metorrent.peer;

import com.metorrent.network.Connection;
import com.metorrent.network.MessageDispatcher;
import com.metorrent.network.MessageListener;
import com.metorrent.network.PeerClient;
import com.metorrent.network.PeerServer;
import com.metorrent.protocol.Message;
import com.metorrent.protocol.MessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Orchestrates this instance's dual role as both server and client: accepts
 * inbound connections, opens outbound ones, performs the HELLO handshake,
 * tracks peer status/latency in the {@link PeerRegistry}, and periodically
 * pings connected peers. Registers itself on the shared
 * {@link MessageDispatcher} and only reacts to peer-lifecycle message
 * types (HELLO/PING/PONG/DISCONNECT/ERROR) - transfer-related types are
 * handled by a separate listener in the transfer module, keeping the two
 * concerns decoupled.
 *
 * <p>Inbound connections are only greeted with a HELLO reactively (in
 * {@link #handleHello}), never proactively on accept. This matters because
 * {@code transfer.TransferManager} opens its own dedicated connection per
 * transfer (see docs/PROTOCOL.md section 4) and never sends a HELLO on it -
 * if this class greeted every accepted socket unconditionally, that stray
 * HELLO would make {@link #handleHello} overwrite the peer's registry entry
 * with the transfer connection, and the peer would appear to disconnect the
 * moment that transfer connection closed at the end of the transfer.
 */
public class PeerManager implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(PeerManager.class);
    private static final int PING_INTERVAL_SECONDS = 5;

    private final String localName;
    private final int listenPort;
    private final PeerRegistry registry;
    private final MessageDispatcher dispatcher;
    private final PeerServer server;
    private final PeerClient client = new PeerClient();
    private final Set<String> helloSentConnectionIds = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService pingExecutor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "peer-ping-scheduler");
                t.setDaemon(true);
                return t;
            });

    public PeerManager(String localName, int listenPort, PeerRegistry registry, MessageDispatcher dispatcher) {
        this.localName = localName;
        this.listenPort = listenPort;
        this.registry = registry;
        this.dispatcher = dispatcher;
        this.server = new PeerServer(listenPort, this::handleInboundConnection);
        dispatcher.addListener(this);
    }

    public void start() throws IOException {
        server.start();
        pingExecutor.scheduleAtFixedRate(this::pingConnectedPeers, PING_INTERVAL_SECONDS, PING_INTERVAL_SECONDS, TimeUnit.SECONDS);
        log.info("PeerManager started as '{}' on port {}", localName, getActualPort());
    }

    public int getActualPort() {
        return server.getPort();
    }

    public PeerRegistry getRegistry() {
        return registry;
    }

    /** Opens an outbound connection to a peer we already know the address of. */
    public Peer connect(String host, int port) throws IOException {
        Peer peer = new Peer(host, port, "Connecting...");
        registry.put(peer);

        Connection connection = client.connect(host, port);
        peer.setConnection(connection);
        dispatcher.listen(connection);
        sendHelloOnce(connection);
        return peer;
    }

    public void disconnect(String peerId) {
        registry.get(peerId).ifPresent(peer -> {
            Connection connection = peer.getConnection();
            if (connection != null) {
                try {
                    connection.sendMessage(Message.builder(MessageType.DISCONNECT)
                            .field("reason", "user disconnected")
                            .build());
                } catch (IOException ignored) {
                    // Best-effort notice; we're closing the socket regardless.
                }
                connection.close();
            }
            peer.setStatus(PeerStatus.DISCONNECTED);
            peer.setConnection(null);
            registry.notifyUpdated(peer);
        });
    }

    public void reconnect(String peerId) throws IOException {
        Peer existing = registry.get(peerId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown peer: " + peerId));
        connect(existing.getHost(), existing.getPort());
    }

    public void shutdown() {
        pingExecutor.shutdownNow();
        server.stop();
    }

    /**
     * Inbound connections are only registered with the dispatcher here, not
     * greeted - see the class-level note on why sending a HELLO must wait
     * until we know (via {@link #handleHello}) that the other side sent one
     * first, rather than assuming every accepted socket is a peer greeting.
     */
    private void handleInboundConnection(Connection connection) {
        dispatcher.listen(connection);
    }

    /** Sends our HELLO on this connection exactly once, however it gets triggered. */
    private void sendHelloOnce(Connection connection) {
        if (!helloSentConnectionIds.add(connection.getConnectionId())) {
            return;
        }
        try {
            connection.sendMessage(Message.builder(MessageType.HELLO)
                    .field("peerName", localName)
                    .field("listenPort", server.getPort())
                    .field("protocolVersion", 1)
                    .build());
        } catch (IOException e) {
            log.warn("Failed to send HELLO to {}: {}", connection, e.getMessage());
        }
    }

    @Override
    public void onMessage(Connection connection, Message message) {
        switch (message.getType()) {
            case HELLO -> handleHello(connection, message);
            case PING -> handlePing(connection, message);
            case PONG -> handlePong(connection, message);
            case DISCONNECT -> handleDisconnect(connection);
            case ERROR -> log.warn("Peer {} reported an error: {}", connection,
                    message.getStringOrDefault("message", "(no message)"));
            default -> {
                // Not a peer-lifecycle message; the transfer module's listener handles it.
            }
        }
    }

    @Override
    public void onBinaryFrame(Connection connection, byte[] data) {
        // Chunk payloads are not this class's concern.
    }

    @Override
    public void onConnectionClosed(Connection connection, Throwable cause) {
        helloSentConnectionIds.remove(connection.getConnectionId());
        registry.findByConnectionId(connection.getConnectionId()).ifPresent(peer -> {
            if (peer.getConnection() == connection) {
                peer.setStatus(PeerStatus.DISCONNECTED);
                peer.setConnection(null);
                registry.notifyUpdated(peer);
                log.info("Peer disconnected: {}", peer);
            }
        });
    }

    private void handleHello(Connection connection, Message message) {
        String remoteName = message.getString("peerName");
        int remoteListenPort = message.getInt("listenPort");
        String host = connection.getRemoteHost();
        String peerId = Peer.idFor(host, remoteListenPort);

        Peer peer = registry.get(peerId).orElseGet(() -> new Peer(host, remoteListenPort, remoteName));
        peer.setName(remoteName);
        peer.setStatus(PeerStatus.CONNECTED);
        peer.setConnectionTime(Instant.now());
        peer.setConnection(connection);
        registry.put(peer);
        log.info("Peer connected: {}", peer);

        // Reply in kind if this connection reached us passively (we didn't
        // greet it on accept - see the class-level note); a no-op if we're
        // the side that greeted first, so this never causes a HELLO ping-pong.
        sendHelloOnce(connection);
    }

    private void handlePing(Connection connection, Message message) {
        try {
            connection.sendMessage(Message.builder(MessageType.PONG)
                    .field("nonce", message.getInt("nonce"))
                    .field("sentAt", message.getLong("sentAt"))
                    .build());
        } catch (IOException e) {
            log.debug("Failed to reply to PING from {}: {}", connection, e.getMessage());
        }
    }

    private void handlePong(Connection connection, Message message) {
        long latency = System.currentTimeMillis() - message.getLong("sentAt");
        registry.findByConnectionId(connection.getConnectionId()).ifPresent(peer -> {
            peer.setLatencyMs(latency);
            registry.notifyUpdated(peer);
        });
    }

    private void handleDisconnect(Connection connection) {
        registry.findByConnectionId(connection.getConnectionId()).ifPresent(peer -> {
            peer.setStatus(PeerStatus.DISCONNECTED);
            peer.setConnection(null);
            registry.notifyUpdated(peer);
        });
        connection.close();
    }

    private void pingConnectedPeers() {
        for (Peer peer : registry.list()) {
            Connection connection = peer.getConnection();
            if (peer.getStatus() == PeerStatus.CONNECTED && connection != null) {
                try {
                    connection.sendMessage(Message.builder(MessageType.PING)
                            .field("nonce", ThreadLocalRandom.current().nextInt())
                            .field("sentAt", System.currentTimeMillis())
                            .build());
                } catch (IOException e) {
                    log.debug("Failed to ping {}: {}", peer, e.getMessage());
                }
            }
        }
    }
}
