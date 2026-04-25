package com.oms.guardcam.service;

import com.oms.guardcam.model.OrderRecord;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;

import java.io.File;

public class VideoRecorder {
    private final String SAVE_DIR = System.getProperty("user.dir") + java.io.File.separator + "videos" + java.io.File.separator;
    private FFmpegFrameRecorder recorderPano;
    private FFmpegFrameRecorder recorderQr;
    private final Object lock = new Object();
    private final Object lockPano = new Object();
    private final Object lockQr = new Object();
    private final Object lockStatus = new Object();
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
//    private void setupLiveRecorder(FFmpegFrameRecorder r) {
//
//        r.setFormat("mp4");
//        r.setFrameRate(30);
//
//        // KÍCH HOẠT GPU: Thử dùng NVIDIA NVENC, nếu không có Card rời thì nó tự lùi về CPU
//        try {
//            r.setVideoCodecName("h264_nvenc");
//            r.setVideoOption("preset", "p4"); // Preset chuẩn, cân bằng của NVIDIA
//        } catch (Exception e) {
//            // Fallback về CPU nếu máy tính không có Card đồ họa tương thích
//            r.setVideoCodec(avcodec.AV_CODEC_ID_H264);
//            r.setVideoOption("preset", "veryfast");
//        }
//        // 2. GIỮ NGUYÊN ĐỘ NÉT CAO (CRF + Bitrate)
//        r.setVideoOption("crf", "18");
//        r.setVideoBitrate(0);
//        r.setGopSize(60);
//        r.setVideoOption("profile", "high");
//
//        // 3. TUYỆT ĐỐI KHÔNG DÙNG "zerolatency" KHI LƯU FILE MP4 LOCAL (Thủ phạm gây xước đĩa)
//        // r.setVideoOption("tune", "zerolatency"); <--- ĐÃ XÓA DÒNG NÀY
//
//        // 4. GIỮ GOP SIZE ĐỂ TẠO KHUNG CHUẨN ĐỀU ĐẶN
//        //r.setGopSize(30);
//
//        r.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
//    }

    // 1. CẤU HÌNH LÚC LIVE (Ưu tiên siêu nhẹ máy, không giật)
    // 1. CẤU HÌNH LÚC LIVE: Nét như xem trực tiếp
    // 1. CẤU HÌNH LÚC LIVE: Nét căng, mượt, không bao giờ xước
    private void setupLiveRecorder(FFmpegFrameRecorder r) {
        r.setFormat("mp4");
        r.setVideoCodec(org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264);
        r.setFrameRate(30);

        // ==========================================
        // THUỐC ĐẶC TRỊ ÉP CPU CHẠY SIÊU NHẸ
        // 1. Ép tốc độ nén lên mức nhanh nhất thế giới để không bị tràn RAM
        r.setVideoOption("preset", "ultrafast");

        // 2. Không cho phép FFmpeg ngâm khung hình để tính toán. Có ảnh nào lưu ngay ảnh đó!
        r.setVideoOption("tune", "zerolatency");

        // 3. Thay vì ép Bitrate tĩnh làm đứng ổ cứng, dùng CRF 25 (Siêu nhẹ máy, hình ảnh đủ xài)
        r.setVideoBitrate(0);
        r.setVideoOption("crf", "25");
        // ==========================================

        // TƯƠNG THÍCH JAVAFX & CHỐNG LỖI CHÉO
        r.setVideoOption("profile", "main");
        r.setVideoOption("movflags", "faststart");
        r.setMaxBFrames(0);
        r.setGopSize(30);
        r.setPixelFormat(org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P);
    }

    // 2. CẤU HÌNH LÚC MERGE: Ghép siêu tốc, giữ nguyên chất lượng
    private void setupMergeRecorder(FFmpegFrameRecorder r, double originalFps) {
        r.setFormat("mp4");
        r.setVideoCodec(org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264);
        r.setFrameRate(originalFps);

        // Lúc ghép video (chạy ngầm) thì để CRF 18 cho nét, vẫn dùng ultrafast để ghép trong vài giây
        r.setVideoOption("preset", "ultrafast");
        r.setVideoBitrate(0);
        r.setVideoOption("crf", "18");

        r.setVideoOption("profile", "main");
        r.setVideoOption("movflags", "faststart");
        r.setMaxBFrames(0);
        r.setGopSize(30);
        r.setPixelFormat(org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P);
    }
    // 1. SỬA HÀM PANO: Thêm biến captureTimeMillis
    public void recordPanoFrame(Frame frame, long hardwareTimestamp) {
        synchronized (lockPano) { // DÙNG KHÓA PANO
            if (isRecording && recorderPano != null && frame != null) {
                try {
                    recorderPano.setTimestamp(hardwareTimestamp);
                    recorderPano.record(frame);
                } catch (Exception ignored) {}
            }
        }
    }

