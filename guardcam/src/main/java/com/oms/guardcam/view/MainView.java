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

    // --- CÁC BIẾN UI PHƠI BÀY CHO CONTROLLER ---

    // Đã thay thế cameraSelect bằng 2 biến cho Dual-Cam
    public ComboBox<String> cameraPanoSelect, cameraQrSelect;
    public ComboBox<String> resSelect, audioSelect, autoStopSelect;

    public Button startCamBtn, stopManualBtn, searchBtn, closePlaybackBtn, playPauseBtn;
    public Label statusText, currentCodeDisplay, scanIndicator, overlayText;
    public TextField barcodeInput, searchTrackingCode;

    // Đã thêm cameraQrView cho màn hình nhỏ
    public ImageView cameraView, cameraQrView;

    public StackPane aimBox, liveViewPane, playbackPane;
    public MediaView searchMediaViewPano, searchMediaViewQr;
    public Button mergeVideoBtn;
    public Slider timeSlider;
    public Label timeLabel, pbTitle, pbTime, pbShipper, pbSize;
    public VBox pathBoxContainer;

    public MainView() {
        root = new BorderPane();
        root.setStyle("-fx-background-color: #0f172a; -fx-font-family: 'Segoe UI';");
        buildSidebar();
        buildCenter();
    }

    public BorderPane getRoot() { return root; }

    private void buildSidebar() {
        VBox sidebar = new VBox(15);
        sidebar.setPadding(new Insets(20));
        sidebar.setStyle("-fx-background-color: #1e293b; -fx-border-color: #475569; -fx-border-width: 0 1 0 0;");

        Label brandLabel = new Label("🛡️ Guard Cam Pro");
        brandLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 22));
        brandLabel.setTextFill(Color.web("#60a5fa"));
        brandLabel.setMaxWidth(Double.MAX_VALUE);
        brandLabel.setAlignment(Pos.CENTER);
        VBox.setMargin(brandLabel, new Insets(0, 0, 10, 0));

        // --- BẢNG CẤU HÌNH ---
        VBox configPanel = createPanel("⚙️ Cấu hình Cam");

        // Khởi tạo 2 ComboBox chọn Camera
        cameraPanoSelect = new ComboBox<>();
        cameraPanoSelect.setPromptText("Chọn Cam Toàn Cảnh (Pano)");
        styleControl(cameraPanoSelect);

        cameraQrSelect = new ComboBox<>();
        cameraQrSelect.setPromptText("Chọn Cam Quét Mã (QR)");
        styleControl(cameraQrSelect);

        resSelect = new ComboBox<>();
        resSelect.getItems().addAll("1920x1080", "1280x720");
        resSelect.getSelectionModel().selectFirst();
        styleControl(resSelect);

        autoStopSelect = new ComboBox<>();
        autoStopSelect.getItems().addAll("1 Phút", "1.5 Phút", "2 Phút", "Tắt (Thủ công)");
        autoStopSelect.getSelectionModel().select(1);
        styleControl(autoStopSelect);

        startCamBtn = new Button("▶ BẬT HỆ THỐNG");
        startCamBtn.setMaxWidth(Double.MAX_VALUE);
        startCamBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 12px; -fx-background-radius: 6px;");

        configPanel.getChildren().addAll(
                new Label("Cam Toàn Cảnh:") {{ setTextFill(Color.web("#a6adc8")); }}, cameraPanoSelect,
                new Label("Cam Quét Mã:") {{ setTextFill(Color.web("#a6adc8")); }}, cameraQrSelect,
                new Label("Tự ngắt video sau:") {{ setTextFill(Color.web("#a6adc8")); }}, autoStopSelect,
                startCamBtn
        );

        // --- BẢNG XỬ LÝ ĐƠN HÀNG ---
        VBox orderPanel = createPanel("📦 Xử lý đơn hàng");
        orderPanel.setStyle(orderPanel.getStyle() + "-fx-border-color: #3b82f6;");

        VBox statusBox = new VBox(5);
        statusBox.setAlignment(Pos.CENTER);
        statusText = new Label("CHƯA KẾT NỐI");
        statusText.setFont(Font.font("System", FontWeight.BOLD, 14));
        statusText.setTextFill(Color.web("#10b981"));

        currentCodeDisplay = new Label("---");
        currentCodeDisplay.setFont(Font.font("System", FontWeight.BOLD, 26));
        currentCodeDisplay.setTextFill(Color.web("#facc15"));

        scanIndicator = new Label("Mắt AI: Đang tắt");
        scanIndicator.setTextFill(Color.web("#cbd5e1"));
        statusBox.getChildren().addAll(statusText, currentCodeDisplay, scanIndicator);

        barcodeInput = new TextField();
        barcodeInput.setPromptText("Súng quét / Nhập mã...");
        styleControl(barcodeInput);

        stopManualBtn = new Button("🛑 CẮT ĐƠN SỚM");
        stopManualBtn.setMaxWidth(Double.MAX_VALUE);
        stopManualBtn.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 12px; -fx-background-radius: 6px;");

        orderPanel.getChildren().addAll(statusBox, barcodeInput, stopManualBtn);

        // --- BẢNG TRA CỨU ---
        VBox searchPanel = createPanel("🔍 Tra cứu Video");
        searchTrackingCode = new TextField();
        searchTrackingCode.setPromptText("Nhập mã vận đơn...");
        styleControl(searchTrackingCode);

        searchBtn = new Button("Tìm kiếm & Phát Video");
        searchBtn.setMaxWidth(Double.MAX_VALUE);
        searchBtn.setStyle("-fx-background-color: #10b981; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 12px; -fx-background-radius: 6px;");

        searchPanel.getChildren().addAll(searchTrackingCode, searchBtn);

        sidebar.getChildren().addAll(brandLabel, configPanel, orderPanel, searchPanel);

        ScrollPane scroll = new ScrollPane(sidebar);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setMinWidth(380);
        scroll.setPrefWidth(380);
        scroll.setStyle("-fx-background-color: #1e293b; -fx-background: #1e293b; -fx-padding: 0;");

        root.setLeft(scroll);
    }

    private void buildCenter() {
        StackPane centerPane = new StackPane();
        centerPane.setStyle("-fx-background-color: #0f172a;");
        centerPane.setMinSize(0, 0);

        // --- KHU VỰC LIVE VIEW ---
        liveViewPane = new StackPane();
        liveViewPane.setPadding(new Insets(20));
        liveViewPane.setMinSize(0, 0);

        // ==========================================
        // THUẬT TOÁN "CÁI LỒNG TỈ LỆ VÀNG 16:9"
        // ==========================================
        StackPane videoWrapper = new StackPane();

        // Toán học: Rộng = Min(chiều rộng cửa sổ, chiều cao cửa sổ * 16/9)
        videoWrapper.maxWidthProperty().bind(
                Bindings.min(liveViewPane.widthProperty().subtract(40), liveViewPane.heightProperty().subtract(40).multiply(16.0 / 9.0))
        );
        // Toán học: Cao = Min(chiều cao cửa sổ, chiều rộng cửa sổ * 9/16)
        videoWrapper.maxHeightProperty().bind(
                Bindings.min(liveViewPane.heightProperty().subtract(40), liveViewPane.widthProperty().subtract(40).multiply(9.0 / 16.0))
        );

        // 1. Khung lớn cho Toàn cảnh (Pano)
        cameraView = new ImageView();
        cameraView.setPreserveRatio(true);
        // Khóa chặt vào cái lồng, không bao giờ bị thừa viền
        cameraView.fitWidthProperty().bind(videoWrapper.maxWidthProperty());
        cameraView.fitHeightProperty().bind(videoWrapper.maxHeightProperty());

        // 2. Khung nhỏ cho QR (Cam dọc)
        cameraQrView = new ImageView();
        cameraQrView.setPreserveRatio(true);

        // Chiều cao bám theo CÁI LỒNG (35%)
        cameraQrView.fitHeightProperty().bind(videoWrapper.maxHeightProperty().multiply(0.35));
        // Ép chuẩn chiều rộng dọc 9:16
        cameraQrView.fitWidthProperty().bind(cameraQrView.fitHeightProperty().multiply(9.0 / 16.0));

        StackPane qrContainer = new StackPane(cameraQrView);
        qrContainer.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        qrContainer.setStyle("-fx-border-color: rgba(255,255,255,0.6); -fx-border-width: 1.5; -fx-background-color: #000000; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.8), 15, 0, 0, 5); -fx-border-radius: 6; -fx-background-radius: 6;");

        // Nhốt QR vào góc phải của CÁI LỒNG
        StackPane.setAlignment(qrContainer, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(qrContainer, new Insets(0, 20, 20, 0));

        // 3. Đưa luôn Chữ thông báo vào lồng để nó bám chặt góc trái video
        overlayText = new Label("HÃY BẬT CAMERA");
        overlayText.setStyle("-fx-background-color: rgba(0,0,0,0.7); -fx-text-fill: #10b981; -fx-font-weight: bold; -fx-font-size: 20px; -fx-padding: 8 16; -fx-background-radius: 6;");
        StackPane.setAlignment(overlayText, Pos.TOP_LEFT);
        StackPane.setMargin(overlayText, new Insets(20));

        // Nạp tất cả vào Lồng
        videoWrapper.getChildren().addAll(cameraView, qrContainer, overlayText);

        // Nạp Lồng vào Live View
        liveViewPane.getChildren().add(videoWrapper);


        // --- KHU VỰC PLAYBACK VIEW ---
        playbackPane = new StackPane();
        playbackPane.setStyle("-fx-background-color: #0f172a;"); // Đổi nền màu Navy cho chuẩn tone
        playbackPane.setVisible(false);

        // Dùng VBox làm trục chính: Chia 2 tầng rõ rệt (Tầng Video và Tầng Control)
        VBox playerLayout = new VBox(15);
        playerLayout.setAlignment(Pos.CENTER);
        playerLayout.setPadding(new Insets(20, 20, 30, 20));

        // ==========================================
        // TẦNG 1: KHU VỰC VIDEO (Tự động co giãn)
        // ==========================================
        StackPane pbVideoArea = new StackPane();
        VBox.setVgrow(pbVideoArea, Priority.ALWAYS); // Ép chiếm hết toàn bộ không gian trống
        pbVideoArea.setMinSize(0, 0); // Cho phép thu nhỏ để không bị kẹt

        // Khung lồng 16:9 tự động tính toán dựa trên vùng pbVideoArea
        StackPane pbVideoWrapper = new StackPane();
        pbVideoWrapper.maxWidthProperty().bind(
                javafx.beans.binding.Bindings.min(pbVideoArea.widthProperty(), pbVideoArea.heightProperty().multiply(16.0 / 9.0))
        );
        pbVideoWrapper.maxHeightProperty().bind(
                javafx.beans.binding.Bindings.min(pbVideoArea.heightProperty(), pbVideoArea.widthProperty().multiply(9.0 / 16.0))
        );

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

        // Nút Đóng Video: Gắn thẳng vào Tầng Video thay vì ngoài cùng để tránh đè viền Windows
        closePlaybackBtn = new Button("❌ Đóng Video");
        closePlaybackBtn.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 16; -fx-background-radius: 6px; -fx-cursor: hand;");
        StackPane.setAlignment(closePlaybackBtn, Pos.TOP_RIGHT);
        StackPane.setMargin(closePlaybackBtn, new Insets(0));
        pbVideoArea.getChildren().add(closePlaybackBtn);

        // ==========================================
        // TẦNG 2: KHU VỰC THÔNG TIN (Khóa cố định)
        // ==========================================
        VBox bottomControls = new VBox(15);
        bottomControls.setAlignment(Pos.CENTER);

        // CHỐNG MẤT DATA: Lệnh này cấm JavaFX thu nhỏ khu vực này khi Resize
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

        pbTitle = new Label("MÃ ĐƠN: ---"); pbTitle.setFont(Font.font("System", FontWeight.BOLD, 22)); pbTitle.setTextFill(Color.web("#facc15"));
        pbTime = new Label("🕒 Đóng gói: ---"); pbTime.setTextFill(Color.web("#a6e3a1"));
        pbShipper = new Label("👤 Nhân viên: Admin"); pbShipper.setTextFill(Color.web("#89b4fa"));
        pbSize = new Label("💾 Dung lượng: ---"); pbSize.setTextFill(Color.web("#f38ba8"));

        Label pbl = new Label("📂 Danh sách File (Click để mở):"); pbl.setTextFill(Color.web("#cbd5e1"));
        pathBoxContainer = new VBox(5);
        pathBoxContainer.setPadding(new Insets(5, 0, 0, 10));

        pbMetaBox.getChildren().addAll(pbTitle, pbTime, pbShipper, pbSize, pbl, pathBoxContainer);

        bottomControls.getChildren().addAll(controlBar, pbMetaBox);

        // Lắp 2 tầng vào VBox chính
        playerLayout.getChildren().addAll(pbVideoArea, bottomControls);
        playbackPane.getChildren().add(playerLayout);

        centerPane.getChildren().addAll(liveViewPane, playbackPane);
        root.setCenter(centerPane);
    }

    private VBox createPanel(String titleStr) {
        VBox panel = new VBox(10);
        panel.setStyle("-fx-background-color: rgba(0,0,0,0.2); -fx-padding: 15px; -fx-border-radius: 8px; -fx-border-color: #475569;");
        Label title = new Label(titleStr);
        title.setTextFill(Color.web("#94a3b8"));
        title.setFont(Font.font("System", FontWeight.BOLD, 13));
        panel.getChildren().add(title);
        return panel;
    }

    private void styleControl(Control control) {
        control.setStyle("-fx-background-color: #0f172a; -fx-text-fill: white; -fx-border-color: #475569; -fx-border-radius: 6px; -fx-padding: 5px;");
        control.setMaxWidth(Double.MAX_VALUE);
    }
}