package com.trioscope.chameleon.util.security;

import android.util.Base64;

import com.trioscope.chameleon.aop.Timed;

import org.apache.commons.lang3.RandomStringUtils;

import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by phand on 11/24/15.
 */
@RequiredArgsConstructor
@Slf4j
public class AESEncryptionUtil implements EncryptionUtil {
    private final String key;
    private SecureRandom secureRandom = new SecureRandom();

    @Override
    @Timed
    public EncryptedValue encrypt(String unencrypted) {
        try {
            byte[] ivBytes = generateIV();
            IvParameterSpec iv = new IvParameterSpec(ivBytes);
            SecretKeySpec skeySpec = null;
            skeySpec = new SecretKeySpec(key.getBytes("UTF-8"), "AES");


            Cipher cipher = null;
            cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            cipher.init(Cipher.ENCRYPT_MODE, skeySpec, iv);

            byte[] encrypted = cipher.doFinal(unencrypted.getBytes());
            String encryptedStr = Base64.encodeToString(encrypted, Base64.DEFAULT);
            EncryptedValue value = new EncryptedValue();
            value.setIvAsStr(Base64.encodeToString(ivBytes, Base64.DEFAULT));
            value.setEncryptedStr(encryptedStr);

            return value;
        } catch (NoSuchAlgorithmException e) {
            log.error("AES algorithm not implemented on this device", e);
        } catch (NoSuchPaddingException e) {
            log.error("PKCS5 padding not implemented on this device", e);
        } catch (UnsupportedEncodingException e) {
            log.error("UTF-8 encoding exception on this device", e);
        } catch (IllegalBlockSizeException e) {
            log.error("Illegal AES block size", e);
        } catch (BadPaddingException e) {
            log.error("Bad AES padding", e);
        } catch (InvalidAlgorithmParameterException e) {
            log.error("Invalid AES parameter ", e);
        } catch (InvalidKeyException e) {
            log.error("Invalid AES key", e);
        }

        return null;
    }

    private byte[] generateIV() {
        byte[] iv = new byte[16];

        secureRandom.nextBytes(iv);

        return iv;
    }

    @Override
    @Timed
    public String decrypt(EncryptedValue encrypted) {
        try {
            byte[] ivBytes = Base64.decode(encrypted.getIvAsStr(), Base64.DEFAULT);
            byte[] encryptedBytes = Base64.decode(encrypted.getEncryptedStr(), Base64.DEFAULT);

            IvParameterSpec iv = new IvParameterSpec(ivBytes);
            SecretKeySpec skeySpec = new SecretKeySpec(key.getBytes("UTF-8"), "AES");

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            cipher.init(Cipher.DECRYPT_MODE, skeySpec, iv);

            byte[] original = cipher.doFinal(encryptedBytes);

            return new String(original);
        } catch (Exception e) {
            log.error("Unable to decrypt encryption values {}", encrypted, e);
        }

        return null;
    }

    @Override
    public String generateKey() {
        int keyLength = 12;
        return RandomStringUtils.randomAlphanumeric(keyLength);
    }
}
