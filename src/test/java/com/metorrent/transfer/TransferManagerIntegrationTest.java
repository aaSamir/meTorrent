package com.metorrent.transfer;

import com.metorrent.filesystem.FileManager;
import com.metorrent.network.MessageDispatcher;
import com.metorrent.peer.Peer;
import com.metorrent.peer.PeerManager;
import com.metorrent.peer.PeerRegistry;
import com.metorrent.peer.PeerStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Full end-to-end test of the whole system: two simulated meTorrent
 * instances (each with its own PeerManager + TransferManager) talk over
 * real loopback TCP sockets to browse a remote file list and transfer a
 * file, with chunking and SHA-256 integrity verification exercised for
 * real - no mocks.
 */
class TransferManagerIntegrationTest {

    private static final int CHUNK_SIZE = 1024; // small on purpose, to force multiple chunks in tests

    private Node alice;
    private Node bob;

    @AfterEach
    void tearDown() {
        if (alice != null) alice.shutdown();
        if (bob != null) bob.shutdown();
    }

    @Test
    void downloadsSmallSingleChunkFileSuccessfully(@TempDir Path root) throws Exception {
        alice = Node.start("Alice", root.resolve("alice"));
        bob = Node.start("Bob", root.resolve("bob"));
        connect(alice, bob);

        Files.writeString(alice.sharedDir.resolve("hello.txt"), "Hello, meTorrent!", StandardCharsets.UTF_8);
        alice.fileManager.getSharedFolder().refresh();

        String aliceId = peerIdOf(bob, alice);
        bob.transferManager.requestFileList(aliceId);
        waitUntil(() -> !bob.transferManager.getCachedRemoteFiles(aliceId).isEmpty());

        RemoteFileEntry remoteFile = bob.transferManager.getCachedRemoteFiles(aliceId).get(0);
        assertEquals("hello.txt", remoteFile.name());

        Transfer download = bob.transferManager.requestDownload(aliceId, remoteFile);
        waitUntil(() -> download.getStatus() == TransferStatus.COMPLETED || download.getStatus() == TransferStatus.FAILED);

        assertEquals(TransferStatus.COMPLETED, download.getStatus());
        Path downloaded = bob.downloadDir.resolve("hello.txt");
        assertTrue(Files.exists(downloaded));
        assertEquals("Hello, meTorrent!", Files.readString(downloaded, StandardCharsets.UTF_8));
    }

    @Test
    void peerConnectionSurvivesMultipleDownloadsWithoutFlappingToDisconnected(@TempDir Path root) throws Exception {
        // Regression test: each download opens its own dedicated connection (docs/PROTOCOL.md
        // section 4). PeerManager must not mistake that transfer connection for the control
        // connection - otherwise the peer appears to disconnect the instant each download's
        // connection closes, even though the real control connection is still open.
        alice = Node.start("Alice", root.resolve("alice"));
        bob = Node.start("Bob", root.resolve("bob"));
        connect(alice, bob);

        Files.writeString(alice.sharedDir.resolve("hello.txt"), "Hello, meTorrent!", StandardCharsets.UTF_8);
        alice.fileManager.getSharedFolder().refresh();

        String aliceId = peerIdOf(bob, alice);
        String bobId = peerIdOf(alice, bob);
        bob.transferManager.requestFileList(aliceId);
        waitUntil(() -> !bob.transferManager.getCachedRemoteFiles(aliceId).isEmpty());
        RemoteFileEntry remoteFile = bob.transferManager.getCachedRemoteFiles(aliceId).get(0);

        for (int i = 0; i < 3; i++) {
            Transfer download = bob.transferManager.requestDownload(aliceId, remoteFile);
            waitUntil(() -> download.getStatus() == TransferStatus.COMPLETED || download.getStatus() == TransferStatus.FAILED);
            assertEquals(TransferStatus.COMPLETED, download.getStatus());

            // Give PeerManager a moment to (incorrectly, if the bug regresses) process any
            // stray HELLO and react to the transfer connection closing.
            Thread.sleep(150);
            assertEquals(PeerStatus.CONNECTED, bob.registry.get(aliceId).orElseThrow().getStatus(),
                    "Bob's view of Alice flipped to disconnected after download #" + i);
            assertEquals(PeerStatus.CONNECTED, alice.registry.get(bobId).orElseThrow().getStatus(),
                    "Alice's view of Bob flipped to disconnected after download #" + i);
        }
    }

    @Test
    void downloadsMultiChunkFileWithMatchingIntegrityHash(@TempDir Path root) throws Exception {
        alice = Node.start("Alice", root.resolve("alice"));
        bob = Node.start("Bob", root.resolve("bob"));
        connect(alice, bob);

        byte[] content = new byte[CHUNK_SIZE * 5 + 137]; // 6 chunks, last one partial
        for (int i = 0; i < content.length; i++) {
            content[i] = (byte) (i % 256);
        }
        Files.write(alice.sharedDir.resolve("big.bin"), content);
        alice.fileManager.getSharedFolder().refresh();

        String aliceId = peerIdOf(bob, alice);
        bob.transferManager.requestFileList(aliceId);
        waitUntil(() -> !bob.transferManager.getCachedRemoteFiles(aliceId).isEmpty());
        RemoteFileEntry remoteFile = bob.transferManager.getCachedRemoteFiles(aliceId).get(0);

        Transfer download = bob.transferManager.requestDownload(aliceId, remoteFile);
        waitUntil(() -> download.getStatus() == TransferStatus.COMPLETED || download.getStatus() == TransferStatus.FAILED, 15000);

        assertEquals(TransferStatus.COMPLETED, download.getStatus());
        assertEquals(6, download.getTotalChunks());
        byte[] downloadedContent = Files.readAllBytes(bob.downloadDir.resolve("big.bin"));
        assertTrue(java.util.Arrays.equals(content, downloadedContent));

        // The uploader's mirrored Transfer object should also report completion.
        List<Transfer> aliceTransfers = alice.transferManager.listTransfers();
        assertEquals(1, aliceTransfers.size());
        assertEquals(TransferStatus.COMPLETED, aliceTransfers.get(0).getStatus());
    }

