package com.metorrent.transfer;

/** One entry from a remote peer's FILE_LIST response - metadata only, we don't have the bytes yet. */
public record RemoteFileEntry(String fileId, String name, long size, String extension) {
}
