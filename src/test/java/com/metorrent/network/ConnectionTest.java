package com.metorrent.network;

import com.metorrent.protocol.Message;
import com.metorrent.protocol.MessageType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionTest {

    private ServerSocket serverSocket;
    private Connection client;
    private Connection server;

    @BeforeEach
    void setUp() throws IOException {
        serverSocket = new ServerSocket(0);
        Socket clientSocket = new Socket("127.0.0.1", serverSocket.getLocalPort());
        Socket serverSideSocket = serverSocket.accept();
        client = new Connection(clientSocket);
        server = new Connection(serverSideSocket);
    }

    @AfterEach
    void tearDown() throws IOException {
        client.close();
        server.close();
        serverSocket.close();
    }

    @Test
    void sendMessageRoundTripsExactContent() throws IOException {
        Message sent = Message.builder(MessageType.PING).field("nonce", 42).build();
        client.sendMessage(sent);

        Frame frame = server.readFrame();

        assertTrue(frame.isControl());
        String json = new String(frame.payload(), StandardCharsets.UTF_8);
        assertTrue(json.contains("PING"));
        assertTrue(json.contains("42"));
    }

    @Test
    void sendBinaryRoundTripsExactBytes() throws IOException {
        byte[] chunk = new byte[]{1, 2, 3, 4, 5, (byte) 250};
        client.sendBinary(chunk);

        Frame frame = server.readFrame();

        assertTrue(frame.isBinary());
        assertArrayEquals(chunk, frame.payload());
    }

    @Test
    void multipleFramesArriveInOrder() throws IOException {
        client.sendMessage(Message.builder(MessageType.PING).build());
        client.sendBinary(new byte[]{9, 9, 9});
        client.sendMessage(Message.builder(MessageType.PONG).build());

        assertTrue(server.readFrame().isControl());
        assertTrue(server.readFrame().isBinary());
        assertTrue(server.readFrame().isControl());
    }

    @Test
    void closeMakesIsClosedTrue() {
        client.close();
        assertTrue(client.isClosed());
    }

    @Test
    void readFrameThrowsWhenPeerClosesConnection() throws IOException {
        client.close();
        assertThrows(IOException.class, () -> server.readFrame());
    }

    @Test
    void oversizedFrameLengthIsRejected() throws Exception {
        // Bypass Connection's own writeFrame() to simulate a hostile/corrupt peer
        // announcing a frame far larger than MAX_FRAME_SIZE.
        ServerSocket secondServer = new ServerSocket(0);
        java.util.concurrent.CompletableFuture<Connection> accepted = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                return new Connection(secondServer.accept());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        Socket rawClientSocket = new Socket("127.0.0.1", secondServer.getLocalPort());
        Connection serverSideConnection = accepted.get(5, java.util.concurrent.TimeUnit.SECONDS);

        DataOutputStream rawOut = new DataOutputStream(rawClientSocket.getOutputStream());
        rawOut.writeInt(Connection.MAX_FRAME_SIZE + 1000);
        rawOut.flush();

        assertThrows(IOException.class, serverSideConnection::readFrame);

        rawClientSocket.close();
        serverSideConnection.close();
        secondServer.close();
    }

    @Test
    void connectionIdIsUniquePerInstance() {
        assertTrue(!client.getConnectionId().equals(server.getConnectionId()));
    }
}
