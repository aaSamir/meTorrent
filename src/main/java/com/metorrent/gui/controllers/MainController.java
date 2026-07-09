package com.metorrent.gui.controllers;

import com.metorrent.app.AppContext;
import com.metorrent.config.KnownPeer;
import com.metorrent.filesystem.SharedFile;
import com.metorrent.gui.models.PeerRow;
import com.metorrent.gui.models.RemoteFileRow;
import com.metorrent.gui.models.SharedFileRow;
import com.metorrent.gui.models.TransferRow;
import com.metorrent.peer.Peer;
import com.metorrent.peer.PeerRegistryListener;
import com.metorrent.transfer.RemoteFileEntry;
import com.metorrent.transfer.Transfer;
import com.metorrent.transfer.TransferListener;
import com.metorrent.util.NetworkUtils;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.cell.ProgressBarTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Controller for the single-window meTorrent shell. Owns no business logic
 * itself - every action delegates to the backend services in
 * {@link AppContext}. Backend events (peer connects, transfer progress,
 * remote file lists) arrive on background threads and are marshalled onto
 * the JavaFX application thread via {@link Platform#runLater}.
 */
public class MainController {

    private static final Logger log = LoggerFactory.getLogger(MainController.class);
    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int MAX_LOG_LINES = 500;

    @FXML private TabPane mainTabPane;
    @FXML private Label localPeerInfoLabel;
    @FXML private Button connectButton;

    @FXML private TableView<PeerRow> peersTable;
    @FXML private TableColumn<PeerRow, String> peerNameColumn;
    @FXML private TableColumn<PeerRow, String> peerHostColumn;
    @FXML private TableColumn<PeerRow, String> peerPortColumn;
    @FXML private TableColumn<PeerRow, String> peerStatusColumn;
    @FXML private TableColumn<PeerRow, String> peerLatencyColumn;
    @FXML private TableColumn<PeerRow, String> peerConnectedAtColumn;
    @FXML private Button refreshPeersButton;
    @FXML private Button browsePeerFilesButton;
    @FXML private Button disconnectPeerButton;
    @FXML private Button reconnectPeerButton;

    @FXML private TableView<SharedFileRow> sharedFilesTable;
    @FXML private TableColumn<SharedFileRow, String> sharedNameColumn;
    @FXML private TableColumn<SharedFileRow, String> sharedSizeColumn;
    @FXML private TableColumn<SharedFileRow, String> sharedTypeColumn;
    @FXML private TableColumn<SharedFileRow, String> sharedModifiedColumn;
    @FXML private TableColumn<SharedFileRow, String> sharedOwnerColumn;
    @FXML private Button addFileButton;
    @FXML private Button deleteFileButton;
    @FXML private Button renameFileButton;
    @FXML private Button refreshSharedButton;

    @FXML private Label remoteBrowseLabel;
    @FXML private TableView<RemoteFileRow> remoteFilesTable;
    @FXML private TableColumn<RemoteFileRow, String> remoteNameColumn;
    @FXML private TableColumn<RemoteFileRow, String> remotePeerColumn;
    @FXML private TableColumn<RemoteFileRow, String> remoteSizeColumn;
    @FXML private Button downloadButton;

    @FXML private TableView<TransferRow> transfersTable;
    @FXML private TableColumn<TransferRow, String> transferFileColumn;
    @FXML private TableColumn<TransferRow, String> transferDirectionColumn;
    @FXML private TableColumn<TransferRow, String> transferPeerColumn;
    @FXML private TableColumn<TransferRow, Double> transferProgressColumn;
    @FXML private TableColumn<TransferRow, String> transferSpeedColumn;
    @FXML private TableColumn<TransferRow, String> transferEtaColumn;
    @FXML private TableColumn<TransferRow, String> transferStatusColumn;
    @FXML private Button pauseTransferButton;
    @FXML private Button resumeTransferButton;
    @FXML private Button cancelTransferButton;

    @FXML private ListView<String> logListView;

    private final ObservableList<PeerRow> peerRows = FXCollections.observableArrayList();
    private final ObservableList<SharedFileRow> sharedFileRows = FXCollections.observableArrayList();
    private final ObservableList<RemoteFileRow> remoteFileRows = FXCollections.observableArrayList();
    private final ObservableList<TransferRow> transferRows = FXCollections.observableArrayList();
    private final ObservableList<String> logLines = FXCollections.observableArrayList();

    private AppContext context;
    private String browsedPeerId;

    @FXML
    public void initialize() {
        peerNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        peerHostColumn.setCellValueFactory(new PropertyValueFactory<>("host"));
        peerPortColumn.setCellValueFactory(new PropertyValueFactory<>("port"));
        peerStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        peerLatencyColumn.setCellValueFactory(new PropertyValueFactory<>("latency"));
        peerConnectedAtColumn.setCellValueFactory(new PropertyValueFactory<>("connectedAt"));
        peersTable.setItems(peerRows);

        sharedNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        sharedSizeColumn.setCellValueFactory(new PropertyValueFactory<>("size"));
        sharedTypeColumn.setCellValueFactory(new PropertyValueFactory<>("type"));
        sharedModifiedColumn.setCellValueFactory(new PropertyValueFactory<>("lastModified"));
        sharedOwnerColumn.setCellValueFactory(new PropertyValueFactory<>("owner"));
        sharedFilesTable.setItems(sharedFileRows);

        remoteNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        remotePeerColumn.setCellValueFactory(new PropertyValueFactory<>("peerName"));
        remoteSizeColumn.setCellValueFactory(new PropertyValueFactory<>("size"));
        remoteFilesTable.setItems(remoteFileRows);

        transferFileColumn.setCellValueFactory(new PropertyValueFactory<>("fileName"));
        transferDirectionColumn.setCellValueFactory(new PropertyValueFactory<>("direction"));
        transferPeerColumn.setCellValueFactory(new PropertyValueFactory<>("peerName"));
        transferProgressColumn.setCellValueFactory(new PropertyValueFactory<>("progress"));
        transferProgressColumn.setCellFactory(ProgressBarTableCell.forTableColumn());
        transferSpeedColumn.setCellValueFactory(new PropertyValueFactory<>("speed"));
        transferEtaColumn.setCellValueFactory(new PropertyValueFactory<>("eta"));
        transferStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        transfersTable.setItems(transferRows);

        logListView.setItems(logLines);
    }

    public void init(AppContext context) {
        this.context = context;

        var config = context.getConfigManager().get();
        localPeerInfoLabel.setText(config.getUsername() + " @ " + NetworkUtils.getLocalIpAddress()
                + ":" + context.getPeerManager().getActualPort());

        refreshSharedFiles();
        context.getPeerRegistry().addListener(new PeerRegistryListener() {
            @Override
            public void onPeerAdded(Peer peer) {
                Platform.runLater(() -> {
                    peerRows.add(new PeerRow(peer));
                    log("Peer connected: " + peer.getName() + " (" + peer.getHost() + ":" + peer.getPort() + ")");
                });
            }

            @Override
            public void onPeerUpdated(Peer peer) {
                Platform.runLater(() -> updateOrAddPeerRow(peer));
            }

            @Override
            public void onPeerRemoved(Peer peer) {
                Platform.runLater(() -> peerRows.removeIf(r -> r.getPeerId().equals(peer.getPeerId())));
            }
        });

        context.getTransferManager().addTransferListener(new TransferListener() {
            @Override
            public void onTransferChanged(Transfer transfer) {
                Platform.runLater(() -> updateOrAddTransferRow(transfer));
            }
        });

        context.getTransferManager().addRemoteFileListener((peerId, files) -> Platform.runLater(() -> {
            if (peerId.equals(browsedPeerId)) {
                renderRemoteFiles(peerId, files);
            }
        }));

        wireActions();
        log("meTorrent started as '" + config.getUsername() + "' on port " + context.getPeerManager().getActualPort());
    }

    private void wireActions() {
        connectButton.setOnAction(e -> onConnectToPeer());
        refreshPeersButton.setOnAction(e -> resyncPeerRows());
        browsePeerFilesButton.setOnAction(e -> onBrowseSelectedPeer());
        disconnectPeerButton.setOnAction(e -> withSelectedPeer(peer -> context.getPeerManager().disconnect(peer.getPeerId())));
        reconnectPeerButton.setOnAction(e -> withSelectedPeer(this::onReconnect));

        addFileButton.setOnAction(e -> onAddFile());
        deleteFileButton.setOnAction(e -> withSelectedSharedFile(this::onDeleteFile));
        renameFileButton.setOnAction(e -> withSelectedSharedFile(this::onRenameFile));
        refreshSharedButton.setOnAction(e -> refreshSharedFiles());

        downloadButton.setOnAction(e -> withSelectedRemoteFile(this::onDownload));

        pauseTransferButton.setOnAction(e -> withSelectedTransfer(t -> context.getTransferManager().pauseTransfer(t.getTransferId())));
        resumeTransferButton.setOnAction(e -> withSelectedTransfer(t -> context.getTransferManager().resumeTransfer(t.getTransferId())));
        cancelTransferButton.setOnAction(e -> withSelectedTransfer(t -> context.getTransferManager().cancelTransfer(t.getTransferId())));
    }

    private void onConnectToPeer() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Connect to Peer");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField hostField = new TextField("127.0.0.1");
        TextField portField = new TextField();
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 10, 10, 10));
        grid.addRow(0, new Label("IP Address:"), hostField);
        grid.addRow(1, new Label("Port:"), portField);
        dialog.getDialogPane().setContent(grid);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }
        String host = hostField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException ex) {
            showError("Invalid port number");
            return;
        }
        try {
            context.getPeerManager().connect(host, port);
            context.getConfigManager().addRecentConnection(host, port);
            log("Connecting to " + host + ":" + port + "...");
        } catch (IOException ex) {
            showError("Could not connect to " + host + ":" + port + " - " + ex.getMessage());
        }
    }

    private void onReconnect(PeerRow row) {
        try {
            context.getPeerManager().reconnect(row.getPeerId());
        } catch (IOException ex) {
            showError("Could not reconnect: " + ex.getMessage());
        }
    }

    private void onBrowseSelectedPeer() {
        withSelectedPeer(row -> {
            browsedPeerId = row.getPeerId();
            remoteBrowseLabel.setText("Browsing: " + row.nameProperty().get());
            try {
                context.getTransferManager().requestFileList(browsedPeerId);
                log("Requested file list from " + row.nameProperty().get());
            } catch (IOException ex) {
                showError("Could not request file list: " + ex.getMessage());
            }
            // Remote Files is the 3rd tab (index 2): Peers, Shared Files, Remote Files, Transfers.
            mainTabPane.getSelectionModel().select(2);
        });
    }

    private void onAddFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Add File to Shared Folder");
        var file = chooser.showOpenDialog(addFileButton.getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            SharedFile added = context.getFileManager().getSharedFolder().addFile(file.toPath());
            refreshSharedFiles();
            log("Added shared file: " + added.name());
        } catch (IOException ex) {
            showError("Could not add file: " + ex.getMessage());
        }
    }

    private void onDeleteFile(SharedFileRow row) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Delete '" + row.nameProperty().get() + "'?");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }
        try {
            context.getFileManager().getSharedFolder().deleteFile(row.getFileId());
            refreshSharedFiles();
            log("Deleted shared file: " + row.nameProperty().get());
        } catch (IOException ex) {
            showError("Could not delete file: " + ex.getMessage());
        }
    }

    private void onRenameFile(SharedFileRow row) {
        TextInputDialog dialog = new TextInputDialog(row.nameProperty().get());
        dialog.setTitle("Rename File");
        dialog.setHeaderText(null);
        dialog.setContentText("New name:");
        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty() || result.get().isBlank()) {
            return;
        }
        try {
            context.getFileManager().getSharedFolder().renameFile(row.getFileId(), result.get().trim());
            refreshSharedFiles();
            log("Renamed file to: " + result.get().trim());
        } catch (IOException ex) {
            showError("Could not rename file: " + ex.getMessage());
        }
    }

    private void onDownload(RemoteFileRow row) {
        Transfer transfer = context.getTransferManager().requestDownload(row.getPeerId(), row.getEntry());
        log("Started download: " + transfer.getFileName() + " from " + row.peerNameProperty().get());
    }

    private void refreshSharedFiles() {
        List<SharedFileRow> rows = context.getFileManager().getSharedFolder().list().stream()
                .map(SharedFileRow::new)
                .collect(Collectors.toList());
        sharedFileRows.setAll(rows);
    }

    private void resyncPeerRows() {
        List<PeerRow> rows = context.getPeerRegistry().list().stream()
                .map(PeerRow::new)
                .collect(Collectors.toList());
        peerRows.setAll(rows);
    }

    private void updateOrAddPeerRow(Peer peer) {
        for (PeerRow row : peerRows) {
            if (row.getPeerId().equals(peer.getPeerId())) {
                row.update(peer);
                return;
            }
        }
        peerRows.add(new PeerRow(peer));
    }

    private void updateOrAddTransferRow(Transfer transfer) {
        for (TransferRow row : transferRows) {
            if (row.getTransferId().equals(transfer.getTransferId())) {
                row.update(transfer);
                return;
            }
        }
        transferRows.add(new TransferRow(transfer));
    }

    private void renderRemoteFiles(String peerId, List<RemoteFileEntry> files) {
        String peerName = context.getPeerRegistry().get(peerId).map(Peer::getName).orElse(peerId);
        remoteFileRows.setAll(files.stream()
                .map(f -> new RemoteFileRow(f, peerId, peerName))
                .collect(Collectors.toList()));
    }

    private void withSelectedPeer(java.util.function.Consumer<PeerRow> action) {
        PeerRow selected = peersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Select a peer first");
            return;
        }
        action.accept(selected);
    }

    private void withSelectedSharedFile(java.util.function.Consumer<SharedFileRow> action) {
        SharedFileRow selected = sharedFilesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Select a shared file first");
            return;
        }
        action.accept(selected);
    }

    private void withSelectedRemoteFile(java.util.function.Consumer<RemoteFileRow> action) {
        RemoteFileRow selected = remoteFilesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Select a remote file first");
            return;
        }
        action.accept(selected);
    }

    private void withSelectedTransfer(java.util.function.Consumer<TransferRow> action) {
        TransferRow selected = transfersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Select a transfer first");
            return;
        }
        action.accept(selected);
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.showAndWait();
        log("ERROR: " + message);
    }

    private void log(String message) {
        String line = "[" + LOG_TIME.format(LocalTime.now()) + "] " + message;
        logLines.add(0, line);
        if (logLines.size() > MAX_LOG_LINES) {
            logLines.remove(logLines.size() - 1);
        }
        log.info(message);
    }
}
