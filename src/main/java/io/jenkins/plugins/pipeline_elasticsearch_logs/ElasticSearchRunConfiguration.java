package io.jenkins.plugins.pipeline_elasticsearch_logs;

import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.ZoneId;
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

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSSX");

    private static final long serialVersionUID = 1L;

    private final String username;

    private final String password;

    private final URI uri;

    private final boolean saveAnnotations;

    private final boolean writeAnnotationsToLogFile;

    private final String uniqueId;

    private final SerializableSupplier<ElasticSearchWriteAccess> writeAccessFactory;

    private final String runIdJsonString;

    private final int splitMessagesLongerThan;

    public ElasticSearchRunConfiguration(URI uri, String username, String password, boolean saveAnnotations,
            String uniqueId, JSONObject runId, SerializableSupplier<ElasticSearchWriteAccess> writeAccessFactory,
            int splitMessagesLongerThan, boolean writeAnnotationsToLogFile) {
        super();
        this.uri = uri;
        this.username = username;
        this.password = password;
        this.runIdJsonString = runId.toString();
        this.uniqueId = uniqueId;
        this.writeAccessFactory = writeAccessFactory;
        this.splitMessagesLongerThan = splitMessagesLongerThan;
        this.saveAnnotations = saveAnnotations;
        this.writeAnnotationsToLogFile = writeAnnotationsToLogFile;
    }

    public String getUniqueId() {
        return uniqueId;
    }

    public boolean isSaveAnnotations() {
        return saveAnnotations;
    }

    public boolean isWriteAnnotationsToLogFile() {
        return writeAnnotationsToLogFile;
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

    public int getSplitMessagesLongerThan() {
        return splitMessagesLongerThan;
    }

    public Map<String, Object> createData() {
        Map<String, Object> data = new LinkedHashMap<>();
        Date date = new Date();
        Instant now = Instant.now();
        data.put(TIMESTAMP, now.atZone(ZoneId.of("UTC")).format(TIMESTAMP_FORMAT));
        data.put(TIMESTAMP_MILLIS, now.toEpochMilli());
        data.put(RUN_ID, JSONObject.fromObject(runIdJsonString));
        data.put(UID, uniqueId);
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
