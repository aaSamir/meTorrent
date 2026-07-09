package com.metorrent.app;

import com.metorrent.config.AppConfig;
import com.metorrent.config.ConfigManager;
import com.metorrent.filesystem.FileManager;
import com.metorrent.network.MessageDispatcher;
import com.metorrent.peer.PeerManager;
import com.metorrent.peer.PeerRegistry;
import com.metorrent.transfer.TransferManager;
import com.metorrent.util.NetworkUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Bundles every backend singleton this instance needs and wires them
 * together in the right order: config first, then file/peer/transfer
 * managers built on top of it. One AppContext == one running meTorrent
 * node (server + client + GUI backend).
 */
public class AppContext {

    private static final Logger log = LoggerFactory.getLogger(AppContext.class);

    private final ConfigManager configManager;
    private final PeerRegistry peerRegistry;
    private final MessageDispatcher dispatcher;
    private final FileManager fileManager;
    private final PeerManager peerManager;
    private final TransferManager transferManager;

    private AppContext(ConfigManager configManager, PeerRegistry peerRegistry, MessageDispatcher dispatcher,
                        FileManager fileManager, PeerManager peerManager, TransferManager transferManager) {
        this.configManager = configManager;
        this.peerRegistry = peerRegistry;
        this.dispatcher = dispatcher;
        this.fileManager = fileManager;
        this.peerManager = peerManager;
        this.transferManager = transferManager;
    }

    public static AppContext bootstrap() throws IOException {
        ConfigManager configManager = new ConfigManager();
        AppConfig config = configManager.get();

        FileManager fileManager = new FileManager(
                Path.of(config.getSharedFolder()), Path.of(config.getDownloadFolder()), config.getUsername());

        PeerRegistry peerRegistry = new PeerRegistry();
        MessageDispatcher dispatcher = new MessageDispatcher();

        int port = NetworkUtils.findAvailablePort(config.getDefaultPort());
        PeerManager peerManager = new PeerManager(config.getUsername(), port, peerRegistry, dispatcher);
        peerManager.start();

        TransferManager transferManager = new TransferManager(
                fileManager, peerRegistry, dispatcher, config.getChunkSizeBytes(), peerManager.getActualPort());

        log.info("meTorrent node '{}' bootstrapped on port {}", config.getUsername(), peerManager.getActualPort());
        return new AppContext(configManager, peerRegistry, dispatcher, fileManager, peerManager, transferManager);
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public PeerRegistry getPeerRegistry() {
        return peerRegistry;
    }

    public FileManager getFileManager() {
        return fileManager;
    }

    public PeerManager getPeerManager() {
        return peerManager;
    }

    public TransferManager getTransferManager() {
        return transferManager;
    }

    public void shutdown() {
        transferManager.shutdown();
        peerManager.shutdown();
        log.info("meTorrent node shut down");
    }
}
