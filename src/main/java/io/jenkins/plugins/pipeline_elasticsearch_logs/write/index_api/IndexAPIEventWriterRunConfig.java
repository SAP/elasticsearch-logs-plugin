package io.jenkins.plugins.pipeline_elasticsearch_logs.write.index_api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectStreamException;
import java.net.URI;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.apache.commons.lang.StringUtils;

import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;

import io.jenkins.plugins.pipeline_elasticsearch_logs.write.EventWriter;
import io.jenkins.plugins.pipeline_elasticsearch_logs.write.EventWriterRunConfig;
import io.jenkins.plugins.pipeline_elasticsearch_logs.write.utils.SharedEventWriterFactory;

/**
 * A run-specific config for {@link IndexAPIEventWriter} created from a {@link
 * IndexAPIEventWriterGlobalConfig}.
 */
public class IndexAPIEventWriterRunConfig implements EventWriterRunConfig {

    private static final Logger LOGGER = Logger.getLogger(IndexAPIEventWriterRunConfig.class.getName());

    private final URI indexUrl;

    @CheckForNull
    private final Integer connectTimeoutMillis;

    @CheckForNull
    private final Integer requestTimeoutMillis;

    @CheckForNull
    private final Integer socketTimeoutMillis;

    @CheckForNull
    private final String username;

    @CheckForNull
    private final String password;

    @CheckForNull
    private final byte[] trustStoreBytes;

    private transient SharedEventWriterFactory sharedWriterFactory;

    IndexAPIEventWriterRunConfig(
        @Nonnull IndexAPIEventWriterGlobalConfig globalConfig
    ) {
        this.indexUrl = globalConfig.getIndexUrl();
        this.connectTimeoutMillis = globalConfig.getConnectTimeoutMillis();
        this.requestTimeoutMillis = globalConfig.getRequestTimeoutMillis();
        this.socketTimeoutMillis = globalConfig.getSocketTimeoutMillis();

        // credentials must be loaded here because they are not accessible on agents
        StandardUsernamePasswordCredentials authCredentials = globalConfig.getAuthCredentials();
        if (authCredentials != null && authCredentials.getUsername() != "") {
            this.username = authCredentials.getUsername();
            this.password = authCredentials.getPassword().getPlainText();
        } else {
            this.username = null;
            this.password = null;
        }

        // trust store must be loaded here to have it available on agents
        if (StringUtils.isNotBlank(globalConfig.getTrustStoreCredentialsId())) {
            this.trustStoreBytes = getTrustStoreBytes(globalConfig);
        }
        else {
            this.trustStoreBytes = null;
        }

        init();
    }

    private void init() {
        this.sharedWriterFactory = new SharedEventWriterFactory(
            () -> new IndexAPIEventWriter(this)
        );
    }

    protected Object readResolve() throws ObjectStreamException {
        init();
        return this;
    }

    @Nonnull
    public URI getIndexUrl() {
        return this.indexUrl;
    }

    @Nonnull
    public Integer getConnectTimeoutMillis() {
        return this.connectTimeoutMillis;
    }

    @Nonnull
    public Integer getRequestTimeoutMillis() {
        return this.requestTimeoutMillis;
    }

    @Nonnull
    public Integer getSocketTimeoutMillis() {
        return this.socketTimeoutMillis;
    }

    String getUsername() {
        return this.username;
    }

    String getPassword() {
        return this.password;
    }

    byte[] getTrustStoreBytes() {
        return this.trustStoreBytes;
    }

    private static byte[] getTrustStoreBytes(IndexAPIEventWriterGlobalConfig globalConfig) throws RuntimeException {
        if (!isTls(globalConfig.getIndexUrl())) return null;

        StandardCertificateCredentials trustStoreCredentials = globalConfig.getTrustStoreCredentials();
        if (trustStoreCredentials == null) {
            return null;
        }

        KeyStore trustStore = trustStoreCredentials.getKeyStore();
        ByteArrayOutputStream b = new ByteArrayOutputStream(2048);
        try {
            trustStore.store(b, "".toCharArray());
            return b.toByteArray();
        } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException e) {
            LOGGER.log(Level.WARNING, "Could not serialize trust store", e);
            throw new RuntimeException("Could not serialize trust store", e);
        }
    }

    private static boolean isTls(URI indexUrl) {
        String scheme = indexUrl.getScheme();
        return "https".equals(scheme);
    }

    @Override
    public EventWriter createEventWriter() {
        return this.sharedWriterFactory.createEventWriter();
    }
}
