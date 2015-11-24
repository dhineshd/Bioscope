package com.trioscope.chameleon.util.security;


/**
 * Created by phand on 11/24/15.
 */
public interface EncryptionUtil {
    public EncryptedValue encrypt(String unencrypted);

    public String decrypt(EncryptedValue encrypted);
}
