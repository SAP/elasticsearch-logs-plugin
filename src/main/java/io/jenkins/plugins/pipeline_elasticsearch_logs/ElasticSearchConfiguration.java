package io.jenkins.plugins.pipeline_elasticsearch_logs;

import java.io.IOException;

import javax.annotation.CheckForNull;

import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.uniqueid.IdStore;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundSetter;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Run;
import io.jenkins.plugins.pipeline_elasticsearch_logs.runid.DefaultRunIdProvider;
import io.jenkins.plugins.pipeline_elasticsearch_logs.runid.RunIdProvider;
import io.jenkins.plugins.pipeline_elasticsearch_logs.write.ElasticSearchWriteAccess;

public class ElasticSearchConfiguration extends AbstractDescribableImpl<ElasticSearchConfiguration> {

    private static final int DEFAULT_SPLIT_MESSAGES_LONGER_THAN = 2000;

    private int splitMessagesLongerThan = DEFAULT_SPLIT_MESSAGES_LONGER_THAN;

    private Boolean saveAnnotations = true;

    private Boolean writeAnnotationsToLogFile = true;

    private RunIdProvider runIdProvider;

    private ElasticSearchWriteAccess elasticsearchWriteAccess;

    public RunIdProvider getRunIdProvider() {
        return runIdProvider;
    }

    @DataBoundSetter
    public void setRunIdProvider(RunIdProvider runIdProvider) {
        this.runIdProvider = runIdProvider;
    }

    public ElasticSearchWriteAccess getElasticsearchWriteAccess() {
        return elasticsearchWriteAccess;
    }

    @DataBoundSetter
    public void setElasticsearchWriteAccess(ElasticSearchWriteAccess elasticsearchWriteAccess) {
        this.elasticsearchWriteAccess = elasticsearchWriteAccess;
    }

    public int getSplitMessagesLongerThan() {
        return splitMessagesLongerThan;
    }

    @DataBoundSetter
    public void setSplitMessagesLongerThan(int chars) {
        this.splitMessagesLongerThan = chars;
    }

    public boolean isSaveAnnotations() {
        return saveAnnotations;
    }

    @DataBoundSetter
    public void setSaveAnnotations(boolean saveAnnotations) {
        this.saveAnnotations = saveAnnotations;
    }

    public boolean isWriteAnnotationsToLogFile() {
        return writeAnnotationsToLogFile;
    }

    @DataBoundSetter
    public void setWriteAnnotationsToLogFile(boolean writeAnnotationsToLogFile) {
        this.writeAnnotationsToLogFile = writeAnnotationsToLogFile;
    }

    // TODO This class is not serializable, so why do we need this?
    public Object readResolve() {
        if (splitMessagesLongerThan <= 0) {
            splitMessagesLongerThan = DEFAULT_SPLIT_MESSAGES_LONGER_THAN;
        }
        if (saveAnnotations == null) {
            saveAnnotations = true;
        }
        if (writeAnnotationsToLogFile == null) {
            writeAnnotationsToLogFile = true;
        }
        if (runIdProvider == null) {
            runIdProvider = new DefaultRunIdProvider("");
        }
        return this;
    }

    /**
     * Returns a serializable representation of the plugin configuration with credentials resolved.
     * Reason: on remote side credentials cannot be accessed by credentialsId, same for keystore.
     * That's why the values are transfered to remote.
     *
     * @return the ElasticSearchSerializableConfiguration
     * @throws IOException
     */
    public ElasticSearchRunConfiguration getRunConfiguration(Run<?, ?> run) throws IOException {
        return new ElasticSearchRunConfiguration(
            isSaveAnnotations(),
            getUniqueRunId(run),
            getRunIdProvider().getRunId(run),
            getWriteAccessFactory(),
            getSplitMessagesLongerThan(),
            isWriteAnnotationsToLogFile()
        );
    }

    // Can be overwritten in tests
    @CheckForNull
    @Restricted(NoExternalUse.class)
    protected SerializableSupplier<ElasticSearchWriteAccess> getWriteAccessFactory() throws IOException {
        return elasticsearchWriteAccess.getSupplier();
    }

    public static String getUniqueRunId(Run<?, ?> run) {
        String runId = IdStore.getId(run);
        if (runId == null) {
            IdStore.makeId(run);
            runId = IdStore.getId(run);
        }

        return runId;
    }

    @Extension
    @Symbol("elasticsearch")
    public static class DescriptorImpl extends Descriptor<ElasticSearchConfiguration> {
    }
}
