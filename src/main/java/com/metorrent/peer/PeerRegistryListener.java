package com.metorrent.peer;

/** Observer notified whenever the peer table changes, so the GUI can stay in sync. */
public interface PeerRegistryListener {
    void onPeerAdded(Peer peer);

    void onPeerUpdated(Peer peer);

    void onPeerRemoved(Peer peer);
}
