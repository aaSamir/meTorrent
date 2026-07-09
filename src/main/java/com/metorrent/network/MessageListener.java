package com.metorrent.network;

import com.metorrent.protocol.Message;

/**
 * Observer notified of activity on a {@link Connection}. Multiple listeners
 * (peer management, transfers, GUI event log, ...) can be registered on the
 * same {@link MessageDispatcher}; each receives every event and filters for
 * what it cares about.
 */
public interface MessageListener {

    void onMessage(Connection connection, Message message);

    /** A raw chunk payload, always immediately following a FILE_CHUNK control message on the same connection. */
    void onBinaryFrame(Connection connection, byte[] data);

    void onConnectionClosed(Connection connection, Throwable cause);
}
