package com.metorrent.filesystem;

import com.metorrent.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Owns one local directory that this peer shares with others: keeps an
 * in-memory index of its contents and exposes add/delete/rename/refresh
 * operations. Not thread-safe by design - callers (the GUI controller /
 * PeerServer request handler) are expected to serialize access, typically
 * by only mutating it from one dedicated thread or under an external lock.
 */
public class SharedFolder {

    private static final Logger log = LoggerFactory.getLogger(SharedFolder.class);

    private final Path directory;
    private final String owner;
    private final FileScanner scanner;
    private Map<String, SharedFile> index = new LinkedHashMap<>();

    public SharedFolder(Path directory, String owner) {
        this(directory, owner, new FileScanner());
    }

    public SharedFolder(Path directory, String owner, FileScanner scanner) {
        this.directory = directory;
        this.owner = owner;
        this.scanner = scanner;
        try {
            FileUtils.ensureDirectory(directory);
        } catch (IOException e) {
            log.error("Could not create shared directory {}", directory, e);
        }
        refresh();
    }

    public Path getDirectory() {
        return directory;
    }

    public synchronized void refresh() {
        List<SharedFile> scanned = scanner.scan(directory, owner);
        Map<String, SharedFile> newIndex = new LinkedHashMap<>();
        for (SharedFile file : scanned) {
            newIndex.put(file.fileId(), file);
        }
        this.index = newIndex;
        log.debug("Refreshed shared folder {}: {} files", directory, index.size());
    }

    public synchronized List<SharedFile> list() {
        return index.values().stream()
                .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                .collect(Collectors.toUnmodifiableList());
    }

    public synchronized Optional<SharedFile> findById(String fileId) {
        return Optional.ofNullable(index.get(fileId));
    }

    /** Copies {@code source} into the shared directory (never overwriting an existing file) and re-indexes. */
    public synchronized SharedFile addFile(Path source) throws IOException {
        if (!Files.isRegularFile(source)) {
            throw new IOException("Not a regular file: " + source);
        }
        Path destination = FileUtils.uniqueDestination(directory, source.getFileName().toString());
        Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES);
        refresh();
        return findById(FileScanner.fileIdFor(destination.toAbsolutePath().normalize()))
                .orElseThrow(() -> new IOException("File was added but could not be re-indexed: " + destination));
    }

    public synchronized void deleteFile(String fileId) throws IOException {
        SharedFile file = index.get(fileId);
        if (file == null) {
            throw new IOException("No shared file with id " + fileId);
        }
        Files.deleteIfExists(file.path());
        refresh();
    }

    public synchronized SharedFile renameFile(String fileId, String newName) throws IOException {
        SharedFile file = index.get(fileId);
        if (file == null) {
            throw new IOException("No shared file with id " + fileId);
        }
        String safeName = FileUtils.sanitizeFileName(newName);
        Path renamed = directory.resolve(safeName);
        if (Files.exists(renamed)) {
            throw new IOException("A file named '" + safeName + "' already exists");
        }
        Files.move(file.path(), renamed);
        refresh();
        return findById(FileScanner.fileIdFor(renamed.toAbsolutePath().normalize()))
                .orElseThrow(() -> new IOException("File was renamed but could not be re-indexed: " + renamed));
    }
}
