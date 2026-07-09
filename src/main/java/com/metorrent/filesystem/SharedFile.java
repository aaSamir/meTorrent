package com.metorrent.filesystem;

import java.nio.file.Path;
import java.time.Instant;

/**
 * A single entry in a shared-file catalogue: either one of our own shared
 * files, or an entry received from a remote peer's FILE_LIST (in which case
 * {@code path} is {@code null} - we don't have the bytes locally yet).
 */
public record SharedFile(
        String fileId,
        String name,
        Path path,
        long size,
        String extension,
        String category,
        Instant lastModified,
        String owner) {
}
