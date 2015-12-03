package com.trioscope.chameleon.types;

import android.util.Base64;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.annotations.Expose;
import com.trioscope.chameleon.ChameleonApplication;

import org.json.JSONException;
import org.json.JSONObject;
import org.spongycastle.crypto.params.RSAKeyParameters;
import org.spongycastle.jcajce.provider.asymmetric.rsa.BCRSAPublicKey;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StreamCorruptedException;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.security.PublicKey;
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
    private static final boolean SHOULD_COMPRESS = false;

    // Version will be used for handling backward incompatible
    // changes to message format
    public static final int CURRENT_VERSION = 1;
    public static final int X509_CERTIFICATE_TYPE = 1;

    private static Gson gson = new GsonBuilder()
            .registerTypeHierarchyAdapter(PublicKey.class, new PublicKeySerializer())
            .create();

    private int version;

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

    private int certificateType;

    private byte[] certificate;

    @NonNull
    private PublicKey certificatePublicKey;

    public static WiFiNetworkConnectionInfo deserializeConnectionInfo(final String base64Str) {
        String jsonStr = base64Str;
        if (SHOULD_COMPRESS) {
            try {
                byte[] bytes = Base64.decode(base64Str, Base64.DEFAULT);
                Inflater decompresser = new Inflater();
                decompresser.setInput(bytes);
                byte[] result = new byte[ChameleonApplication.CERTIFICATE_BUFFER_SIZE];
                int resultLength = decompresser.inflate(result);
                decompresser.end();
                jsonStr = new String(result, 0, resultLength);
            } catch (Exception e) {
                log.error("Failed to deserialize connection info", e);
                return null;
            }
        }
        return gson.fromJson(jsonStr, WiFiNetworkConnectionInfo.class);
    }

    public static String serializeConnectionInfo(final WiFiNetworkConnectionInfo connectionInfo) {
        String jsonString = gson.toJson(connectionInfo);
        if (SHOULD_COMPRESS) {
            log.info("Uncompressed data length = {}", jsonString.length());
            byte[] output = new byte[ChameleonApplication.CERTIFICATE_BUFFER_SIZE];
            Deflater compresser = new Deflater();
            compresser.setLevel(Deflater.BEST_COMPRESSION);
            compresser.setInput(jsonString.getBytes());
            compresser.finish();
            int compressedDataLength = compresser.deflate(output);
            log.info("Compressed data length = {}", compressedDataLength);
            compresser.end();
            byte[] compressedArr = Arrays.copyOfRange(output, 0, compressedDataLength);

            return Base64.encodeToString(compressedArr, Base64.DEFAULT);
        } else {
            return jsonString;
        }
    }

    public static String getSerializedPublicKey(PublicKey publicKey) {
        if (publicKey == null)
            return null;

        try (
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                ObjectOutputStream o = new ObjectOutputStream(b)) {
            o.writeObject(publicKey);
            byte[] res = b.toByteArray();

            return Base64.encodeToString(res, Base64.DEFAULT);
        } catch (IOException e) {
            log.warn("Unable to serialize public key");
            return null;
        }
    }

    public static PublicKey fromSerializedPublicKey(String serialized) {
        if (serialized == null)
            return null;

        byte[] bytes = Base64.decode(serialized, Base64.DEFAULT);

        try (
                ByteArrayInputStream bi = new ByteArrayInputStream(bytes);
                ObjectInputStream oi = new ObjectInputStream(bi)) {
            Object obj = oi.readObject();

            return (PublicKey) obj;
        } catch (IOException | ClassNotFoundException e) {
            log.warn("Unable to deserialize due to exception", e);
        }

        return null;
    }

    public String getSerializedPublicKey() {
        return WiFiNetworkConnectionInfo.getSerializedPublicKey(certificatePublicKey);
    }

    private static class PublicKeySerializer implements JsonSerializer<PublicKey>, JsonDeserializer<PublicKey> {
        @Override
        public JsonElement serialize(PublicKey src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(WiFiNetworkConnectionInfo.getSerializedPublicKey(src));
        }

        @Override
        public PublicKey deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return WiFiNetworkConnectionInfo.fromSerializedPublicKey(json.getAsJsonPrimitive().getAsString());
        }
    }

}
