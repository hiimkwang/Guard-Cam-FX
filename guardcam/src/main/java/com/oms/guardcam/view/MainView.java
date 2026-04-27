package com.oms.guardcam.view;

import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public class MainView {
    private BorderPane root;

    public ComboBox<String> cameraPanoSelect, cameraQrSelect;
    public ComboBox<String> resPanoSelect, resQrSelect, autoStopSelect;

    public Button startCamBtn, stopManualBtn, searchBtn, closePlaybackBtn, playPauseBtn;
    public Label statusText, currentCodeDisplay, scanIndicator, overlayText;
    public TextField barcodeInput, searchTrackingCode;

    public ImageView cameraView, cameraQrView;

    public StackPane aimBox, liveViewPane, playbackPane, qrContainer;
    public MediaView searchMediaViewPano, searchMediaViewQr;
    public Button mergeVideoBtn;
    public Slider timeSlider;
    public Label timeLabel, pbTitle, pbTime, pbShipper, pbSize;
    public VBox pathBoxContainer;

    public Button saveSettingsBtn;

    public MainView() {
        root = new BorderPane();
        root.setStyle("-fx-background-color: #0f172a; -fx-font-family: 'Segoe UI';");
        buildSidebar();
        buildCenter();
    }

    public BorderPane getRoot() {
        return root;
    }

    private void buildSidebar() {
        VBox sidebar = new VBox(8); // Tăng nhẹ khoảng cách cho thoáng mắt
        sidebar.setPadding(new Insets(8, 12, 8, 12));
        sidebar.setStyle("-fx-background-color: #1e293b; -fx-border-color: #334155; -fx-border-width: 0 1 0 0;");

        Label brandLabel = new Label("🛡 GUARD CAM PRO");
        brandLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        brandLabel.setTextFill(Color.web("#38bdf8")); // Màu xanh sáng neon hiện đại
        brandLabel.setMaxWidth(Double.MAX_VALUE);
        brandLabel.setAlignment(Pos.CENTER);
        VBox.setMargin(brandLabel, new Insets(0, 0, 5, 0));

        // --- CẤU HÌNH HỆ THỐNG ---
        VBox configPanel = createPanel("⚙ CẤU HÌNH HỆ THỐNG");

        cameraPanoSelect = new ComboBox<>();
        cameraPanoSelect.setPromptText("Chọn Camera Toàn Cảnh...");
        styleControl(cameraPanoSelect);

        cameraQrSelect = new ComboBox<>();
        cameraQrSelect.setPromptText("Chọn Camera Quét Mã...");
        styleControl(cameraQrSelect);

        resPanoSelect = new ComboBox<>();
        resPanoSelect.getItems().addAll("1920x1080", "1280x720", "640x480");
        resPanoSelect.getSelectionModel().selectFirst();
        styleControl(resPanoSelect);

        resQrSelect = new ComboBox<>();
        resQrSelect.getItems().addAll("1920x1080", "1280x720", "640x480");
        resQrSelect.getSelectionModel().select(1);
        styleControl(resQrSelect);

        autoStopSelect = new ComboBox<>();
        autoStopSelect.getItems().addAll("1 Phút", "1.5 Phút", "2 Phút", "Tắt (Thủ công)");
        autoStopSelect.getSelectionModel().select(1);
        styleControl(autoStopSelect);

        // GỘP: Hai ô độ phân giải
        HBox rowRes = new HBox(8);
        VBox panoResBox = new VBox(2, new Label("🖥 Cỡ Pano:") {{
            setTextFill(Color.web("#94a3b8"));
            setFont(Font.font(11));
        }}, resPanoSelect);
        VBox qrResBox = new VBox(2, new Label("📱 Cỡ QR:") {{
            setTextFill(Color.web("#94a3b8"));
            setFont(Font.font(11));
        }}, resQrSelect);
        HBox.setHgrow(panoResBox, Priority.ALWAYS);
        HBox.setHgrow(qrResBox, Priority.ALWAYS);
        rowRes.getChildren().addAll(panoResBox, qrResBox);

        // TÁCH RỜI: Ô tự động ngắt
        VBox stopBox = new VBox(2, new Label("⏱ Tự động ngắt video:") {{
            setTextFill(Color.web("#94a3b8"));
            setFont(Font.font(11));
        }}, autoStopSelect);

        saveSettingsBtn = new Button("💾 Lưu Cấu Hình");
        saveSettingsBtn.setStyle("-fx-background-color: #059669; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8px; -fx-background-radius: 6px; -fx-cursor: hand;");
        saveSettingsBtn.setMaxWidth(Double.MAX_VALUE);

        startCamBtn = new Button("▶ BẮT ĐẦU GHI HÌNH");
        startCamBtn.setMaxWidth(Double.MAX_VALUE);
        startCamBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10px; -fx-background-radius: 6px; -fx-cursor: hand;");

        configPanel.getChildren().addAll(
                new Label("📹 Camera Toàn Cảnh (Pano):") {{ setTextFill(Color.web("#94a3b8")); setFont(Font.font(11)); }}, cameraPanoSelect,
                new Label("📷 Camera Quét Mã (QR):") {{ setTextFill(Color.web("#94a3b8")); setFont(Font.font(11)); }}, cameraQrSelect,
                rowRes,
                stopBox,
                saveSettingsBtn,
                startCamBtn
        );

        // --- TRẠNG THÁI VẬN ĐƠN ---
        VBox orderPanel = createPanel("📦 QUÉT MÃ ĐÓNG HÀNG");

        // THIẾT KẾ MỚI: Bọc khu vực trạng thái vào một "Màn hình mini" trông cực ngầu
        VBox statusBox = new VBox(2);
        statusBox.setAlignment(Pos.CENTER);
        statusBox.setStyle("-fx-background-color: #0f172a; -fx-padding: 10px; -fx-background-radius: 6px; -fx-border-color: #3b82f6; -fx-border-radius: 6px; -fx-border-width: 1px;");

        statusText = new Label("🔴 CHƯA KẾT NỐI");
        statusText.setFont(Font.font("System", FontWeight.BOLD, 12));
        statusText.setTextFill(Color.web("#10b981"));

        currentCodeDisplay = new Label("---");
        currentCodeDisplay.setFont(Font.font("System", FontWeight.BOLD, 22));
        currentCodeDisplay.setTextFill(Color.web("#facc15"));

        scanIndicator = new Label("🤖 Mắt AI: Đang tắt...");
        scanIndicator.setFont(Font.font(11));
        scanIndicator.setTextFill(Color.web("#94a3b8"));
        statusBox.getChildren().addAll(statusText, currentCodeDisplay, scanIndicator);

        barcodeInput = new TextField();
        barcodeInput.setPromptText("🔫 Súng quét hoặc nhập mã...");
        styleControl(barcodeInput);
        barcodeInput.setPrefHeight(32);

        stopManualBtn = new Button("🛑 CẮT ĐƠN SỚM");
        stopManualBtn.setMaxWidth(Double.MAX_VALUE);
        stopManualBtn.setStyle("-fx-background-color: #dc2626; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8px; -fx-background-radius: 6px; -fx-cursor: hand;");

        orderPanel.getChildren().addAll(statusBox, barcodeInput, stopManualBtn);

        // --- TRA CỨU DỮ LIỆU ---
        VBox searchPanel = createPanel("🔍 TRA CỨU DỮ LIỆU");
        searchTrackingCode = new TextField();
        searchTrackingCode.setPromptText("Nhập mã vận đơn cần tìm...");
        styleControl(searchTrackingCode);
        searchTrackingCode.setPrefHeight(32);

        searchBtn = new Button("▶ Phát Video");
        searchBtn.setMaxWidth(Double.MAX_VALUE);
        searchBtn.setStyle("-fx-background-color: #0ea5e9; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8px; -fx-background-radius: 6px; -fx-cursor: hand;");

        searchPanel.getChildren().addAll(searchTrackingCode, searchBtn);

        sidebar.getChildren().addAll(brandLabel, configPanel, orderPanel, searchPanel);

        ScrollPane scroll = new ScrollPane(sidebar);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setMinWidth(330);
        scroll.setPrefWidth(330);
        scroll.setStyle("-fx-background-color: #1e293b; -fx-background: #1e293b; -fx-padding: 0;");

        root.setLeft(scroll);
    }

    private VBox createPanel(String titleStr) {
        VBox panel = new VBox(6);
        // Làm tối màu nền panel 1 chút, viền tinh tế hơn
        panel.setStyle("-fx-background-color: rgba(15, 23, 42, 0.4); -fx-padding: 10px; -fx-border-radius: 8px; -fx-border-color: #334155; -fx-border-width: 1px;");
        Label title = new Label(titleStr);
        title.setTextFill(Color.web("#cbd5e1"));
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        panel.getChildren().add(title);
        return panel;
    }

    private void buildCenter() {
        StackPane centerPane = new StackPane();
        centerPane.setStyle("-fx-background-color: #0f172a;");
        centerPane.setMinSize(0, 0);

        liveViewPane = new StackPane();
        liveViewPane.setPadding(new Insets(20));
        liveViewPane.setMinSize(0, 0);

        StackPane videoWrapper = new StackPane();

        videoWrapper.maxWidthProperty().bind(Bindings.min(liveViewPane.widthProperty().subtract(40), liveViewPane.heightProperty().subtract(40).multiply(16.0 / 9.0)));
        videoWrapper.maxHeightProperty().bind(Bindings.min(liveViewPane.heightProperty().subtract(40), liveViewPane.widthProperty().subtract(40).multiply(9.0 / 16.0)));

        cameraView = new ImageView();
        cameraView.setPreserveRatio(true);
        cameraView.fitWidthProperty().bind(videoWrapper.maxWidthProperty());
        cameraView.fitHeightProperty().bind(videoWrapper.maxHeightProperty());

        cameraQrView = new ImageView();
        cameraQrView.setPreserveRatio(true);
        cameraQrView.fitHeightProperty().bind(videoWrapper.maxHeightProperty().multiply(0.35));
        cameraQrView.fitWidthProperty().bind(cameraQrView.fitHeightProperty().multiply(9.0 / 16.0));

        qrContainer = new StackPane(cameraQrView);
        qrContainer.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        qrContainer.setStyle("-fx-border-color: rgba(255,255,255,0.6); -fx-border-width: 1.5; -fx-background-color: #000000; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.8), 15, 0, 0, 5); -fx-border-radius: 6; -fx-background-radius: 6;");
        StackPane.setAlignment(qrContainer, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(qrContainer, new Insets(0, 20, 20, 0));

        qrContainer.setVisible(false);

        overlayText = new Label("HÃY BẬT CAMERA");
        overlayText.setStyle("-fx-background-color: rgba(0,0,0,0.7); -fx-text-fill: #10b981; -fx-font-weight: bold; -fx-font-size: 20px; -fx-padding: 8 16; -fx-background-radius: 6;");
        StackPane.setAlignment(overlayText, Pos.TOP_RIGHT);
        StackPane.setMargin(overlayText, new Insets(20));

        overlayText.setVisible(false);

        videoWrapper.getChildren().addAll(cameraView, qrContainer, overlayText);
        liveViewPane.getChildren().add(videoWrapper);

        // --- KHU VỰC PLAYBACK VIEW ---
        playbackPane = new StackPane();
        playbackPane.setStyle("-fx-background-color: #0f172a;");
        playbackPane.setVisible(false);

        VBox playerLayout = new VBox(15);
        playerLayout.setAlignment(Pos.CENTER);
        playerLayout.setPadding(new Insets(20, 20, 30, 20));

        StackPane pbVideoArea = new StackPane();
        VBox.setVgrow(pbVideoArea, Priority.ALWAYS);
        pbVideoArea.setMinSize(0, 0);

        StackPane pbVideoWrapper = new StackPane();
        pbVideoWrapper.maxWidthProperty().bind(javafx.beans.binding.Bindings.min(pbVideoArea.widthProperty(), pbVideoArea.heightProperty().multiply(16.0 / 9.0)));
        pbVideoWrapper.maxHeightProperty().bind(javafx.beans.binding.Bindings.min(pbVideoArea.heightProperty(), pbVideoArea.widthProperty().multiply(9.0 / 16.0)));

        searchMediaViewPano = new MediaView();
        searchMediaViewPano.setPreserveRatio(true);
        searchMediaViewPano.fitWidthProperty().bind(pbVideoWrapper.maxWidthProperty());
        searchMediaViewPano.fitHeightProperty().bind(pbVideoWrapper.maxHeightProperty());

        searchMediaViewQr = new MediaView();
        searchMediaViewQr.setPreserveRatio(true);
        searchMediaViewQr.fitHeightProperty().bind(pbVideoWrapper.maxHeightProperty().multiply(0.35));
        searchMediaViewQr.fitWidthProperty().bind(searchMediaViewQr.fitHeightProperty().multiply(9.0 / 16.0));

        StackPane pbQrContainer = new StackPane(searchMediaViewQr);
        pbQrContainer.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        pbQrContainer.setStyle("-fx-border-color: rgba(255,255,255,0.6); -fx-border-width: 1.5; -fx-background-color: #000000; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.8), 15, 0, 0, 5); -fx-border-radius: 6; -fx-background-radius: 6;");
        StackPane.setAlignment(pbQrContainer, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(pbQrContainer, new Insets(0, 20, 20, 0));

        pbVideoWrapper.getChildren().addAll(searchMediaViewPano, pbQrContainer);
        pbVideoArea.getChildren().add(pbVideoWrapper);

        closePlaybackBtn = new Button("❌ Đóng Video");
        closePlaybackBtn.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 16; -fx-background-radius: 6px; -fx-cursor: hand;");
        StackPane.setAlignment(closePlaybackBtn, Pos.TOP_RIGHT);
        StackPane.setMargin(closePlaybackBtn, new Insets(0));
        pbVideoArea.getChildren().add(closePlaybackBtn);

        VBox bottomControls = new VBox(15);
        bottomControls.setAlignment(Pos.CENTER);
        bottomControls.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        HBox controlBar = new HBox(15);
        controlBar.setAlignment(Pos.CENTER);
        controlBar.setPadding(new Insets(10, 20, 10, 20));
        controlBar.setStyle("-fx-background-color: #1e293b; -fx-border-radius: 8px; -fx-border-color: #475569;");
        controlBar.setMaxWidth(1000);

        playPauseBtn = new Button("⏸ Tạm dừng");
        playPauseBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-weight: bold; -fx-pref-width: 100px; -fx-cursor: hand;");

        timeSlider = new Slider();
        HBox.setHgrow(timeSlider, Priority.ALWAYS);

        timeLabel = new Label("00:00 / 00:00");
        timeLabel.setTextFill(Color.WHITE);

        mergeVideoBtn = new Button("✂️ Ghép thành 1 File");
        mergeVideoBtn.setStyle("-fx-background-color: #f59e0b; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");

        controlBar.getChildren().addAll(playPauseBtn, timeSlider, timeLabel, mergeVideoBtn);

        VBox pbMetaBox = new VBox(8);
        pbMetaBox.setMaxWidth(1000);
        pbMetaBox.setStyle("-fx-background-color: #1e293b; -fx-padding: 20px; -fx-border-radius: 12px; -fx-border-color: #475569;");

        pbTitle = new Label("MÃ ĐƠN: ---");
        pbTitle.setFont(Font.font("System", FontWeight.BOLD, 22));
        pbTitle.setTextFill(Color.web("#facc15"));
        pbTime = new Label("🕒 Đóng gói: ---");
        pbTime.setTextFill(Color.web("#a6e3a1"));
        pbShipper = new Label("👤 Nhân viên: Admin");
        pbShipper.setTextFill(Color.web("#89b4fa"));
        pbSize = new Label("💾 Dung lượng: ---");
        pbSize.setTextFill(Color.web("#f38ba8"));

        Label pbl = new Label("📂 Danh sách File (Click để mở):");
        pbl.setTextFill(Color.web("#cbd5e1"));
        pathBoxContainer = new VBox(5);
        pathBoxContainer.setPadding(new Insets(5, 0, 0, 10));

        pbMetaBox.getChildren().addAll(pbTitle, pbTime, pbShipper, pbSize, pbl, pathBoxContainer);
        bottomControls.getChildren().addAll(controlBar, pbMetaBox);

        playerLayout.getChildren().addAll(pbVideoArea, bottomControls);
        playbackPane.getChildren().add(playerLayout);

        centerPane.getChildren().addAll(liveViewPane, playbackPane);
        root.setCenter(centerPane);
    }

//    private VBox createPanel(String titleStr) {
//        VBox panel = new VBox(5);
//        panel.setStyle("-fx-background-color: rgba(0,0,0,0.2); -fx-padding: 8px 10px; -fx-border-radius: 8px; -fx-border-color: #475569;");
//        Label title = new Label(titleStr);
//        title.setTextFill(Color.web("#94a3b8"));
//        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
//        panel.getChildren().add(title);
//        return panel;
//    }

    private void styleControl(Control control) {
        control.setStyle("-fx-background-color: #0f172a; -fx-text-fill: white; -fx-border-color: #475569; -fx-border-radius: 6px; -fx-padding: 5px;");
        control.setMaxWidth(Double.MAX_VALUE);
    }
}