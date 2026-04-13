package com.oms.guardcam;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.util.Duration; // Thêm thư viện xử lý thời gian Media
import org.bytedeco.javacv.*;
import org.bytedeco.javacv.Frame;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Point;
import org.bytedeco.opencv.opencv_core.Scalar;

import java.awt.image.BufferedImage;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.awt.Desktop;

public class GuardCamApp extends Application {

    // --- CÁC BIẾN GIAO DIỆN CỘT TRÁI ---
    private ComboBox<String> cameraSelect, resSelect, audioSelect, autoStopSelect;
    private CheckBox autoFocusToggle;
    private Slider focusDistanceSlider;
    private Button startCamBtn, stopManualBtn, searchBtn;
    private Label statusText, currentCodeDisplay, scanIndicator;
    private TextField barcodeInput, searchTrackingCode;

    // --- CÁC BIẾN GIAO DIỆN MAIN VIEW (GIỮA) ---
    private StackPane mainCenterPane;

    // 1. Giao diện Live Camera
    private StackPane liveViewPane;
    private ImageView cameraView;
    private StackPane aimBox;
    private Label overlayText;

    // 2. Giao diện Playback Video
    private StackPane playbackPane;
    private MediaView searchMediaView;
    private MediaPlayer searchMediaPlayer;
    private Label pbTitle, pbTime, pbShipper, pbSize;
    private Hyperlink pbPathLink;
    private Button closePlaybackBtn;

    // Các biến cho Thanh điều khiển Video (Control Bar)
    private Button playPauseBtn;
    private Slider timeSlider;
    private Label timeLabel;

    // --- CÁC BIẾN XỬ LÝ LÕI ---
    private FFmpegFrameGrabber grabber;
    private FFmpegFrameRecorder recorder;
    private JavaFXFrameConverter fxConverter = new JavaFXFrameConverter();
    private Java2DFrameConverter java2dConverter = new Java2DFrameConverter();
    private OpenCVFrameConverter.ToMat matConverter = new OpenCVFrameConverter.ToMat();
    private MultiFormatReader barcodeReader;

    private volatile boolean isSystemRunning = false;
    private volatile boolean isRecording = false;
    private volatile boolean isScanning = false;
    private volatile boolean isUiUpdating = false;
    private String currentTrackingCode = "";
    private int frameCount = 0;
    private long recordingStartTime = 0;
    private SimpleDateFormat videoTimeFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

    private final Object recorderLock = new Object();
    private ExecutorService aiScannerThread = Executors.newSingleThreadExecutor();

    // ĐƯỜNG DẪN LƯU VIDEO
    private final String SAVE_DIR = "E:\\Quang Workspace\\GuardCam FX\\videos\\";

