package com.metorrent.network;

import com.metorrent.protocol.Message;
import com.metorrent.protocol.ProtocolParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs one read loop per {@link Connection} on a background thread, parses
 * incoming frames, and broadcasts them to every registered
 * {@link MessageListener} (observer pattern). Decouples socket I/O from the
 * peer/transfer logic that reacts to messages.
 */
public class MessageDispatcher {

    private static final Logger log = LoggerFactory.getLogger(MessageDispatcher.class);

    private final List<MessageListener> listeners = new CopyOnWriteArrayList<>();
    private final ProtocolParser parser = new ProtocolParser();
    private final ExecutorService executor;

    public MessageDispatcher() {
        AtomicInteger counter = new AtomicInteger();
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "conn-reader-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    public void addListener(MessageListener listener) {
        listeners.add(listener);
    }

    public void removeListener(MessageListener listener) {
        listeners.remove(listener);
    }

    /** Starts an asynchronous read loop for this connection; returns immediately. */
    public void listen(Connection connection) {
        executor.submit(() -> readLoop(connection));
    }

    private void readLoop(Connection connection) {
        Throwable closeCause = null;
        try {
            while (!connection.isClosed()) {
                Frame frame = connection.readFrame();
                dispatch(connection, frame);
            }
        } catch (IOException e) {
            closeCause = e;
        } finally {
            connection.close();
            for (MessageListener listener : listeners) {
                listener.onConnectionClosed(connection, closeCause);
            }
        }
    }

    private void dispatch(Connection connection, Frame frame) {
        if (frame.isControl()) {
            Message message;
            try {
                message = parser.parse(frame.payload());
            } catch (RuntimeException e) {
                log.warn("Dropping malformed message from {}: {}", connection, e.getMessage());
                return;
            }
            for (MessageListener listener : listeners) {
                listener.onMessage(connection, message);
            }
        } else {
            for (MessageListener listener : listeners) {
                listener.onBinaryFrame(connection, frame.payload());
            }
        }
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
