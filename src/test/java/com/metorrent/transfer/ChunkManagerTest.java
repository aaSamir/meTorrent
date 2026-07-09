package com.metorrent.transfer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChunkManagerTest {

    @Test
    void totalChunksRoundsUpForPartialLastChunk() {
        assertEquals(3, ChunkManager.totalChunks(250, 100)); // 100 + 100 + 50
        assertEquals(2, ChunkManager.totalChunks(200, 100)); // exact multiple
        assertEquals(1, ChunkManager.totalChunks(1, 100));
    }

    @Test
    void totalChunksOfEmptyFileIsZero() {
        assertEquals(0, ChunkManager.totalChunks(0, 100));
    }

    @Test
    void offsetOfComputesSequenceTimesChunkSize() {
        assertEquals(0, ChunkManager.offsetOf(0, 262144));
        assertEquals(262144, ChunkManager.offsetOf(1, 262144));
        assertEquals(524288, ChunkManager.offsetOf(2, 262144));
    }

    @Test
    void lengthOfReturnsFullChunkSizeExceptForLastChunk() {
        // fileSize=250, chunkSize=100 -> chunks: [0,100), [100,200), [200,250)
        assertEquals(100, ChunkManager.lengthOf(0, 250, 100));
        assertEquals(100, ChunkManager.lengthOf(1, 250, 100));
        assertEquals(50, ChunkManager.lengthOf(2, 250, 100));
    }

    @Test
    void lengthOfThrowsForSequenceBeyondFileEnd() {
        assertThrows(IllegalArgumentException.class, () -> ChunkManager.lengthOf(3, 250, 100));
    }

    @Test
    void chunkIndexForByteOffsetRoundsDownToWholeChunkBoundary() {
        assertEquals(0, ChunkManager.chunkIndexForByteOffset(0, 100));
        assertEquals(0, ChunkManager.chunkIndexForByteOffset(99, 100));
        assertEquals(1, ChunkManager.chunkIndexForByteOffset(100, 100));
        assertEquals(1, ChunkManager.chunkIndexForByteOffset(150, 100));
        assertEquals(2, ChunkManager.chunkIndexForByteOffset(200, 100));
    }

    @Test
    void offsetAndLengthCoverWholeFileWithoutGapsOrOverlap() {
        long fileSize = 1_000_003; // not a clean multiple of chunkSize
        int chunkSize = 262144;
        int total = ChunkManager.totalChunks(fileSize, chunkSize);

        long coveredBytes = 0;
        for (int seq = 0; seq < total; seq++) {
            assertEquals(coveredBytes, ChunkManager.offsetOf(seq, chunkSize));
            coveredBytes += ChunkManager.lengthOf(seq, fileSize, chunkSize);
        }
        assertEquals(fileSize, coveredBytes);
    }
}
