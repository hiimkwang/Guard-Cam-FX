package com.oms.guardcam.service;

import com.oms.guardcam.model.OrderRecord;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;

import java.io.File;

public class VideoRecorder {
    private final String SAVE_DIR = System.getProperty("user.dir") + java.io.File.separator + "videos" + java.io.File.separator;
    private FFmpegFrameRecorder recorderPano;
    private FFmpegFrameRecorder recorderQr;
    private final Object lock = new Object();
    private volatile boolean isRecording = false;

    public VideoRecorder() {
        new File(SAVE_DIR).mkdirs();
    }

    public void startDualRecording(OrderRecord order, int panoW, int panoH, int qrW, int qrH, boolean usePano, boolean useQr) {
        synchronized (lock) {
            try {
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

                isRecording = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // 1. CẤU HÌNH QUAY TRỰC TIẾP: Cân bằng để không lag máy nhưng vẫn nét
    private void setupLiveRecorder(FFmpegFrameRecorder r) {
        r.setVideoCodec(avcodec.AV_CODEC_ID_H264);
        r.setFormat("mp4");
        r.setFrameRate(30);
        r.setVideoOption("preset", "veryfast"); // Tốc độ nén nhanh
        r.setVideoOption("crf", "18"); // Độ nét cao
        r.setVideoQuality(0); // LỆNH BẮT BUỘC: Xóa bỏ mọi giới hạn mờ ảnh do Bitrate
        r.setVideoOption("profile", "high");
        r.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
    }

    // 2. CẤU HÌNH GHÉP VIDEO: ÉP CHẤT LƯỢNG CAO NHẤT (LOSSLESS)
    private void setupMergeRecorder(FFmpegFrameRecorder r, double originalFps) {
        r.setVideoCodec(avcodec.AV_CODEC_ID_H264);
        r.setFormat("mp4");
        r.setFrameRate(originalFps); // Khớp chuẩn 100% FPS với video gốc
        r.setVideoOption("preset", "medium"); // Ép CPU nén kỹ, cẩn thận từng Pixel
        r.setVideoOption("crf", "14"); // 14 là ngưỡng video siêu nét không tì vết
        r.setVideoQuality(0); // LỆNH BẮT BUỘC: Không cho phép FFmpeg tự động giảm chất lượng
        r.setVideoOption("profile", "high");
        r.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
    }

    public void recordPanoFrame(Frame frame) {
        synchronized (lock) {
            if (isRecording && recorderPano != null) {
                try {
                    recorderPano.record(frame);
                } catch (Exception ignored) {
                }
            }
        }
    }

    public void recordQrFrame(Frame frame) {
        synchronized (lock) {
            if (isRecording && recorderQr != null) {
                try {
                    recorderQr.record(frame);
                } catch (Exception ignored) {
                }
            }
        }
    }

    public void stopRecording() {
        synchronized (lock) {
            if (!isRecording) return;
            isRecording = false;

            try {
                if (recorderPano != null) {
                    recorderPano.stop();
                    recorderPano.release();
                    recorderPano = null;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

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
    }

    public void mergeDualCamVideos(OrderRecord order, Runnable onComplete) {
        order.setMergedVideoPath(SAVE_DIR + order.getTrackingCode() + "_merged.mp4");

        new Thread(() -> {
            try (org.bytedeco.javacv.FFmpegFrameGrabber gPano = new org.bytedeco.javacv.FFmpegFrameGrabber(order.getPanoVideoPath());
                 org.bytedeco.javacv.FFmpegFrameGrabber gQr = new org.bytedeco.javacv.FFmpegFrameGrabber(order.getQrVideoPath())) {

                gPano.setPixelFormat(org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_BGR24);
                gQr.setPixelFormat(org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_BGR24);

                gPano.start();
                gQr.start();

                // Lấy chính xác tốc độ khung hình (FPS) từ video gốc
                double fps = gPano.getFrameRate();
                if (fps <= 0) fps = 30;

                org.bytedeco.javacv.FFmpegFrameRecorder r = new org.bytedeco.javacv.FFmpegFrameRecorder(order.getMergedVideoPath(), gPano.getImageWidth(), gPano.getImageHeight());

                // Gọi cấu hình LOSSLESS
                setupMergeRecorder(r, fps);
                r.start();

                org.bytedeco.javacv.OpenCVFrameConverter.ToMat convPano = new org.bytedeco.javacv.OpenCVFrameConverter.ToMat();
                org.bytedeco.javacv.OpenCVFrameConverter.ToMat convQr = new org.bytedeco.javacv.OpenCVFrameConverter.ToMat();

                org.bytedeco.javacv.Frame fPano;
                while ((fPano = gPano.grabImage()) != null) {
                    org.bytedeco.javacv.Frame fQr = gQr.grabImage();

                    if (fQr == null) {
                        r.record(fPano);
                        continue;
                    }

                    org.bytedeco.opencv.opencv_core.Mat matPano = convPano.convert(fPano);
                    org.bytedeco.opencv.opencv_core.Mat matQr = convQr.convert(fQr);

                    if (matPano == null || matQr == null || matPano.isNull() || matQr.isNull()) {
                        r.record(fPano);
                        continue;
                    }

                    // Kích thước ô QR: 1/8 màn hình
                    int smallW = matPano.cols() / 8;
                    int smallH = (matQr.rows() * smallW) / matQr.cols();

                    org.bytedeco.opencv.opencv_core.Mat smallResized = null;
                    org.bytedeco.opencv.opencv_core.Mat roi = null;

                    try {
                        smallResized = new org.bytedeco.opencv.opencv_core.Mat();
                        // Dùng INTER_AREA để khử răng cưa và giữ tối đa độ nét khi thu nhỏ hình ảnh quét QR
                        org.bytedeco.opencv.global.opencv_imgproc.resize(
                                matQr,
                                smallResized,
                                new org.bytedeco.opencv.opencv_core.Size(smallW, smallH),
                                0,
                                0,
                                org.bytedeco.opencv.global.opencv_imgproc.INTER_AREA
                        );

                        int posX = matPano.cols() - smallW - 20;
                        int posY = matPano.rows() - smallH - 20;

                        roi = matPano.apply(new org.bytedeco.opencv.opencv_core.Rect(posX, posY, smallW, smallH));
                        smallResized.copyTo(roi);

                        r.record(convPano.convert(matPano));
                    } finally {
                        // GIẢI PHÓNG RAM
                        if (smallResized != null) smallResized.close();
                        if (roi != null) roi.close();
                    }
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