package io.jenkins.plugins.pipeline_elasticsearch_logs;

import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A serializable representation of the plugin configuration with credentials
 * resolved. Reason: on remote side credentials cannot be accessed by
 * credentialsId, same for keystore. That's why the values are transfered to
 * remote.
 */
@Restricted(NoExternalUse.class)
public class ElasticSearchRunConfiguration implements Serializable {
    private static final String UID = "uid";

    private static final String RUN_ID = "runId";

    private static final String TIMESTAMP_MILLIS = "timestampMillis";

    private static final String TIMESTAMP = "timestamp";

    private static final Logger LOGGER = Logger.getLogger(ElasticSearchRunConfiguration.class.getName());

    private static final DateTimeFormatter UTC_MILLIS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX");

    private static final long serialVersionUID = 1L;

    private final String username;

    private final String password;

    private final byte[] keyStoreBytes;

    private final URI uri;

    private transient KeyStore trustKeyStore;

    private final boolean saveAnnotations;

    private final String uid;
    private transient Supplier<ElasticSearchAccess> accessFactory;

    private final String runIdJsonString;

    private final boolean readLogsFromElasticsearch;

    public ElasticSearchRunConfiguration(URI uri, String username, String password, byte[] keyStoreBytes,
            boolean saveAnnotations, String uid, JSONObject runId, boolean readLogsFromElasticsearch,
            Supplier<ElasticSearchAccess> accessFactory) {
        super();
        this.uri = uri;
        this.username = username;
        this.password = password;
        this.runIdJsonString = runId.toString();
        this.uid = uid;
        this.accessFactory = accessFactory;
        if (keyStoreBytes != null) {
            this.keyStoreBytes = keyStoreBytes.clone();
        } else {
            this.keyStoreBytes = null;
        }
        this.saveAnnotations = saveAnnotations;
        this.readLogsFromElasticsearch = readLogsFromElasticsearch;
    }

    public String getUid() {
        return uid;
    }

    public boolean isSaveAnnotations() {
        return saveAnnotations;
    }

    public boolean isReadLogsFromElasticsearch() {
        return readLogsFromElasticsearch;
    }

    public URI getUri() {
        return uri;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public KeyStore getTrustKeyStore() {
        if (trustKeyStore == null && keyStoreBytes != null) {
            try {
                trustKeyStore = KeyStore.getInstance("PKCS12");
                trustKeyStore.load(new ByteArrayInputStream(keyStoreBytes), "".toCharArray());
            } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException e) {
                LOGGER.log(Level.WARNING, "Failed to create KeyStore from bytes", e);
            }
        }
        return trustKeyStore;
    }

    public Map<String, Object> createData() {
        Map<String, Object> data = new LinkedHashMap<>();
        Date date = new Date();
        data.put(TIMESTAMP, ZonedDateTime.now(ZoneOffset.UTC).format(UTC_MILLIS));
        data.put(TIMESTAMP_MILLIS, date.getTime());
        data.put(RUN_ID, JSONObject.fromObject(runIdJsonString));
        data.put(UID, uid);
        return data;
    }

    public ElasticSearchAccess createAccess() {
        if (accessFactory != null) {
            return accessFactory.get();
        } else {
            ElasticSearchAccess writer = new ElasticSearchAccess(getUri(), getUsername(), getPassword());
            if (getTrustKeyStore() != null) {
                writer.setTrustKeyStore(getTrustKeyStore());
            }
            return writer;
        }
    }

    public String[] getIndices() {
        String path = uri.getPath();
        while (path.startsWith("/"))
            path = path.substring(1);
        String[] splitPath = path.split("/");
        return new String[] { splitPath[0] };
    }

}
