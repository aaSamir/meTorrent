package com.metorrent.filesystem;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharedFolderTest {

    @Test
    void addFileCopiesIntoSharedDirectoryAndIndexesIt(@TempDir Path shareDir, @TempDir Path sourceDir) throws IOException {
        SharedFolder folder = new SharedFolder(shareDir, "Alice");
        Path source = sourceDir.resolve("doc.txt");
        Files.writeString(source, "content");

        SharedFile added = folder.addFile(source);

        assertEquals("doc.txt", added.name());
        assertTrue(Files.exists(shareDir.resolve("doc.txt")));
        assertEquals(1, folder.list().size());
    }

    @Test
    void addFileNeverOverwritesExistingFile(@TempDir Path shareDir, @TempDir Path sourceDir) throws IOException {
        SharedFolder folder = new SharedFolder(shareDir, "Alice");
        Files.writeString(shareDir.resolve("doc.txt"), "original");
        Path source = sourceDir.resolve("doc.txt");
        Files.writeString(source, "incoming");

        SharedFile added = folder.addFile(source);

        assertEquals("doc (1).txt", added.name());
        assertEquals("original", Files.readString(shareDir.resolve("doc.txt")));
    }

    @Test
    void deleteFileRemovesFromDiskAndIndex(@TempDir Path shareDir) throws IOException {
        Files.writeString(shareDir.resolve("doc.txt"), "content");
        SharedFolder folder = new SharedFolder(shareDir, "Alice");
        String fileId = folder.list().get(0).fileId();

        folder.deleteFile(fileId);

        assertFalse(Files.exists(shareDir.resolve("doc.txt")));
        assertTrue(folder.list().isEmpty());
    }

    @Test
    void renameFileMovesOnDiskAndReturnsUpdatedEntry(@TempDir Path shareDir) throws IOException {
        Files.writeString(shareDir.resolve("old.txt"), "content");
        SharedFolder folder = new SharedFolder(shareDir, "Alice");
        String fileId = folder.list().get(0).fileId();

        SharedFile renamed = folder.renameFile(fileId, "new.txt");

        assertEquals("new.txt", renamed.name());
        assertTrue(Files.exists(shareDir.resolve("new.txt")));
        assertFalse(Files.exists(shareDir.resolve("old.txt")));
    }

    @Test
    void renameFileRejectsCollisionWithExistingFile(@TempDir Path shareDir) throws IOException {
        Files.writeString(shareDir.resolve("a.txt"), "a");
        Files.writeString(shareDir.resolve("b.txt"), "b");
        SharedFolder folder = new SharedFolder(shareDir, "Alice");
        String aId = folder.list().stream().filter(f -> f.name().equals("a.txt")).findFirst().orElseThrow().fileId();

        assertThrows(IOException.class, () -> folder.renameFile(aId, "b.txt"));
    }

    @Test
    void findByIdReturnsEmptyForUnknownId(@TempDir Path shareDir) {
        SharedFolder folder = new SharedFolder(shareDir, "Alice");
        Optional<SharedFile> result = folder.findById("nonexistent");
        assertTrue(result.isEmpty());
    }

    @Test
    void refreshPicksUpFilesAddedOutOfBand(@TempDir Path shareDir) throws IOException {
        SharedFolder folder = new SharedFolder(shareDir, "Alice");
        assertTrue(folder.list().isEmpty());

        Files.writeString(shareDir.resolve("external.txt"), "added directly on disk");
        folder.refresh();

        List<SharedFile> files = folder.list();
        assertEquals(1, files.size());
        assertEquals("external.txt", files.get(0).name());
    }

    @Test
    void constructorCreatesSharedDirectoryIfMissing(@TempDir Path parent) {
        Path notYetCreated = parent.resolve("shared");
        new SharedFolder(notYetCreated, "Alice");
        assertTrue(Files.isDirectory(notYetCreated));
    }
}
