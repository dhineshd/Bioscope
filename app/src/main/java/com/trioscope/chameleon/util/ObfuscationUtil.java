package com.trioscope.chameleon.util;

import android.util.Base64;

import java.io.UnsupportedEncodingException;

import lombok.extern.slf4j.Slf4j;

/**
 * Goal of obfuscation is to be same length and unintelligible, but not secure
 * <p/>
 * Main use is for QR code
 * <p/>
 * Created by phand on 12/15/15.
 */
@Slf4j
public class ObfuscationUtil {
    private static String ORIGIN_SET = "{}\"1234567890";
    private static String OBFUSCATED_SET = "\"0147258369{}";

    public static String obfuscate(String plaintext) {
        String obfuscated = ROT13(plaintext);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < obfuscated.length(); i++) {
            String c = obfuscated.substring(i, i + 1);
            int index = ORIGIN_SET.indexOf(c);
            if (index != -1) {
                sb.append(OBFUSCATED_SET.charAt(index));
            } else {
                sb.append(c);
            }
        }

        return sb.toString();
    }

    public static String unobfuscate(String obfuscated) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < obfuscated.length(); i++) {
            String c = obfuscated.substring(i, i + 1);
            int index = OBFUSCATED_SET.indexOf(c);
            if (index != -1) {
                sb.append(ORIGIN_SET.charAt(index));
            } else {
                sb.append(c);
            }
        }

        String unobfuscated = ROT13(sb.toString());

        return unobfuscated;
    }

    private static String ROT13(String input) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c >= 'a' && c <= 'm') c += 13;
            else if (c >= 'A' && c <= 'M') c += 13;
            else if (c >= 'n' && c <= 'z') c -= 13;
            else if (c >= 'N' && c <= 'Z') c -= 13;
            sb.append(c);
        }
        return sb.toString();
    }
}
