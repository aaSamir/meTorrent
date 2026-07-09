package com.metorrent.transfer;

/**
 * Pure chunk-index arithmetic shared by {@link UploadTask} and
 * {@link DownloadTask}: how many chunks a file splits into, where each
 * chunk starts, how long it is (the last chunk is usually shorter), and
 * how a downloader's "bytes already on disk" maps to a starting chunk
 * index when resuming.
 */
public final class ChunkManager {

    private ChunkManager() {
    }

    public static int totalChunks(long fileSize, int chunkSize) {
        if (fileSize <= 0) {
            return 0;
        }
        return (int) ((fileSize + chunkSize - 1) / chunkSize);
    }

    public static long offsetOf(int sequenceNumber, int chunkSize) {
        return (long) sequenceNumber * chunkSize;
    }

    public static int lengthOf(int sequenceNumber, long fileSize, int chunkSize) {
        long offset = offsetOf(sequenceNumber, chunkSize);
        long remaining = fileSize - offset;
        if (remaining <= 0) {
            throw new IllegalArgumentException(
                    "Sequence number " + sequenceNumber + " is out of range for file size " + fileSize);
        }
        return (int) Math.min(chunkSize, remaining);
    }

    /** Rounds a byte count already present on disk down to the nearest whole chunk boundary. */
    public static int chunkIndexForByteOffset(long byteOffset, int chunkSize) {
        return (int) (byteOffset / chunkSize);
    }
}
