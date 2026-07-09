package com.metorrent.transfer;

import java.util.List;

/** Observer notified when a remote peer's shared-file catalogue is (re)received, for the Remote File Browser. */
public interface RemoteFileListener {
    void onRemoteFileListUpdated(String peerId, List<RemoteFileEntry> files);
}
