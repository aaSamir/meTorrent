package com.metorrent.peer;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Thread-safe table of known peers (connected and previously-connected). */
public class PeerRegistry {

    private final ConcurrentHashMap<String, Peer> peers = new ConcurrentHashMap<>();
    private final List<PeerRegistryListener> listeners = new CopyOnWriteArrayList<>();

    public void addListener(PeerRegistryListener listener) {
        listeners.add(listener);
    }

    public void removeListener(PeerRegistryListener listener) {
        listeners.remove(listener);
    }

    public void put(Peer peer) {
        boolean isNew = peers.put(peer.getPeerId(), peer) == null;
        notifyListeners(peer, isNew);
    }

    public void notifyUpdated(Peer peer) {
        for (PeerRegistryListener listener : listeners) {
            listener.onPeerUpdated(peer);
        }
    }

    public Optional<Peer> get(String peerId) {
        return Optional.ofNullable(peers.get(peerId));
    }

    public Optional<Peer> findByConnectionId(String connectionId) {
        return peers.values().stream()
                .filter(p -> p.getConnection() != null && p.getConnection().getConnectionId().equals(connectionId))
                .findFirst();
    }

    public void remove(String peerId) {
        Peer removed = peers.remove(peerId);
        if (removed != null) {
            for (PeerRegistryListener listener : listeners) {
                listener.onPeerRemoved(removed);
            }
        }
    }

    public Collection<Peer> list() {
        return List.copyOf(peers.values());
    }

    private void notifyListeners(Peer peer, boolean isNew) {
        for (PeerRegistryListener listener : listeners) {
            if (isNew) {
                listener.onPeerAdded(peer);
            } else {
                listener.onPeerUpdated(peer);
            }
        }
    }
}
