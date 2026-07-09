package com.metorrent.transfer;

import com.metorrent.crypto.HashUtils;
import com.metorrent.filesystem.FileManager;
import com.metorrent.filesystem.SharedFile;
import com.metorrent.network.Connection;
import com.metorrent.protocol.Message;
import com.metorrent.protocol.MessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Drives the uploader's side of one file transfer: for each chunk from
 * {@code resumeFromChunk} onward, sends a FILE_CHUNK header + binary frame
 * and stop-and-waits for the matching ACK before moving on. A failed ACK
 * (checksum mismatch on the receiving end) retransmits the same chunk
 * rather than advancing. Runs on its own thread, owned by TransferManager's
 * executor.
 */
public class UploadTask implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(UploadTask.class);
    private static final int ACK_TIMEOUT_SECONDS = 30;
    private static final int MAX_RETRIES_PER_CHUNK = 5;
    private static final int PAUSE_POLL_MILLIS = 200;

    private final Connection connection;
    private final Transfer transfer;
    private final SharedFile file;
    private final FileManager fileManager;
    private final int resumeFromChunk;
    private final TransferListener listener;

    private final BlockingQueue<Boolean> ackQueue = new ArrayBlockingQueue<>(1);
    private volatile int awaitingAckForSequence = -1;
    private volatile boolean paused = false;
    private volatile boolean cancelled = false;
    private volatile Thread runnerThread;

    public UploadTask(Connection connection, Transfer transfer, SharedFile file, FileManager fileManager,
                       int resumeFromChunk, TransferListener listener) {
        this.connection = connection;
        this.transfer = transfer;
        this.file = file;
        this.fileManager = fileManager;
        this.resumeFromChunk = resumeFromChunk;
        this.listener = listener;
    }

    @Override
    public void run() {
        runnerThread = Thread.currentThread();
        transfer.setStatus(TransferStatus.IN_PROGRESS);
        notifyChanged();
        try {
            String fileHash = HashUtils.sha256Hex(file.path());
            int totalChunks = transfer.getTotalChunks();

            for (int seq = resumeFromChunk; seq < totalChunks; seq++) {
                waitWhilePaused();
                if (cancelled) {
                    finishAs(TransferStatus.CANCELLED);
                    return;
                }
                if (!sendChunkWithRetries(seq)) {
                    return; // sendChunkWithRetries already finalized the transfer as FAILED/CANCELLED
                }
                long absoluteBytesDone = ChunkManager.offsetOf(seq, transfer.getChunkSize()) + lastChunkLength(seq);
                transfer.recordChunkCompleted(seq + 1, absoluteBytesDone);
                notifyChanged();
            }

            connection.sendMessage(Message.builder(MessageType.FILE_COMPLETE)
                    .field("transferId", transfer.getTransferId())
                    .field("fileHash", fileHash)
                    .build());
            finishAs(TransferStatus.COMPLETED);
        } catch (IOException e) {
            log.warn("Upload {} failed: {}", transfer.getTransferId(), e.getMessage());
            transfer.setErrorMessage(e.getMessage());
            finishAs(TransferStatus.FAILED);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            finishAs(cancelled ? TransferStatus.CANCELLED : TransferStatus.FAILED);
        }
    }

    private int lastChunkLength(int seq) {
        return ChunkManager.lengthOf(seq, transfer.getFileSize(), transfer.getChunkSize());
    }

    /** Sends one chunk, retrying on a failed/timed-out ACK. Returns false if the transfer was finalized (failed/cancelled). */
    private boolean sendChunkWithRetries(int seq) throws IOException, InterruptedException {
        int chunkSize = transfer.getChunkSize();
        long offset = ChunkManager.offsetOf(seq, chunkSize);
        int length = ChunkManager.lengthOf(seq, transfer.getFileSize(), chunkSize);
        byte[] data = fileManager.readChunk(file, offset, length);
        String checksum = HashUtils.sha256Hex(data);

        for (int attempt = 1; attempt <= MAX_RETRIES_PER_CHUNK; attempt++) {
            if (cancelled) {
                finishAs(TransferStatus.CANCELLED);
                return false;
            }
            awaitingAckForSequence = seq;
            ackQueue.clear();

            connection.sendMessage(Message.builder(MessageType.FILE_CHUNK)
                    .field("transferId", transfer.getTransferId())
                    .field("sequenceNumber", seq)
                    .field("offset", offset)
                    .field("length", length)
                    .field("checksum", checksum)
                    .build());
            connection.sendBinary(data);

            Boolean success = ackQueue.poll(ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (success == null) {
                log.warn("Timed out waiting for ACK of chunk {} (attempt {}/{})", seq, attempt, MAX_RETRIES_PER_CHUNK);
                continue;
            }
            if (success) {
                return true;
            }
            log.debug("Chunk {} failed checksum verification on receiver, retrying (attempt {}/{})", seq, attempt, MAX_RETRIES_PER_CHUNK);
        }

        transfer.setErrorMessage("Chunk " + seq + " failed after " + MAX_RETRIES_PER_CHUNK + " attempts");
        finishAs(TransferStatus.FAILED);
        return false;
    }

    /** Called by TransferManager when an ACK arrives for this upload's connection. */
    public void onAck(int sequenceNumber, boolean success) {
        if (sequenceNumber == awaitingAckForSequence) {
            ackQueue.offer(success);
        }
    }

    public void pause() {
        paused = true;
        transfer.setStatus(TransferStatus.PAUSED);
        notifyChanged();
    }

    public void resume() {
        paused = false;
        transfer.setStatus(TransferStatus.IN_PROGRESS);
        notifyChanged();
    }

    public void cancel() {
        cancelled = true;
        paused = false;
        Thread t = runnerThread;
        if (t != null) {
            t.interrupt();
        }
    }

    private void waitWhilePaused() throws InterruptedException {
        while (paused && !cancelled) {
            Thread.sleep(PAUSE_POLL_MILLIS);
        }
    }

    private void finishAs(TransferStatus status) {
        transfer.setStatus(status);
        notifyChanged();
    }

    private void notifyChanged() {
        if (listener != null) {
            listener.onTransferChanged(transfer);
        }
    }
}
