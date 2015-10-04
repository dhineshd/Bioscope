package com.trioscope.chameleon.util.security;

import android.content.Context;

import com.trioscope.chameleon.R;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import lombok.extern.slf4j.Slf4j;

/**
 * Created by dhinesh.dharman on 10/2/15.
 */
@Slf4j
public class SSLUtil {

    /**
     * Get initialized SSLServerSocket factory that can be used
     *  to create SSLServerSockets.
     * @param context
     * @return SSLServerSocketFactory
     */
    public static SSLServerSocketFactory getInitializedSSLServerSocketFactory(final Context context) {
        SSLServerSocketFactory sslServerSocketFactory = null;
        try {
            // Load the keyStore that includes self-signed cert as a "trusted" entry.
            KeyStore keyStore = KeyStore.getInstance("BKS");
            InputStream keyStoreInputStream = context.getResources().openRawResource(R.raw.chameleon_keystore);
            char[] password = "poiuyt".toCharArray();
            keyStore.load(keyStoreInputStream, password);
            keyStoreInputStream.close();
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, password);
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), null, new SecureRandom());
            sslServerSocketFactory = sslContext.getServerSocketFactory();
        } catch (IOException |
                NoSuchAlgorithmException |
                KeyStoreException |
                KeyManagementException |
                CertificateException |
                UnrecoverableKeyException e) {
            log.error("Failed to initialize SSL server socket factory", e);
        }
        return sslServerSocketFactory;
    }

    /**
     * Get initialized SSLServerSocket factory that can be used
     * to create SSLServerSockets.
     * @param context
     * @return SSLSocketFactory
     */
    public static SSLSocketFactory getInitializedSSLSocketFactory(final Context context) {
        SSLSocketFactory sslSocketFactory = null;
        try {
            // Load the keyStore that includes self-signed cert as a "trusted" entry.
            KeyStore trustStore = KeyStore.getInstance("BKS");
            InputStream trustStoreInputStream = context.getResources().openRawResource(R.raw.chameleon_truststore);
            trustStore.load(trustStoreInputStream, "poiuyt".toCharArray());
            trustStoreInputStream.close();
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, tmf.getTrustManagers(), null);
            sslSocketFactory = ctx.getSocketFactory();
        } catch (IOException |
                NoSuchAlgorithmException |
                KeyStoreException |
                KeyManagementException |
                CertificateException e) {
            log.error("Failed to initialize SSL socket factory", e);
        }
        return sslSocketFactory;
    }
}
