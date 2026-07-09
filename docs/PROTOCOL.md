# meTorrent Wire Protocol

meTorrent peers talk directly to each other over plain TCP sockets using a
custom application-layer protocol. There is no tracker, no DHT, no
`.torrent` file and no BitTorrent peer-wire protocol involved anywhere.

## 1. Framing

Every connection is a stream of length-prefixed **frames**:

```
+------------------+------------------+---------------------------+
| length (4 bytes) | frame type (1 B) | payload (length-1 bytes)  |
+------------------+------------------+---------------------------+
```

- `length` — big-endian `int32`, the number of bytes that follow (frame type
  byte + payload). This lets the reader know exactly how many bytes to pull
  off the socket before decoding.
- `frame type`:
  - `0x01` = **CONTROL** — payload is a UTF-8 JSON-encoded `Message` (see
    below).
  - `0x02` = **BINARY** — payload is raw bytes with no envelope. Used
    exclusively for chunk data, always sent as the frame immediately
    following a `FILE_CHUNK` control frame.

Splitting chunk data into its own binary frame (rather than embedding it
Base64-encoded inside JSON) avoids ~33% encoding overhead on the bulk of the
traffic while keeping every other message simple, readable JSON.

## 2. Control Message Envelope

Every CONTROL frame's payload deserializes to:

```json
{
  "type": "HELLO",
  "timestamp": 1735689600000,
  "fields": { "...": "message-specific fields, see below" }
}
```

`type` is one of the `MessageType` enum values. `fields` is a free-form
JSON object; each message type's expected fields are documented below and
enforced at read-time by `Message`'s typed accessors (`getString`, `getInt`,
`getLong`, `getBoolean`, `getList`), which throw `ProtocolException` on a
missing or mis-typed field rather than failing silently.

## 3. Message Types

| Type | Direction | Purpose |
|---|---|---|
| `HELLO` | both, on connect | Protocol version + identity handshake |
| `PEER_INFO` | both, after HELLO | Detailed peer metadata |
| `FILE_LIST_REQUEST` | requester → owner | "send me your shared-file catalogue" |
| `FILE_LIST` | owner → requester | The shared-file catalogue |
| `REQUEST_FILE` | downloader → uploader | "start sending me this file" |
| `FILE_METADATA` | uploader → downloader | Size, chunk size, chunk count, whole-file hash |
| `FILE_CHUNK` | uploader → downloader | Header for one chunk (binary frame follows) |
| `ACK` | downloader → uploader | Chunk accepted or must be retransmitted |
| `FILE_COMPLETE` | uploader → downloader | All chunks acknowledged, transfer done |
| `TRANSFER_PAUSE` | either | Pause an in-progress transfer |
| `TRANSFER_RESUME` | either | Resume a paused transfer |
| `TRANSFER_CANCEL` | either | Cancel a transfer |
| `ERROR` | either | Generic error report |
| `DISCONNECT` | either | Graceful teardown notice |
| `PING` / `PONG` | either | Liveness / latency probe |

### HELLO
Sent immediately by both sides after the TCP connection is established.
```json
{ "peerName": "Alice", "listenPort": 7777, "protocolVersion": 1 }
```

### PEER_INFO
```json
{ "peerName": "Alice", "listenPort": 7777, "sharedFileCount": 12 }
```

### FILE_LIST_REQUEST
No fields required.

### FILE_LIST
```json
{
  "files": [
    { "fileId": "sha256-of-path-or-uuid", "name": "movie.mp4", "size": 734003200, "extension": "mp4" }
  ]
}
```

### REQUEST_FILE
```json
{
  "fileId": "...",
  "transferId": "uuid-generated-by-downloader",
  "resumeFromByteOffset": 0,
  "requesterListenPort": 7777
}
```
`requesterListenPort` is the downloader's own advertised listen port (from
its HELLO). Since this request travels over a brand-new dedicated transfer
connection that never did its own HELLO handshake (see section 4), the
uploader combines this port with the socket's observed remote IP to
reconstruct the same peer id it already knows the downloader by from the
control connection - IP address alone is ambiguous when demoing multiple
meTorrent instances on localhost, where every peer shares `127.0.0.1`.

