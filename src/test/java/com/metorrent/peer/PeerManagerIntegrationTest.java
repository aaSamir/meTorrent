package com.metorrent.peer;

import com.metorrent.network.MessageDispatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PeerManagerIntegrationTest {

    private PeerManager alice;
    private PeerManager bob;

    @AfterEach
    void tearDown() {
        if (alice != null) alice.shutdown();
        if (bob != null) bob.shutdown();
    }

    @Test
    void connectingPerformsHelloHandshakeOnBothSides() throws Exception {
        alice = new PeerManager("Alice", 0, new PeerRegistry(), new MessageDispatcher());
        alice.start();
        bob = new PeerManager("Bob", 0, new PeerRegistry(), new MessageDispatcher());
        bob.start();

        alice.connect("127.0.0.1", bob.getActualPort());

        // Wait for both sides to complete the symmetric HELLO handshake.
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline
                && (alice.getRegistry().list().isEmpty() || bob.getRegistry().list().isEmpty())) {
            Thread.sleep(20);
        }

        assertEquals(1, alice.getRegistry().list().size());
        assertEquals(1, bob.getRegistry().list().size());

        Peer bobAsSeenByAlice = alice.getRegistry().list().iterator().next();
        Peer aliceAsSeenByBob = bob.getRegistry().list().iterator().next();

        assertEquals("Bob", bobAsSeenByAlice.getName());
        assertEquals("Alice", aliceAsSeenByBob.getName());
        assertEquals(PeerStatus.CONNECTED, bobAsSeenByAlice.getStatus());
        assertEquals(PeerStatus.CONNECTED, aliceAsSeenByBob.getStatus());
    }

    @Test
    void disconnectMarksPeerDisconnectedOnBothEnds() throws Exception {
        alice = new PeerManager("Alice", 0, new PeerRegistry(), new MessageDispatcher());
        alice.start();
        bob = new PeerManager("Bob", 0, new PeerRegistry(), new MessageDispatcher());
        bob.start();

        alice.connect("127.0.0.1", bob.getActualPort());
        waitUntil(() -> !alice.getRegistry().list().isEmpty() && !bob.getRegistry().list().isEmpty());

        String bobPeerId = alice.getRegistry().list().iterator().next().getPeerId();
        alice.disconnect(bobPeerId);

        waitUntil(() -> alice.getRegistry().get(bobPeerId).map(p -> p.getStatus() == PeerStatus.DISCONNECTED).orElse(false));
        assertEquals(PeerStatus.DISCONNECTED, alice.getRegistry().get(bobPeerId).orElseThrow().getStatus());

        // Bob's side should also observe the disconnect notice / closed socket.
        waitUntil(() -> bob.getRegistry().list().iterator().next().getStatus() == PeerStatus.DISCONNECTED);
    }

    private void waitUntil(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline && !condition.getAsBoolean()) {
            Thread.sleep(20);
        }
        assertTrue(condition.getAsBoolean(), "Condition was not met within timeout");
    }
}
