package com.metorrent.peer;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PeerRegistryTest {

    @Test
    void putNewPeerFiresOnPeerAdded() {
        PeerRegistry registry = new PeerRegistry();
        AtomicInteger added = new AtomicInteger();
        AtomicInteger updated = new AtomicInteger();
        registry.addListener(new PeerRegistryListener() {
            public void onPeerAdded(Peer peer) { added.incrementAndGet(); }
            public void onPeerUpdated(Peer peer) { updated.incrementAndGet(); }
            public void onPeerRemoved(Peer peer) { }
        });

        registry.put(new Peer("127.0.0.1", 7777, "Alice"));

        assertEquals(1, added.get());
        assertEquals(0, updated.get());
    }

    @Test
    void puttingSamePeerIdAgainFiresOnPeerUpdated() {
        PeerRegistry registry = new PeerRegistry();
        AtomicInteger added = new AtomicInteger();
        AtomicInteger updated = new AtomicInteger();
        registry.addListener(new PeerRegistryListener() {
            public void onPeerAdded(Peer peer) { added.incrementAndGet(); }
            public void onPeerUpdated(Peer peer) { updated.incrementAndGet(); }
            public void onPeerRemoved(Peer peer) { }
        });

        registry.put(new Peer("127.0.0.1", 7777, "Alice"));
        registry.put(new Peer("127.0.0.1", 7777, "Alice-renamed"));

        assertEquals(1, added.get());
        assertEquals(1, updated.get());
        assertEquals("Alice-renamed", registry.get("127.0.0.1:7777").orElseThrow().getName());
    }

    @Test
    void removeFiresOnPeerRemoved() {
        PeerRegistry registry = new PeerRegistry();
        AtomicInteger removed = new AtomicInteger();
        registry.addListener(new PeerRegistryListener() {
            public void onPeerAdded(Peer peer) { }
            public void onPeerUpdated(Peer peer) { }
            public void onPeerRemoved(Peer peer) { removed.incrementAndGet(); }
        });

        registry.put(new Peer("127.0.0.1", 7777, "Alice"));
        registry.remove("127.0.0.1:7777");

        assertEquals(1, removed.get());
        assertTrue(registry.get("127.0.0.1:7777").isEmpty());
    }

    @Test
    void listReturnsAllPeers() {
        PeerRegistry registry = new PeerRegistry();
        registry.put(new Peer("127.0.0.1", 7777, "Alice"));
        registry.put(new Peer("127.0.0.1", 7778, "Bob"));

        assertEquals(2, registry.list().size());
    }

    @Test
    void removingUnknownPeerDoesNotFireListener() {
        PeerRegistry registry = new PeerRegistry();
        AtomicInteger removed = new AtomicInteger();
        registry.addListener(new PeerRegistryListener() {
            public void onPeerAdded(Peer peer) { }
            public void onPeerUpdated(Peer peer) { }
            public void onPeerRemoved(Peer peer) { removed.incrementAndGet(); }
        });

        registry.remove("nonexistent:1234");

        assertEquals(0, removed.get());
    }
}
