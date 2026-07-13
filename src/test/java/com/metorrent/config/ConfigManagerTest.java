package com.metorrent.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigManagerTest {

    @Test
    void missingFileFallsBackToDefaults(@TempDir Path tempDir) {
        ConfigManager manager = new ConfigManager(tempDir.resolve("config.json"));
        AppConfig config = manager.get();
        assertNotNull(config.getUsername());
        assertEquals(7777, config.getDefaultPort());
        assertTrue(config.getKnownPeers().isEmpty());
    }

    @Test
    void saveThenReloadRoundTripsValues(@TempDir Path tempDir) {
        Path file = tempDir.resolve("config.json");
        ConfigManager first = new ConfigManager(file);
        first.get().setUsername("Alice");
        first.get().setDefaultPort(9000);
        first.save();

        ConfigManager second = new ConfigManager(file);
        assertEquals("Alice", second.get().getUsername());
        assertEquals(9000, second.get().getDefaultPort());
    }

    @Test
    void corruptedFileFallsBackToDefaultsInsteadOfCrashing(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("config.json");
        Files.writeString(file, "{ this is not valid json ");

        ConfigManager manager = new ConfigManager(file);
        assertNotNull(manager.get());
        assertEquals(7777, manager.get().getDefaultPort());
    }

    @Test
    void addKnownPeerDedupesByHostAndPort(@TempDir Path tempDir) {
        ConfigManager manager = new ConfigManager(tempDir.resolve("config.json"));
        manager.addKnownPeer(new KnownPeer("Bob", "192.168.1.5", 7777));
        manager.addKnownPeer(new KnownPeer("Bob (renamed)", "192.168.1.5", 7777));

        assertEquals(1, manager.get().getKnownPeers().size());
        assertEquals("Bob (renamed)", manager.get().getKnownPeers().get(0).getName());
    }

    @Test
    void addRecentConnectionMovesDuplicateToFrontAndCapsSize(@TempDir Path tempDir) {
        ConfigManager manager = new ConfigManager(tempDir.resolve("config.json"));
        for (int i = 0; i < 15; i++) {
            manager.addRecentConnection("10.0.0." + i, 7777);
        }
        manager.addRecentConnection("10.0.0.0", 7777); // re-add the very first one

        assertEquals(10, manager.get().getRecentConnections().size());
        assertEquals("10.0.0.0", manager.get().getRecentConnections().get(0).getHost());
    }

    @Test
    void savePersistsToDiskAsJsonFile(@TempDir Path tempDir) {
        Path file = tempDir.resolve("nested/dir/config.json");
        ConfigManager manager = new ConfigManager(file);
        manager.save();
        assertTrue(Files.exists(file));
    }
}
