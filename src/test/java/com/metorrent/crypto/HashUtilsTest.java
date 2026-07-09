package com.metorrent.crypto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HashUtilsTest {

    // Well-known SHA-256 test vector for the empty string.
    private static final String EMPTY_SHA256 =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    @Test
    void sha256HexOfEmptyInputMatchesKnownVector() {
        assertEquals(EMPTY_SHA256, HashUtils.sha256Hex(new byte[0]));
    }

    @Test
    void sha256IsDeterministic() {
        byte[] data = "meTorrent".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(HashUtils.sha256(data), HashUtils.sha256(data));
    }

    @Test
    void differentInputsProduceDifferentHashes() {
        byte[] a = "chunk-a".getBytes(StandardCharsets.UTF_8);
        byte[] b = "chunk-b".getBytes(StandardCharsets.UTF_8);
        assertFalse(HashUtils.sha256Hex(a).equals(HashUtils.sha256Hex(b)));
    }

    @Test
    void hexRoundTripPreservesBytes() {
        byte[] data = "round-trip-me".getBytes(StandardCharsets.UTF_8);
        byte[] digest = HashUtils.sha256(data);
        assertArrayEquals(digest, HashUtils.fromHex(HashUtils.toHex(digest)));
    }

    @Test
    void matchesValidatesChecksum() {
        byte[] data = "verify-me".getBytes(StandardCharsets.UTF_8);
        String hex = HashUtils.sha256Hex(data);
        assertTrue(HashUtils.matches(data, hex));
        assertFalse(HashUtils.matches(data, HashUtils.sha256Hex("tampered".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void offsetLengthHashMatchesSlicedArrayHash() {
        byte[] whole = "0123456789".getBytes(StandardCharsets.UTF_8);
        byte[] slice = "34567".getBytes(StandardCharsets.UTF_8);
        assertEquals(HashUtils.sha256Hex(slice), HashUtils.sha256Hex(whole, 3, 5));
    }

    @Test
    void fileHashMatchesInMemoryHashOfSameContent() throws IOException {
        Path file = Files.createTempFile("metorrent-hash-test", ".bin");
        try {
            byte[] content = new byte[200_000];
            for (int i = 0; i < content.length; i++) {
                content[i] = (byte) (i % 251);
            }
            Files.write(file, content);
            assertEquals(HashUtils.sha256Hex(content), HashUtils.sha256Hex(file));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void fromHexRejectsOddLength() {
        assertThrows(IllegalArgumentException.class, () -> HashUtils.fromHex("abc"));
    }

    @Test
    void fromHexRejectsNonHexCharacters() {
        assertThrows(IllegalArgumentException.class, () -> HashUtils.fromHex("zz"));
    }
}
