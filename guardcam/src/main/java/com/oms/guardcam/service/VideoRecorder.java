package com.oms.guardcam.service;

import com.oms.guardcam.model.OrderRecord;
import com.oms.guardcam.util.ImageUtils;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_core.Size;

import java.io.File;

public class VideoRecorder {
    private final String SAVE_DIR = "D:\\Quang-Wordspace\\Guard-Cam-FX\\videos\\";
    private FFmpegFrameRecorder recorderPano;
    private FFmpegFrameRecorder recorderQr;
    private final Object lock = new Object();
    private volatile boolean isRecording = false;

    public VideoRecorder() {
        new File(SAVE_DIR).mkdirs();
    }

    public void startDualRecording(OrderRecord order, int panoW, int panoH, int qrW, int qrH) {
        synchronized (lock) {
            try {
                order.setPanoVideoPath(SAVE_DIR + order.getTrackingCode() + "_pano.mp4");
                order.setQrVideoPath(SAVE_DIR + order.getTrackingCode() + "_qr.mp4");

                // Config Recorder Toàn cảnh
                recorderPano = new FFmpegFrameRecorder(order.getPanoVideoPath(), panoW, panoH);
                setupRecorder(recorderPano);
                recorderPano.start();

                // Config Recorder QR
                recorderQr = new FFmpegFrameRecorder(order.getQrVideoPath(), qrW, qrH);
                setupRecorder(recorderQr);
                recorderQr.start();

                isRecording = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void setupRecorder(FFmpegFrameRecorder r) {
        r.setVideoCodec(avcodec.AV_CODEC_ID_H264);
        r.setFormat("mp4");
        r.setFrameRate(30);
        r.setVideoOption("preset", "veryfast"); // Optimize for real-time CPU encoding
        r.setVideoOption("crf", "18"); // Đổi từ 23 về 18 hoặc 15 để video cực nét
        r.setVideoBitrate(15000000);   // Có thể tăng thêm bitrate lên 15Mbps
        r.setVideoOption("profile", "main");
        r.setVideoOption("level", "3.1");
        r.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
    }

    public void recordPanoFrame(Frame frame) {
        synchronized (lock) {
            if (isRecording && recorderPano != null) {
                try { recorderPano.record(frame); } catch (Exception ignored) {}
            }
        }
    }

    public void recordQrFrame(Frame frame) {
        synchronized (lock) {
            if (isRecording && recorderQr != null) {
                try { recorderQr.record(frame); } catch (Exception ignored) {}
            }
        }
    }

    public void stopRecording() {
        synchronized (lock) {
            if (!isRecording) return; // Chống Crash nếu vô tình gọi hàm này 2 lần
            isRecording = false;      // Ngắt cờ ngay lập tức để block mọi frame mới bay vào

            try {
                if (recorderPano != null) {
                    recorderPano.stop();
                    recorderPano.release();
                    recorderPano = null; // BẮT BUỘC CÓ: Xóa dấu vết con trỏ C++
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                if (recorderQr != null) {
                    recorderQr.stop();
                    recorderQr.release();
                    recorderQr = null; // BẮT BUỘC CÓ: Xóa dấu vết con trỏ C++
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // Tính năng chèn Picture-in-Picture khi xem lại
    public void mergeDualCamVideos(OrderRecord order, Runnable onComplete) {
        order.setMergedVideoPath(SAVE_DIR + order.getTrackingCode() + "_merged.mp4");

        new Thread(() -> {
            try (org.bytedeco.javacv.FFmpegFrameGrabber gPano = new org.bytedeco.javacv.FFmpegFrameGrabber(order.getPanoVideoPath());
                 org.bytedeco.javacv.FFmpegFrameGrabber gQr = new org.bytedeco.javacv.FFmpegFrameGrabber(order.getQrVideoPath())) {

                // 1. Ép chung hệ màu BGR24 để OpenCV chắc chắn cho phép vẽ đè
                gPano.setPixelFormat(org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_BGR24);
                gQr.setPixelFormat(org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_BGR24);

                gPano.start();
                gQr.start();

                org.bytedeco.javacv.FFmpegFrameRecorder r = new org.bytedeco.javacv.FFmpegFrameRecorder(order.getMergedVideoPath(), gPano.getImageWidth(), gPano.getImageHeight());
                setupRecorder(r);
                r.start();

                // =========================================================
                // FIX LỖI TÀNG HÌNH: TẠO 2 BỘ CONVERTER ĐỘC LẬP
                // Không dùng ImageUtils ở đây để tránh bị ghi đè bộ nhớ đệm
                // =========================================================
                org.bytedeco.javacv.OpenCVFrameConverter.ToMat convPano = new org.bytedeco.javacv.OpenCVFrameConverter.ToMat();
                org.bytedeco.javacv.OpenCVFrameConverter.ToMat convQr = new org.bytedeco.javacv.OpenCVFrameConverter.ToMat();

                org.bytedeco.javacv.Frame fPano;
                // Chỉ lấy hình ảnh (grabImage) để chống lỗi NullPointerException của âm thanh
                while ((fPano = gPano.grabImage()) != null) {
                    org.bytedeco.javacv.Frame fQr = gQr.grabImage();

                    if (fQr == null) {
                        r.record(fPano);
                        continue;
                    }

                    // Ép sang Mat qua 2 "ống" độc lập, không xâm phạm nhau
                    org.bytedeco.opencv.opencv_core.Mat matPano = convPano.convert(fPano);
                    org.bytedeco.opencv.opencv_core.Mat matQr = convQr.convert(fQr);

                    if (matPano == null || matQr == null || matPano.isNull() || matQr.isNull()) {
                        r.record(fPano);
                        continue;
                    }

                    // Thu nhỏ QR bằng 1/6 chiều rộng Pano
                    int smallW = matPano.cols() / 6;
                    int smallH = (matQr.rows() * smallW) / matQr.cols();

                    org.bytedeco.opencv.opencv_core.Mat smallResized = new org.bytedeco.opencv.opencv_core.Mat();
                    org.bytedeco.opencv.global.opencv_imgproc.resize(matQr, smallResized, new org.bytedeco.opencv.opencv_core.Size(smallW, smallH));

                    // Tọa độ góc phải dưới (cách lề 20px)
                    int posX = matPano.cols() - smallW - 20;
                    int posY = matPano.rows() - smallH - 20;

                    // Vẽ đè ảnh QR lên vùng (ROI) của ảnh Pano
                    org.bytedeco.opencv.opencv_core.Mat roi = matPano.apply(new org.bytedeco.opencv.opencv_core.Rect(posX, posY, smallW, smallH));
                    smallResized.copyTo(roi);

                    // Đóng gói lại bằng ống của Pano và lưu
                    r.record(convPano.convert(matPano));
                }
                r.stop();
                if (onComplete != null) onComplete.run();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
    public boolean isRecording() { return isRecording; }
}