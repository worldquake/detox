package hu.detox.spring;

import hu.detox.Agent;
import hu.detox.utils.CollectionUtils;
import jakarta.annotation.PostConstruct;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.springframework.stereotype.Component;

import javax.net.ssl.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.stream.Stream;

@Component
public class DynamicTrustManager {
    private static final char[] DEF_PASS = "changeit".toCharArray();

    private int loadFolder(File folder, KeyStore customKs) throws KeyStoreException, CertificateException, IOException, NoSuchAlgorithmException {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        int count = 0;
        for (File file : folder.listFiles()) {
            if (file.getName().endsWith(".jks") || file.getName().endsWith(".keystore")) {
                // Load full keystore
                KeyStore ks = KeyStore.getInstance("JKS");
                try (InputStream is = new FileInputStream(file)) {
                    ks.load(is, DEF_PASS); // try default pass
                }
                Enumeration<String> aliases = ks.aliases();
                while (aliases.hasMoreElements()) {
                    String alias = aliases.nextElement();
                    customKs.setCertificateEntry("folder-" + count++,
                            ks.getCertificate(alias));
                }
            } else if (file.getName().endsWith(".p12") || file.getName().endsWith(".pfx")) {
                KeyStore ks = KeyStore.getInstance("PKCS12");
                try (InputStream is = new FileInputStream(file)) {
                    ks.load(is, DEF_PASS);
                }
                Enumeration<String> aliases = ks.aliases();
                while (aliases.hasMoreElements()) {
                    customKs.setCertificateEntry("folder-" + count++,
                            ks.getCertificate(aliases.nextElement()));
                }
            } else {
                // PEM / CRT / CER — any X.509 format
                try (InputStream is = new FileInputStream(file)) {
                    for (Certificate cert : cf.generateCertificates(is)) {
                        customKs.setCertificateEntry("folder-" + count++, cert);
                    }
                }
            }
            System.out.println("Loaded cert/keystore: " + file.getName());
        }
        return count;
    }

    @PostConstruct
    public void init() throws Exception {
        Collection<File> trust = Agent.getFiles("certs", DirectoryFileFilter.DIRECTORY);
        if (CollectionUtils.isEmpty(trust)) return;

        // Load default JDK truststore
        TrustManagerFactory defaultTmf = TrustManagerFactory
                .getInstance(TrustManagerFactory.getDefaultAlgorithm());
        defaultTmf.init((KeyStore) null);

        // Build custom keystore from folder
        KeyStore customKs = KeyStore.getInstance(KeyStore.getDefaultType());
        customKs.load(null, null);

        int count = 0;
        for (File dir : trust) {
            count += loadFolder(dir, customKs);
        }
        if (count == 0) return;

        // Merge default + custom trust managers
        TrustManagerFactory customTmf = TrustManagerFactory
                .getInstance(TrustManagerFactory.getDefaultAlgorithm());
        customTmf.init(customKs);

        X509TrustManager defaultTm = (X509TrustManager) defaultTmf.getTrustManagers()[0];
        X509TrustManager customTm = (X509TrustManager) customTmf.getTrustManagers()[0];

        X509TrustManager merged = new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
                return Stream.of(defaultTm.getAcceptedIssuers(), customTm.getAcceptedIssuers())
                        .flatMap(Arrays::stream)
                        .toArray(X509Certificate[]::new);
            }

            public void checkClientTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException {
                try {
                    defaultTm.checkClientTrusted(chain, authType);
                } catch (CertificateException e) {
                    customTm.checkClientTrusted(chain, authType);
                }
            }

            public void checkServerTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException {
                try {
                    defaultTm.checkServerTrusted(chain, authType);
                } catch (CertificateException e) {
                    customTm.checkServerTrusted(chain, authType);
                }
            }
        };

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new TrustManager[]{merged}, null);
        SSLContext.setDefault(sslContext);

        // Also set for HttpsURLConnection globally
        HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());

        System.out.println("Loaded " + count + " extra trusted certs from " + trust);
    }
}