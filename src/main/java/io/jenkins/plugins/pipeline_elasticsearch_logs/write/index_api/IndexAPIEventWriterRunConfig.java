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
 * IndexAPIEventWriterConfig}.
 * <p>
 * Auth credential data is loaded and stored here so that it is also available
 * on remove agents.
 * </p>
 * <p>
 * The TLS truststore credential data is loaded and stored here so that it is
 * also available on remote agents.
 * </p>
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
        @Nonnull IndexAPIEventWriterConfig config
    ) {
        this.indexUrl = config.getIndexUrl();
        this.connectTimeoutMillis = config.getConnectTimeoutMillis();
        this.requestTimeoutMillis = config.getRequestTimeoutMillis();
        this.socketTimeoutMillis = config.getSocketTimeoutMillis();

        // credentials must be loaded here because they are not accessible on agents
        StandardUsernamePasswordCredentials authCredentials = config.getAuthCredentials();
        if (authCredentials != null && authCredentials.getUsername() != "") {
            this.username = authCredentials.getUsername();
            this.password = authCredentials.getPassword().getPlainText();
        } else {
            this.username = null;
            this.password = null;
        }

        // trust store must be loaded here to have it available on agents
        if (StringUtils.isNotBlank(config.getTrustStoreCredentialsId())) {
            this.trustStoreBytes = getTrustStoreBytes(config);
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

    private static byte[] getTrustStoreBytes(IndexAPIEventWriterConfig config) throws RuntimeException {
        if (!isTls(config.getIndexUrl())) return null;

        StandardCertificateCredentials trustStoreCredentials = config.getTrustStoreCredentials();
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
