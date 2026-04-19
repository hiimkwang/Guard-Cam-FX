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
                java.io.File directory = new java.io.File(SAVE_DIR);
                if (!directory.exists()) {
                    directory.mkdirs();
                }
                order.setPanoVideoPath(SAVE_DIR + order.getTrackingCode() + "_pano.mp4");
                order.setQrVideoPath(SAVE_DIR + order.getTrackingCode() + "_qr.mp4");

                if (usePano) {
                    recorderPano = new FFmpegFrameRecorder(order.getPanoVideoPath() , panoW, panoH);
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

        // 1. SỬA THÀNH 'veryfast': Đủ nhanh để không lag máy, nhưng nén kỹ hơn ultrafast để chống rỗ ảnh
        r.setVideoOption("preset", "veryfast");

        // 2. GIỮ NGUYÊN ĐỘ NÉT CAO (CRF + Bitrate)
        r.setVideoOption("crf", "18");
        r.setVideoBitrate(0);
        r.setGopSize(60);
        r.setVideoOption("profile", "high");

        // 3. TUYỆT ĐỐI KHÔNG DÙNG "zerolatency" KHI LƯU FILE MP4 LOCAL (Thủ phạm gây xước đĩa)
        // r.setVideoOption("tune", "zerolatency"); <--- ĐÃ XÓA DÒNG NÀY

        // 4. GIỮ GOP SIZE ĐỂ TẠO KHUNG CHUẨN ĐỀU ĐẶN
        //r.setGopSize(30);

        r.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
    }

    // 2. CẤU HÌNH GHÉP VIDEO: ÉP CHẤT LƯỢNG CAO NHẤT (LOSSLESS)
    private void setupMergeRecorder(FFmpegFrameRecorder r, double originalFps) {
        r.setVideoCodec(avcodec.AV_CODEC_ID_H264);
        r.setFormat("mp4");
        r.setFrameRate(originalFps); // Khớp chuẩn 100% FPS với video gốc
        r.setVideoOption("preset", "fast"); // Ép CPU nén kỹ, cẩn thận từng Pixel
        r.setVideoOption("crf", "14"); // 14 là ngưỡng video siêu nét không tì vết
        //r.setVideoQuality(0); // LỆNH BẮT BUỘC: Không cho phép FFmpeg tự động giảm chất lượng
        r.setVideoBitrate(10000000);
        r.setVideoOption("profile", "high");
        r.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
    }

    // 1. SỬA HÀM PANO: Thêm biến captureTimeMillis
    public void recordPanoFrame(Frame frame, long captureTimeMillis) {
        synchronized (lock) {
            if (isRecording && recorderPano != null && frame != null) {
                try {
                    // ĐÃ XÓA SẠCH LOGIC TÍNH TIMESTAMP (Không dùng setTimestamp nữa)
                    // FFmpeg sẽ tự động sắp xếp khung hình đều tăm tắp theo đúng 30 FPS

                    recorderPano.record(frame);
                } catch (Exception ignored) {
                }
            }
        }
    }

    // 2. SỬA HÀM QR: Thêm biến captureTimeMillis
    public void recordQrFrame(Frame frame, long captureTimeMillis) {
        synchronized (lock) {
            if (isRecording && recorderQr != null && frame != null) {
                try {
                    // Tương tự, để FFmpeg tự lo thời gian
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

                org.bytedeco.opencv.opencv_core.Mat lastValidQrMat = null;

                try {
                    while ((fPano = gPano.grabImage()) != null) {
                        org.bytedeco.opencv.opencv_core.Mat matPano = convPano.convert(fPano);
                        if (matPano == null || matPano.isNull()) continue;

                        org.bytedeco.javacv.Frame fQr = gQr.grabImage();
                        org.bytedeco.opencv.opencv_core.Mat matQr = null;

                        if (fQr != null) {
                            matQr = convQr.convert(fQr);
                        }

                        // Nếu chộp được ảnh QR chuẩn, cập nhật lại biến Cache
                        if (matQr != null && !matQr.isNull()) {
                            if (lastValidQrMat != null) lastValidQrMat.close(); // Dọn rác ảnh cũ
                            lastValidQrMat = matQr.clone(); // Lưu ảnh mới
                        }

                        // LẤY ẢNH QR ĐỂ VẼ: Nếu ảnh hiện tại bị null, lấy ảnh cũ ra dùng
                        org.bytedeco.opencv.opencv_core.Mat matQrToUse = (matQr != null && !matQr.isNull()) ? matQr : lastValidQrMat;

                        // Nếu video vừa mới bắt đầu mà vẫn chưa có ảnh QR nào, đành ghi nguyên Pano
                        if (matQrToUse == null || matQrToUse.isNull()) {
                            r.record(fPano);
                            continue;
                        }

                        // Kích thước ô QR: 1/8 màn hình
                        int smallW = matPano.cols() / 8;
                        int smallH = (matQrToUse.rows() * smallW) / matQrToUse.cols();

                        org.bytedeco.opencv.opencv_core.Mat smallResized = null;
                        org.bytedeco.opencv.opencv_core.Mat roi = null;

                        try {
                            smallResized = new org.bytedeco.opencv.opencv_core.Mat();
                            org.bytedeco.opencv.global.opencv_imgproc.resize(
                                    matQrToUse,
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
                            if (smallResized != null) smallResized.close();
                            if (roi != null) roi.close();
                        }
                    }
                } finally {
                    // Dọn dẹp RAM của ảnh Cache sau khi merge xong
                    if (lastValidQrMat != null) lastValidQrMat.close();
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