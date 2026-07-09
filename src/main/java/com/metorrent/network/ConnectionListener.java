package com.metorrent.network;

/** Notified by {@link PeerServer} whenever a remote peer opens a new connection to us. */
@FunctionalInterface
public interface ConnectionListener {
    void onNewConnection(Connection connection);
}
