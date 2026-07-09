package com.metorrent.filesystem;

import com.metorrent.crypto.HashUtils;
import com.metorrent.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Scans a shared directory's top-level files (non-recursive - meTorrent
 * shares a single flat folder, not a directory tree) and builds
 * {@link SharedFile} entries for each one.
 */
public class FileScanner {

    private static final Logger log = LoggerFactory.getLogger(FileScanner.class);

    /** Deterministic id derived from the file's absolute path, so it stays stable across rescans. */
    public static String fileIdFor(Path absolutePath) {
        return HashUtils.sha256Hex(absolutePath.toString().getBytes(StandardCharsets.UTF_8));
    }

    public List<SharedFile> scan(Path directory, String owner) {
        List<SharedFile> results = new ArrayList<>();
        if (!Files.isDirectory(directory)) {
            log.warn("Shared directory does not exist: {}", directory);
            return results;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry) || isHidden(entry)) {
                    continue;
                }
                try {
                    results.add(toSharedFile(entry, owner));
                } catch (IOException e) {
                    log.warn("Skipping unreadable file {}: {}", entry, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("Failed to scan shared directory {}", directory, e);
        }
        return results;
    }

    private SharedFile toSharedFile(Path entry, String owner) throws IOException {
        BasicFileAttributes attrs = Files.readAttributes(entry, BasicFileAttributes.class);
        Path absolute = entry.toAbsolutePath().normalize();
        String name = entry.getFileName().toString();
        return new SharedFile(
                fileIdFor(absolute),
                name,
                absolute,
                attrs.size(),
                FileUtils.extensionOf(name),
                FileUtils.categoryOf(name),
                Instant.ofEpochMilli(attrs.lastModifiedTime().toMillis()),
                owner);
    }

    private boolean isHidden(Path entry) throws IOException {
        return Files.isHidden(entry);
    }
}