The **downloader** generates `transferId` and includes it here; the
uploader echoes the same id in every message for the rest of the transfer,
so no separate request/response correlation step is needed. `resumeFromByteOffset`
is simply how many bytes of the file the downloader already has on disk
(from a `.part` file left over from an earlier interrupted attempt) - the
uploader, who owns the chunk size, converts that to a starting chunk index
by rounding down to the nearest whole chunk boundary
(`resumeFromByteOffset / chunkSize`). Sending a byte count instead of a
chunk index sidesteps a chicken-and-egg problem: the downloader doesn't
know the uploader's chunk size until `FILE_METADATA` arrives, which is
sent *after* `REQUEST_FILE`.

### FILE_METADATA
```json
{
  "fileId": "...",
  "fileName": "movie.mp4",
  "fileSize": 734003200,
  "chunkSize": 262144,
  "totalChunks": 2800,
  "fileHash": "sha256-hex-of-whole-file"
}
```

### FILE_CHUNK
Header frame; the raw chunk bytes follow immediately in a BINARY frame.
```json
{
  "transferId": "...",
  "sequenceNumber": 42,
  "offset": 11010048,
  "length": 262144,
  "checksum": "sha256-hex-of-this-chunk"
}
```

### ACK
```json
{ "transferId": "...", "sequenceNumber": 42, "success": true }
```
`success: false` means the checksum did not match; the uploader must
retransmit that exact chunk.

### FILE_COMPLETE
```json
{ "transferId": "...", "fileHash": "sha256-hex-of-whole-file" }
```

### TRANSFER_PAUSE / TRANSFER_RESUME / TRANSFER_CANCEL
```json
{ "transferId": "..." }
```

### ERROR
```json
{ "code": "FILE_NOT_FOUND", "message": "No such file: movie.mp4" }
```

### DISCONNECT
```json
{ "reason": "user quit" }
```

### PING / PONG
```json
{ "nonce": 123456 }
```
The responder echoes back the same `nonce` in `PONG` so the sender can
measure round-trip latency for the peer table's "Ping" column.

## 4. One Connection Per Transfer

Each active transfer (upload or download) uses its own dedicated TCP
connection, separate from the "control" connection established at HELLO
time and reused for `FILE_LIST_REQUEST`/`FILE_LIST`. This keeps chunk
framing trivially unambiguous - a `FILE_CHUNK` header is always
immediately followed by exactly one binary frame *on that same socket*,
with no risk of two simultaneous transfers to the same peer interleaving
their frames. The cost is one extra socket per concurrent transfer, which
is a standard, well-understood trade-off (control/data connection
separation appears in many real protocols) and is far simpler to reason
about than multiplexing several chunk streams over one socket.

## 5. Chunked Transfer & Resume

Files are split into fixed-size chunks (64 KB / 256 KB / 512 KB,
configurable — see `AppConfig.chunkSizeBytes`). Each chunk is independently
checksummed with SHA-256. The downloader ACKs each chunk; a failed checksum
triggers a targeted retransmission of just that chunk rather than the whole
file. Because `REQUEST_FILE.resumeFromChunk` lets a transfer restart at an
arbitrary chunk index, an interrupted download can resume without
re-transferring already-verified data.

## 6. Whole-File Integrity

After the last chunk is ACKed, the uploader sends `FILE_COMPLETE` with the
SHA-256 hash of the entire file (computed once, up front, and stored in
`FILE_METADATA.fileHash`). The downloader independently hashes the
reassembled file and compares; a mismatch is surfaced as an `ERROR` and the
file is rejected rather than kept in a possibly-corrupted state.
