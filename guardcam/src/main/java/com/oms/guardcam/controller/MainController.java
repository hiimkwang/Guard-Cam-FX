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
import java.util.prefs.Preferences;

public class MainController {
    private MainView view;

    private CameraManager panoCamManager;
    private CameraManager qrCamManager;
    private VideoRecorder videoRecorder;
    private BarcodeScanner barcodeScanner;
    private ApiSyncService apiSyncService;

    private boolean isSystemRunning = false;
    private volatile boolean isPanoWorking = false;
    private volatile boolean isQrWorking = false;
    private OrderRecord currentOrder = null;

    private ExecutorService scannerThreadPool = Executors.newSingleThreadExecutor();
    private ScheduledExecutorService timerThreadPool = Executors.newScheduledThreadPool(1);
    private int scanFrameCounter = 0;
    private volatile boolean isScanning = false;
    private String lastFinishedCode = "";
    private java.text.SimpleDateFormat videoTimeFormat = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
    private volatile boolean isFlushingVideo = false;
    private volatile com.google.zxing.ResultPoint[] lastDetectedPoints = null;
    private volatile long lastDetectionTime = 0;

    private javafx.scene.media.MediaPlayer playerPano;
    private javafx.scene.media.MediaPlayer playerQr;
    private volatile boolean isUiUpdatingPano = false;
    private volatile boolean isUiUpdatingQr = false;

    private volatile long systemStartNano = -1;
    // Giữ lại 2 biến toàn cục này để dùng lúc quay video
    private int camWidth = 1920;
    private int camHeight = 1080;
    // Tạo 2 luồng ngầm xếp hàng tuần tự để ghi video
    private final java.util.concurrent.ExecutorService panoRecordExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
    private final java.util.concurrent.ExecutorService qrRecordExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();

    // Khai báo 2 bộ chuyển đổi độc lập (Bắt buộc để chống crash khi chạy đa luồng)
    private final org.bytedeco.javacv.OpenCVFrameConverter.ToMat panoRecordConverter = new org.bytedeco.javacv.OpenCVFrameConverter.ToMat();
    private final org.bytedeco.javacv.OpenCVFrameConverter.ToMat qrRecordConverter = new org.bytedeco.javacv.OpenCVFrameConverter.ToMat();
    public MainController(MainView view) {
        this.view = view;

        this.panoCamManager = new CameraManager();
        this.qrCamManager = new CameraManager();
        this.videoRecorder = new VideoRecorder();
        this.barcodeScanner = new BarcodeScanner();
        this.apiSyncService = new ApiSyncService();

        loadDeviceList();
        initEventHandlers();
    }

    private void loadDeviceList() {
        try {
            String[] devices = org.bytedeco.javacv.VideoInputFrameGrabber.getDeviceDescriptions();
            if (devices != null && devices.length > 0) {
                view.cameraPanoSelect.getItems().addAll(devices);
                view.cameraQrSelect.getItems().addAll(devices);
            }
        } catch (Exception e) {
        }
    }

    private void loadSettings() {
        Preferences prefs = Preferences.userNodeForPackage(MainController.class);
        if (!view.cameraPanoSelect.getItems().isEmpty()) {
            view.cameraPanoSelect.getSelectionModel().select(prefs.get("camPano", view.cameraPanoSelect.getItems().get(0)));
        }
        if (!view.cameraQrSelect.getItems().isEmpty()) {
            view.cameraQrSelect.getSelectionModel().select(prefs.get("camQr", view.cameraQrSelect.getItems().get(view.cameraQrSelect.getItems().size() - 1)));
        }
        view.autoStopSelect.getSelectionModel().select(prefs.getInt("autoStop", 1));
        view.resSelect.getSelectionModel().select(prefs.getInt("resIndex", 0));
    }

    private void saveSettings() {
        Preferences prefs = Preferences.userNodeForPackage(MainController.class);
        prefs.put("camPano", view.cameraPanoSelect.getValue() != null ? view.cameraPanoSelect.getValue() : "0");
        prefs.put("camQr", view.cameraQrSelect.getValue() != null ? view.cameraQrSelect.getValue() : "0");
        prefs.putInt("autoStop", view.autoStopSelect.getSelectionModel().getSelectedIndex());
        prefs.putInt("resIndex", view.resSelect.getSelectionModel().getSelectedIndex());
        Alert a = new Alert(Alert.AlertType.INFORMATION, "Đã lưu cấu hình Camera thành công!");
        a.show();
    }

