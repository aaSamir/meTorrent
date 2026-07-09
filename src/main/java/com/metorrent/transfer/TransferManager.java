package com.metorrent.transfer;

import com.metorrent.crypto.HashUtils;
import com.metorrent.filesystem.FileManager;
import com.metorrent.filesystem.SharedFile;
import com.metorrent.network.Connection;
import com.metorrent.network.MessageDispatcher;
import com.metorrent.network.MessageListener;
import com.metorrent.network.PeerClient;
import com.metorrent.protocol.Message;
import com.metorrent.protocol.MessageType;
import com.metorrent.peer.Peer;
import com.metorrent.peer.PeerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Orchestrates file transfers: serves remote FILE_LIST_REQUEST /
 * REQUEST_FILE from other peers (uploader role) and drives outgoing
 * downloads (downloader role). Registers on the shared MessageDispatcher
 * and only reacts to transfer-related message types; peer-lifecycle
 * messages are PeerManager's concern. See docs/PROTOCOL.md section 4 for
 * why every transfer gets its own dedicated connection.
 */
public class TransferManager implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(TransferManager.class);

    private final FileManager fileManager;
    private final PeerRegistry peerRegistry;
    private final MessageDispatcher dispatcher;
    private final PeerClient peerClient = new PeerClient();
    private final int chunkSizeBytes;
    private final int localListenPort;

    private final Map<String, UploadTask> activeUploads = new ConcurrentHashMap<>();
    private final Map<String, DownloadTask> activeDownloads = new ConcurrentHashMap<>();
    private final Map<String, Transfer> transfersById = new ConcurrentHashMap<>();
    private final Map<String, String> connectionIdToTransferId = new ConcurrentHashMap<>();
    private final Map<String, List<RemoteFileEntry>> remoteFileCache = new ConcurrentHashMap<>();

    private final List<TransferListener> transferListeners = new CopyOnWriteArrayList<>();
    private final List<RemoteFileListener> remoteFileListeners = new CopyOnWriteArrayList<>();

    private final ExecutorService executor;

    public TransferManager(FileManager fileManager, PeerRegistry peerRegistry, MessageDispatcher dispatcher,
                            int chunkSizeBytes, int localListenPort) {
        this.fileManager = fileManager;
        this.peerRegistry = peerRegistry;
        this.dispatcher = dispatcher;
        this.chunkSizeBytes = chunkSizeBytes;
        this.localListenPort = localListenPort;
        AtomicInteger counter = new AtomicInteger();
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "transfer-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        dispatcher.addListener(this);
    }

    public void addTransferListener(TransferListener listener) {
        transferListeners.add(listener);
    }

    public void addRemoteFileListener(RemoteFileListener listener) {
        remoteFileListeners.add(listener);
    }

    public List<Transfer> listTransfers() {
        return List.copyOf(transfersById.values());
    }

    public List<RemoteFileEntry> getCachedRemoteFiles(String peerId) {
        return remoteFileCache.getOrDefault(peerId, List.of());
    }

    /** Asks a peer (over its existing control connection) to send its shared-file catalogue. */
    public void requestFileList(String peerId) throws IOException {
        Peer peer = peerRegistry.get(peerId).orElseThrow(() -> new IllegalArgumentException("Unknown peer: " + peerId));
        Connection connection = peer.getConnection();
        if (connection == null) {
            throw new IOException("Peer " + peerId + " is not currently connected");
        }
        connection.sendMessage(Message.builder(MessageType.FILE_LIST_REQUEST).build());
    }

    /** Starts downloading a file from a peer on a brand-new dedicated connection. Resumes automatically if a .part file exists. */
    public Transfer requestDownload(String peerId, RemoteFileEntry remoteFile) {
        Peer peer = peerRegistry.get(peerId).orElseThrow(() -> new IllegalArgumentException("Unknown peer: " + peerId));
        String transferId = UUID.randomUUID().toString();

        Path finalDestination = fileManager.resolveDownloadDestination(remoteFile.name());
        Path partPath = finalDestination.resolveSibling(finalDestination.getFileName().toString() + ".part");
        long resumeFromByteOffset = resolveExistingPartSize(partPath);

        Transfer transfer = new Transfer(transferId, TransferDirection.DOWNLOAD, peerId, peer.getName(),
                remoteFile.name(), remoteFile.size(), chunkSizeBytes);
        transfersById.put(transferId, transfer);
        fireTransferChanged(transfer);

        executor.submit(() -> {
            try {
                Connection transferConnection = peerClient.connect(peer.getHost(), peer.getPort());
                connectionIdToTransferId.put(transferConnection.getConnectionId(), transferId);
                dispatcher.listen(transferConnection);

                DownloadTask task = new DownloadTask(transferConnection, transfer, remoteFile.fileId(), partPath,
                        finalDestination, resumeFromByteOffset, localListenPort, this::fireTransferChanged);
                activeDownloads.put(transferId, task);
                task.start();
            } catch (IOException e) {
                log.warn("Failed to start download {}: {}", transferId, e.getMessage());
                transfer.setErrorMessage(e.getMessage());
                transfer.setStatus(TransferStatus.FAILED);
                fireTransferChanged(transfer);
            }
        });

        return transfer;
    }

    public void pauseTransfer(String transferId) {
        Optional.ofNullable(activeUploads.get(transferId)).ifPresentOrElse(UploadTask::pause,
                () -> Optional.ofNullable(activeDownloads.get(transferId)).ifPresent(DownloadTask::pause));
    }

    public void resumeTransfer(String transferId) {
        Optional.ofNullable(activeUploads.get(transferId)).ifPresentOrElse(UploadTask::resume,
                () -> Optional.ofNullable(activeDownloads.get(transferId)).ifPresent(DownloadTask::resume));
    }

    public void cancelTransfer(String transferId) {
        Optional.ofNullable(activeUploads.get(transferId)).ifPresentOrElse(UploadTask::cancel,
                () -> Optional.ofNullable(activeDownloads.get(transferId)).ifPresent(DownloadTask::cancel));
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    @Override
    public void onMessage(Connection connection, Message message) {
        switch (message.getType()) {
            case FILE_LIST_REQUEST -> handleFileListRequest(connection);
            case FILE_LIST -> handleFileList(connection, message);
            case REQUEST_FILE -> handleRequestFile(connection, message);
            case FILE_METADATA -> withDownloadTask(message, DownloadTask::onMetadataReceived);
            case FILE_CHUNK -> withDownloadTask(message, DownloadTask::onChunkHeader);
            case ACK -> handleAck(message);
            case FILE_COMPLETE -> handleFileComplete(message);
            case TRANSFER_PAUSE -> withUploadTask(message, UploadTask::pause);
            case TRANSFER_RESUME -> withUploadTask(message, UploadTask::resume);
            case TRANSFER_CANCEL -> withUploadTask(message, UploadTask::cancel);
            default -> {
                // Peer-lifecycle message; PeerManager's own listener handles it.
            }
        }
    }

    @Override
    public void onBinaryFrame(Connection connection, byte[] data) {
        String transferId = connectionIdToTransferId.get(connection.getConnectionId());
        if (transferId == null) {
            return;
        }
        DownloadTask task = activeDownloads.get(transferId);
        if (task != null) {
            task.onBinaryFrame(data);
        }
    }

    @Override
    public void onConnectionClosed(Connection connection, Throwable cause) {
        String transferId = connectionIdToTransferId.remove(connection.getConnectionId());
        if (transferId == null) {
            return;
        }
        activeUploads.remove(transferId);
        activeDownloads.remove(transferId);
        Transfer transfer = transfersById.get(transferId);
        if (transfer != null && isUnfinished(transfer.getStatus())) {
            transfer.setStatus(TransferStatus.FAILED);
            transfer.setErrorMessage("Connection closed unexpectedly");
            fireTransferChanged(transfer);
        }
    }

    private boolean isUnfinished(TransferStatus status) {
        return status == TransferStatus.PENDING || status == TransferStatus.IN_PROGRESS || status == TransferStatus.PAUSED;
    }

    private void handleFileListRequest(Connection connection) {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (SharedFile file : fileManager.getSharedFolder().list()) {
            entries.add(Map.of(
                    "fileId", file.fileId(),
                    "name", file.name(),
                    "size", file.size(),
                    "extension", file.extension()));
        }
        try {
            connection.sendMessage(Message.builder(MessageType.FILE_LIST).field("files", entries).build());
        } catch (IOException e) {
            log.warn("Failed to send file list to {}: {}", connection, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void handleFileList(Connection connection, Message message) {
        Peer peer = peerRegistry.findByConnectionId(connection.getConnectionId()).orElse(null);
        if (peer == null) {
            log.warn("Received FILE_LIST from an unrecognized connection {}", connection);
            return;
        }
        List<RemoteFileEntry> entries = new ArrayList<>();
        for (Object raw : message.getList("files")) {
            Map<String, Object> entry = (Map<String, Object>) raw;
            entries.add(new RemoteFileEntry(
                    (String) entry.get("fileId"),
                    (String) entry.get("name"),
                    ((Number) entry.get("size")).longValue(),
                    (String) entry.get("extension")));
        }
        remoteFileCache.put(peer.getPeerId(), entries);
        for (RemoteFileListener listener : remoteFileListeners) {
            listener.onRemoteFileListUpdated(peer.getPeerId(), entries);
        }
    }

    private void handleRequestFile(Connection connection, Message message) {
        String fileId = message.getString("fileId");
        String transferId = message.getString("transferId");
        long resumeFromByteOffset = message.getLong("resumeFromByteOffset");
        int requesterListenPort = message.getInt("requesterListenPort");

        Optional<SharedFile> fileOpt = fileManager.getSharedFolder().findById(fileId);
        if (fileOpt.isEmpty()) {
            sendError(connection, "FILE_NOT_FOUND", "No such shared file");
            connection.close();
            return;
        }
        SharedFile file = fileOpt.get();

        String peerId = Peer.idFor(connection.getRemoteHost(), requesterListenPort);
        String peerName = peerRegistry.get(peerId).map(Peer::getName).orElse(connection.getRemoteHost());

        int totalChunks = ChunkManager.totalChunks(file.size(), chunkSizeBytes);
        int resumeFromChunk = ChunkManager.chunkIndexForByteOffset(resumeFromByteOffset, chunkSizeBytes);

        Transfer transfer = new Transfer(transferId, TransferDirection.UPLOAD, peerId, peerName,
                file.name(), file.size(), chunkSizeBytes);
        transfersById.put(transferId, transfer);
        connectionIdToTransferId.put(connection.getConnectionId(), transferId);
        fireTransferChanged(transfer);

        try {
            String fileHash = HashUtils.sha256Hex(file.path());
            connection.sendMessage(Message.builder(MessageType.FILE_METADATA)
                    .field("transferId", transferId)
                    .field("fileId", fileId)
                    .field("fileName", file.name())
                    .field("fileSize", file.size())
                    .field("chunkSize", chunkSizeBytes)
                    .field("totalChunks", totalChunks)
                    .field("fileHash", fileHash)
                    .build());
        } catch (IOException e) {
            log.warn("Failed to send FILE_METADATA for transfer {}: {}", transferId, e.getMessage());
            transfer.setStatus(TransferStatus.FAILED);
            fireTransferChanged(transfer);
            return;
        }

        UploadTask task = new UploadTask(connection, transfer, file, fileManager, resumeFromChunk, this::fireTransferChanged);
        activeUploads.put(transferId, task);
        executor.submit(task);
    }

    private void handleAck(Message message) {
        String transferId = message.getString("transferId");
        UploadTask task = activeUploads.get(transferId);
        if (task != null) {
            task.onAck(message.getInt("sequenceNumber"), message.getBoolean("success"));
        }
    }

    private void handleFileComplete(Message message) {
        String transferId = message.getString("transferId");
        DownloadTask task = activeDownloads.get(transferId);
        if (task != null) {
            task.onFileComplete(message);
            activeDownloads.remove(transferId);
        }
    }

    private void withDownloadTask(Message message, java.util.function.BiConsumer<DownloadTask, Message> action) {
        String transferId = message.getString("transferId");
        DownloadTask task = activeDownloads.get(transferId);
        if (task != null) {
            action.accept(task, message);
        }
    }

    private void withUploadTask(Message message, java.util.function.Consumer<UploadTask> action) {
        String transferId = message.getString("transferId");
        UploadTask task = activeUploads.get(transferId);
        if (task != null) {
            action.accept(task);
        }
    }

    private void sendError(Connection connection, String code, String errorMessage) {
        try {
            connection.sendMessage(Message.builder(MessageType.ERROR)
                    .field("code", code)
                    .field("message", errorMessage)
                    .build());
        } catch (IOException e) {
            log.debug("Failed to send ERROR to {}: {}", connection, e.getMessage());
        }
    }

    private long resolveExistingPartSize(Path partPath) {
        try {
            return Files.exists(partPath) ? Files.size(partPath) : 0L;
        } catch (IOException e) {
            return 0L;
        }
    }

    private void fireTransferChanged(Transfer transfer) {
        for (TransferListener listener : transferListeners) {
            listener.onTransferChanged(transfer);
        }
    }
}
