package com.metorrent.transfer;

/** Observer notified whenever a transfer's progress or status changes, for GUI updates. */
public interface TransferListener {
    void onTransferChanged(Transfer transfer);
}
