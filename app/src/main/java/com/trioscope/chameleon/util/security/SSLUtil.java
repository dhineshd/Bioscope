package com.trioscope.chameleon.util.security;

import org.apache.commons.lang3.time.DateUtils;
import org.spongycastle.jce.X509Principal;
import org.spongycastle.x509.X509V3CertificateGenerator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.KeyManagementException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by dhinesh.dharman on 10/2/15.
 */
@Slf4j
public class SSLUtil {

    static {
        Security.insertProviderAt(new org.spongycastle.jce.provider.BouncyCastleProvider(), 1);
    }

    @Getter
    public static X509Certificate serverCertificate;

    /**
     * Create SSLServerSocket factory that can be used to create SSLServerSockets.
     * @return SSLServerSocketFactory
     */
    public static SSLServerSocketFactory createSSLServerSocketFactory() {
        SSLServerSocketFactory sslServerSocketFactory = null;
        try {
            // Load the keyStore that includes self-signed cert as a "trusted" entry.
            KeyStore keyStore = KeyStore.getInstance("BKS");
            keyStore.load(null, null); // create empty keystore

            // Load key for new self-signed cert
            KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
            serverCertificate = generateCertificate(keyPair);
            // TODO : Change alias
            keyStore.setKeyEntry("new key", keyPair.getPrivate(), null, new Certificate[]{serverCertificate});

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, null);
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
     * Create SSLSocket factory that can be used to create SSLSockets.
     * @param certificate
     * @return SSLSocketFactory
     */
    public static SSLSocketFactory createSSLSocketFactory(final X509Certificate certificate) {
        SSLSocketFactory sslSocketFactory = null;
        try {
            // Load the keyStore that includes self-signed cert as a "trusted" entry.
            KeyStore trustStore = KeyStore.getInstance("BKS");
            trustStore.load(null, null); // create empty truststore
            // TODO : Change alias
            trustStore.setCertificateEntry("new key", certificate);
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
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


    public static byte[] serializeCertificateToByteArray(final X509Certificate certificate) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutput out = new ObjectOutputStream(bos);
            out.writeObject(certificate);
            byte[] data = bos.toByteArray();
            bos.close();
            return data;
        } catch (IOException e) {
            log.error("Failed to serialize certificate", e);
        }
        return null;
    }

    public static X509Certificate deserializeByteArrayToCertificate(final byte[] bytes) {
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
            ObjectInput in = new ObjectInputStream(bis);
            X509Certificate cert = (X509Certificate) in.readObject();
            bis.close();
            return cert;
        } catch (IOException  | ClassNotFoundException e) {
            log.error("Failed to deserialize blob to get certificate", e);
        }
        return null;
    }

    private static X509Certificate generateCertificate(KeyPair keyPair) {
        X509V3CertificateGenerator cert = new X509V3CertificateGenerator();
        cert.setSerialNumber(BigInteger.valueOf(1));   //or generate a random number
        cert.setSubjectDN(new X509Principal("CN=localhost"));  //see examples to add O,OU etc
        cert.setIssuerDN(new X509Principal("CN=localhost")); //same since it is self-signed
        cert.setPublicKey(keyPair.getPublic());
        // Setting cert start date to 1 day ago and 1 day after current time to handle clock skew
        // when certificate is shared with other devices
        cert.setNotBefore(DateUtils.addDays(new Date(), -1));
        cert.setNotAfter(DateUtils.addDays(new Date(), 1));
        cert.setSignatureAlgorithm("SHA1WithRSAEncryption");
        PrivateKey signingKey = keyPair.getPrivate();
        try {
            return cert.generate(signingKey, "BC");
        } catch (NoSuchAlgorithmException |
                NoSuchProviderException |
                SignatureException |
                InvalidKeyException |
                CertificateException e) {
            log.error("Failed to generate certificate for given keypair", e);
        }
        return null;
    }
}
