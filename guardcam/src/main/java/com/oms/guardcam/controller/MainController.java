package com.oms.guardcam.controller;

import com.google.zxing.Result;
import com.oms.guardcam.model.OrderRecord;
import com.oms.guardcam.service.ApiSyncService;
import com.oms.guardcam.service.BarcodeScanner;
import com.oms.guardcam.service.CameraManager;
import com.oms.guardcam.service.VideoRecorder;
import com.oms.guardcam.util.ImageUtils;
import com.oms.guardcam.view.MainView;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.paint.Color;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.awt.image.BufferedImage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public class MainController {
    private MainView view;

    // Các Service Core
    private CameraManager panoCamManager;
    private CameraManager qrCamManager;
    private VideoRecorder videoRecorder;
    private BarcodeScanner barcodeScanner;
    private ApiSyncService apiSyncService;

    // Trạng thái hệ thống
    private boolean isSystemRunning = false;
    private OrderRecord currentOrder = null;

    // Xử lý đa luồng cho AI Quét mã và Hẹn giờ tắt
    private ExecutorService scannerThreadPool = Executors.newSingleThreadExecutor();
    private ScheduledExecutorService timerThreadPool = Executors.newScheduledThreadPool(1);
    private int scanFrameCounter = 0;
    private volatile boolean isScanning = false;
    private String lastFinishedCode = "";
    private java.text.SimpleDateFormat videoTimeFormat = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
    private volatile boolean isFlushingVideo = false;
    private volatile com.google.zxing.ResultPoint[] lastDetectedPoints = null;
    private volatile long lastDetectionTime = 0;
    // Trình phát video khi xem lại
    private javafx.scene.media.MediaPlayer playerPano;
    private javafx.scene.media.MediaPlayer playerQr;
    public MainController(MainView view) {
        this.view = view;

        // Khởi tạo các Service
        this.panoCamManager = new CameraManager();
        this.qrCamManager = new CameraManager();
        this.videoRecorder = new VideoRecorder();
        this.barcodeScanner = new BarcodeScanner();
        this.apiSyncService = new ApiSyncService();

        initEventHandlers();
        loadDeviceList();
    }

    private void loadDeviceList() {
        try {
            String[] devices = org.bytedeco.javacv.VideoInputFrameGrabber.getDeviceDescriptions();
            if (devices != null && devices.length > 0) {
                // MainView cần có 2 ComboBox cho 2 cam
                view.cameraPanoSelect.getItems().addAll(devices);
                view.cameraQrSelect.getItems().addAll(devices);
            }
        } catch (Exception e) {}

        if (!view.cameraPanoSelect.getItems().isEmpty()) {
            view.cameraPanoSelect.getSelectionModel().selectFirst();
            view.cameraQrSelect.getSelectionModel().selectLast(); // Tạm chọn cam cuối cho QR
        }
    }

    private void initEventHandlers() {
        view.startCamBtn.setOnAction(e -> toggleSystem());
        view.barcodeInput.setOnAction(e -> processNewOrder(view.barcodeInput.getText().trim()));
        view.stopManualBtn.setOnAction(e -> stopCurrentRecording());

        // Bắt sự kiện ấn nút ghép Video lúc xem lại
        view.searchBtn.setOnAction(e -> handleSearchVideo());
        view.closePlaybackBtn.setOnAction(e -> closePlaybackMode());
        view.playPauseBtn.setOnAction(e -> togglePlayback());
        view.mergeVideoBtn.setOnAction(e -> handleMergeAction());

        // Lắng nghe thao tác kéo thanh thời gian
        view.timeSlider.valueChangingProperty().addListener((obs, wasChanging, isChanging) -> {
            if (!isChanging) seekVideo(view.timeSlider.getValue());
        });
        view.timeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (!view.timeSlider.isValueChanging() && Math.abs(newVal.doubleValue() - oldVal.doubleValue()) > 0.5) {
                seekVideo(newVal.doubleValue());
            }
        });
    }

    private void toggleSystem() {
        if (isSystemRunning) {
            shutdownSystem();
        } else {
            startSystem();
        }
    }

    private void startSystem() {
        isSystemRunning = true;
        view.startCamBtn.setText("⏹ TẮT HỆ THỐNG");
        view.startCamBtn.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 12px; -fx-background-radius: 6px;");
        updateUiStatus("SẴN SÀNG QUÉT MÃ", "#10b981");
        //view.aimBox.setVisible(true);
        final org.bytedeco.javacv.OpenCVFrameConverter.ToMat toMatConverter = new org.bytedeco.javacv.OpenCVFrameConverter.ToMat();
        final org.bytedeco.javacv.OpenCVFrameConverter.ToMat toFrameConverter = new org.bytedeco.javacv.OpenCVFrameConverter.ToMat();
        final org.bytedeco.javacv.JavaFXFrameConverter fxConverter = new org.bytedeco.javacv.JavaFXFrameConverter();

        String panoCamName = view.cameraPanoSelect.getValue();
        String qrCamName = view.cameraQrSelect.getValue();

        // 1. Khởi động Camera Toàn cảnh (Pano)
        // Khởi tạo Converter dùng riêng cho Cam Toàn Cảnh
        final org.bytedeco.javacv.OpenCVFrameConverter.ToMat panoToMat = new org.bytedeco.javacv.OpenCVFrameConverter.ToMat();
        final org.bytedeco.javacv.OpenCVFrameConverter.ToMat panoToFrame = new org.bytedeco.javacv.OpenCVFrameConverter.ToMat();
        final org.bytedeco.javacv.JavaFXFrameConverter panoFx = new org.bytedeco.javacv.JavaFXFrameConverter();

        // 1. Khởi động Camera Toàn cảnh (Pano)
        panoCamManager.startCamera(panoCamName, 1920, 1080, new CameraManager.FrameListener() {
            @Override
            public void onFrameCaptured(org.bytedeco.javacv.Frame frame, long timestamp) {
                // Chuyển sang Mat để vẽ chữ
                org.bytedeco.opencv.opencv_core.Mat panoMat = panoToMat.convert(frame).clone();

                // 1. VẼ THỜI GIAN THỰC (Góc trái trên)
                String timeText = videoTimeFormat.format(new java.util.Date());
                org.bytedeco.opencv.global.opencv_imgproc.putText(panoMat, timeText,
                        new org.bytedeco.opencv.opencv_core.Point(20, 50),
                        org.bytedeco.opencv.global.opencv_imgproc.FONT_HERSHEY_SIMPLEX,
                        1.2, new org.bytedeco.opencv.opencv_core.Scalar(0, 255, 255, 0), 2, org.bytedeco.opencv.global.opencv_imgproc.LINE_AA, false);

                // 2. VẼ MÃ ĐƠN HÀNG (Nếu đang trong trạng thái quay)
                if (videoRecorder.isRecording() && currentOrder != null) {
                    org.bytedeco.opencv.global.opencv_imgproc.putText(panoMat, "ORDER: " + currentOrder.getTrackingCode(),
                            new org.bytedeco.opencv.opencv_core.Point(20, 100),
                            org.bytedeco.opencv.global.opencv_imgproc.FONT_HERSHEY_SIMPLEX,
                            1.0, new org.bytedeco.opencv.opencv_core.Scalar(0, 0, 255, 0), 2, org.bytedeco.opencv.global.opencv_imgproc.LINE_AA, false);
                }

                // Đóng gói lại thành Frame sau khi đã vẽ xong
                org.bytedeco.javacv.Frame drawnFrame = panoToFrame.convert(panoMat);

                // FIX CRASH: Phải convert sang ảnh JavaFX ngay tại luồng này (Đồng bộ)
                // Tuyệt đối không ném Frame C++ thẳng vào runLater nữa
                javafx.scene.image.Image panoImg = panoFx.convert(drawnFrame);

                // Đưa ảnh đã an toàn lên UI
                Platform.runLater(() -> view.cameraView.setImage(panoImg));

                // Ghi hình an toàn
                if (videoRecorder.isRecording()) videoRecorder.recordPanoFrame(drawnFrame);
            }
            @Override
            public void onError(Exception e) { handleCameraError("Cam Toàn Cảnh", e); }
        });

        // 2. Khởi động Camera Quét Mã (QR)
        qrCamManager.startCamera(qrCamName, 1280, 720, new CameraManager.FrameListener() {
            @Override
            public void onFrameCaptured(org.bytedeco.javacv.Frame frame, long timestamp) {
                // 1. Lấy khung ngang gốc
                org.bytedeco.opencv.opencv_core.Mat originalMat = toMatConverter.convert(frame);

                // 2. Xoay dọc 90 độ -> TẠO BẢN "SẠCH" (Dùng để ghi video và quét AI)
                org.bytedeco.opencv.opencv_core.Mat cleanMat = new org.bytedeco.opencv.opencv_core.Mat();
                org.bytedeco.opencv.global.opencv_core.rotate(originalMat, cleanMat, org.bytedeco.opencv.global.opencv_core.ROTATE_90_COUNTERCLOCKWISE);

                // Đóng gói bản sạch thành Frame dọc (720x1280)
                org.bytedeco.javacv.Frame cleanFrame = toFrameConverter.convert(cleanMat);

                // 3. Ghi hình bản Sạch (Không có khung xanh)
                if (videoRecorder.isRecording()) {
                    videoRecorder.recordQrFrame(cleanFrame);
                }

                // 4. TẠO BẢN "NHÁP" (Clone) ĐỂ HIỂN THỊ UI & VẼ KHUNG
                org.bytedeco.opencv.opencv_core.Mat uiMat = cleanMat.clone();
                if (lastDetectedPoints != null && (System.currentTimeMillis() - lastDetectionTime < 500)) {
                    drawBoundingBox(uiMat, lastDetectedPoints); // Chỉ vẽ lên bản nháp
                }

                // Đẩy bản nháp (có khung xanh) lên màn hình JavaFX
                javafx.scene.image.Image uiImg = fxConverter.convert(toFrameConverter.convert(uiMat));
                Platform.runLater(() -> view.cameraQrView.setImage(uiImg));

                // 5. Quét AI (Dùng bản sạch để AI đọc nhanh hơn, không bị khung che)
                if (!isScanning) {
                    scanFrameCounter++;
                    if (scanFrameCounter % 8 == 0) {
                        scanBarcodeAsync(cleanFrame.clone());
                    }
                }
            }
            @Override
            public void onError(Exception e) { handleCameraError("Cam Quét Mã", e); }
        });
    }

    private void drawBoundingBox(org.bytedeco.opencv.opencv_core.Mat mat, com.google.zxing.ResultPoint[] points) {
        if (points == null || points.length < 2) return;

        // Tính toán tọa độ Min/Max từ các điểm ZXing trả về
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = 0, maxY = 0;

        for (com.google.zxing.ResultPoint p : points) {
            if (p.getX() < minX) minX = p.getX();
            if (p.getY() < minY) minY = p.getY();
            if (p.getX() > maxX) maxX = p.getX();
            if (p.getY() > maxY) maxY = p.getY();
        }

        // Vẽ hình chữ nhật xanh neon bao quanh mã QR
        org.bytedeco.opencv.global.opencv_imgproc.rectangle(
                mat,
                new org.bytedeco.opencv.opencv_core.Point((int)minX - 10, (int)minY - 10),
                new org.bytedeco.opencv.opencv_core.Point((int)maxX + 10, (int)maxY + 10),
                new org.bytedeco.opencv.opencv_core.Scalar(0, 255, 0, 0), // Màu xanh lá
                3,
                org.bytedeco.opencv.global.opencv_imgproc.LINE_AA,
                0
        );
    }
    private void scanBarcodeAsync(org.bytedeco.javacv.Frame frameToScan) {
        isScanning = true;
        scannerThreadPool.submit(() -> {
            try {
                BufferedImage bImage = ImageUtils.frameToBufferedImage(frameToScan);
                Result result = barcodeScanner.scan(bImage);

                if (result != null) {
                    // Cập nhật tọa độ điểm và thời gian để luồng UI vẽ khung định vị
                    lastDetectedPoints = result.getResultPoints();
                    lastDetectionTime = System.currentTimeMillis();

                    // Kiểm tra độ dài mã hợp lệ để tạo đơn
                    if (result.getText().length() > 5) {
                        String scannedCode = result.getText();
                        // CHẶN NGAY NẾU MÃ VỪA QUÉT GIỐNG HỆT MÃ VỪA CẮT
                        if (scannedCode.equals(lastFinishedCode)) {
                            return;
                        }
                        if (currentOrder == null || !scannedCode.equals(currentOrder.getTrackingCode())) {
                            playScannerBeep();
                            Platform.runLater(() -> processNewOrder(scannedCode));
                        }
                    }
                }
            } finally {
                frameToScan.close();
                isScanning = false;
            }
        });
    }
    private void processNewOrder(String trackingCode) {
        if (trackingCode.isEmpty()) return;

        // Nếu đang quay dở đơn cũ, ngắt ngay lập tức
        if (videoRecorder.isRecording()) {
            stopCurrentRecording();
        }

        currentOrder = new OrderRecord(trackingCode);

        // Cập nhật giao diện
        view.currentCodeDisplay.setText(trackingCode);
        updateUiStatus("ĐANG GHI HÌNH: " + trackingCode, "#ef4444");
        view.barcodeInput.clear();

        // Kích hoạt ghi hình 2 luồng
        videoRecorder.startDualRecording(currentOrder, 1920, 1080, 720, 1280);
        // Xử lý tự động ngắt
        int stopIndex = view.autoStopSelect.getSelectionModel().getSelectedIndex();
        if (stopIndex != 3) { // Khác "Tắt (Thủ công)"
            int delayMs = stopIndex == 0 ? 60000 : (stopIndex == 1 ? 90000 : 120000);
            String codeAtStart = trackingCode;

            timerThreadPool.schedule(() -> {
                if (videoRecorder.isRecording() && currentOrder != null && currentOrder.getTrackingCode().equals(codeAtStart)) {
                    Platform.runLater(this::stopCurrentRecording);
                }
            }, delayMs, TimeUnit.MILLISECONDS);
        }
    }

    private void stopCurrentRecording() {
        if (videoRecorder.isRecording()) {
            final OrderRecord finishedOrder = currentOrder;
            if (finishedOrder != null) {
                lastFinishedCode = finishedOrder.getTrackingCode();
            }

            currentOrder = null;
            updateUiStatus("SẴN SÀNG QUÉT MÃ", "#10b981");
            view.currentCodeDisplay.setText("---");

            // BẬT CỜ: Báo hiệu hệ thống đang bận đóng gói file video
            isFlushingVideo = true;

            new Thread(() -> {
                videoRecorder.stopRecording();

                if (finishedOrder != null) {
                    apiSyncService.syncOrderData(finishedOrder).thenAccept(success -> {
                        if(success) System.out.println("Đã đồng bộ đơn: " + finishedOrder.getTrackingCode());
                    });
                }

                // TẮT CỜ: Đã lưu xong, cho phép tìm kiếm
                isFlushingVideo = false;
            }).start();
        }
    }

    // Ghép video khi tìm kiếm đơn hàng
    // ===============================================
    // KHU VỰC LOGIC XEM LẠI & GHÉP VIDEO (PLAYBACK)
    // ===============================================
    private void handleSearchVideo() {
        String code = view.searchTrackingCode.getText().trim();
        if (code.isEmpty()) return;

        if (videoRecorder.isRecording() || isFlushingVideo) {
            Platform.runLater(() -> {
                Alert a = new Alert(Alert.AlertType.WARNING, "Hệ thống đang xử lý và đóng gói Video.\nVui lòng đợi 2-3 giây rồi ấn tìm kiếm lại!");
                a.show();
            });
            return;
        }

        String dir = "D:\\Quang-Wordspace\\Guard-Cam-FX\\videos\\";
        java.io.File filePano = new java.io.File(dir + code + "_pano.mp4");
        java.io.File fileQr = new java.io.File(dir + code + "_qr.mp4");

        if (filePano.exists()) {
            // Cập nhật thông tin UI
            view.pbTitle.setText("MÃ ĐƠN: " + code);
            view.pbTime.setText("🕒 Đóng gói: " + videoTimeFormat.format(filePano.lastModified()));
            long sizeMB = (filePano.length() + (fileQr.exists() ? fileQr.length() : 0)) / (1024 * 1024);
            view.pbSize.setText("💾 Tổng dung lượng: " + sizeMB + " MB");
            view.pathBoxContainer.getChildren().clear();
            view.pathBoxContainer.getChildren().add(createFileLink("📹 Cam Toàn Cảnh", filePano));
            if (fileQr.exists()) {
                view.pathBoxContainer.getChildren().add(createFileLink("📱 Cam Quét Mã", fileQr));
            }
            java.io.File fileMerged = new java.io.File(dir + code + "_merged.mp4");
            if (fileMerged.exists()) {
                view.pathBoxContainer.getChildren().add(createFileLink("✂️ File Đã Ghép", fileMerged));
            }

            // Tắt Live, Bật Playback
            view.liveViewPane.setVisible(false);
            view.playbackPane.setVisible(true);

            // Xóa player cũ nếu có
            disposePlayers();

            // Khởi tạo Player Pano
            playerPano = new javafx.scene.media.MediaPlayer(new javafx.scene.media.Media(filePano.toURI().toString()));
            view.searchMediaViewPano.setMediaPlayer(playerPano);

            // Khởi tạo Player QR (Nếu tồn tại)
            if (fileQr.exists()) {
                playerQr = new javafx.scene.media.MediaPlayer(new javafx.scene.media.Media(fileQr.toURI().toString()));
                view.searchMediaViewQr.setMediaPlayer(playerQr);
                view.searchMediaViewQr.getParent().setVisible(true);
            } else {
                view.searchMediaViewQr.getParent().setVisible(false);
            }

            // Đồng bộ UI theo Player Pano (Lấy Pano làm chuẩn)
            playerPano.setOnReady(() -> {
                // CHỐNG CRASH: Chỉ lấy thời gian nếu player chưa bị hủy
                if (playerPano != null) {
                    view.timeSlider.setMax(playerPano.getTotalDuration().toSeconds());
                }
            });

            playerPano.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
                // CHỐNG CRASH: Chỉ cập nhật thanh Slider nếu player còn tồn tại
                if (playerPano != null) {
                    if (!view.timeSlider.isValueChanging()) {
                        view.timeSlider.setValue(newTime.toSeconds());
                    }
                    String currentStr = formatTime(newTime.toSeconds());
                    String totalStr = formatTime(playerPano.getTotalDuration().toSeconds());
                    view.timeLabel.setText(currentStr + " / " + totalStr);
                }
            });

            playerPano.setOnEndOfMedia(() -> {
                view.playPauseBtn.setText("🔄 Phát lại");
                // CHỐNG CRASH: Kiểm tra trước khi gọi hàm pause()
                if (playerPano != null) playerPano.pause();
                if (playerQr != null) playerQr.pause();
            });

            // Tự động phát khi tìm thấy
            view.playPauseBtn.setText("⏸ Tạm dừng");
            playerPano.play();
            if (playerQr != null) playerQr.play();

        } else {
            Alert a = new Alert(Alert.AlertType.WARNING, "Không tìm thấy video toàn cảnh cho mã: " + code);
            a.show();
        }
    }
    // Hàm tạo link, click vào sẽ tự động mở thư mục và bôi đen file đó
    private javafx.scene.control.Hyperlink createFileLink(String title, java.io.File file) {
        javafx.scene.control.Hyperlink link = new javafx.scene.control.Hyperlink(title + " (" + file.getName() + ")");
        link.setTextFill(Color.web("#89b4fa")); // Màu xanh dương nhạt
        link.setFont(javafx.scene.text.Font.font(13));
        link.setOnAction(e -> {
            try {
                // Lệnh CMD của Windows để mở thư mục và select file
                Runtime.getRuntime().exec("explorer.exe /select,\"" + file.getAbsolutePath() + "\"");
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
        return link;
    }
    private void togglePlayback() {
        if (playerPano == null) return;

        // Nếu đang ở cuối video (Nút hiển thị Phát lại) -> Tua về đầu và phát
        if (view.playPauseBtn.getText().contains("Phát lại")) {
            seekVideo(0);
            playerPano.play();
            if (playerQr != null) playerQr.play();
            view.playPauseBtn.setText("⏸ Tạm dừng");
            return;
        }

        if (playerPano.getStatus() == javafx.scene.media.MediaPlayer.Status.PLAYING) {
            playerPano.pause();
            if (playerQr != null) playerQr.pause();
            view.playPauseBtn.setText("▶ Phát");
        } else {
            playerPano.play();
            if (playerQr != null) playerQr.play();
            view.playPauseBtn.setText("⏸ Tạm dừng");
        }
    }

    private void seekVideo(double seconds) {
        javafx.util.Duration duration = javafx.util.Duration.seconds(seconds);
        if (playerPano != null) playerPano.seek(duration);
        if (playerQr != null) playerQr.seek(duration);
    }

    private void closePlaybackMode() {
        disposePlayers();
        view.playbackPane.setVisible(false);
        view.liveViewPane.setVisible(true);
        view.barcodeInput.requestFocus();
    }

    private void disposePlayers() {
        if (playerPano != null) { playerPano.stop(); playerPano.dispose(); playerPano = null; }
        if (playerQr != null) { playerQr.stop(); playerQr.dispose(); playerQr = null; }
    }

    private String formatTime(double totalSeconds) {
        if (Double.isNaN(totalSeconds)) return "00:00";
        int minutes = (int) (totalSeconds / 60);
        int seconds = (int) (totalSeconds % 60);
        return String.format("%02d:%02d", minutes, seconds);
    }

    private void handleMergeAction() {
        String code = view.searchTrackingCode.getText().trim();
        if (code.isEmpty()) return;

        view.mergeVideoBtn.setText("⏳ Đang ghép...");
        view.mergeVideoBtn.setDisable(true);

        // Tạm dừng video nếu đang phát
        if (playerPano != null && playerPano.getStatus() == javafx.scene.media.MediaPlayer.Status.PLAYING) {
            togglePlayback();
        }

        OrderRecord tempRecord = new OrderRecord(code);
        String dir = "D:\\Quang-Wordspace\\Guard-Cam-FX\\videos\\";
        tempRecord.setPanoVideoPath(dir + code + "_pano.mp4");
        tempRecord.setQrVideoPath(dir + code + "_qr.mp4");

        videoRecorder.mergeDualCamVideos(tempRecord, () -> {
            Platform.runLater(() -> {
                view.mergeVideoBtn.setText("✂️ Ghép thành 1 File");
                view.mergeVideoBtn.setDisable(false);
                Alert a = new Alert(Alert.AlertType.INFORMATION, "Ghép thành công! Đã lưu tại:\n" + tempRecord.getMergedVideoPath());
                a.show();
            });
        });
    }

    private void shutdownSystem() {
        isSystemRunning = false;
        stopCurrentRecording();
        panoCamManager.stopCamera();
        qrCamManager.stopCamera();

        view.startCamBtn.setText("▶ BẬT HỆ THỐNG");
        view.startCamBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 12px; -fx-background-radius: 6px;");
        updateUiStatus("CHƯA KẾT NỐI", "#a6adc8");
        view.cameraView.setImage(null);
        view.cameraQrView.setImage(null);
        //view.aimBox.setVisible(false);
    }

    public void shutdown() {
        shutdownSystem();
        scannerThreadPool.shutdown();
        timerThreadPool.shutdown();
    }

    private void updateUiStatus(String text, String hexColor) {
        view.statusText.setText(text);
        view.statusText.setTextFill(Color.web(hexColor));
        view.overlayText.setText(text);
        view.overlayText.setTextFill(Color.web(hexColor));
    }

    private void handleCameraError(String camName, Exception e) {
        Platform.runLater(() -> {
            shutdownSystem();
            Alert a = new Alert(Alert.AlertType.ERROR, "Lỗi kết nối " + camName + ": " + e.getMessage());
            a.show();
        });
    }

    private void playScannerBeep() {
        new Thread(() -> {
            try {
                int sampleRate = 8000;
                double freq = 1800d;
                byte[] buf = new byte[800];
                AudioFormat af = new AudioFormat(sampleRate, 8, 1, true, false);
                SourceDataLine sdl = AudioSystem.getSourceDataLine(af);
                sdl.open(af);
                sdl.start();
                for (int i = 0; i < buf.length; i++) {
                    double angle = i / (sampleRate / freq) * 2.0 * Math.PI;
                    double volume = 127.0;
                    if (i > buf.length - 200) volume = 127.0 * ((buf.length - i) / 200.0);
                    buf[i] = (byte) (Math.sin(angle) * volume);
                }
                sdl.write(buf, 0, buf.length);
                sdl.drain();
                sdl.stop();
                sdl.close();
            } catch (Exception ignored) {}
        }).start();
    }
}