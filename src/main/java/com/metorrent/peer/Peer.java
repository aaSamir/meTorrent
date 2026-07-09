package com.metorrent.peer;

import com.metorrent.network.Connection;

import java.time.Instant;

/**
 * A remote peer, identified by its advertised host:listenPort (not the
 * ephemeral local port of whichever TCP connection happens to be open to
 * it right now). Mutable: status, latency and the live connection change
 * over the peer's lifetime, so fields are volatile for cross-thread
 * visibility between the network reader threads, the ping scheduler and
 * the GUI thread.
 */
public class Peer {

    private final String peerId;
    private final String host;
    private final int port;

    private volatile String name;
    private volatile PeerStatus status;
    private volatile Instant connectionTime;
    private volatile long latencyMs = -1;
    private volatile Connection connection;

    public Peer(String host, int port, String name) {
        this.host = host;
        this.port = port;
        this.peerId = idFor(host, port);
        this.name = name;
        this.status = PeerStatus.CONNECTING;
    }

    public static String idFor(String host, int port) {
        return host + ":" + port;
    }

    public String getPeerId() {
        return peerId;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public PeerStatus getStatus() {
        return status;
    }

    public void setStatus(PeerStatus status) {
        this.status = status;
    }

    public Instant getConnectionTime() {
        return connectionTime;
    }

    public void setConnectionTime(Instant connectionTime) {
        this.connectionTime = connectionTime;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public Connection getConnection() {
        return connection;
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    @Override
    public String toString() {
        return "Peer{" + peerId + ", name=" + name + ", status=" + status + '}';
    }
}
