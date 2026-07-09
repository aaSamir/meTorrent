package com.metorrent.network;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/** Opens outgoing TCP connections to other peers. */
public class PeerClient {

    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 5000;

    public Connection connect(String host, int port) throws IOException {
        return connect(host, port, DEFAULT_CONNECT_TIMEOUT_MS);
    }

    public Connection connect(String host, int port, int timeoutMs) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        return new Connection(socket);
    }
}