    private void initEventHandlers() {
        loadSettings();

        view.saveSettingsBtn.setOnAction(e -> saveSettings());
        view.startCamBtn.setOnAction(e -> toggleSystem());
        view.stopManualBtn.setOnAction(e -> stopCurrentRecording());
        view.barcodeInput.setOnAction(e -> processNewOrder(view.barcodeInput.getText().trim()));
        view.searchBtn.setOnAction(e -> handleSearchVideo());
        view.closePlaybackBtn.setOnAction(e -> closePlaybackMode());
        view.playPauseBtn.setOnAction(e -> togglePlayback());
        view.mergeVideoBtn.setOnAction(e -> handleMergeAction());

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
            stopCameraSystem();
        } else {
            startSystem();
        }
    }

    private void startSystem() {
        isSystemRunning = true;

        view.qrContainer.setVisible(true);
        view.overlayText.setVisible(true);

        view.startCamBtn.setText("⏹ DỪNG GHI HÌNH");
        view.startCamBtn.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10px; -fx-background-radius: 6px;");
        updateUiStatus("SẴN SÀNG QUÉT MÃ", "#10b981");

        view.scanIndicator.setText("Mắt AI: Đang dò mã...");
        view.scanIndicator.setTextFill(Color.web("#3b82f6"));

        final org.bytedeco.javacv.OpenCVFrameConverter.ToMat toMatConverter = new org.bytedeco.javacv.OpenCVFrameConverter.ToMat();
        final org.bytedeco.javacv.OpenCVFrameConverter.ToMat toFrameConverter = new org.bytedeco.javacv.OpenCVFrameConverter.ToMat();
        final org.bytedeco.javacv.JavaFXFrameConverter fxConverter = new org.bytedeco.javacv.JavaFXFrameConverter();

        String panoCamName = view.cameraPanoSelect.getValue();
        String qrCamName = view.cameraQrSelect.getValue();
        boolean is1080p = view.resSelect.getValue() == null || view.resSelect.getValue().equals("1920x1080");
        camWidth = is1080p ? 1920 : 1280;
        camHeight = is1080p ? 1080 : 720;
        final org.bytedeco.javacv.OpenCVFrameConverter.ToMat panoToMat = new org.bytedeco.javacv.OpenCVFrameConverter.ToMat();
        final org.bytedeco.javacv.OpenCVFrameConverter.ToMat panoToFrame = new org.bytedeco.javacv.OpenCVFrameConverter.ToMat();
        final org.bytedeco.javacv.JavaFXFrameConverter panoFx = new org.bytedeco.javacv.JavaFXFrameConverter();

        isPanoWorking = true;
        panoCamManager.startCamera(panoCamName, camWidth, camHeight, new CameraManager.FrameListener() {
            @Override
            public void onFrameCaptured(org.bytedeco.javacv.Frame frame, long timestamp) {
                org.bytedeco.opencv.opencv_core.Mat panoMat = null;
                try {
                    // clone() cấp phát bộ nhớ mới C++ -> BẮT BUỘC PHẢI CLOSE
                    panoMat = panoToMat.convert(frame).clone();

                    String timeText = videoTimeFormat.format(new java.util.Date());
                    org.bytedeco.opencv.global.opencv_imgproc.putText(panoMat, timeText,
                            new org.bytedeco.opencv.opencv_core.Point(20, 50),
                            org.bytedeco.opencv.global.opencv_imgproc.FONT_HERSHEY_SIMPLEX,
                            1.2, new org.bytedeco.opencv.opencv_core.Scalar(0, 255, 255, 0), 2, org.bytedeco.opencv.global.opencv_imgproc.LINE_AA, false);

                    if (videoRecorder.isRecording() && currentOrder != null) {
                        org.bytedeco.opencv.global.opencv_imgproc.putText(panoMat, "ORDER: " + currentOrder.getTrackingCode(),
                                new org.bytedeco.opencv.opencv_core.Point(20, 100),
                                org.bytedeco.opencv.global.opencv_imgproc.FONT_HERSHEY_SIMPLEX,
                                1.0, new org.bytedeco.opencv.opencv_core.Scalar(0, 0, 255, 0), 2, org.bytedeco.opencv.global.opencv_imgproc.LINE_AA, false);
                    }

                    org.bytedeco.javacv.Frame drawnFrame = panoToFrame.convert(panoMat);
                    if (!isUiUpdatingPano) {
                        isUiUpdatingPano = true;
                        javafx.scene.image.Image panoImg = panoFx.convert(drawnFrame);
                        Platform.runLater(() -> {
                            view.cameraView.setImage(panoImg);
                            isUiUpdatingPano = false;
                        });
                    }

                    // Sửa lại đoạn ghi video Pano
                    if (videoRecorder.isRecording() && isPanoWorking) {
                        // Chốt mốc thời gian duy nhất của toàn hệ thống
                        if (systemStartNano == -1) systemStartNano = System.nanoTime();

                        // Tự tính thời gian trôi qua (Micro-giây) cực kỳ chính xác
                        long timestampMicro = (System.nanoTime() - systemStartNano) / 1000L;

                        org.bytedeco.opencv.opencv_core.Mat recordMat = panoMat.clone();
                        panoRecordExecutor.submit(() -> {
                            try {
                                videoRecorder.recordPanoFrame(panoRecordConverter.convert(recordMat), timestampMicro);
                            } catch (Exception e) {
                                e.printStackTrace();
                            } finally {
                                recordMat.close();
                            }
                        });
                    }
                } finally {
                    // DỌN RÁC NGAY LẬP TỨC: NGĂN CHẶN TRÀN 6GB RAM
                    if (panoMat != null) panoMat.close();
                    if (frame != null) frame.close();
                }
            }

            @Override
            public void onError(Exception e) {
                isPanoWorking = false;
                Platform.runLater(() -> System.out.println("Cảnh báo: Cam Toàn Cảnh mất kết nối."));
            }
        });

        isQrWorking = true;
        qrCamManager.startCamera(qrCamName, camWidth, camHeight, new CameraManager.FrameListener() {
            @Override
            public void onFrameCaptured(org.bytedeco.javacv.Frame frame, long timestamp) {
                org.bytedeco.opencv.opencv_core.Mat cleanMat = null;
                org.bytedeco.opencv.opencv_core.Mat uiMat = null;
                try {
                    org.bytedeco.opencv.opencv_core.Mat originalMat = toMatConverter.convert(frame);

                    // Cấp phát bộ nhớ -> BẮT BUỘC PHẢI CLOSE
                    cleanMat = new org.bytedeco.opencv.opencv_core.Mat();
                    org.bytedeco.opencv.global.opencv_core.rotate(originalMat, cleanMat, org.bytedeco.opencv.global.opencv_core.ROTATE_90_COUNTERCLOCKWISE);

                    org.bytedeco.javacv.Frame cleanFrame = toFrameConverter.convert(cleanMat);

                    // Sửa lại đoạn ghi video QR (Dùng cleanMat để video lưu không bị dính nét vẽ UI)
                    if (videoRecorder.isRecording() && isQrWorking) {
                        // Dùng chung mốc thời gian với Pano để đồng bộ tuyệt đối
                        if (systemStartNano == -1) systemStartNano = System.nanoTime();

                        long timestampMicro = (System.nanoTime() - systemStartNano) / 1000L;

                        org.bytedeco.opencv.opencv_core.Mat recordMat = cleanMat.clone();
                        qrRecordExecutor.submit(() -> {
                            try {
                                videoRecorder.recordQrFrame(qrRecordConverter.convert(recordMat), timestampMicro);
                            } catch (Exception e) {
                                e.printStackTrace();
                            } finally {
                                recordMat.close();
                            }
                        });
                    }

                    // Cấp phát bộ nhớ -> BẮT BUỘC PHẢI CLOSE
                    if (!isUiUpdatingQr) {
                        isUiUpdatingQr = true;
                        uiMat = cleanMat.clone();
                        if (lastDetectedPoints != null && (System.currentTimeMillis() - lastDetectionTime < 500)) {
                            drawBoundingBox(uiMat, lastDetectedPoints);
                        }
                        javafx.scene.image.Image uiImg = fxConverter.convert(toFrameConverter.convert(uiMat));
                        Platform.runLater(() -> {
                            view.cameraQrView.setImage(uiImg);
                            isUiUpdatingQr = false;
                        });
                    } else {
                        // Nếu UI đang bận, vẫn cần rác phải được dọn
                        uiMat = null;
                    }

                    if (!isScanning) {
                        scanFrameCounter++;
                        if (scanFrameCounter % 8 == 0) {
                            scanBarcodeAsync(cleanFrame.clone());
                        }
                    }
                } finally {
                    // DỌN RÁC TRÁNH CRASH ỨNG DỤNG
                    if (cleanMat != null) cleanMat.close();
                    if (uiMat != null) uiMat.close();
                    if (frame != null) frame.close();
                }
            }

            @Override
            public void onError(Exception e) {
                isQrWorking = false;
                Platform.runLater(() -> System.out.println("Cảnh báo: Cam Quét Mã mất kết nối."));
            }
        });
    }

    private void drawBoundingBox(org.bytedeco.opencv.opencv_core.Mat mat, com.google.zxing.ResultPoint[] points) {
        if (points == null || points.length < 2) return;

        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = 0, maxY = 0;

        for (com.google.zxing.ResultPoint p : points) {
            if (p.getX() < minX) minX = p.getX();
            if (p.getY() < minY) minY = p.getY();
            if (p.getX() > maxX) maxX = p.getX();
            if (p.getY() > maxY) maxY = p.getY();
        }

        org.bytedeco.opencv.global.opencv_imgproc.rectangle(
                mat,
                new org.bytedeco.opencv.opencv_core.Point((int) minX - 10, (int) minY - 10),
                new org.bytedeco.opencv.opencv_core.Point((int) maxX + 10, (int) maxY + 10),
                new org.bytedeco.opencv.opencv_core.Scalar(0, 255, 0, 0),
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
                    lastDetectedPoints = result.getResultPoints();
                    lastDetectionTime = System.currentTimeMillis();

                    if (result.getText().length() > 5) {
                        String scannedCode = result.getText();
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
                frameToScan.close(); // Giải phóng rác
                isScanning = false;
            }
        });
    }

    private void processNewOrder(String trackingCode) {
        if (trackingCode.isEmpty()) return;

        if (videoRecorder.isRecording() || isFlushingVideo) {
            stopCurrentRecording(() -> startNewRecordingLogic(trackingCode));
        } else {
            startNewRecordingLogic(trackingCode);
        }
    }

    private void startNewRecordingLogic(String trackingCode) {
        systemStartNano = -1; // Reset lại đồng hồ
        currentOrder = new OrderRecord(trackingCode);

        Platform.runLater(() -> {
            view.currentCodeDisplay.setText(trackingCode);
            updateUiStatus("ĐANG GHI HÌNH: " + trackingCode, "#ef4444");
            view.scanIndicator.setText("Mắt AI: Đang dò liên tục...");
            view.scanIndicator.setTextFill(Color.web("#3b82f6"));
            view.barcodeInput.clear();
        });

        videoRecorder.startDualRecording(currentOrder, camWidth, camHeight, camHeight, camWidth, isPanoWorking, isQrWorking);

        int stopIndex = view.autoStopSelect.getSelectionModel().getSelectedIndex();
        if (stopIndex != 3) {
            int delayMs = stopIndex == 0 ? 60000 : (stopIndex == 1 ? 90000 : 120000);
            String codeAtStart = trackingCode;

            timerThreadPool.schedule(() -> {
                if (videoRecorder.isRecording() && currentOrder != null && currentOrder.getTrackingCode().equals(codeAtStart)) {
                    stopCurrentRecording();
                }
            }, delayMs, TimeUnit.MILLISECONDS);
        }
    }

    private void stopCurrentRecording(Runnable onComplete) {
        if (videoRecorder.isRecording()) {
            final OrderRecord finishedOrder = currentOrder;
            if (finishedOrder != null) {
                lastFinishedCode = finishedOrder.getTrackingCode();
            }

            currentOrder = null;
            Platform.runLater(() -> {
                updateUiStatus("SẴN SÀNG QUÉT MÃ", "#10b981");
                view.currentCodeDisplay.setText("---");
                view.scanIndicator.setText("Mắt AI: Đang dò mã...");
                view.scanIndicator.setTextFill(Color.web("#3b82f6"));
            });

            isFlushingVideo = true;

            new Thread(() -> {
                videoRecorder.stopRecording();

                if (finishedOrder != null) {
                    apiSyncService.syncOrderData(finishedOrder).thenAccept(success -> {
                        if (success) System.out.println("Đã đồng bộ đơn: " + finishedOrder.getTrackingCode());
                    });
                }

                isFlushingVideo = false;
                if (onComplete != null) {
                    onComplete.run();
                }
            }).start();
        } else {
            if (onComplete != null) onComplete.run();
        }
    }

    private void stopCurrentRecording() {
        stopCurrentRecording(null);
    }

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

        String dir = System.getProperty("user.dir") + java.io.File.separator + "videos" + java.io.File.separator;
        java.io.File filePano = new java.io.File(dir + code + "_pano.mp4");
        java.io.File fileQr = new java.io.File(dir + code + "_qr.mp4");

        if (filePano.exists() || fileQr.exists()) {
            view.pbTitle.setText("MÃ ĐƠN: " + code);

            long lastModified = filePano.exists() ? filePano.lastModified() : fileQr.lastModified();
            view.pbTime.setText("🕒 Đóng gói: " + videoTimeFormat.format(lastModified));

            long sizeMB = ((filePano.exists() ? filePano.length() : 0) + (fileQr.exists() ? fileQr.length() : 0)) / (1024 * 1024);
            view.pbSize.setText("💾 Tổng dung lượng: " + sizeMB + " MB");
            view.pathBoxContainer.getChildren().clear();

            if (filePano.exists()) {
                view.pathBoxContainer.getChildren().add(createFileLink("📹 Cam Toàn Cảnh", filePano));
            }
            if (fileQr.exists()) {
                view.pathBoxContainer.getChildren().add(createFileLink("📱 Cam Quét Mã", fileQr));
            }
            java.io.File fileMerged = new java.io.File(dir + code + "_merged.mp4");
            if (fileMerged.exists()) {
                view.pathBoxContainer.getChildren().add(createFileLink("✂️ File Đã Ghép", fileMerged));
            }

            view.liveViewPane.setVisible(false);
            view.playbackPane.setVisible(true);

            disposePlayers();

            if (filePano.exists()) {
                playerPano = new javafx.scene.media.MediaPlayer(new javafx.scene.media.Media(filePano.toURI().toString()));
                view.searchMediaViewPano.setMediaPlayer(playerPano);
                view.searchMediaViewPano.getParent().setVisible(true);
            } else {
                view.searchMediaViewPano.getParent().setVisible(false);
            }

            if (fileQr.exists()) {
                playerQr = new javafx.scene.media.MediaPlayer(new javafx.scene.media.Media(fileQr.toURI().toString()));
                view.searchMediaViewQr.setMediaPlayer(playerQr);
                view.searchMediaViewQr.getParent().setVisible(true);
            } else {
                view.searchMediaViewQr.getParent().setVisible(false);
            }

            javafx.scene.media.MediaPlayer masterPlayer = playerPano != null ? playerPano : playerQr;

            if (masterPlayer != null) {
                masterPlayer.setOnReady(() -> {
                    if (masterPlayer != null) {
                        view.timeSlider.setMax(masterPlayer.getTotalDuration().toSeconds());
                    }
                });

                masterPlayer.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
                    if (masterPlayer != null) {
                        if (!view.timeSlider.isValueChanging()) {
                            view.timeSlider.setValue(newTime.toSeconds());
                        }
                        String currentStr = formatTime(newTime.toSeconds());
                        String totalStr = formatTime(masterPlayer.getTotalDuration().toSeconds());
                        view.timeLabel.setText(currentStr + " / " + totalStr);
                    }
                });

                masterPlayer.setOnEndOfMedia(() -> {
                    view.playPauseBtn.setText("🔄 Phát lại");
                    if (playerPano != null) playerPano.pause();
                    if (playerQr != null) playerQr.pause();
                });

                view.playPauseBtn.setText("⏸ Tạm dừng");
                if (playerPano != null) playerPano.play();
                if (playerQr != null) playerQr.play();
            }

        } else {
            Alert a = new Alert(Alert.AlertType.WARNING, "Không tìm thấy video nào cho mã: " + code);
            a.show();
        }
    }

    private javafx.scene.control.Hyperlink createFileLink(String title, java.io.File file) {
        javafx.scene.control.Hyperlink link = new javafx.scene.control.Hyperlink(title + " (" + file.getName() + ")");
        link.setTextFill(Color.web("#89b4fa"));
        link.setFont(javafx.scene.text.Font.font(13));
        link.setOnAction(e -> {
            try {
                Runtime.getRuntime().exec("explorer.exe /select,\"" + file.getAbsolutePath() + "\"");
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
        return link;
    }

    private void togglePlayback() {
        javafx.scene.media.MediaPlayer masterPlayer = playerPano != null ? playerPano : playerQr;
        if (masterPlayer == null) return;

        if (view.playPauseBtn.getText().contains("Phát lại")) {
            seekVideo(0);
            if (playerPano != null) playerPano.play();
            if (playerQr != null) playerQr.play();
            view.playPauseBtn.setText("⏸ Tạm dừng");
            return;
        }

        if (masterPlayer.getStatus() == javafx.scene.media.MediaPlayer.Status.PLAYING) {
            if (playerPano != null) playerPano.pause();
            if (playerQr != null) playerQr.pause();
            view.playPauseBtn.setText("▶ Phát");
        } else {
            if (playerPano != null) playerPano.play();
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
        if (playerPano != null) {
            playerPano.stop();
            playerPano.dispose();
            playerPano = null;
        }
        if (playerQr != null) {
            playerQr.stop();
            playerQr.dispose();
            playerQr = null;
        }
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

        javafx.scene.media.MediaPlayer masterPlayer = playerPano != null ? playerPano : playerQr;
        if (masterPlayer != null && masterPlayer.getStatus() == javafx.scene.media.MediaPlayer.Status.PLAYING) {
            togglePlayback();
        }

        OrderRecord tempRecord = new OrderRecord(code);
        String dir = System.getProperty("user.dir") + java.io.File.separator + "videos" + java.io.File.separator;
        tempRecord.setPanoVideoPath(dir + code + "_pano.mp4");
        tempRecord.setQrVideoPath(dir + code + "_qr.mp4");

        videoRecorder.mergeDualCamVideos(tempRecord, () -> {
            Platform.runLater(() -> {
                view.mergeVideoBtn.setText("✂️ Ghép thành 1 File");
                view.mergeVideoBtn.setDisable(false);

                java.io.File fileMerged = new java.io.File(tempRecord.getMergedVideoPath());
                if (fileMerged.exists()) {
                    boolean alreadyHasMerged = view.pathBoxContainer.getChildren().stream()
                            .filter(node -> node instanceof javafx.scene.control.Hyperlink)
                            .anyMatch(node -> ((javafx.scene.control.Hyperlink) node).getText().contains("File Đã Ghép"));
                    if (!alreadyHasMerged) {
                        view.pathBoxContainer.getChildren().add(createFileLink("✂️ File Đã Ghép", fileMerged));
                    }
                }

                Alert a = new Alert(Alert.AlertType.INFORMATION, "Ghép thành công! Đã lưu tại:\n" + tempRecord.getMergedVideoPath());
                a.show();
            });
        });
    }

    private void stopCameraSystem() {
        isSystemRunning = false;
        isPanoWorking = false;
        isQrWorking = false;

        stopCurrentRecording();
        panoCamManager.stopCamera();
        qrCamManager.stopCamera();

        view.qrContainer.setVisible(false);
        view.overlayText.setVisible(false);

        view.startCamBtn.setText("▶ BẮT ĐẦU GHI HÌNH");
        view.startCamBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10px; -fx-background-radius: 6px;");
        updateUiStatus("CHƯA KẾT NỐI", "#a6adc8");

        view.scanIndicator.setText("Mắt AI: Đang tắt");
        view.scanIndicator.setTextFill(Color.web("#cbd5e1"));

        view.cameraView.setImage(null);
        view.cameraQrView.setImage(null);
    }

    public void shutdown() {
        stopCameraSystem();
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
            } catch (Exception ignored) {
            }
        }).start();
    }
}