package com.metorrent.gui.models;

import com.metorrent.transfer.RemoteFileEntry;
import com.metorrent.util.FileUtils;
import javafx.beans.property.SimpleStringProperty;

/** JavaFX-observable row backing the Remote File Browser table. */
public class RemoteFileRow {

    private final RemoteFileEntry entry;
    private final String peerId;
    private final SimpleStringProperty name = new SimpleStringProperty();
    private final SimpleStringProperty peerName = new SimpleStringProperty();
    private final SimpleStringProperty size = new SimpleStringProperty();

    public RemoteFileRow(RemoteFileEntry entry, String peerId, String peerName) {
        this.entry = entry;
        this.peerId = peerId;
        this.name.set(entry.name());
        this.peerName.set(peerName);
        this.size.set(FileUtils.humanReadableSize(entry.size()));
    }

    public RemoteFileEntry getEntry() {
        return entry;
    }

    public String getPeerId() {
        return peerId;
    }

    public SimpleStringProperty nameProperty() {
        return name;
    }

    public SimpleStringProperty peerNameProperty() {
        return peerName;
    }

    public SimpleStringProperty sizeProperty() {
        return size;
    }
}
