package com.metorrent.filesystem;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileManagerTest {

    @Test
    void readChunkReturnsExactByteRange(@TempDir Path shareDir, @TempDir Path downloadDir) throws IOException {
        Files.writeString(shareDir.resolve("data.bin"), "0123456789", StandardCharsets.UTF_8);
        FileManager manager = new FileManager(shareDir, downloadDir, "Alice");
        SharedFile file = manager.getSharedFolder().list().get(0);

        byte[] chunk = manager.readChunk(file, 3, 5);

        assertArrayEquals("34567".getBytes(StandardCharsets.UTF_8), chunk);
    }

    @Test
    void readChunkThrowsOnOutOfRangeRequest(@TempDir Path shareDir, @TempDir Path downloadDir) throws IOException {
        Files.writeString(shareDir.resolve("data.bin"), "short");
        FileManager manager = new FileManager(shareDir, downloadDir, "Alice");
        SharedFile file = manager.getSharedFolder().list().get(0);

        assertThrows(IOException.class, () -> manager.readChunk(file, 0, 1000));
    }

    @Test
    void resolveDownloadDestinationSanitizesAndDedupesNames(@TempDir Path shareDir, @TempDir Path downloadDir) throws IOException {
        FileManager manager = new FileManager(shareDir, downloadDir, "Alice");

        Path destination = manager.resolveDownloadDestination("../../evil.txt");

        assertEquals(downloadDir.resolve("evil.txt"), destination);
        assertTrue(destination.startsWith(downloadDir));
    }

    @Test
    void constructorCreatesDownloadFolderIfMissing(@TempDir Path shareDir, @TempDir Path parent) throws IOException {
        Path downloadDir = parent.resolve("downloads");
        new FileManager(shareDir, downloadDir, "Alice");
        assertTrue(Files.isDirectory(downloadDir));
    }
}
