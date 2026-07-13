package com.metorrent.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Root JSON-serializable settings object persisted by {@link ConfigManager}.
 * Plain mutable bean so Jackson can (de)serialize it directly.
 */
public class AppConfig {

    private String username = "Peer-" + Integer.toHexString((int) (Math.random() * 0xFFFF));
    private String sharedFolder = defaultUnderHome("meTorrent/shared");
    private String downloadFolder = defaultUnderHome("meTorrent/downloads");
    private int defaultPort = 7777;
    private int chunkSizeBytes = 256 * 1024;
    private String theme = "light";
    private int windowWidth = 1100;
    private int windowHeight = 720;
    private List<KnownPeer> knownPeers = new ArrayList<>();
    private List<RecentConnection> recentConnections = new ArrayList<>();

    private static String defaultUnderHome(String relative) {
        return java.nio.file.Path.of(System.getProperty("user.home"), relative.split("/")).toString();
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getSharedFolder() {
        return sharedFolder;
    }

    public void setSharedFolder(String sharedFolder) {
        this.sharedFolder = sharedFolder;
    }

    public String getDownloadFolder() {
        return downloadFolder;
    }

    public void setDownloadFolder(String downloadFolder) {
        this.downloadFolder = downloadFolder;
    }

    public int getDefaultPort() {
        return defaultPort;
    }

    public void setDefaultPort(int defaultPort) {
        this.defaultPort = defaultPort;
    }

    public int getChunkSizeBytes() {
        return chunkSizeBytes;
    }

    public void setChunkSizeBytes(int chunkSizeBytes) {
        this.chunkSizeBytes = chunkSizeBytes;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public int getWindowWidth() {
        return windowWidth;
    }

    public void setWindowWidth(int windowWidth) {
        this.windowWidth = windowWidth;
    }

    public int getWindowHeight() {
        return windowHeight;
    }

    public void setWindowHeight(int windowHeight) {
        this.windowHeight = windowHeight;
    }

    public List<KnownPeer> getKnownPeers() {
        return knownPeers;
    }

    public void setKnownPeers(List<KnownPeer> knownPeers) {
        this.knownPeers = knownPeers;
    }

    public List<RecentConnection> getRecentConnections() {
        return recentConnections;
    }

    public void setRecentConnections(List<RecentConnection> recentConnections) {
        this.recentConnections = recentConnections;
    }
}
