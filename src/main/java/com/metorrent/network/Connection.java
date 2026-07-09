package com.metorrent.network;

import com.metorrent.protocol.Message;
import com.metorrent.protocol.ProtocolSerializer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.UUID;

/**
 * A single TCP connection to a peer, speaking the meTorrent frame protocol
 * (see {@code docs/PROTOCOL.md}). Writes are synchronized so control
 * messages and chunk data from different threads never interleave
 * mid-frame; reads are expected to happen from exactly one thread at a
 * time (the {@link MessageDispatcher} read loop), so they need no locking.
 */
public class Connection implements AutoCloseable {

    /** Refuses to allocate a buffer for a frame larger than this, guarding against a hostile/corrupt length prefix. */
    public static final int MAX_FRAME_SIZE = 8 * 1024 * 1024;

    private final String connectionId = UUID.randomUUID().toString();
    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;
    private final ProtocolSerializer serializer = new ProtocolSerializer();
    private final Object writeLock = new Object();

    public Connection(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
    }

    public String getConnectionId() {
        return connectionId;
    }

    public String getRemoteHost() {
        return socket.getInetAddress().getHostAddress();
    }

    public int getRemotePort() {
        return socket.getPort();
    }

    public void sendMessage(Message message) throws IOException {
        writeFrame(Frame.TYPE_CONTROL, serializer.serialize(message));
    }

    public void sendBinary(byte[] data) throws IOException {
        writeFrame(Frame.TYPE_BINARY, data);
    }

    private void writeFrame(byte type, byte[] payload) throws IOException {
        synchronized (writeLock) {
            out.writeInt(payload.length + 1);
            out.writeByte(type);
            out.write(payload);
            out.flush();
        }
    }

    /** Blocks until the next full frame has arrived. Throws IOException (typically EOFException) when the peer disconnects. */
    public Frame readFrame() throws IOException {
        int length = in.readInt();
        if (length < 1 || length > MAX_FRAME_SIZE) {
            throw new IOException("Invalid or oversized frame length: " + length);
        }
        byte type = in.readByte();
        byte[] payload = new byte[length - 1];
        in.readFully(payload);
        return new Frame(type, payload);
    }

    public boolean isClosed() {
        return socket.isClosed();
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // Already closed or unusable; nothing further to do.
        }
    }

    @Override
    public String toString() {
        return "Connection{" + getRemoteHost() + ":" + getRemotePort() + '}';
    }
}
