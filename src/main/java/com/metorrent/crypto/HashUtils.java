package com.metorrent.crypto;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-256 hashing helpers used for chunk checksums and whole-file integrity
 * verification. Every method is stateless and thread-safe; a fresh
 * {@link MessageDigest} is created per call since MessageDigest itself is not
 * thread-safe.
 */
public final class HashUtils {

    public static final String ALGORITHM = "SHA-256";
    private static final int STREAM_BUFFER_SIZE = 64 * 1024;
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    private HashUtils() {
    }

    public static byte[] sha256(byte[] data) {
        return sha256(data, 0, data.length);
    }

    public static byte[] sha256(byte[] data, int offset, int length) {
        MessageDigest digest = newDigest();
        digest.update(data, offset, length);
        return digest.digest();
    }

    /** Streams the file's contents through SHA-256 without loading it into memory. */
    public static byte[] sha256(Path file) throws IOException {
        MessageDigest digest = newDigest();
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[STREAM_BUFFER_SIZE];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return digest.digest();
    }

    public static String sha256Hex(byte[] data) {
        return toHex(sha256(data));
    }

    public static String sha256Hex(byte[] data, int offset, int length) {
        return toHex(sha256(data, offset, length));
    }

    public static String sha256Hex(Path file) throws IOException {
        return toHex(sha256(file));
    }

    public static boolean matches(byte[] data, String expectedHex) {
        return toHex(sha256(data)).equalsIgnoreCase(expectedHex);
    }

    public static boolean matches(byte[] data, int offset, int length, String expectedHex) {
        return toHex(sha256(data, offset, length)).equalsIgnoreCase(expectedHex);
    }

    public static String toHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[i * 2] = HEX_CHARS[v >>> 4];
            out[i * 2 + 1] = HEX_CHARS[v & 0x0F];
        }
        return new String(out);
    }

    public static byte[] fromHex(String hex) {
        int len = hex.length();
        if (len % 2 != 0) {
            throw new IllegalArgumentException("Hex string must have an even length: " + hex);
        }
        byte[] out = new byte[len / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(hex.charAt(i * 2), 16);
            int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("Invalid hex string: " + hex);
            }
            out[i] = (byte) ((hi << 4) + lo);
        }
        return out;
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available on every standard JVM implementation.
            throw new IllegalStateException(ALGORITHM + " not available", e);
        }
    }
}
