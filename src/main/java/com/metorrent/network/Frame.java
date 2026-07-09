package com.metorrent.network;

/**
 * One length-prefixed frame on the wire, per {@code docs/PROTOCOL.md}:
 * either a JSON control message or a raw binary chunk payload.
 */
public record Frame(byte type, byte[] payload) {

    public static final byte TYPE_CONTROL = 0x01;
    public static final byte TYPE_BINARY = 0x02;

    public boolean isControl() {
        return type == TYPE_CONTROL;
    }

    public boolean isBinary() {
        return type == TYPE_BINARY;
    }
}
