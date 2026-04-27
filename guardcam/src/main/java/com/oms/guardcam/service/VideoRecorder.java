package com.oms.guardcam.service;

import com.oms.guardcam.model.OrderRecord;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;

import java.io.File;

public class VideoRecorder {
    private final String SAVE_DIR = System.getProperty("user.dir") + File.separator + "videos" + File.separator;
    private FFmpegFrameRecorder recorderPano;
    private FFmpegFrameRecorder recorderQr;

    private final Object lock = new Object();
    private final Object lockPano = new Object();
    private final Object lockQr = new Object();
    private volatile boolean isRecording = false;

    // Bộ đếm khung hình để đồng bộ
    private long panoFrameCount = 0;
    private long qrFrameCount = 0;
    private final long FRAME_TIME_MICRO = 33333L; // 30 FPS = 33333 micro-giây/frame

    public VideoRecorder() {
        new File(SAVE_DIR).mkdirs();
    }

    public void startDualRecording(OrderRecord order, int panoW, int panoH, int qrW, int qrH, boolean usePano, boolean useQr) {
        synchronized (lock) {
            try {
                File directory = new File(SAVE_DIR);
                if (!directory.exists()) {
                    directory.mkdirs();
                }
                order.setPanoVideoPath(SAVE_DIR + order.getTrackingCode() + "_pano.mp4");
                order.setQrVideoPath(SAVE_DIR + order.getTrackingCode() + "_qr.mp4");

                if (usePano) {
                    recorderPano = new FFmpegFrameRecorder(order.getPanoVideoPath(), panoW, panoH);
                    setupLiveRecorder(recorderPano);
                    recorderPano.start();
                } else {
                    recorderPano = null;
                }

                if (useQr) {
                    recorderQr = new FFmpegFrameRecorder(order.getQrVideoPath(), qrW, qrH);
                    setupLiveRecorder(recorderQr);
                    recorderQr.start();
                } else {
                    recorderQr = null;
                }

                // Reset bộ đếm khi bắt đầu quay đơn mới
                panoFrameCount = 0;
                qrFrameCount = 0;

                isRecording = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // 1. CẤU HÌNH LIVE: Nét căng cho máy mạnh + Tương thích tuyệt đối JavaFX
    private void setupLiveRecorder(FFmpegFrameRecorder r) {
        r.setFormat("mp4");
        r.setVideoCodec(org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264);
        r.setFrameRate(30);

        r.setVideoOption("preset", "fast");
        r.setVideoBitrate(0);
        r.setVideoOption("crf", "18"); // Giữ nguyên độ nét cao

        // FIX JAVAFX STUTTER: Ép về profile "main" và bật "fastdecode"
        r.setVideoOption("profile", "main");
        r.setVideoOption("tune", "fastdecode");
        r.setVideoOption("movflags", "faststart");

        r.setMaxBFrames(0); // Tắt B-frames để JavaFX không bị trễ khi tính toán hình ảnh
        r.setGopSize(60);
        r.setPixelFormat(org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P);
    }

    // 2. CẤU HÌNH MERGE
    private void setupMergeRecorder(FFmpegFrameRecorder r, double originalFps) {
        r.setFormat("mp4");
        r.setVideoCodec(org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264);
        r.setFrameRate(originalFps > 0 ? originalFps : 30);

        r.setVideoOption("preset", "fast");
        r.setVideoBitrate(0);
        r.setVideoOption("crf", "18");

        r.setVideoOption("profile", "main");
        r.setVideoOption("tune", "fastdecode");
        r.setVideoOption("movflags", "faststart");

        r.setMaxBFrames(0);
        r.setGopSize(30);
        r.setPixelFormat(org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P);
    }

    // =====================================================================
    // FIX ĐỨNG HÌNH: Dùng timestamp truyền thẳng từ Camera (Không tự gọi nanoTime nữa)
    // =====================================================================
    public void recordPanoFrame(Frame frame, Long hardwareTimestamp) {
        if (!isRecording || recorderPano == null || frame == null || hardwareTimestamp == null) return;
        synchronized (lockPano) {
            try {
                // Đếm xem với mốc thời gian này thì đáng lẽ video phải có bao nhiêu khung hình
                long expectedFrames = hardwareTimestamp / FRAME_TIME_MICRO;

                if (panoFrameCount > expectedFrames) {
                    return; // Camera gửi quá nhanh -> Bỏ qua
                }

                while (panoFrameCount <= expectedFrames) {
                    recorderPano.record(frame);
                    panoFrameCount++;
                }
            } catch (Exception ignored) {
            }
        }
    }

    public void recordQrFrame(Frame frame, Long hardwareTimestamp) {
        if (!isRecording || recorderQr == null || frame == null || hardwareTimestamp == null) return;
        synchronized (lockQr) {
            try {
                long expectedFrames = hardwareTimestamp / FRAME_TIME_MICRO;

                if (qrFrameCount > expectedFrames) {
                    return;
                }

                while (qrFrameCount <= expectedFrames) {
                    recorderQr.record(frame);
                    qrFrameCount++;
                }
            } catch (Exception ignored) {
            }
        }
    }

    public void stopRecording() {
        synchronized (lock) {
            if (!isRecording) return;
            isRecording = false;
        }

        Thread tPano = new Thread(() -> {
            synchronized (lockPano) {
                try {
                    if (recorderPano != null) {
                        recorderPano.stop();
                        recorderPano.release();
                        recorderPano = null;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        Thread tQr = new Thread(() -> {
            synchronized (lockQr) {
                try {
                    if (recorderQr != null) {
                        recorderQr.stop();
                        recorderQr.release();
                        recorderQr = null;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        tPano.start();
        tQr.start();

        try {
            // Lệnh này ép hệ thống phải đợi đóng file xong 100% mới chạy tiếp
            tPano.join();
            tQr.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    public void mergeDualCamVideos(OrderRecord order, Runnable onComplete) {
        order.setMergedVideoPath(SAVE_DIR + order.getTrackingCode() + "_merged.mp4");

        new Thread(() -> {
            try (FFmpegFrameGrabber gPano = new FFmpegFrameGrabber(order.getPanoVideoPath());
                 FFmpegFrameGrabber gQr = new FFmpegFrameGrabber(order.getQrVideoPath())) {

                gPano.setVideoOption("threads", "0");
                gQr.setVideoOption("threads", "0");

                gPano.start();
                gQr.start();

                double fps = gPano.getFrameRate();
                if (fps <= 0) fps = 30;

                org.bytedeco.javacv.FFmpegFrameRecorder r = new org.bytedeco.javacv.FFmpegFrameRecorder(order.getMergedVideoPath(), gPano.getImageWidth(), gPano.getImageHeight());

                setupMergeRecorder(r, fps);
                r.start();

                org.bytedeco.javacv.OpenCVFrameConverter.ToMat convPano = new org.bytedeco.javacv.OpenCVFrameConverter.ToMat();
                org.bytedeco.javacv.OpenCVFrameConverter.ToMat convQr = new org.bytedeco.javacv.OpenCVFrameConverter.ToMat();

                Frame fPano = null;
                Frame fQr = null;

                org.bytedeco.opencv.opencv_core.Mat lastValidQrMat = null;
                org.bytedeco.opencv.opencv_core.Mat smallResized = new org.bytedeco.opencv.opencv_core.Mat();

                try {
                    fPano = gPano.grabImage();
                    fQr = gQr.grabImage();

                    while (fPano != null) {
                        long currentPanoTs = gPano.getTimestamp();

                        org.bytedeco.opencv.opencv_core.Mat matPano = convPano.convert(fPano);
                        if (matPano == null || matPano.isNull()) {
                            fPano = gPano.grabImage();
                            continue;
                        }

                        while (fQr != null && gQr.getTimestamp() <= currentPanoTs) {
                            org.bytedeco.opencv.opencv_core.Mat matQr = convQr.convert(fQr);
                            if (matQr != null && !matQr.isNull()) {
                                if (lastValidQrMat != null) lastValidQrMat.close();
                                lastValidQrMat = matQr.clone();
                            }
                            fQr = gQr.grabImage();
                        }

                        if (lastValidQrMat != null && !lastValidQrMat.isNull()) {
                            int smallW = matPano.cols() / 6;
                            int smallH = (lastValidQrMat.rows() * smallW) / lastValidQrMat.cols();

                            org.bytedeco.opencv.global.opencv_imgproc.resize(
                                    lastValidQrMat, smallResized,
                                    new org.bytedeco.opencv.opencv_core.Size(smallW, smallH),
                                    0, 0, org.bytedeco.opencv.global.opencv_imgproc.INTER_AREA
                            );

                            int posX = matPano.cols() - smallW - 20;
                            int posY = matPano.rows() - smallH - 20;

                            try (org.bytedeco.opencv.opencv_core.Mat roi = matPano.apply(new org.bytedeco.opencv.opencv_core.Rect(posX, posY, smallW, smallH))) {
                                smallResized.copyTo(roi);
                            }
                        }

                        r.record(convPano.convert(matPano));
                        fPano = gPano.grabImage();
                    }
                } finally {
                    if (lastValidQrMat != null) lastValidQrMat.close();
                    if (smallResized != null) smallResized.close();
                }
                r.stop();
                if (onComplete != null) onComplete.run();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public boolean isRecording() {
        return isRecording;
    }
}