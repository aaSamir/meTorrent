package com.metorrent.config;

/**
 * A manually-saved peer entry (name + address) that the user can reconnect
 * to later without re-typing the IP and port.
 */
public class KnownPeer {

    private String name;
    private String host;
    private int port;

    public KnownPeer() {
        // required by Jackson
    }

    public KnownPeer(String name, String host, int port) {
        this.name = name;
        this.host = host;
        this.port = port;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public String address() {
        return host + ":" + port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof KnownPeer other)) return false;
        return port == other.port && java.util.Objects.equals(host, other.host);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(host, port);
    }
}
