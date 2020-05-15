package io.jenkins.plugins.pipeline_elasticsearch_logs;

import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import io.jenkins.plugins.pipeline_elasticsearch_logs.write.ElasticSearchWriteAccess;
import net.sf.json.JSONObject;

/**
 * A serializable representation of the plugin configuration with credentials resolved.
 * Reason: on remote side credentials cannot be accessed by credentialsId, same for keystore.
 * That's why the values are transfered to remote.
 */
@Restricted(NoExternalUse.class)
public class ElasticSearchRunConfiguration implements Serializable {
    private static final String UID = "uid";

    private static final String RUN_ID = "runId";

    private static final String TIMESTAMP_MILLIS = "timestampMillis";

    private static final String TIMESTAMP = "timestamp";

    private static final DateTimeFormatter UTC_MILLIS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX");

    private static final long serialVersionUID = 1L;

    private final String username;

    private final String password;

    private final URI uri;

    private final boolean saveAnnotations;

    private final String uid;

    private Supplier<ElasticSearchWriteAccess> writeAccessFactory;

    private final String runIdJsonString;

    public ElasticSearchRunConfiguration(URI uri, String username, String password, boolean saveAnnotations,
            String uid, JSONObject runId, Supplier<ElasticSearchWriteAccess> writeAccessFactory, int connectionTimeoutMillis) {
        super();
        this.uri = uri;
        this.username = username;
        this.password = password;
        this.runIdJsonString = runId.toString();
        this.uid = uid;
        this.writeAccessFactory = writeAccessFactory;
        this.saveAnnotations = saveAnnotations;
    }

    public String getUid() {
        return uid;
    }

    public boolean isSaveAnnotations() {
        return saveAnnotations;
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

    public Map<String, Object> createData() {
        Map<String, Object> data = new LinkedHashMap<>();
        Date date = new Date();
        data.put(TIMESTAMP, ZonedDateTime.now(ZoneOffset.UTC).format(UTC_MILLIS));
        data.put(TIMESTAMP_MILLIS, date.getTime());
        data.put(RUN_ID, JSONObject.fromObject(runIdJsonString));
        data.put(UID, uid);
        return data;
    }

    public ElasticSearchWriteAccess createWriteAccess() throws URISyntaxException {
        return writeAccessFactory.get();
    }
    
    public String[] getIndices() {
        String path = uri.getPath();
        while (path.startsWith("/"))
            path = path.substring(1);
        String[] splitPath = path.split("/");
        return new String[] { splitPath[0] };
    }

}
