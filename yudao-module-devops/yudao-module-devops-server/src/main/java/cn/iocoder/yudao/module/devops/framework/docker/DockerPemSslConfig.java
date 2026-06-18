package cn.iocoder.yudao.module.devops.framework.docker;

import cn.hutool.core.util.StrUtil;
import com.github.dockerjava.core.SSLConfig;
import com.github.dockerjava.core.util.CertificateUtils;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.StringReader;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.util.List;

/**
 * 基于内存 PEM 内容创建 Docker TLS SSLContext。
 */
public class DockerPemSslConfig implements SSLConfig {

    private static final String KEY_ALIAS = "docker-client";

    private final DockerEnvironmentConfig config;

    public DockerPemSslConfig(DockerEnvironmentConfig config) {
        this.config = config;
    }

    @Override
    public SSLContext getSSLContext() throws KeyManagementException, UnrecoverableKeyException,
            NoSuchAlgorithmException, KeyStoreException {
        try {
            KeyManagerFactory keyManagerFactory = buildKeyManagerFactory();
            TrustManagerFactory trustManagerFactory = buildTrustManagerFactory();
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagerFactory == null ? null : keyManagerFactory.getKeyManagers(),
                    trustManagerFactory == null ? null : trustManagerFactory.getTrustManagers(), new SecureRandom());
            return sslContext;
        } catch (KeyManagementException | UnrecoverableKeyException | NoSuchAlgorithmException
                 | KeyStoreException ex) {
            throw ex;
        } catch (Exception ex) {
            KeyStoreException keyStoreException = new KeyStoreException("Docker TLS PEM 配置解析失败");
            keyStoreException.initCause(ex);
            throw keyStoreException;
        }
    }

    private KeyManagerFactory buildKeyManagerFactory() throws Exception {
        if (StrUtil.isBlank(config.getClientCert()) || StrUtil.isBlank(config.getClientKey())) {
            return null;
        }
        List<Certificate> certificates = CertificateUtils.loadCertificates(new StringReader(config.getClientCert()));
        PrivateKey privateKey = CertificateUtils.loadPrivateKey(new StringReader(config.getClientKey()));
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null);
        keyStore.setKeyEntry(KEY_ALIAS, privateKey, new char[0], certificates.toArray(new Certificate[0]));
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, new char[0]);
        return keyManagerFactory;
    }

    private TrustManagerFactory buildTrustManagerFactory() throws Exception {
        if (StrUtil.isBlank(config.getCaCert())) {
            return null;
        }
        KeyStore trustStore = CertificateUtils.createTrustStore(new StringReader(config.getCaCert()));
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);
        return trustManagerFactory;
    }

}
