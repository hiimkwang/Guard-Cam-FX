package com.oms.guardcam.service;

import javafx.scene.image.Image;
import org.bytedeco.javacv.*;

public class CameraService {
    private FFmpegFrameGrabber grabber;
    private FFmpegFrameRecorder recorder;
    private boolean isRunning = false;
    private JavaFXFrameConverter fxConverter = new JavaFXFrameConverter();

    // Callback interfaces để gửi dữ liệu về Controller
    public interface FrameListener { void onFrame(Image fxImage); }
    public interface BarcodeListener { void onBarcodeDetected(String barcode); }

    public void startCamera(String camName, int width, int height, FrameListener frameListener, BarcodeListener barcodeListener) {
        isRunning = true;
        new Thread(() -> {
            try {
                grabber = new FFmpegFrameGrabber("video=" + camName);
                grabber.setFormat("dshow");
                grabber.setImageWidth(width);
                grabber.setImageHeight(height);
                grabber.setFrameRate(30);
                grabber.setOption("rtbufsize", "1024M"); // Chống tràn buffer
                grabber.setVideoOption("input_format", "mjpeg");
                grabber.setVideoOption("vcodec", "mjpeg");
                grabber.start();

                while (isRunning) {
                    Frame frame = grabber.grab();
                    if (frame == null) continue;

                    // TODO: Đưa logic BarcodeScanner và ghi Video vào đây (Tách từ file cũ)

                    // Convert frame và gửi về View
                    Image img = fxConverter.convert(frame);
                    if (img != null && frameListener != null) {
                        frameListener.onFrame(img);
                    }
                }
                grabber.stop();
                grabber.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void stopCamera() {
        isRunning = false;
        // Logic stop recorder...
    }
}