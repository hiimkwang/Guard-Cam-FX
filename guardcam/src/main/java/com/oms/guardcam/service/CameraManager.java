package com.oms.guardcam.service;
import org.bytedeco.ffmpeg.global.avutil;
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

        Thread camThread = new Thread(() -> {
            try {
                if (camName.equals("0")) {
                    grabber = new FFmpegFrameGrabber("video=0");
                } else {
                    grabber = new FFmpegFrameGrabber("video=" + camName);
                }

                grabber.setFormat("dshow");
                // Ép phân giải
                grabber.setOption("video_size", width + "x" + height);
                grabber.setImageWidth(width);
                grabber.setImageHeight(height);
                grabber.setFrameRate(30);

                // GIỮ LẠI CÁC CẤU HÌNH TỐI ƯU CHỐNG DELAY TRƯỚC ĐÓ
                grabber.setOption("rtbufsize", "100M");
                grabber.setOption("fflags", "nobuffer");
                grabber.setOption("flags", "low_delay");

                grabber.setVideoOption("input_format", "mjpeg");
                grabber.setVideoOption("vcodec", "mjpeg");

                grabber.start();

                // VÒNG LẶP ĐÃ ĐƯỢC GIA CỐ CHỐNG SẬP
                while (isRunning) {
                    try {
                        Frame frame = grabber.grab();
                        if (frame != null && listener != null) {
                            // KHÔNG DÙNG frame.clone() NỮA ĐỂ CHỐNG TRÀN RAM NATIVE
                            listener.onFrameCaptured(frame, grabber.getTimestamp());
                        } else if (frame == null) {
                            // Nếu cam bị giật lag trả về null, nghỉ 10ms để USB phục hồi
                            Thread.sleep(10);
                        }
                    } catch (org.bytedeco.javacv.FrameGrabber.Exception e) {
                        // NẾU CÓ LỖI RỚT FRAME -> BỎ QUA VÀ THỬ LẠI, KHÔNG SẬP LUỒNG
                        System.err.println("Cảnh báo: Rớt frame ở cam " + camName + ", đang thử lại...");
                        Thread.sleep(15);
                    }
                }
            } catch (Exception e) {
                if (listener != null) listener.onError(e);
            } finally {
                // Tự động dọn dẹp an toàn
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