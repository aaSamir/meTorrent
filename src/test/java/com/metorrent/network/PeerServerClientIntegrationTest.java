package com.metorrent.network;

import com.metorrent.protocol.Message;
import com.metorrent.protocol.MessageType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end test of the whole network layer: a real PeerServer accepting a
 * real PeerClient connection over loopback TCP, with MessageDispatcher
 * routing frames to observers on both sides.
 */
class PeerServerClientIntegrationTest {

    private PeerServer server;
    private final MessageDispatcher serverDispatcher = new MessageDispatcher();
    private final MessageDispatcher clientDispatcher = new MessageDispatcher();

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
        serverDispatcher.shutdown();
        clientDispatcher.shutdown();
    }

    @Test
    void clientCanSendControlMessageToServer() throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        List<Message> messagesReceived = new CopyOnWriteArrayList<>();

        server = new PeerServer(0, connection -> {
            serverDispatcher.listen(connection);
        });
        serverDispatcher.addListener(new RecordingListener(messagesReceived, received));
        server.start();

        PeerClient peerClient = new PeerClient();
        Connection connection = peerClient.connect("127.0.0.1", server.getPort());
        clientDispatcher.listen(connection);

        connection.sendMessage(Message.builder(MessageType.HELLO)
                .field("peerName", "Alice")
                .field("listenPort", 9999)
                .build());

        assertTrue(received.await(5, TimeUnit.SECONDS), "Server never received the HELLO message");
        assertEquals(1, messagesReceived.size());
        assertEquals(MessageType.HELLO, messagesReceived.get(0).getType());
        assertEquals("Alice", messagesReceived.get(0).getString("peerName"));
    }

    @Test
    void chunkHeaderFollowedByBinaryFrameArrivesInOrder() throws Exception {
        CountDownLatch bothReceived = new CountDownLatch(2);
        List<Message> messages = new CopyOnWriteArrayList<>();
        List<byte[]> binaries = new CopyOnWriteArrayList<>();

        server = new PeerServer(0, connection -> serverDispatcher.listen(connection));
        serverDispatcher.addListener(new MessageListener() {
            @Override
            public void onMessage(Connection connection, Message message) {
                messages.add(message);
                bothReceived.countDown();
            }

            @Override
            public void onBinaryFrame(Connection connection, byte[] data) {
                binaries.add(data);
                bothReceived.countDown();
            }

            @Override
            public void onConnectionClosed(Connection connection, Throwable cause) {
            }
        });
        server.start();

        Connection connection = new PeerClient().connect("127.0.0.1", server.getPort());
        Message header = Message.builder(MessageType.FILE_CHUNK)
                .field("sequenceNumber", 0)
                .field("length", 3)
                .build();
        connection.sendMessage(header);
        connection.sendBinary(new byte[]{7, 8, 9});

        assertTrue(bothReceived.await(5, TimeUnit.SECONDS));
        assertEquals(1, messages.size());
        assertEquals(1, binaries.size());
        assertEquals(3, binaries.get(0).length);
    }

    @Test
    void serverNotifiesListenerWhenClientDisconnects() throws Exception {
        CountDownLatch closed = new CountDownLatch(1);

        server = new PeerServer(0, connection -> serverDispatcher.listen(connection));
        serverDispatcher.addListener(new MessageListener() {
            @Override
            public void onMessage(Connection connection, Message message) {
            }

            @Override
            public void onBinaryFrame(Connection connection, byte[] data) {
            }

            @Override
            public void onConnectionClosed(Connection connection, Throwable cause) {
                closed.countDown();
            }
        });
        server.start();

        Connection connection = new PeerClient().connect("127.0.0.1", server.getPort());
        connection.close();

        assertTrue(closed.await(5, TimeUnit.SECONDS), "Server never observed the client disconnecting");
    }

    @Test
    void peerClientConnectFailsFastForClosedPort() throws Exception {
        PeerServer temp = new PeerServer(0, c -> {
        });
        temp.start();
        int port = temp.getPort();
        temp.stop();

        assertTrue(assertThrowsIoException(() -> new PeerClient().connect("127.0.0.1", port, 1000)));
    }

    private boolean assertThrowsIoException(org.junit.jupiter.api.function.Executable executable) {
        try {
            executable.execute();
            return false;
        } catch (Throwable t) {
            return t instanceof java.io.IOException;
        }
    }

    private record RecordingListener(List<Message> sink, CountDownLatch latch) implements MessageListener {
        @Override
        public void onMessage(Connection connection, Message message) {
            sink.add(message);
            latch.countDown();
        }

        @Override
        public void onBinaryFrame(Connection connection, byte[] data) {
        }

        @Override
        public void onConnectionClosed(Connection connection, Throwable cause) {
        }
    }
}
