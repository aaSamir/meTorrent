package com.metorrent.gui.models;

import com.metorrent.filesystem.SharedFile;
import com.metorrent.util.FileUtils;
import javafx.beans.property.SimpleStringProperty;

import java.time.format.DateTimeFormatter;
import java.time.ZoneId;

/** JavaFX-observable row backing the Shared Files table. */
public class SharedFileRow {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final String fileId;
    private final SimpleStringProperty name = new SimpleStringProperty();
    private final SimpleStringProperty size = new SimpleStringProperty();
    private final SimpleStringProperty type = new SimpleStringProperty();
    private final SimpleStringProperty lastModified = new SimpleStringProperty();
    private final SimpleStringProperty owner = new SimpleStringProperty();

    public SharedFileRow(SharedFile file) {
        this.fileId = file.fileId();
        name.set(file.name());
        size.set(FileUtils.humanReadableSize(file.size()));
        type.set(file.category());
        lastModified.set(DATE_FORMAT.format(file.lastModified()));
        owner.set(file.owner());
    }

    public String getFileId() {
        return fileId;
    }

    public SimpleStringProperty nameProperty() {
        return name;
    }

    public SimpleStringProperty sizeProperty() {
        return size;
    }

    public SimpleStringProperty typeProperty() {
        return type;
    }

    public SimpleStringProperty lastModifiedProperty() {
        return lastModified;
    }

    public SimpleStringProperty ownerProperty() {
        return owner;
    }
}
