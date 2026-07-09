package com.metorrent.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkUtilsTest {

    @Test
    void isValidPortRejectsOutOfRangeValues() {
        assertFalse(NetworkUtils.isValidPort(80));
        assertFalse(NetworkUtils.isValidPort(70000));
        assertTrue(NetworkUtils.isValidPort(6881));
    }

    @Test
    void isPortAvailableDetectsBoundPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            int boundPort = socket.getLocalPort();
            assertFalse(NetworkUtils.isPortAvailable(boundPort));
        }
    }

    @Test
    void findAvailablePortSkipsOccupiedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            int boundPort = socket.getLocalPort();
            int found = NetworkUtils.findAvailablePort(boundPort);
            assertTrue(found >= boundPort);
            assertTrue(NetworkUtils.isPortAvailable(found));
        }
    }

    @Test
    void measureLatencyReturnsNonNegativeForReachableLocalPeer() throws IOException {
        try (ServerSocket server = new ServerSocket(0)) {
            long latency = NetworkUtils.measureLatency("127.0.0.1", server.getLocalPort(), 1000);
            assertTrue(latency >= 0, "Expected a non-negative latency measurement to a reachable local port");
        }
    }

    @Test
    void measureLatencyReturnsMinusOneForClosedPort() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        // Port is now closed again; a connection attempt should fail fast.
        long latency = NetworkUtils.measureLatency("127.0.0.1", closedPort, 500);
        assertEquals(-1, latency);
    }

    @Test
    void getLocalIpAddressReturnsNonEmptyValue() {
        assertFalse(NetworkUtils.getLocalIpAddress().isEmpty());
    }
}
