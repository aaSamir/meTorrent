package com.metorrent.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * Loads and persists {@link AppConfig} as JSON on disk. A corrupted or
 * missing config file never prevents the application from starting: this
 * class falls back to sensible defaults and logs a warning instead.
 */
public class ConfigManager {

    private static final Logger log = LoggerFactory.getLogger(ConfigManager.class);
    private static final int MAX_RECENT_CONNECTIONS = 10;

    private final Path configFile;
    private final ObjectMapper mapper;
    private AppConfig config;

    public ConfigManager() {
        this(Path.of(System.getProperty("user.home"), ".metorrent", "config.json"));
    }

    public ConfigManager(Path configFile) {
        this.configFile = configFile;
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.config = load();
    }

    public synchronized AppConfig get() {
        return config;
    }

    public synchronized void save() {
        try {
            Files.createDirectories(configFile.getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(configFile.toFile(), config);
            log.debug("Configuration saved to {}", configFile);
        } catch (IOException e) {
            log.error("Failed to save configuration to {}", configFile, e);
        }
    }

    public synchronized void addKnownPeer(KnownPeer peer) {
        List<KnownPeer> peers = config.getKnownPeers();
        peers.remove(peer); // equals() is by host:port, so this dedupes
        peers.add(peer);
        save();
    }

    public synchronized void removeKnownPeer(String host, int port) {
        config.getKnownPeers().removeIf(p -> p.getHost().equals(host) && p.getPort() == port);
        save();
    }

    public synchronized void addRecentConnection(String host, int port) {
        List<RecentConnection> recents = config.getRecentConnections();
        recents.removeIf(r -> r.getHost().equals(host) && r.getPort() == port);
        recents.add(0, new RecentConnection(host, port, Instant.now()));
        while (recents.size() > MAX_RECENT_CONNECTIONS) {
            recents.remove(recents.size() - 1);
        }
        save();
    }

    private AppConfig load() {
        if (!Files.exists(configFile)) {
            log.info("No configuration file found at {}, using defaults", configFile);
            return new AppConfig();
        }
        try {
            return mapper.readValue(configFile.toFile(), AppConfig.class);
        } catch (IOException e) {
            log.warn("Configuration file at {} is unreadable/corrupted, falling back to defaults", configFile, e);
            return new AppConfig();
        }
    }
}
