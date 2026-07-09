package com.metorrent.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileUtilsTest {

    @Test
    void humanReadableSizeFormatsAcrossUnits() {
        assertEquals("0 B", FileUtils.humanReadableSize(0));
        assertEquals("512 B", FileUtils.humanReadableSize(512));
        assertEquals("1.0 KB", FileUtils.humanReadableSize(1024));
        assertEquals("1.5 MB", FileUtils.humanReadableSize((long) (1.5 * 1024 * 1024)));
        assertEquals("2.0 GB", FileUtils.humanReadableSize(2L * 1024 * 1024 * 1024));
    }

    @Test
    void extensionOfHandlesNormalAndMissingExtensions() {
        assertEquals("txt", FileUtils.extensionOf("notes.txt"));
        assertEquals("", FileUtils.extensionOf("README"));
        assertEquals("gz", FileUtils.extensionOf("archive.tar.gz"));
    }

    @Test
    void categoryOfClassifiesKnownExtensions() {
        assertEquals("Document", FileUtils.categoryOf("report.pdf"));
        assertEquals("Image", FileUtils.categoryOf("photo.png"));
        assertEquals("Video", FileUtils.categoryOf("movie.mp4"));
        assertEquals("Archive", FileUtils.categoryOf("backup.zip"));
        assertEquals("File", FileUtils.categoryOf("noextension"));
    }

    @Test
    void sanitizeFileNameStripsPathTraversal() {
        assertEquals("passwd", FileUtils.sanitizeFileName("../../etc/passwd"));
        assertEquals("passwd", FileUtils.sanitizeFileName("..\\..\\windows\\passwd"));
        assertEquals("unnamed_file", FileUtils.sanitizeFileName(".."));
        assertEquals("my_file.txt", FileUtils.sanitizeFileName("my:file.txt"));
    }

    @Test
    void sanitizeFileNameKeepsSimpleNamesUnchanged() {
        assertEquals("document.pdf", FileUtils.sanitizeFileName("document.pdf"));
    }

    @Test
    void ensureDirectoryCreatesMissingDirectories(@TempDir Path tempDir) throws IOException {
        Path nested = tempDir.resolve("a/b/c");
        FileUtils.ensureDirectory(nested);
        assertTrue(Files.isDirectory(nested));
    }

    @Test
    void uniqueDestinationReturnsSamePathWhenNoCollision(@TempDir Path tempDir) {
        Path dest = FileUtils.uniqueDestination(tempDir, "file.txt");
        assertEquals(tempDir.resolve("file.txt"), dest);
    }

    @Test
    void uniqueDestinationAvoidsCollisions(@TempDir Path tempDir) throws IOException {
        Files.createFile(tempDir.resolve("file.txt"));
        Path dest = FileUtils.uniqueDestination(tempDir, "file.txt");
        assertNotEquals(tempDir.resolve("file.txt"), dest);
        assertEquals(tempDir.resolve("file (1).txt"), dest);
        assertFalse(Files.exists(dest));
    }
}
