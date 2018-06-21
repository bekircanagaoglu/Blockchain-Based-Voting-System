/**
 * This class cited from with slight changes:
 * https://stackoverflow.com/questions/42342470/javax-net-ssl-sslhandshakeexception-no-cipher-suites-in-common-no-cipher-suites
 */

package Common.TLS;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;

public class SSLUtil {
    private static final String KEY_STORE_TYPE = "JKS";
    private static final String TRUST_STORE_TYPE = "JKS";
    private static final String KEY_MANAGER_TYPE = "SunX509";
    private static final String TRUST_MANAGER_TYPE = "SunX509";
    private static final String PROTOCOL = "TLS";

    private static SSLContext serverSSLCtx = null;
    private static SSLContext clientSSLCtx = null;

    public static SSLContext createServerSSLContext()
            throws CertificateException, UnrecoverableKeyException, NoSuchAlgorithmException,
            KeyStoreException, KeyManagementException, IOException {
        return createServerSSLContext("TLS/keystore.jks", "111111");
    }

    public static SSLContext createClientSSLContext()
            throws CertificateException, NoSuchAlgorithmException,
            KeyStoreException, KeyManagementException, IOException {
        return createClientSSLContext("TLS/truststore.jks", "111111");
    }

    public static SSLContext createServerSSLContext(final String keyStoreLocation,
                                                    final String keyStorePwd)
            throws KeyStoreException,
            NoSuchAlgorithmException,
            CertificateException,
            IOException,
            UnrecoverableKeyException,
            KeyManagementException {
        if (serverSSLCtx == null) {
            KeyStore keyStore = KeyStore.getInstance(KEY_STORE_TYPE);
            FileInputStream in = new FileInputStream(keyStoreLocation);
            keyStore.load(in, keyStorePwd.toCharArray());
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KEY_MANAGER_TYPE);
            keyManagerFactory.init(keyStore, keyStorePwd.toCharArray());
            serverSSLCtx = SSLContext.getInstance(PROTOCOL);
            serverSSLCtx.init(keyManagerFactory.getKeyManagers(), null, null);
            in.close();
        }

        return serverSSLCtx;
    }

    public static SSLContext createClientSSLContext(final String trustStoreLocation,
                                                    final String trustStorePwd)
            throws KeyStoreException,
            NoSuchAlgorithmException,
            CertificateException,
            IOException,
            KeyManagementException {
        if (clientSSLCtx == null) {
            KeyStore trustStore = KeyStore.getInstance(TRUST_STORE_TYPE);
            FileInputStream in = new FileInputStream(trustStoreLocation);
            trustStore.load(in, trustStorePwd.toCharArray());
            TrustManagerFactory trustManagerFactory =
                    TrustManagerFactory.getInstance(TRUST_MANAGER_TYPE);
            trustManagerFactory.init(trustStore);
            clientSSLCtx = SSLContext.getInstance(PROTOCOL);
            clientSSLCtx.init(null, trustManagerFactory.getTrustManagers(), null);
            in.close();
        }

        return clientSSLCtx;

    }

}