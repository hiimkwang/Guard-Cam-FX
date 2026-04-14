package com.oms.guardcam.util;

import javafx.scene.image.Image;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.JavaFXFrameConverter;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.opencv_core.Mat;

import java.awt.image.BufferedImage;

public class ImageUtils {
    // Lưu ý: Các converter của JavaCV không thread-safe,
    // nếu dùng đa luồng liên tục nên khởi tạo riêng bằng ThreadLocal hoặc new instance
    private static final ThreadLocal<JavaFXFrameConverter> fxConverter = ThreadLocal.withInitial(JavaFXFrameConverter::new);
    private static final ThreadLocal<Java2DFrameConverter> java2dConverter = ThreadLocal.withInitial(Java2DFrameConverter::new);
    private static final ThreadLocal<OpenCVFrameConverter.ToMat> matConverter = ThreadLocal.withInitial(OpenCVFrameConverter.ToMat::new);

    public static Image frameToFxImage(Frame frame) {
        if (frame == null) return null;
        return fxConverter.get().convert(frame);
    }

    public static BufferedImage frameToBufferedImage(Frame frame) {
        if (frame == null) return null;
        return java2dConverter.get().getBufferedImage(frame);
    }

    public static Mat frameToMat(Frame frame) {
        if (frame == null) return null;
        return matConverter.get().convert(frame);
    }

    public static Frame matToFrame(Mat mat) {
        if (mat == null) return null;
        return matConverter.get().convert(mat);
    }
}