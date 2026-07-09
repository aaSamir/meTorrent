package com.metorrent.transfer;

import java.time.Duration;
import java.time.Instant;

/**
 * Mutable, GUI-observable state for one upload or download. Progress and
 * speed are updated from whichever thread is driving the transfer
 * (an UploadTask's own thread, or the network reader thread for a
 * DownloadTask) and read from the JavaFX thread for the Transfers table,
 * so every field is volatile and mutation is synchronized.
 */
public class Transfer {

    private final String transferId;
    private final TransferDirection direction;
    private final String peerId;
    private final String peerName;
    private final String fileName;
    private final Instant startTime = Instant.now();

    private volatile long fileSize;
    private volatile int chunkSize;
    private volatile int totalChunks;
    private volatile TransferStatus status = TransferStatus.PENDING;
    private volatile int chunksCompleted = 0;
    private volatile long bytesTransferred = 0;
    private volatile double speedBytesPerSecond = 0;
    private volatile String errorMessage;

    private Instant lastProgressTime = startTime;
    private long bytesAtLastProgressUpdate = 0;

    public Transfer(String transferId, TransferDirection direction, String peerId, String peerName,
                     String fileName, long fileSize, int chunkSize) {
        this.transferId = transferId;
        this.direction = direction;
        this.peerId = peerId;
        this.peerName = peerName;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.chunkSize = chunkSize;
        this.totalChunks = ChunkManager.totalChunks(fileSize, chunkSize);
    }

    public String getTransferId() {
        return transferId;
    }

    public TransferDirection getDirection() {
        return direction;
    }

    public String getPeerId() {
        return peerId;
    }

    public String getPeerName() {
        return peerName;
    }

    public String getFileName() {
        return fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    /** For downloads, the real size/chunking isn't known until FILE_METADATA arrives. */
    public synchronized void applyMetadata(long fileSize, int chunkSize, int totalChunks) {
        this.fileSize = fileSize;
        this.chunkSize = chunkSize;
        this.totalChunks = totalChunks;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public void setStatus(TransferStatus status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public int getChunksCompleted() {
        return chunksCompleted;
    }

    public long getBytesTransferred() {
        return bytesTransferred;
    }

    public double getSpeedBytesPerSecond() {
        return speedBytesPerSecond;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public synchronized void recordChunkCompleted(int chunksCompleted, long bytesTransferred) {
        Instant now = Instant.now();
        double elapsedSeconds = Duration.between(lastProgressTime, now).toMillis() / 1000.0;
        if (elapsedSeconds > 0.001) {
            long deltaBytes = bytesTransferred - bytesAtLastProgressUpdate;
            this.speedBytesPerSecond = deltaBytes / elapsedSeconds;
            this.lastProgressTime = now;
            this.bytesAtLastProgressUpdate = bytesTransferred;
        }
        this.chunksCompleted = chunksCompleted;
        this.bytesTransferred = bytesTransferred;
    }

    public double getProgressFraction() {
        if (status == TransferStatus.COMPLETED) {
            return 1.0;
        }
        return totalChunks <= 0 ? 0.0 : (double) chunksCompleted / totalChunks;
    }

    /** Estimated seconds remaining, or -1 if it cannot be estimated yet (no speed data). */
    public long getEtaSeconds() {
        if (speedBytesPerSecond <= 0 || status != TransferStatus.IN_PROGRESS) {
            return -1;
        }
        long remaining = fileSize - bytesTransferred;
        return (long) (remaining / speedBytesPerSecond);
    }

    @Override
    public String toString() {
        return "Transfer{" + transferId + ", " + direction + " '" + fileName + "', status=" + status + '}';
    }
}
