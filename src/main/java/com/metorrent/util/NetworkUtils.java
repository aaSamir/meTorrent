package com.metorrent.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * Small networking helpers: LAN address discovery, port probing and
 * TCP-connect latency measurement (used for the peer table's "Ping" column).
 */
public final class NetworkUtils {

    private static final Logger log = LoggerFactory.getLogger(NetworkUtils.class);

    public static final int MIN_PORT = 1024;
    public static final int MAX_PORT = 65535;

    private NetworkUtils() {
    }

    /**
     * Best-effort discovery of this machine's LAN-facing IPv4 address
     * (i.e. not loopback, not link-local, and belonging to an interface
     * that is up). Falls back to the loopback address if nothing suitable
     * is found, which still works for same-machine demos.
     */
    public static String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp() || iface.isVirtual()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address.isLoopbackAddress() || address.isLinkLocalAddress()) {
                        continue;
                    }
                    if (address.getAddress().length == 4) { // prefer IPv4
                        return address.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            log.warn("Could not enumerate network interfaces, falling back to loopback", e);
        }
        return InetAddress.getLoopbackAddress().getHostAddress();
    }

    public static List<String> getAllLocalIpAddresses() {
        List<String> result = new java.util.ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!address.isLinkLocalAddress()) {
                        result.add(address.getHostAddress());
                    }
                }
            }
        } catch (SocketException e) {
            log.warn("Could not enumerate network interfaces", e);
        }
        return Collections.unmodifiableList(result);
    }

    public static boolean isValidPort(int port) {
        return port >= MIN_PORT && port <= MAX_PORT;
    }

    public static boolean isPortAvailable(int port) {
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(port));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Finds an available port, starting the scan at {@code preferredPort}. */
    public static int findAvailablePort(int preferredPort) {
        int port = preferredPort;
        while (!isPortAvailable(port)) {
            port++;
            if (port > MAX_PORT) {
                throw new IllegalStateException("No available ports found starting from " + preferredPort);
            }
        }
        return port;
    }

    /**
     * Measures round-trip TCP connect latency to a peer, in milliseconds.
     * Returns -1 if the peer could not be reached within the timeout.
     */
    public static long measureLatency(String host, int port, int timeoutMs) {
        long start = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            long elapsedNanos = System.nanoTime() - start;
            return elapsedNanos / 1_000_000;
        } catch (SocketTimeoutException e) {
            return -1;
        } catch (IOException e) {
            log.debug("Latency probe to {}:{} failed: {}", host, port, e.getMessage());
            return -1;
        }
    }
}