    @Test
    void cancellingDownloadRemovesPartialFile(@TempDir Path root) throws Exception {
        alice = Node.start("Alice", root.resolve("alice"));
        bob = Node.start("Bob", root.resolve("bob"));
        connect(alice, bob);

        // Stop-and-wait chunk round trips on loopback take well under 1ms each, so a small
        // file can finish before the test's polling loop ever observes IN_PROGRESS. Use enough
        // chunks that the transfer reliably takes long enough to cancel mid-flight.
        byte[] content = new byte[CHUNK_SIZE * 8000];
        Files.write(alice.sharedDir.resolve("large.bin"), content);
        alice.fileManager.getSharedFolder().refresh();

        String aliceId = peerIdOf(bob, alice);
        bob.transferManager.requestFileList(aliceId);
        waitUntil(() -> !bob.transferManager.getCachedRemoteFiles(aliceId).isEmpty());
        RemoteFileEntry remoteFile = bob.transferManager.getCachedRemoteFiles(aliceId).get(0);

        Transfer download = bob.transferManager.requestDownload(aliceId, remoteFile);
        waitUntil(() -> download.getStatus() == TransferStatus.IN_PROGRESS, 15000);

        bob.transferManager.cancelTransfer(download.getTransferId());
        waitUntil(() -> download.getStatus() == TransferStatus.CANCELLED, 15000);

        assertEquals(TransferStatus.CANCELLED, download.getStatus());
        assertFalse(Files.exists(bob.downloadDir.resolve("large.bin")));
        assertFalse(Files.exists(bob.downloadDir.resolve("large.bin.part")));
    }

    @Test
    void requestingUnknownFileSendsErrorAndClosesConnection(@TempDir Path root) throws Exception {
        alice = Node.start("Alice", root.resolve("alice"));
        bob = Node.start("Bob", root.resolve("bob"));
        connect(alice, bob);

        RemoteFileEntry bogus = new RemoteFileEntry("nonexistent-file-id", "ghost.txt", 100, "txt");
        Transfer download = bob.transferManager.requestDownload(peerIdOf(bob, alice), bogus);

        waitUntil(() -> download.getStatus() == TransferStatus.FAILED, 5000);
        assertEquals(TransferStatus.FAILED, download.getStatus());
    }

    private static String peerIdOf(Node from, Node to) throws InterruptedException {
        waitUntilStatic(() -> !from.registry.list().isEmpty());
        return from.registry.list().iterator().next().getPeerId();
    }

    private void connect(Node alice, Node bob) throws IOException, InterruptedException {
        alice.peerManager.connect("127.0.0.1", bob.peerManager.getActualPort());
        waitUntil(() -> !alice.registry.list().isEmpty() && !bob.registry.list().isEmpty());
    }

    private void waitUntil(BooleanSupplier condition) throws InterruptedException {
        waitUntil(condition, 5000);
    }

    private void waitUntil(BooleanSupplier condition, long timeoutMs) throws InterruptedException {
        waitUntilStatic(condition, timeoutMs);
    }

    private static void waitUntilStatic(BooleanSupplier condition) throws InterruptedException {
        waitUntilStatic(condition, 5000);
    }

    private static void waitUntilStatic(BooleanSupplier condition, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline && !condition.getAsBoolean()) {
            Thread.sleep(20);
        }
        assertTrue(condition.getAsBoolean(), "Condition was not met within " + timeoutMs + "ms");
    }

    /** Bundles one simulated meTorrent instance's backend components. */
    private static final class Node {
        final FileManager fileManager;
        final PeerRegistry registry;
        final PeerManager peerManager;
        final TransferManager transferManager;
        final Path sharedDir;
        final Path downloadDir;
        final List<Transfer> transferEvents = new CopyOnWriteArrayList<>();

        private Node(FileManager fileManager, PeerRegistry registry, PeerManager peerManager,
                      TransferManager transferManager, Path sharedDir, Path downloadDir) {
            this.fileManager = fileManager;
            this.registry = registry;
            this.peerManager = peerManager;
            this.transferManager = transferManager;
            this.sharedDir = sharedDir;
            this.downloadDir = downloadDir;
        }

        static Node start(String name, Path baseDir) throws IOException {
            Path sharedDir = baseDir.resolve("shared");
            Path downloadDir = baseDir.resolve("downloads");
            FileManager fileManager = new FileManager(sharedDir, downloadDir, name);

            PeerRegistry registry = new PeerRegistry();
            MessageDispatcher dispatcher = new MessageDispatcher();
            PeerManager peerManager = new PeerManager(name, 0, registry, dispatcher);
            peerManager.start();

            TransferManager transferManager = new TransferManager(fileManager, registry, dispatcher,
                    CHUNK_SIZE, peerManager.getActualPort());

            Node node = new Node(fileManager, registry, peerManager, transferManager, sharedDir, downloadDir);
            transferManager.addTransferListener(node.transferEvents::add);
            return node;
        }

        void shutdown() {
            transferManager.shutdown();
            peerManager.shutdown();
        }
    }
}
