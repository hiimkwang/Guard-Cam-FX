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
            try (FFmpegFrameGrabber gPano = new FFmpegFrameGrabber(order.getPanoVideoPath());
                 FFmpegFrameGrabber gQr = new FFmpegFrameGrabber(order.getQrVideoPath())) {

                // 1. FIX LỖI MẤT HÌNH: Ép cả 2 video phải dùng chung hệ màu BGR24
                // Đảm bảo OpenCV 100% cho phép vẽ đè ảnh này lên ảnh kia
                gPano.setPixelFormat(org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_BGR24);
                gQr.setPixelFormat(org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_BGR24);

                gPano.start();
                gQr.start();

                FFmpegFrameRecorder r = new FFmpegFrameRecorder(order.getMergedVideoPath(), gPano.getImageWidth(), gPano.getImageHeight());
                setupRecorder(r);
                r.start();

                Frame fPano;
                // Dùng grabImage() để bỏ qua luồng âm thanh, chống lỗi Null Pointer
                while ((fPano = gPano.grabImage()) != null) {
                    Frame fQr = gQr.grabImage();

                    if (fQr == null) {
                        r.record(fPano);
                        continue;
                    }

                    Mat matPano = ImageUtils.frameToMat(fPano);
                    Mat matQr = ImageUtils.frameToMat(fQr);

                    if (matPano == null || matQr == null || matPano.isNull() || matQr.isNull()) {
                        r.record(fPano);
                        continue;
                    }

                    // Tính toán kích thước cam nhỏ (Bằng 1/4 bề ngang cam lớn)
                    int smallW = matPano.cols() / 4;
                    int smallH = (matQr.rows() * smallW) / matQr.cols();

                    // Thu nhỏ cam QR
                    Mat smallResized = new Mat();
                    opencv_imgproc.resize(matQr, smallResized, new Size(smallW, smallH));

                    // Xác định tọa độ góc dưới bên phải (cách lề 20px)
                    int posX = matPano.cols() - smallW - 20;
                    int posY = matPano.rows() - smallH - 20;

                    // 2. FIX LỖI MẤT HÌNH: Dùng .apply() để trỏ đúng vào vùng nhớ của ảnh Pano
                    Mat roi = matPano.apply(new Rect(posX, posY, smallW, smallH));

                    // Vẽ đè ảnh QR lên vùng nhớ đó
                    smallResized.copyTo(roi);

                    // Lưu frame đã được vẽ đè vào video mới
                    r.record(ImageUtils.matToFrame(matPano));
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