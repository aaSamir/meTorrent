package com.metorrent.transfer;

import com.metorrent.crypto.HashUtils;
import com.metorrent.network.Connection;
import com.metorrent.protocol.Message;
import com.metorrent.protocol.MessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Drives the downloader's side of one file transfer. Unlike UploadTask this
 * is purely reactive: it doesn't loop, it just responds to FILE_METADATA,
 * FILE_CHUNK-header + binary-frame pairs, and FILE_COMPLETE as they arrive
 * on the (dedicated, per-transfer) connection - all delivered sequentially
 * from that connection's single network reader thread, so no locking is
 * needed around the pending-chunk-header handoff.
 */
public class DownloadTask {

    private static final Logger log = LoggerFactory.getLogger(DownloadTask.class);

    private record PendingChunk(int sequenceNumber, long offset, int length, String checksum) {
    }

    private final Connection connection;
    private final Transfer transfer;
    private final String fileId;
    private final Path partPath;
    private final Path finalDestination;
    private final long resumeFromByteOffset;
    private final int localListenPort;
    private final TransferListener listener;

    private RandomAccessFile raf;
    private PendingChunk pendingChunk;
    private boolean cancelled = false;

    public DownloadTask(Connection connection, Transfer transfer, String fileId, Path partPath,
                         Path finalDestination, long resumeFromByteOffset, int localListenPort, TransferListener listener) {
        this.connection = connection;
        this.transfer = transfer;
        this.fileId = fileId;
        this.partPath = partPath;
        this.finalDestination = finalDestination;
        this.resumeFromByteOffset = resumeFromByteOffset;
        this.localListenPort = localListenPort;
        this.listener = listener;
    }

    /** Sends the initial REQUEST_FILE. The rest of the transfer happens via the onXxx callbacks below. */
    public void start() {
        try {
            connection.sendMessage(Message.builder(MessageType.REQUEST_FILE)
                    .field("fileId", fileId)
                    .field("transferId", transfer.getTransferId())
                    .field("resumeFromByteOffset", resumeFromByteOffset)
                    .field("requesterListenPort", localListenPort)
                    .build());
        } catch (IOException e) {
            fail("Failed to send file request: " + e.getMessage());
        }
    }

    public void onMetadataReceived(Message message) {
        try {
            long fileSize = message.getLong("fileSize");
            int chunkSize = message.getInt("chunkSize");
            int totalChunks = message.getInt("totalChunks");
            transfer.applyMetadata(fileSize, chunkSize, totalChunks);

            int resumeFromChunk = ChunkManager.chunkIndexForByteOffset(resumeFromByteOffset, chunkSize);
            Files.createDirectories(partPath.getParent());
            raf = new RandomAccessFile(partPath.toFile(), "rw");

            if (resumeFromChunk > 0) {
                long bytesAlready = ChunkManager.offsetOf(resumeFromChunk, chunkSize);
                transfer.recordChunkCompleted(resumeFromChunk, bytesAlready);
            }
            transfer.setStatus(TransferStatus.IN_PROGRESS);
            notifyChanged();
        } catch (IOException e) {
            fail("Failed to prepare download destination: " + e.getMessage());
        }
    }

    public void onChunkHeader(Message message) {
        pendingChunk = new PendingChunk(
                message.getInt("sequenceNumber"),
                message.getLong("offset"),
                message.getInt("length"),
                message.getString("checksum"));
    }

    public void onBinaryFrame(byte[] data) {
        PendingChunk chunk = pendingChunk;
        pendingChunk = null;
        if (chunk == null) {
            log.warn("Received unexpected binary frame for transfer {} with no pending chunk header", transfer.getTransferId());
            return;
        }

        boolean valid = data.length == chunk.length() && HashUtils.matches(data, chunk.checksum());
        try {
            if (valid) {
                raf.seek(chunk.offset());
                raf.write(data);
                connection.sendMessage(ackMessage(chunk.sequenceNumber(), true));
                transfer.recordChunkCompleted(chunk.sequenceNumber() + 1, chunk.offset() + data.length);
                notifyChanged();
            } else {
                log.warn("Chunk {} failed checksum verification for transfer {}, requesting retransmit",
                        chunk.sequenceNumber(), transfer.getTransferId());
                connection.sendMessage(ackMessage(chunk.sequenceNumber(), false));
            }
        } catch (IOException e) {
            fail("I/O error while writing chunk " + chunk.sequenceNumber() + ": " + e.getMessage());
        }
    }

    public void onFileComplete(Message message) {
        try {
            closeQuietly();
            String expectedHash = message.getString("fileHash");
            String actualHash = HashUtils.sha256Hex(partPath);

            if (actualHash.equalsIgnoreCase(expectedHash)) {
                Files.move(partPath, finalDestination, StandardCopyOption.REPLACE_EXISTING);
                transfer.setStatus(TransferStatus.COMPLETED);
                log.info("Download complete and verified: {}", finalDestination);
            } else {
                log.error("Integrity check failed for {}: expected {} but got {}", transfer.getFileName(), expectedHash, actualHash);
                transfer.setErrorMessage("File integrity check failed - corrupted transfer rejected");
                transfer.setStatus(TransferStatus.FAILED);
                Files.deleteIfExists(partPath);
            }
        } catch (IOException e) {
            fail("Failed to finalize download: " + e.getMessage());
        } finally {
            notifyChanged();
            connection.close();
        }
    }

    public void pause() {
        transfer.setStatus(TransferStatus.PAUSED);
        sendControlMessage(MessageType.TRANSFER_PAUSE);
        notifyChanged();
    }

    public void resume() {
        transfer.setStatus(TransferStatus.IN_PROGRESS);
        sendControlMessage(MessageType.TRANSFER_RESUME);
        notifyChanged();
    }

    public void cancel() {
        cancelled = true;
        sendControlMessage(MessageType.TRANSFER_CANCEL);
        closeQuietly();
        try {
            Files.deleteIfExists(partPath);
        } catch (IOException ignored) {
            // Best-effort cleanup.
        }
        transfer.setStatus(TransferStatus.CANCELLED);
        notifyChanged();
        connection.close();
    }

    public boolean isCancelled() {
        return cancelled;
    }

    private Message ackMessage(int sequenceNumber, boolean success) {
        return Message.builder(MessageType.ACK)
                .field("transferId", transfer.getTransferId())
                .field("sequenceNumber", sequenceNumber)
                .field("success", success)
                .build();
    }

    private void sendControlMessage(MessageType type) {
        try {
            connection.sendMessage(Message.builder(type).field("transferId", transfer.getTransferId()).build());
        } catch (IOException e) {
            log.debug("Failed to send {} for transfer {}: {}", type, transfer.getTransferId(), e.getMessage());
        }
    }

    private void closeQuietly() {
        if (raf != null) {
            try {
                raf.close();
            } catch (IOException ignored) {
                // Nothing further to do.
            }
        }
    }

    private void fail(String reason) {
        log.warn("Download {} failed: {}", transfer.getTransferId(), reason);
        transfer.setErrorMessage(reason);
        transfer.setStatus(TransferStatus.FAILED);
        closeQuietly();
        notifyChanged();
        connection.close();
    }

    private void notifyChanged() {
        if (listener != null) {
            listener.onTransferChanged(transfer);
        }
    }
}
