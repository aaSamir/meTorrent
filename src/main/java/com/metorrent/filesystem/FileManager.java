package com.metorrent.filesystem;

import com.metorrent.util.FileUtils;

import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

/**
 * Facade combining the local {@link SharedFolder} with the download
 * directory and the byte-range I/O that {@code transfer} needs to read
 * chunks for upload. Keeps the transfer layer from having to know anything
 * about how files are indexed or where downloads land on disk.
 */
public class FileManager {

    private final SharedFolder sharedFolder;
    private final Path downloadFolder;

    public FileManager(Path sharedDirectory, Path downloadDirectory, String owner) throws IOException {
        this.sharedFolder = new SharedFolder(sharedDirectory, owner);
        this.downloadFolder = downloadDirectory;
        FileUtils.ensureDirectory(downloadDirectory);
    }

    public SharedFolder getSharedFolder() {
        return sharedFolder;
    }

    public Path getDownloadFolder() {
        return downloadFolder;
    }

    /** Resolves a non-colliding destination path for an incoming download. */
    public Path resolveDownloadDestination(String remoteFileName) {
        return FileUtils.uniqueDestination(downloadFolder, remoteFileName);
    }

    /** Reads exactly {@code length} bytes starting at {@code offset} from a locally shared file. */
    public byte[] readChunk(SharedFile file, long offset, int length) throws IOException {
        byte[] buffer = new byte[length];
        try (RandomAccessFile raf = new RandomAccessFile(file.path().toFile(), "r")) {
            raf.seek(offset);
            int totalRead = 0;
            while (totalRead < length) {
                int read = raf.read(buffer, totalRead, length - totalRead);
                if (read == -1) {
                    throw new EOFException("Unexpected end of file at offset " + (offset + totalRead)
                            + " while reading " + file.name());
                }
                totalRead += read;
            }
        }
        return buffer;
    }
}
