package com.trioscope.chameleon.types;

import com.google.gson.Gson;
import com.trioscope.chameleon.ChameleonApplication;

import java.util.Arrays;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by rohitraghunathan on 6/28/15.
 */

@ToString
@Getter
@Builder
@Slf4j
public class WiFiNetworkConnectionInfo {
    private static Gson gson = new Gson();
    @NonNull
    private String SSID;
    @NonNull
    private String passPhrase;
    @NonNull
    private String serverIpAddress;
    @NonNull
    private Integer serverPort;
    @NonNull
    private String userName;
    @NonNull
    private byte[] certificate;

    public static WiFiNetworkConnectionInfo deserializeConnectionInfo(final byte[] bytes) {
        try {
            Inflater decompresser = new Inflater();
            decompresser.setInput(bytes);
            byte[] result = new byte[ChameleonApplication.CERTIFICATE_BUFFER_SIZE];
            int resultLength = decompresser.inflate(result);
            decompresser.end();
            return gson.fromJson(new String(result, 0, resultLength), WiFiNetworkConnectionInfo.class);
        } catch (Exception e) {
            log.error("Failed to deserialize connection info", e);
        }
        return null;
    }

    public static byte[] serializeConnectionInfo(final WiFiNetworkConnectionInfo connectionInfo) {
        String str = gson.toJson(connectionInfo);
        log.info("Uncompressed data length = {}", str.length());
        byte[] output = new byte[ChameleonApplication.CERTIFICATE_BUFFER_SIZE];
        Deflater compresser = new Deflater();
        compresser.setLevel(Deflater.BEST_COMPRESSION);
        compresser.setInput(str.getBytes());
        compresser.finish();
        int compressedDataLength = compresser.deflate(output);
        log.info("Compressed data length = {}", compressedDataLength);
        compresser.end();
        return Arrays.copyOfRange(output, 0, compressedDataLength);
    }
}
