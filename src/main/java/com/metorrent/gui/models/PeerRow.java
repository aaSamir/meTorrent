package com.metorrent.gui.models;

import com.metorrent.peer.Peer;
import javafx.beans.property.SimpleStringProperty;

import java.time.format.DateTimeFormatter;
import java.time.ZoneId;

/** JavaFX-observable row backing the Connected Peers table. */
public class PeerRow {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final String peerId;
    private final SimpleStringProperty name = new SimpleStringProperty();
    private final SimpleStringProperty host = new SimpleStringProperty();
    private final SimpleStringProperty port = new SimpleStringProperty();
    private final SimpleStringProperty status = new SimpleStringProperty();
    private final SimpleStringProperty latency = new SimpleStringProperty();
    private final SimpleStringProperty connectedAt = new SimpleStringProperty();

    public PeerRow(Peer peer) {
        this.peerId = peer.getPeerId();
        update(peer);
    }

    public void update(Peer peer) {
        name.set(peer.getName());
        host.set(peer.getHost());
        port.set(String.valueOf(peer.getPort()));
        status.set(peer.getStatus().toString());
        latency.set(peer.getLatencyMs() < 0 ? "-" : peer.getLatencyMs() + " ms");
        connectedAt.set(peer.getConnectionTime() == null ? "-" : TIME_FORMAT.format(peer.getConnectionTime()));
    }

    public String getPeerId() {
        return peerId;
    }

    public SimpleStringProperty nameProperty() {
        return name;
    }

    public SimpleStringProperty hostProperty() {
        return host;
    }

    public SimpleStringProperty portProperty() {
        return port;
    }

    public SimpleStringProperty statusProperty() {
        return status;
    }

    public SimpleStringProperty latencyProperty() {
        return latency;
    }

    public SimpleStringProperty connectedAtProperty() {
        return connectedAt;
    }
}
