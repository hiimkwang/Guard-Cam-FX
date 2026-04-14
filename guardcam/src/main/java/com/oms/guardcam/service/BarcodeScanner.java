package com.oms.guardcam.service;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

public class BarcodeScanner {
    private MultiFormatReader barcodeReader;

    public BarcodeScanner() {
        barcodeReader = new MultiFormatReader();
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        // Ưu tiên Code 128 (phổ biến nhất cho vận đơn) và QR Code
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Arrays.asList(
                com.google.zxing.BarcodeFormat.CODE_128,
                com.google.zxing.BarcodeFormat.QR_CODE
        ));
        barcodeReader.setHints(hints);
    }

    public Result scan(BufferedImage image) {
        if (image == null) return null;

        try {
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
            return barcodeReader.decodeWithState(bitmap);
        } catch (Exception e) {
            return null; // Không tìm thấy mã
        } finally {
            barcodeReader.reset();
        }
    }
}