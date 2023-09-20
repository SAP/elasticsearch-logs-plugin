package io.jenkins.plugins.pipeline_elasticsearch_logs;

import static io.jenkins.plugins.pipeline_elasticsearch_logs.ElasticSearchFieldNames.RUN_ID;
import static io.jenkins.plugins.pipeline_elasticsearch_logs.ElasticSearchFieldNames.TIMESTAMP;
import static io.jenkins.plugins.pipeline_elasticsearch_logs.ElasticSearchFieldNames.TIMESTAMP_FORMAT;
import static io.jenkins.plugins.pipeline_elasticsearch_logs.ElasticSearchFieldNames.TIMESTAMP_MILLIS;
import static io.jenkins.plugins.pipeline_elasticsearch_logs.ElasticSearchFieldNames.UID;

import java.io.IOException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.jenkinsci.remoting.SerializableOnlyOverRemoting;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import hudson.model.Run;
import io.jenkins.plugins.pipeline_elasticsearch_logs.utils.RunUtils;
import io.jenkins.plugins.pipeline_elasticsearch_logs.write.EventWriter;
import io.jenkins.plugins.pipeline_elasticsearch_logs.write.EventWriterRunConfig;
import net.sf.json.JSONObject;

/**
 * A serializable representation of the plugin configuration with credentials resolved.
 * Reason: on remote side credentials cannot be accessed by credentialsId, same for keystore.
 * That's why the values are transfered to remote.
 *
 * The factory method {@link #get(Run)} ensures that for the same run the same
 * instance is returned, i.e. instances are cached. This ensures that the
 * configuration used for processing a run stays unchanged even if the plugin
 * configuration is changed in parallel. In addition, all config consumers
 * processing the same run implicitly use the same instance of the {@link
 * EventWriter} factory, which then can ensure that not more than one writer is
 * used per run.
 *
 * When instances of this class are deserialized on remote agents, they get
 * deduplicated using the internal instance cache, so that the same
 * single-instance semantics applies here, too.
 */
@Restricted(NoExternalUse.class)
public class ElasticSearchRunConfiguration implements SerializableOnlyOverRemoting {

    private static final long serialVersionUID = 1L;

    private final boolean saveAnnotations;

    private final boolean writeAnnotationsToLogFile;

    private final String uniqueId;

    private final EventWriterRunConfig eventWriterConfig;

    private final String runIdJsonString;

    private final int splitMessagesLongerThan;

    protected ElasticSearchRunConfiguration(
        @Nonnull ElasticSearchConfiguration config,
        @Nonnull Run<?, ?> run
    ) throws IOException {
        this.saveAnnotations = config.isSaveAnnotations();
        this.writeAnnotationsToLogFile = config.isWriteAnnotationsToLogFile();
        this.uniqueId = RunUtils.getUniqueRunId(run);
        this.runIdJsonString = config.getRunIdProvider().getRunId(run).toString();
        this.splitMessagesLongerThan = config.getSplitMessagesLongerThan();
        this.eventWriterConfig = config.getEventWriterConfig().createRunConfig(run);
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

    public int getSplitMessagesLongerThan() {
        return splitMessagesLongerThan;
    }

    // TODO Move this method elsewhere, as this class should not be concerned with event creation
    public Map<String, Object> createData() {
        Map<String, Object> data = new LinkedHashMap<>();
        Instant now = Instant.now();
        data.put(TIMESTAMP, now.atZone(ZoneId.of("UTC")).format(TIMESTAMP_FORMAT));
        data.put(TIMESTAMP_MILLIS, now.toEpochMilli());
        data.put(RUN_ID, JSONObject.fromObject(runIdJsonString));
        data.put(UID, uniqueId);
        return data;
    }

    @Nonnull
    public EventWriter createEventWriter() {
        return this.eventWriterConfig.createEventWriter();
    }

    protected Object readResolve() throws ObjectStreamException {
        // deduplicate after deserialization using the factory cache
        // especially required to ensure single instances on remote agents
        return factory.registerAndDedup(this);
    }

    /*
     * Returns the configuration to be used for the given run.
     *
     * The returned instance is cached and will be returned by subsequent
     * calls with the same run.
     *
     * After <code>run</code> has finished, <code>release</code>
     * MUST be called to clean up the cache.
     */
    @CheckForNull
    public static ElasticSearchRunConfiguration get(Run<?, ?> run) throws IOException {
        return factory.getInstance(run);
    }

    /*
     * Removes the cache entry for the given run.
     */
    public static void release(Run<?, ?> run) {
        factory.release(run);
    }

    private static final Factory factory = new Factory();

    /*
     * Factory keeps track of ElasticSearchRunConfiguration instances to ensure
     * that multiple users always get the same instance for the same run.
     *
     * On the Jenkins Controller cached entries MUST be removed after the run
     * is no longer handled.
     *
     * Deserialization on remote agents adds instances to the cache, but never
     * removes them because there is no signal in a remote agent JVM indicating
     * that a run is no longer processed.
     * Long-running agents thereby have a memory leak.
     *
     * TODO Fix memory leak
     *
     * Idea: Call back via Jenkins remoting to the factory on the Jenkins
     * controller. Remove all entries which are not present there.
     */
    private static final class Factory {
        private final Map<String, ElasticSearchRunConfiguration> cache =
            new HashMap<String, ElasticSearchRunConfiguration>();

        protected synchronized ElasticSearchRunConfiguration getInstance(Run<?, ?> run) throws IOException {
            String runId = RunUtils.getUniqueRunId(run);

            if (this.cache.containsKey(runId)) {
                // value may be null, i.e. when a config for the run has been
                // requested the first time, the logs plugin was disabled
                // (not configured).
                return this.cache.get(runId);
            }

            ElasticSearchRunConfiguration instance = newInstanceOrNull(run);
            this.cache.put(runId, instance);
            return instance;
        }

        protected ElasticSearchRunConfiguration newInstanceOrNull(Run<?, ?> run) throws IOException {
            ElasticSearchConfiguration config = ElasticSearchGlobalConfiguration.get().getElasticSearch();
            if (config == null) {
                // Logging to Elasticsearch is disabled
                return null;
            }
            return new ElasticSearchRunConfiguration(config, run);
        }

        protected synchronized ElasticSearchRunConfiguration registerAndDedup(
            @Nonnull ElasticSearchRunConfiguration newInstance
        ) {
            String runId = newInstance.getUniqueId();

            if (this.cache.containsKey(runId)) {
                return this.cache.get(runId);
            }

            this.cache.put(runId, newInstance);
            return newInstance;
        }

        protected synchronized void release(Run<?, ?> run) {
            if (run != null) {
                this.cache.remove(RunUtils.getUniqueRunId(run));
            }
        }
    }
}
