package com.metorrent.config;

import java.time.Instant;

/** A recently-used manual connection (IP + port), most recent first. */
public class RecentConnection {

    private String host;
    private int port;
    private Instant lastConnected;

    public RecentConnection() {
        // required by Jackson
    }

    public RecentConnection(String host, int port, Instant lastConnected) {
        this.host = host;
        this.port = port;
        this.lastConnected = lastConnected;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public Instant getLastConnected() {
        return lastConnected;
    }

    public void setLastConnected(Instant lastConnected) {
        this.lastConnected = lastConnected;
    }

    public String address() {
        return host + ":" + port;
    }
}
