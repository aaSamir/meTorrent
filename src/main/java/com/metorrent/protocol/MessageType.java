package com.metorrent.protocol;

/**
 * Every message type in the meTorrent wire protocol. Field-level contracts
 * for each type are documented in {@code docs/PROTOCOL.md}.
 */
public enum MessageType {

    /** First message sent right after a TCP connection is established: protocol version + identity. */
    HELLO,

    /** Detailed peer info exchanged after HELLO (name, listen port, shared-file count). */
    PEER_INFO,

    /** Asks a peer to send back its shared-file catalogue. */
    FILE_LIST_REQUEST,

    /** Response to FILE_LIST_REQUEST: the sender's shared-file catalogue. */
    FILE_LIST,

    /** Asks a peer to start sending a specific file, optionally resuming from a byte offset. */
    REQUEST_FILE,

    /** Describes the file about to be streamed: size, chunk size, total chunks, whole-file SHA-256. */
    FILE_METADATA,

    /** Header for one binary chunk. This frame is immediately followed by a raw binary frame with the chunk bytes. */
    FILE_CHUNK,

    /** Acknowledges a chunk: success (received + checksum verified) or failure (must be retransmitted). */
    ACK,

    /** Sent by the uploader once every chunk has been acknowledged successfully. */
    FILE_COMPLETE,

    /** Pauses an in-progress transfer. */
    TRANSFER_PAUSE,

    /** Resumes a previously paused transfer. */
    TRANSFER_RESUME,

    /** Cancels an in-progress transfer. */
    TRANSFER_CANCEL,

    /** Generic error report (invalid request, file not found, checksum failure, etc). */
    ERROR,

    /** Graceful connection teardown notice. */
    DISCONNECT,

    /** Liveness probe. */
    PING,

    /** Response to PING. */
    PONG
}
