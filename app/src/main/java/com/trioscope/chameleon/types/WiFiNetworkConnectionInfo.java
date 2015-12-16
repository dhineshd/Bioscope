package com.trioscope.chameleon.types;

import android.util.Base64;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.trioscope.chameleon.util.ObfuscationUtil;
import com.trioscope.chameleon.util.security.SSLUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;

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
    private static final boolean SHOULD_OBFUSCATE = true;

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

    // Password used for StartSession to ensure director that the client read the QR code.
    @NonNull
    private String initPass;

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

    public static WiFiNetworkConnectionInfo deserializeConnectionInfo(final String serializedStr) {
        String jsonStr = serializedStr;
        if (SHOULD_OBFUSCATE) {
            jsonStr = ObfuscationUtil.unobfuscate(serializedStr);
        }
        return gson.fromJson(jsonStr, WiFiNetworkConnectionInfo.class);
    }

    public static String serializeConnectionInfo(final WiFiNetworkConnectionInfo connectionInfo) {
        String jsonString = gson.toJson(connectionInfo);
        if (SHOULD_OBFUSCATE) {
            log.info("Before obfuscation, length = {}", jsonString.length());
            String obfuscated = ObfuscationUtil.obfuscate(jsonString);
            log.info("Obfuscated string is length = {}", obfuscated.length());
            return obfuscated;
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
        private Gson gson = new GsonBuilder().create();
        private static final String MODULUS_KEY = "m";
        private static final String EXPONENT_KEY = "e";

        @Override
        public JsonElement serialize(PublicKey src, Type typeOfSrc, JsonSerializationContext context) {
            if (src instanceof RSAPublicKey) {
                JsonObject obj = new JsonObject();

                String encodedModulus = Base64.encodeToString(((RSAPublicKey) src).getModulus().toByteArray(), Base64.DEFAULT);
                obj.addProperty(EXPONENT_KEY, ((RSAPublicKey) src).getPublicExponent());
                obj.addProperty(MODULUS_KEY, encodedModulus);

                return obj;
            } else {
                return null;
            }

            //return new JsonPrimitive(WiFiNetworkConnectionInfo.getSerializedPublicKey(src));
        }

        @Override
        public PublicKey deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            BigInteger exponent = obj.get(EXPONENT_KEY).getAsBigInteger();
            String encodedModulus = obj.get(MODULUS_KEY).getAsString();
            BigInteger modulus = new BigInteger(Base64.decode(encodedModulus, Base64.DEFAULT));

            return SSLUtil.createPublicKey(modulus, exponent);
            //return WiFiNetworkConnectionInfo.fromSerializedPublicKey(json.getAsJsonPrimitive().getAsString());
        }
    }

}