    public static void main(String[] args) {
        // Tắt log vàng rác của FFmpeg
        org.bytedeco.ffmpeg.global.avutil.av_log_set_level(org.bytedeco.ffmpeg.global.avutil.AV_LOG_ERROR);
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        setupBarcodeReader();

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #0f172a; -fx-font-family: 'Segoe UI';");

        // ==========================================
        // SIDEBAR (CỘT TRÁI)
        // ==========================================
        VBox sidebar = new VBox(15);
        sidebar.setPadding(new Insets(20));
        sidebar.setStyle("-fx-background-color: #1e293b; -fx-border-color: #475569; -fx-border-width: 0 1 0 0;");

        Label brandLabel = new Label("🛡️ Guard Cam Pro");
        brandLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 22));
        brandLabel.setTextFill(Color.web("#60a5fa"));
        brandLabel.setMaxWidth(Double.MAX_VALUE);
        brandLabel.setAlignment(Pos.CENTER);
        VBox.setMargin(brandLabel, new Insets(0, 0, 10, 0));

        VBox configPanel = createPanel("⚙️ Cấu hình Cam");
        cameraSelect = new ComboBox<>();
        try {
            String[] devices = org.bytedeco.javacv.VideoInputFrameGrabber.getDeviceDescriptions();
            if (devices != null && devices.length > 0) cameraSelect.getItems().addAll(devices);
            else cameraSelect.getItems().add("0");
        } catch (Exception e) {
            cameraSelect.getItems().add("0");
        }
        cameraSelect.getSelectionModel().selectFirst();
        styleControl(cameraSelect);

        HBox resAudioBox = new HBox(10);
        resSelect = new ComboBox<>();
        resSelect.getItems().addAll("1920x1080", "1280x720");
        resSelect.getSelectionModel().selectFirst();
        resSelect.setPrefWidth(180);
        styleControl(resSelect);
        audioSelect = new ComboBox<>();
        audioSelect.getItems().addAll("Tắt Mic", "Bật Mic");
        audioSelect.getSelectionModel().selectFirst();
        audioSelect.setPrefWidth(120);
        styleControl(audioSelect);
        resAudioBox.getChildren().addAll(resSelect, audioSelect);

        VBox focusBox = new VBox(5);
        focusBox.setStyle("-fx-background-color: #282a36; -fx-padding: 10px; -fx-border-radius: 5px; -fx-border-color: #475569;");
        autoFocusToggle = new CheckBox("Auto Focus");
        autoFocusToggle.setTextFill(Color.WHITE);
        autoFocusToggle.setSelected(true);
        focusDistanceSlider = new Slider(0, 1, 0.5);
        focusDistanceSlider.setDisable(true);
        autoFocusToggle.setOnAction(e -> focusDistanceSlider.setDisable(autoFocusToggle.isSelected()));
        focusBox.getChildren().addAll(autoFocusToggle, new Label("CHỈNH NÉT") {{
            setTextFill(Color.web("#a6adc8"));
        }}, focusDistanceSlider);

        VBox autoStopBox = new VBox(5);
        autoStopSelect = new ComboBox<>();
        autoStopSelect.getItems().addAll("1 Phút", "1.5 Phút", "2 Phút", "Tắt (Thủ công)");
        autoStopSelect.getSelectionModel().select(1);
        styleControl(autoStopSelect);
        autoStopBox.getChildren().addAll(new Label("Tự ngắt video sau:") {{
            setTextFill(Color.web("#a6adc8"));
        }}, autoStopSelect);

        startCamBtn = new Button("▶ BẬT HỆ THỐNG");
        startCamBtn.setMaxWidth(Double.MAX_VALUE);
        startCamBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 12px; -fx-background-radius: 6px;");
        startCamBtn.setOnAction(e -> toggleSystem());

        configPanel.getChildren().addAll(cameraSelect, resAudioBox, focusBox, autoStopBox, startCamBtn);

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
        barcodeInput.setOnAction(e -> triggerNewOrder(barcodeInput.getText().trim()));

        stopManualBtn = new Button("🛑 CẮT ĐƠN SỚM");
        stopManualBtn.setMaxWidth(Double.MAX_VALUE);
        stopManualBtn.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 12px; -fx-background-radius: 6px;");
        stopManualBtn.setOnAction(e -> stopAndSaveRecording(true));

        orderPanel.getChildren().addAll(statusBox, barcodeInput, stopManualBtn);

        VBox searchPanel = createPanel("🔍 Tra cứu Video");
        searchTrackingCode = new TextField();
        searchTrackingCode.setPromptText("Nhập mã vận đơn...");
        styleControl(searchTrackingCode);
        searchBtn = new Button("Tìm kiếm & Phát Video");
        searchBtn.setMaxWidth(Double.MAX_VALUE);
        searchBtn.setStyle("-fx-background-color: #10b981; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 12px; -fx-background-radius: 6px;");
        searchBtn.setOnAction(e -> searchVideo());
        searchPanel.getChildren().addAll(searchTrackingCode, searchBtn);

        sidebar.getChildren().addAll(brandLabel, configPanel, orderPanel, searchPanel);

        ScrollPane scrollSidebar = new ScrollPane(sidebar);
        scrollSidebar.setFitToWidth(true);
        scrollSidebar.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollSidebar.setMinWidth(380);
        scrollSidebar.setPrefWidth(380);
        scrollSidebar.setStyle("-fx-background-color: #1e293b; -fx-background: #1e293b; -fx-padding: 0;");


        // ==========================================
        // MAIN CENTER VIEW (GIỮA) - ĐÃ FIX MẤT GÓC
        // ==========================================
        mainCenterPane = new StackPane();
        mainCenterPane.setStyle("-fx-background-color: #000000;");
        mainCenterPane.setMinSize(0, 0); // FIX: Ép khu vực giữa cho phép co giãn

        // --- LAYER 1: LIVE CAMERA ---
        liveViewPane = new StackPane();
        liveViewPane.setPadding(new Insets(20));
        liveViewPane.setMinSize(0, 0); // FIX: Tước bỏ sự cứng đầu của ImageView, cho phép thu nhỏ

        cameraView = new ImageView();
        cameraView.setPreserveRatio(true);
        cameraView.fitWidthProperty().bind(liveViewPane.widthProperty().subtract(40));
        cameraView.fitHeightProperty().bind(liveViewPane.heightProperty().subtract(40));

        aimBox = new StackPane();
        aimBox.setMaxSize(400, 250);
        aimBox.setStyle("-fx-border-color: #10b981; -fx-border-width: 3; -fx-border-radius: 10; -fx-background-color: transparent;");
        aimBox.setVisible(false);

        overlayText = new Label("HÃY BẬT CAMERA");
        overlayText.setStyle("-fx-background-color: rgba(0,0,0,0.7); -fx-text-fill: #10b981; -fx-font-weight: bold; -fx-font-size: 20px; -fx-padding: 8 16; -fx-background-radius: 6;");
        StackPane.setAlignment(overlayText, Pos.TOP_LEFT);
        StackPane.setMargin(overlayText, new Insets(20));

        liveViewPane.getChildren().addAll(cameraView, aimBox, overlayText);

        // --- LAYER 2: PLAYBACK VIDEO CÓ THANH ĐIỀU KHIỂN ---
        playbackPane = new StackPane();
        playbackPane.setStyle("-fx-background-color: rgba(15, 23, 42, 0.95);");
        playbackPane.setVisible(false);

        // Khung chứa Video + Thanh điều khiển
        VBox playerContainer = new VBox(15);
        playerContainer.setAlignment(Pos.CENTER);
        playerContainer.setPadding(new Insets(20, 20, 180, 20)); // Để lại khoảng trống bên dưới cho hộp Meta

        searchMediaView = new MediaView();
        searchMediaView.setPreserveRatio(true);
        // Trừ hao chiều cao để chừa chỗ cho thanh điều khiển
        searchMediaView.fitWidthProperty().bind(playbackPane.widthProperty().subtract(100));
        searchMediaView.fitHeightProperty().bind(playbackPane.heightProperty().subtract(250));

        // THANH ĐIỀU KHIỂN (Media Controls)
        HBox controlBar = new HBox(15);
        controlBar.setAlignment(Pos.CENTER);
        controlBar.setPadding(new Insets(10, 20, 10, 20));
        controlBar.setStyle("-fx-background-color: rgba(30, 41, 59, 0.9); -fx-border-radius: 8px; -fx-border-color: #475569;");
        controlBar.setMaxWidth(800);

        playPauseBtn = new Button("⏸ Tạm dừng");
        playPauseBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-weight: bold; -fx-pref-width: 100px;");
        playPauseBtn.setOnAction(e -> {
            if (searchMediaPlayer == null) return;
            if (searchMediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
                searchMediaPlayer.pause();
                playPauseBtn.setText("▶ Phát");
            } else {
                searchMediaPlayer.play();
                playPauseBtn.setText("⏸ Tạm dừng");
            }
        });

        timeSlider = new Slider();
        HBox.setHgrow(timeSlider, Priority.ALWAYS); // Ép thanh slider dãn hết mức có thể

        // Sự kiện khi người dùng chủ động kéo thanh thời gian
        timeSlider.valueChangingProperty().addListener((obs, wasChanging, isChanging) -> {
            if (!isChanging && searchMediaPlayer != null) {
                searchMediaPlayer.seek(Duration.seconds(timeSlider.getValue()));
            }
        });
        timeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (!timeSlider.isValueChanging() && Math.abs(newVal.doubleValue() - oldVal.doubleValue()) > 0.5 && searchMediaPlayer != null) {
                searchMediaPlayer.seek(Duration.seconds(newVal.doubleValue()));
            }
        });

        timeLabel = new Label("00:00 / 00:00");
        timeLabel.setTextFill(Color.WHITE);
        timeLabel.setFont(Font.font("System", FontWeight.BOLD, 13));

        controlBar.getChildren().addAll(playPauseBtn, timeSlider, timeLabel);

        // Bấm thẳng vào video cũng Pause/Play được
        searchMediaView.setOnMouseClicked(e -> playPauseBtn.fire());

        playerContainer.getChildren().addAll(searchMediaView, controlBar);

        // HỘP THÔNG TIN (Metadata Box) nổi bên dưới
        VBox pbMetaBox = new VBox(8);
        pbMetaBox.setMaxWidth(650);
        pbMetaBox.setMaxHeight(150);
        pbMetaBox.setStyle("-fx-background-color: rgba(30, 41, 59, 0.85); -fx-padding: 20px; -fx-border-radius: 12px; -fx-border-color: #475569;");

        pbTitle = new Label("MÃ ĐƠN: ---");
        pbTitle.setFont(Font.font("System", FontWeight.BOLD, 22));
        pbTitle.setTextFill(Color.web("#facc15"));
        pbTime = new Label("🕒 Đóng gói: ---");
        pbTime.setTextFill(Color.web("#a6e3a1"));
        pbTime.setFont(Font.font(14));
        pbShipper = new Label("👤 Nhân viên: Admin");
        pbShipper.setTextFill(Color.web("#89b4fa"));
        pbShipper.setFont(Font.font(14));
        pbSize = new Label("💾 Dung lượng: ---");
        pbSize.setTextFill(Color.web("#f38ba8"));
        pbSize.setFont(Font.font(14));

        HBox pathBox = new HBox(5);
        pathBox.setAlignment(Pos.CENTER_LEFT);
        Label pbl = new Label("📂 Nơi lưu: ");
        pbl.setTextFill(Color.web("#cbd5e1"));
        pbl.setFont(Font.font(14));
        pbPathLink = new Hyperlink("---");
        pbPathLink.setTextFill(Color.web("#cba6f7"));
        pbPathLink.setFont(Font.font(14));
        pbPathLink.setOnAction(e -> openFolderOfVideo());
        pathBox.getChildren().addAll(pbl, pbPathLink);

        pbMetaBox.getChildren().addAll(pbTitle, pbTime, pbShipper, pbSize, pathBox);
        StackPane.setAlignment(pbMetaBox, Pos.BOTTOM_CENTER);
        StackPane.setMargin(pbMetaBox, new Insets(0, 0, 20, 0));

        closePlaybackBtn = new Button("❌ Đóng Video (Quay lại Live)");
        closePlaybackBtn.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 20; -fx-background-radius: 6px; -fx-font-size: 14px;");
        closePlaybackBtn.setOnAction(e -> closePlaybackMode());
        StackPane.setAlignment(closePlaybackBtn, Pos.TOP_RIGHT);
        StackPane.setMargin(closePlaybackBtn, new Insets(20));

        playbackPane.getChildren().addAll(playerContainer, pbMetaBox, closePlaybackBtn);

        mainCenterPane.getChildren().addAll(liveViewPane, playbackPane);

        root.setLeft(scrollSidebar);
        root.setCenter(mainCenterPane);

        Scene scene = new Scene(root, 1366, 768);
        stage.setTitle("Order Guard Cam Pro - Enterprise Native");
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> {
            shutdownSystem();
            Platform.exit();
            System.exit(0);
        });
        stage.show();
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

    private void setupBarcodeReader() {
        barcodeReader = new MultiFormatReader();
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Collections.singletonList(com.google.zxing.BarcodeFormat.CODE_128));
        barcodeReader.setHints(hints);
        new File(SAVE_DIR).mkdirs();
    }

    private void toggleSystem() {
        if (isSystemRunning) {
            stopCameraOnly();
            startCamBtn.setText("▶ BẬT HỆ THỐNG");
            startCamBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-weight: bold;");
            statusText.setText("CHƯA KẾT NỐI");
            statusText.setTextFill(Color.web("#a6adc8"));
            cameraView.setImage(null);
            aimBox.setVisible(false);
            overlayText.setText("HÃY BẬT CAMERA");
            overlayText.setTextFill(Color.web("#a6e3a1"));
            scanIndicator.setText("Mắt AI: Đang tắt");
        } else {
            startCameraThread();
            startCamBtn.setText("⏹ TẮT HỆ THỐNG");
            startCamBtn.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-weight: bold;");
            statusText.setText("SẴN SÀNG QUÉT MÃ");
            statusText.setTextFill(Color.web("#10b981"));
            overlayText.setText("SẴN SÀNG");
            overlayText.setTextFill(Color.web("#10b981"));
            scanIndicator.setText("Mắt AI: Đang dò mã...");
            scanIndicator.setTextFill(Color.web("#3b82f6"));
            aimBox.setVisible(true);
            barcodeInput.requestFocus();
        }
    }

    private void startCameraThread() {
        isSystemRunning = true;
        String camName = cameraSelect.getValue();
        boolean is1080p = resSelect.getSelectionModel().getSelectedIndex() == 0;
        int targetWidth = is1080p ? 1920 : 1280;
        int targetHeight = is1080p ? 1080 : 720;

        Thread camThread = new Thread(() -> {
            try {
                if (camName.equals("0")) grabber = new FFmpegFrameGrabber("video=0");
                else grabber = new FFmpegFrameGrabber("video=" + camName);

                grabber.setFormat("dshow");
                grabber.setImageWidth(targetWidth);
                grabber.setImageHeight(targetHeight);
                grabber.setFrameRate(30);
                grabber.setVideoOption("vcodec", "mjpeg");
                grabber.start();

                while (isSystemRunning) {
                    Frame frame = grabber.grab();
                    if (frame == null) continue;
                    frameCount++;

                    Mat mat = matConverter.convert(frame);
                    String timeText = videoTimeFormat.format(new Date());
                    opencv_imgproc.putText(mat, timeText, new Point(20, 50), opencv_imgproc.FONT_HERSHEY_SIMPLEX, 1.2, new Scalar(0, 255, 255, 0), 2, opencv_imgproc.LINE_AA, false);
                    if (isRecording && !currentTrackingCode.isEmpty()) {
                        opencv_imgproc.putText(mat, "ORDER: " + currentTrackingCode, new Point(20, 100), opencv_imgproc.FONT_HERSHEY_SIMPLEX, 1.0, new Scalar(0, 0, 255, 0), 2, opencv_imgproc.LINE_AA, false);
                    }
                    Frame processedFrame = matConverter.convert(mat);

                    synchronized (recorderLock) {
                        if (isRecording && recorder != null) {
                            try {
                                long elapsedTimeMs = System.currentTimeMillis() - recordingStartTime;
                                recorder.setTimestamp(elapsedTimeMs * 1000L);
                                recorder.record(processedFrame);
                            } catch (Exception e) {
                            }
                        }
                    }

                    if (!isUiUpdating && !playbackPane.isVisible()) {
                        isUiUpdating = true;
                        Image fxImage = fxConverter.convert(processedFrame);
                        if (fxImage != null) {
                            Platform.runLater(() -> {
                                cameraView.setImage(fxImage);
                                isUiUpdating = false;
                            });
                        } else isUiUpdating = false;
                    }

                    if (frameCount % 10 == 0 && !isScanning && !playbackPane.isVisible()) {
                        isScanning = true;
                        Frame frameClone = processedFrame.clone();
                        aiScannerThread.submit(() -> {
                            try {
                                BufferedImage bImage = java2dConverter.getBufferedImage(frameClone);
                                if (bImage != null) {
                                    int cropW = (int) (bImage.getWidth() * 0.5);
                                    int cropH = (int) (bImage.getHeight() * 0.4);
                                    BufferedImage cloneForAI = bImage.getSubimage((bImage.getWidth() - cropW) / 2, (bImage.getHeight() - cropH) / 2, cropW, cropH);
                                    String scannedCode = scanBarcode(cloneForAI);
                                    if (scannedCode != null && scannedCode.length() > 5 && !scannedCode.equals(currentTrackingCode)) {
                                        java.awt.Toolkit.getDefaultToolkit().beep();
                                        Platform.runLater(() -> triggerNewOrder(scannedCode));
                                    }
                                }
                            } finally {
                                frameClone.close();
                                isScanning = false;
                            }
                        });
                    }
                }
                grabber.stop();
                grabber.release();
            } catch (Exception e) {
                Platform.runLater(() -> {
                    toggleSystem();
                    Alert a = new Alert(Alert.AlertType.ERROR, "Lỗi Camera!");
                    a.show();
                });
            }
        });
        camThread.setDaemon(true);
        camThread.start();
    }

    private String scanBarcode(BufferedImage image) {
        try {
            return barcodeReader.decodeWithState(new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)))).getText();
        } catch (Exception e) {
            return null;
        } finally {
            barcodeReader.reset();
        }
    }

    private void triggerNewOrder(String newCode) {
        if (newCode.isEmpty() || newCode.equals(currentTrackingCode)) return;

        currentTrackingCode = newCode;
        currentCodeDisplay.setText(newCode);
        statusText.setText("ĐANG GHI HÌNH");
        statusText.setTextFill(Color.web("#ef4444"));
        scanIndicator.setText("Mắt AI: Đang dò liên tục...");
        scanIndicator.setTextFill(Color.web("#3b82f6"));
        overlayText.setText("🔴 ĐANG QUAY: " + newCode);
        overlayText.setTextFill(Color.web("#ef4444"));
        aimBox.setVisible(false);
        barcodeInput.clear();

        new Thread(() -> {
            synchronized (recorderLock) {
                try {
                    if (isRecording && recorder != null) {
                        isRecording = false;
                        recorder.stop();
                        recorder.release();
                    }
                    recordingStartTime = System.currentTimeMillis();
                    recorder = new FFmpegFrameRecorder(SAVE_DIR + currentTrackingCode + ".mp4", grabber.getImageWidth(), grabber.getImageHeight());
                    recorder.setVideoCodec(org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264);
                    recorder.setFormat("mp4");
                    recorder.setFrameRate(30); // Ép cứng 30fps

                    // --- ĐOẠN CẦN SỬA ĐỂ HẾT XƯỚC ---
                    recorder.setVideoOption("tune", "zerolatency"); // Quan trọng nhất: Loại bỏ độ trễ nén gây xước
                    recorder.setVideoOption("x264opts", "keyint=15:min-keyint=15:no-scenecut:bframes=0");
                    recorder.setVideoOption("preset", "ultrafast"); // Nén cực nhanh để khớp tốc độ camera
                    recorder.setVideoOption("crf", "20");           // Độ nét cao
                    recorder.setVideoBitrate(8000000);              // 8Mbps cho 1080p
                    recorder.setVideoOption("sws_flags", "lanczos+accurate_rnd");
                    recorder.setPixelFormat(org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P);
                    recorder.setVideoOption("color_range", "2");
                    recorder.setGopSize(15);
                    recorder.start();
                    isRecording = true;
                } catch (Exception e) {
                }
            }
        }).start();

        int stopIndex = autoStopSelect.getSelectionModel().getSelectedIndex();
        if (stopIndex != 3) {
            int delayMs = stopIndex == 0 ? 60000 : (stopIndex == 1 ? 90000 : 120000);
            String codeAtStart = currentTrackingCode;
            new Thread(() -> {
                try {
                    Thread.sleep(delayMs);
                } catch (Exception e) {
                }
                if (isRecording && codeAtStart.equals(currentTrackingCode)) stopAndSaveRecording(true);
            }).start();
        }
    }

    private void stopAndSaveRecording(boolean resetUi) {
        new Thread(() -> {
            synchronized (recorderLock) {
                if (isRecording && recorder != null) {
                    try {
                        isRecording = false;
                        recorder.stop();
                        recorder.release();
                        recorder = null;
                    } catch (Exception e) {
                    }
                }
            }
            if (resetUi) {
                Platform.runLater(() -> {
                    currentCodeDisplay.setText("---");
                    statusText.setText("SẴN SÀNG QUÉT MÃ");
                    statusText.setTextFill(Color.web("#10b981"));
                    scanIndicator.setText("Mắt AI: Đang dò mã...");
                    overlayText.setText("SẴN SÀNG");
                    overlayText.setTextFill(Color.web("#10b981"));
                    aimBox.setVisible(true);
                    currentTrackingCode = "";
                });
            }
        }).start();
    }

    // ==========================================
    // LOGIC TÌM KIẾM & ĐIỀU KHIỂN VIDEO
    // ==========================================
    private void searchVideo() {
        String code = searchTrackingCode.getText().trim();
        if (code.isEmpty()) return;

        File videoFile = new File(SAVE_DIR + code + ".mp4");
        if (videoFile.exists()) {
            try {
                pbTitle.setText("MÃ ĐƠN: " + code);
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy - HH:mm:ss");
                pbTime.setText("🕒 Đóng gói: " + sdf.format(videoFile.lastModified()));
                long sizeMB = videoFile.length() / (1024 * 1024);
                pbSize.setText("💾 Dung lượng: " + sizeMB + " MB");
                pbPathLink.setText(videoFile.getAbsolutePath());

                liveViewPane.setVisible(false);
                playbackPane.setVisible(true);

                if (searchMediaPlayer != null) {
                    searchMediaPlayer.stop();
                    searchMediaPlayer.dispose();
                }

                Media media = new Media(videoFile.toURI().toString());
                searchMediaPlayer = new MediaPlayer(media);
                searchMediaView.setMediaPlayer(searchMediaPlayer);

                // Đồng bộ độ dài thanh kéo khi Video đã sẵn sàng
                searchMediaPlayer.setOnReady(() -> {
                    timeSlider.setMax(searchMediaPlayer.getTotalDuration().toSeconds());
                });

                // Cập nhật thanh kéo và Label thời gian khi video đang chạy
                searchMediaPlayer.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
                    if (!timeSlider.isValueChanging()) {
                        timeSlider.setValue(newTime.toSeconds());
                    }
                    String currentStr = formatTime(newTime.toSeconds());
                    String totalStr = formatTime(searchMediaPlayer.getTotalDuration().toSeconds());
                    timeLabel.setText(currentStr + " / " + totalStr);
                });

                // Cập nhật giao diện Nút khi hết video
                searchMediaPlayer.setOnEndOfMedia(() -> {
                    playPauseBtn.setText("🔄 Phát lại");
                    searchMediaPlayer.pause();
                });

                playPauseBtn.setText("⏸ Tạm dừng");
                searchMediaPlayer.play();

            } catch (Exception e) {
            }
        } else {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Không tìm thấy file video: " + code + ".mp4");
            alert.show();
        }
    }

    // Hàm phụ trợ Format Thời gian thành mm:ss
    private String formatTime(double totalSeconds) {
        if (Double.isNaN(totalSeconds)) return "00:00";
        int minutes = (int) (totalSeconds / 60);
        int seconds = (int) (totalSeconds % 60);
        return String.format("%02d:%02d", minutes, seconds);
    }

    private void closePlaybackMode() {
        if (searchMediaPlayer != null) searchMediaPlayer.stop();
        playbackPane.setVisible(false);
        liveViewPane.setVisible(true);
        barcodeInput.requestFocus();
    }

    private void openFolderOfVideo() {
        try {
            File folder = new File(SAVE_DIR);
            if (folder.exists()) Desktop.getDesktop().open(folder);
        } catch (Exception ex) {
        }
    }

    private void stopCameraOnly() {
        isSystemRunning = false;
        stopAndSaveRecording(true);
    }

    private void shutdownSystem() {
        stopCameraOnly();
    }
}