    public void recordQrFrame(Frame frame, long hardwareTimestamp) {
        synchronized (lockQr) { // DÙNG KHÓA QR
            if (isRecording && recorderQr != null && frame != null) {
                try {
                    recorderQr.setTimestamp(hardwareTimestamp);
                    recorderQr.record(frame);
                } catch (Exception ignored) {}
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
            try (FFmpegFrameGrabber gPano = new FFmpegFrameGrabber(order.getPanoVideoPath());
                 FFmpegFrameGrabber gQr = new FFmpegFrameGrabber(order.getQrVideoPath())) {

                // XÓA hwaccel VÀ THAY BẰNG LỆNH ĐỌC FILE ĐA LUỒNG BẰNG CPU:
                gPano.setVideoOption("threads", "0"); // Tự động dùng toàn bộ nhân CPU
                gQr.setVideoOption("threads", "0");

                gPano.start();
                gQr.start();

                // Lấy chính xác tốc độ khung hình (FPS) từ video gốc
                double fps = gPano.getFrameRate();
                if (fps <= 0) fps = 30;

                org.bytedeco.javacv.FFmpegFrameRecorder r = new org.bytedeco.javacv.FFmpegFrameRecorder(order.getMergedVideoPath(), gPano.getImageWidth(), gPano.getImageHeight());

                // Gọi cấu hình LOSSLESS
                setupMergeRecorder(r, 30);
                r.start();

                org.bytedeco.javacv.OpenCVFrameConverter.ToMat convPano = new org.bytedeco.javacv.OpenCVFrameConverter.ToMat();
                org.bytedeco.javacv.OpenCVFrameConverter.ToMat convQr = new org.bytedeco.javacv.OpenCVFrameConverter.ToMat();

                org.bytedeco.javacv.Frame fPano = null;
                org.bytedeco.javacv.Frame fQr = null;

                org.bytedeco.opencv.opencv_core.Mat lastValidQrMat = null;
                org.bytedeco.opencv.opencv_core.Mat smallResized = new org.bytedeco.opencv.opencv_core.Mat();

                long timeStepMicro = 33333L; // 30 FPS
                long currentVideoTime = 0;

                try {
                    // SỬA LỖI Ở ĐÂY: Lấy Frame đầu tiên của 2 camera trước khi chạy vòng lặp
                    fPano = gPano.grabImage();
                    fQr = gQr.grabImage();

                    while (true) {
                        // 1. Tua Video Pano cho khớp thời gian
                        while (fPano != null && gPano.getTimestamp() < currentVideoTime) {
                            fPano = gPano.grabImage();
                        }

                        // Đảm bảo Pano không bị null (Hết video thì kết thúc)
                        if (fPano == null) break;

                        // 2. Tua Video QR cho khớp thời gian
                        while (fQr != null && gQr.getTimestamp() < currentVideoTime) {
                            fQr = gQr.grabImage();
                        }

                        // Tiến hành convert Pano
                        org.bytedeco.opencv.opencv_core.Mat matPano = convPano.convert(fPano);
                        if (matPano == null || matPano.isNull()) {
                            currentVideoTime += timeStepMicro;
                            continue;
                        }

                        // Tiến hành convert QR (Nếu Cam QR chưa hết hình)
                        if (fQr != null) {
                            org.bytedeco.opencv.opencv_core.Mat matQr = convQr.convert(fQr);
                            if (matQr != null && !matQr.isNull()) {
                                if (lastValidQrMat != null) lastValidQrMat.close();
                                lastValidQrMat = matQr.clone();
                            }
                        }

                        org.bytedeco.opencv.opencv_core.Mat matQrToUse = lastValidQrMat;

                        // Xử lý Resize và dán hình ảnh (PiP)
                        if (matQrToUse != null && !matQrToUse.isNull()) {
                            int smallW = matPano.cols() / 8;
                            int smallH = (matQrToUse.rows() * smallW) / matQrToUse.cols();

                            org.bytedeco.opencv.global.opencv_imgproc.resize(
                                    matQrToUse, smallResized,
                                    new org.bytedeco.opencv.opencv_core.Size(smallW, smallH),
                                    0, 0, org.bytedeco.opencv.global.opencv_imgproc.INTER_AREA
                            );

                            int posX = matPano.cols() - smallW - 20;
                            int posY = matPano.rows() - smallH - 20;

                            try (org.bytedeco.opencv.opencv_core.Mat roi = matPano.apply(new org.bytedeco.opencv.opencv_core.Rect(posX, posY, smallW, smallH))) {
                                smallResized.copyTo(roi);
                            }
                        }

                        // 4. Ghi frame đã ghép vào đúng mốc thời gian tĩnh
                       // r.setTimestamp(currentVideoTime);
                        r.record(convPano.convert(matPano));

                        // Tiến tới nhịp frame tiếp theo
                        currentVideoTime += timeStepMicro;
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