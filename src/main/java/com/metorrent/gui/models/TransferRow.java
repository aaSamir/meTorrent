package com.metorrent.gui.models;

import com.metorrent.transfer.Transfer;
import com.metorrent.util.FileUtils;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;

/** JavaFX-observable row backing the Transfers table. */
public class TransferRow {

    private final String transferId;
    private final SimpleStringProperty fileName = new SimpleStringProperty();
    private final SimpleStringProperty direction = new SimpleStringProperty();
    private final SimpleStringProperty peerName = new SimpleStringProperty();
    private final SimpleDoubleProperty progress = new SimpleDoubleProperty();
    private final SimpleStringProperty speed = new SimpleStringProperty();
    private final SimpleStringProperty eta = new SimpleStringProperty();
    private final SimpleStringProperty status = new SimpleStringProperty();

    public TransferRow(Transfer transfer) {
        this.transferId = transfer.getTransferId();
        this.direction.set(transfer.getDirection().toString());
        this.peerName.set(transfer.getPeerName());
        update(transfer);
    }

    public void update(Transfer transfer) {
        fileName.set(transfer.getFileName());
        progress.set(transfer.getProgressFraction());
        speed.set(transfer.getStatus().toString().equals("IN_PROGRESS")
                ? FileUtils.humanReadableSize((long) transfer.getSpeedBytesPerSecond()) + "/s"
                : "-");
        long etaSeconds = transfer.getEtaSeconds();
        eta.set(etaSeconds < 0 ? "-" : formatDuration(etaSeconds));
        status.set(transfer.getStatus().toString());
    }

    private static String formatDuration(long totalSeconds) {
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return minutes > 0 ? minutes + "m " + seconds + "s" : seconds + "s";
    }

    public String getTransferId() {
        return transferId;
    }

    public SimpleStringProperty fileNameProperty() {
        return fileName;
    }

    public SimpleStringProperty directionProperty() {
        return direction;
    }

    public SimpleStringProperty peerNameProperty() {
        return peerName;
    }

    public SimpleDoubleProperty progressProperty() {
        return progress;
    }

    public SimpleStringProperty speedProperty() {
        return speed;
    }

    public SimpleStringProperty etaProperty() {
        return eta;
    }

    public SimpleStringProperty statusProperty() {
        return status;
    }
}
