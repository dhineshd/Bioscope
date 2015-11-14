package com.trioscope.chameleon.util;

import android.graphics.Bitmap;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.EnumMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

/**
 * Created by dhinesh.dharman on 11/9/15.
 */
@Slf4j
public class QRCodeUtil {
    private static final int WHITE = 0xFFFFFFFF;
    private static final int BLACK = 0xFF000000;

    public static Bitmap generateQRCode(final String str, final int width, final int height) {
        try {
            // Reduce padding
            Map<EncodeHintType, Object> hints = new EnumMap<EncodeHintType, Object>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 2); /* default = 4 */
            BitMatrix result = new QRCodeWriter().encode(str,
                    BarcodeFormat.QR_CODE, width, height, hints);
            int w = result.getWidth();
            int h = result.getHeight();
            int[] pixels = new int[w * h];
            for (int y = 0; y < h; y++) {
                int offset = y * w;
                for (int x = 0; x < w; x++) {
                    pixels[offset + x] = result.get(x, y) ? BLACK : WHITE;
                }
            }
            Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, width, 0, 0, w, h);
            return bitmap;
        } catch (Exception e) {
            log.error("Failed to create QR code", e);
        }
        return null;
    }
}
