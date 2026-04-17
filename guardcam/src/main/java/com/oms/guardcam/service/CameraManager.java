package com.oms.guardcam.service;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;

public class CameraManager {
    private FFmpegFrameGrabber grabber;
    private volatile boolean isRunning = false;

    public interface FrameListener {
        void onFrameCaptured(Frame frame, long timestamp);

        void onError(Exception e);
    }

    public void startCamera(String camName, int width, int height, FrameListener listener) {
        isRunning = true;

        // Luồng background chịu trách nhiệm toàn bộ vòng đời của Camera
        Thread camThread = new Thread(() -> {
            try {
                if (camName.equals("0")) {
                    grabber = new FFmpegFrameGrabber("video=0");
                } else {
                    grabber = new FFmpegFrameGrabber("video=" + camName);
                }

                grabber.setFormat("dshow");
                grabber.setImageWidth(width);
                grabber.setImageHeight(height);
                grabber.setFrameRate(30);
                grabber.setOption("rtbufsize", "1024M");
                grabber.setVideoOption("input_format", "mjpeg");
                grabber.setVideoOption("vcodec", "mjpeg");

                grabber.start();

                while (isRunning) {
                    Frame frame = grabber.grab();
                    if (frame != null && listener != null) {
                        listener.onFrameCaptured(frame.clone(), grabber.getTimestamp());
                    }
                }
            } catch (Exception e) {
                if (listener != null) listener.onError(e);
            } finally {
                // TỰ ĐỘNG DỌN DẸP AN TOÀN TRONG LUỒNG RIÊNG
                // Xóa bỏ xung đột với UI Thread
                try {
                    if (grabber != null) {
                        grabber.stop();
                        grabber.release();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                grabber = null;
                isRunning = false;
            }
        });
        camThread.setDaemon(true);
        camThread.start();
    }

    public void stopCamera() {
        isRunning = false;
    }
}