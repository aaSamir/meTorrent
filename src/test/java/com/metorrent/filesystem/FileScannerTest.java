package com.metorrent.filesystem;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileScannerTest {

    private final FileScanner scanner = new FileScanner();

    @Test
    void scanFindsTopLevelFilesOnly(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("a.txt"), "hello");
        Files.writeString(dir.resolve("b.png"), "fake-image-bytes");
        Files.createDirectory(dir.resolve("subdir"));
        Files.writeString(dir.resolve("subdir/c.txt"), "should not be found");

        List<SharedFile> files = scanner.scan(dir, "Alice");

        assertEquals(2, files.size());
        assertTrue(files.stream().anyMatch(f -> f.name().equals("a.txt")));
        assertTrue(files.stream().anyMatch(f -> f.name().equals("b.png")));
    }

    @Test
    void scanPopulatesSizeExtensionCategoryAndOwner(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("report.pdf"), "1234567890");

        SharedFile file = scanner.scan(dir, "Alice").get(0);

        assertEquals(10, file.size());
        assertEquals("pdf", file.extension());
        assertEquals("Document", file.category());
        assertEquals("Alice", file.owner());
    }

    @Test
    void fileIdIsStableAcrossRescans(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("a.txt"), "hello");

        String firstId = scanner.scan(dir, "Alice").get(0).fileId();
        String secondId = scanner.scan(dir, "Alice").get(0).fileId();

        assertEquals(firstId, secondId);
    }

    @Test
    void scanOfMissingDirectoryReturnsEmptyList(@TempDir Path dir) {
        Path missing = dir.resolve("does-not-exist");
        assertTrue(scanner.scan(missing, "Alice").isEmpty());
    }
}
