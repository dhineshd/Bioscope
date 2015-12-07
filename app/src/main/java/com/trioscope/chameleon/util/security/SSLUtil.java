package com.trioscope.chameleon.util.security;

import com.trioscope.chameleon.aop.Timed;

import org.apache.commons.lang3.time.DateUtils;
import org.spongycastle.asn1.x500.X500Name;
import org.spongycastle.asn1.x509.SubjectPublicKeyInfo;
import org.spongycastle.cert.X509v3CertificateBuilder;
import org.spongycastle.cert.jcajce.JcaX509CertificateConverter;
import org.spongycastle.operator.ContentSigner;
import org.spongycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyManagementException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.util.Date;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import lombok.extern.slf4j.Slf4j;

/**
 * Created by dhinesh.dharman on 10/2/15.
 */
@Slf4j
public class SSLUtil {
    private static final String CERT_ALIAS = "bioscope_server_cert";
    private static final int ASYMMETRIC_PRIVATE_KEY_SIZE_BITS = 1024; // Sufficient since keys dont last beyond a session
    private static final int ASYMMETRIC_PRIVATE_KEY_SIZE_BITS_GOOD_ENOUGH = 512; // http://security.stackexchange.com/questions/4518/how-to-estimate-the-time-needed-to-crack-rsa-encryption
    private static final int SYMMETRIC_SECRET_KEY_SIZE_BITS = 256; // Recommended
    public static final int INITIAL_PASSWORD_LENGTH = 5;
    public static final String SSL_PROTOCOL = "TLSv1.2";

    static {
        Security.insertProviderAt(new org.spongycastle.jce.provider.BouncyCastleProvider(), 1);
    }

    /**
     * Create SSLServerSocket factory that can be used to create SSLServerSockets.
     *
     * @param privateKey
     * @param certificate
     * @return SSLServerSocketFactory
     */
    @Timed
    public static SSLServerSocketFactory createSSLServerSocketFactory(
            final PrivateKey privateKey,
            final X509Certificate certificate) {
        SSLServerSocketFactory sslServerSocketFactory = null;
        try {
            KeyStore keyStore = KeyStore.getInstance("BKS");
            keyStore.load(null, null); // create empty keystore
            keyStore.setKeyEntry(CERT_ALIAS, privateKey, null, new Certificate[]{certificate});

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, null);
            SSLContext sslContext = SSLContext.getInstance(SSL_PROTOCOL);
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
     *
     * @param certificate
     * @return SSLSocketFactory
     */
    @Timed
    public static SSLSocketFactory createSSLSocketFactory(final X509Certificate certificate, final PublicKey trustedPublicKey) {
        SSLSocketFactory sslSocketFactory = null;
        try {
            // Load the keyStore that includes self-signed cert as a "trusted" entry.
            KeyStore trustStore = KeyStore.getInstance("BKS");
            trustStore.load(null, null); // create empty truststore

            trustStore.setCertificateEntry(CERT_ALIAS, certificate);
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());

            // Create a trust manager that does not validate certificate chains
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            log.info("Getting accepted issuers");
                            return new X509Certificate[0];
                        }

                        public void checkClientTrusted(
                                java.security.cert.X509Certificate[] certs, String authType) {
                            log.info("Checking if client is trusted {}, {}", certs, authType);
                        }

                        public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) throws CertificateException {
                            boolean trusted = false;
                            log.info("Checking if server is trusted {}, {}", certs, authType);

                            if (trustedPublicKey == null) {
                                log.warn("Trusting all certificates since we weren't given a public key");
                                return;
                            }

                            for (int i = 0; i < certs.length; i++) {
                                try {
                                    certs[i].verify(trustedPublicKey);
                                    log.info("Verified certificate {} of {}", i + 1, certs.length);
                                    trusted = true;
                                    break;
                                } catch (SignatureException | InvalidKeyException | NoSuchAlgorithmException | NoSuchProviderException | CertificateException e) {
                                    log.warn("{}th Certificate {} not trusted", i + 1, certs[i]);
                                }
                            }

                            if (!trusted)
                                throw new CertificateException();
                        }
                    }
            };

            tmf.init(trustStore);
            SSLContext ctx = SSLContext.getInstance(SSL_PROTOCOL);
            ctx.init(null, trustAllCerts, null);
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

    @Timed
    public static byte[] serializeCertificateToByteArray(final X509Certificate certificate) {
        try {
            // Convert to byte array
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

    @Timed
    public static X509Certificate deserializeByteArrayToCertificate(final byte[] bytes) {
        try {
            // Convert to certificate
            ByteArrayInputStream bis = new ByteArrayInputStream(bytes, 0, bytes.length);
            ObjectInput in = new ObjectInputStream(bis);
            X509Certificate cert = (X509Certificate) in.readObject();
            bis.close();
            return cert;
        } catch (Exception e) {
            log.error("Failed to deserialize blob to get certificate", e);
        }
        return null;
    }

    @Timed
    public static X509Certificate generateCertificate(final KeyPair keyPair) {
        // Setting cert start date to 1 day ago and 1 day after current time to handle clock skew
        // when certificate is shared with other devices
        X509v3CertificateBuilder certBuilder = new X509v3CertificateBuilder(
                new X500Name("CN=localhost"),
                BigInteger.ONE,
                DateUtils.addDays(new Date(), -1), // not before
                DateUtils.addDays(new Date(), 1), // not after
                new X500Name("CN=localhost"),
                SubjectPublicKeyInfo.getInstance(keyPair.getPublic().getEncoded()));

        PrivateKey signingKey = keyPair.getPrivate();
        try {
            ContentSigner contentSigner = new JcaContentSignerBuilder("SHA1WithRSAEncryption")
                    .setProvider("BC").build(signingKey);
            X509Certificate cert = new JcaX509CertificateConverter().setProvider("BC").getCertificate(certBuilder
                    .build(contentSigner));

            log.info("Built certificate {}", cert);
            return cert;
        } catch (Exception e) {
            log.error("Failed to generate certificate for given keypair", e);
        }
        return null;
    }

    @Timed
    public static KeyPair createKeypair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(ASYMMETRIC_PRIVATE_KEY_SIZE_BITS);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            log.error("Failed to generate keypair", e);
        }
        return null;
    }

    @Timed
    public static PublicKey createPublicKey(BigInteger modulus, BigInteger exponent) {
        try {
            RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
            KeyFactory factory = KeyFactory.getInstance("RSA");
            PublicKey pub = factory.generatePublic(spec);
            return pub;
        } catch (NoSuchAlgorithmException e) {
            log.error("Failed to generate public key", e);
        } catch (InvalidKeySpecException e) {
            log.error("Failed to generate public key", e);
        }
        return null;
    }

    @Timed
    public static SecretKey createSymmetricKey() {
        try {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(SYMMETRIC_SECRET_KEY_SIZE_BITS);
            return generator.generateKey();
        } catch (NoSuchAlgorithmException e) {
            log.error("Failed to generate symmetric key", e);
        }
        return null;
    }
}
